package com.impca201.noter.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.impca201.noter.data.api.NotionClient
import com.impca201.noter.data.model.Block
import com.impca201.noter.data.model.BlockType
import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.store.CacheStore
import com.impca201.noter.data.store.SettingsStore
import com.impca201.noter.logic.DocumentEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

/**
 * Edits a note the way a person does (typing, Enter, ticking, changing kinds, deleting) while the
 * app keeps sending to a slow Notion that really keeps the blocks. Whatever the timing, Notion must
 * end up with exactly the note on the phone: nothing twice, nothing lost.
 */
class SyncStressTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notion = FakeNotion()

    @After
    fun tearDown() {
        scope.cancel()
        notion.close()
    }

    /** A page in Notion: its blocks in order, deleted ones kept (Notion archives them). */
    private class Page {
        data class B(val id: String, var type: String, var text: String, var checked: Boolean, var archived: Boolean = false)

        val blocks = mutableListOf<B>()
        private var next = 0

        fun live() = blocks.filter { !it.archived }

        fun json(b: B) =
            """{"object":"block","id":"${b.id}","type":"${b.type}","has_children":false,
               "${b.type}":{"checked":${b.checked},"rich_text":[{"type":"text","plain_text":${Json.encodeToString(b.text)},"text":{"content":${Json.encodeToString(b.text)}},"annotations":{}}]}}"""

        fun append(body: JsonObject): String {
            val children = body["children"]!!.jsonArray.map { it.jsonObject }
            val after = body["after"]?.jsonPrimitive?.content
            val atStart = body["position"]?.jsonObject?.get("type")?.jsonPrimitive?.content == "start"
            val added = children.map { child ->
                val type = child["type"]!!.jsonPrimitive.content
                val content = child[type]?.jsonObject
                B("b${++next}", type, richText(content?.get("rich_text") as? JsonArray), content?.get("checked")?.jsonPrimitive?.boolean ?: false)
            }
            val index = when {
                after != null -> blocks.indexOfFirst { it.id == after } + 1
                atStart -> 0
                else -> blocks.size
            }
            blocks.addAll(index, added)
            // Like Notion with an anchor: the new blocks, then the ones after them.
            val answer = if (after == null && !atStart) added else blocks.drop(index).filter { !it.archived }
            return FakeNotion.list(*answer.map(::json).toTypedArray())
        }

        fun update(id: String, body: JsonObject): Pair<Int, String> {
            val block = blocks.firstOrNull { it.id == id } ?: return 404 to "{}"
            if (block.archived) return 400 to FakeNotion.ARCHIVED
            val (type, content) = body.entries.first()
            if (type != block.type) return 400 to """{"object":"error","status":400,"message":"Block type mismatch"}"""
            block.text = richText(content.jsonObject["rich_text"] as? JsonArray)
            content.jsonObject["checked"]?.let { block.checked = it.jsonPrimitive.boolean }
            return 200 to json(block)
        }

        fun delete(id: String): Pair<Int, String> {
            val block = blocks.firstOrNull { it.id == id } ?: return 404 to "{}"
            if (block.archived) return 400 to FakeNotion.ARCHIVED
            block.archived = true
            return 200 to json(block)
        }

        private fun richText(array: JsonArray?): String =
            array.orEmpty().joinToString("") { it.jsonObject["text"]?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty() }
    }

    /** False: Notion can't insert at the start of a page (older API versions); the app then adds at the end. */
    private var startSupported = true

    private fun serve(page: Page, random: Random) {
        notion.respond = { sent ->
            // Notion is slow, so edits keep coming while a request is under way.
            Thread.sleep(random.nextLong(5, 60))
            synchronized(page) {
                val body = if (sent.body.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(sent.body).jsonObject
                when {
                    sent.path.startsWith("/databases/db/query") -> 200 to FakeNotion.list(FakeNotion.page("page-1", "db", "List"))
                    sent.path.startsWith("/databases/db") -> 200 to FakeNotion.database("db")
                    sent.path == "/pages/page-1" -> 200 to FakeNotion.page("page-1", "db", "List")
                    sent.path.startsWith("/blocks/page-1/children") && sent.method == "GET" ->
                        200 to FakeNotion.list(*page.live().map(page::json).toTypedArray())
                    sent.path == "/blocks/page-1/children" && sent.method == "PATCH" ->
                        if (body.containsKey("position") && !startSupported) {
                            400 to """{"object":"error","status":400,"message":"position is not supported"}"""
                        } else {
                            200 to page.append(body)
                        }
                    sent.path.startsWith("/blocks/") && sent.method == "PATCH" -> page.update(sent.path.removePrefix("/blocks/"), body)
                    sent.path.startsWith("/blocks/") && sent.method == "DELETE" -> page.delete(sent.path.removePrefix("/blocks/"))
                    else -> 200 to "{}"
                }
            }
        }
    }

    private fun repository(seed: Int): NotesRepository {
        val settings = SettingsStore(PreferenceDataStoreFactory.create { folder.newFile("settings-$seed.preferences_pb").also { it.delete() } })
        return NotesRepository(
            client = NotionClient(apiKey = { "secret_test" }, baseUrl = notion.baseUrl),
            hasKey = { true },
            settings = settings,
            cache = CacheStore(folder.newFolder("cache-$seed")),
            scope = scope,
        ).also { repo ->
            runBlocking { settings.update { it.copy(databases = listOf(DatabaseConfig("db", "Notes"))) } }
            repo.start()
        }
    }

    /** One random edit, as the editor makes it (the editor keeps its own copy, without new IDs). */
    private fun edit(editor: DocumentEditor, blocks: List<Block>, random: Random): List<Block> {
        val texts = blocks.filter { it.type.hasText }
        val block = texts.randomOrNull(random) ?: return editor.insertAfter(blocks, null, BlockType.TODO).blocks
        return when (random.nextInt(10)) {
            0, 1, 2 -> editor.changeText(blocks, block.key, block.text + ('a' + random.nextInt(26))).blocks
            3, 4 -> editor.changeText(blocks, block.key, block.text + "\n").blocks
            5 -> editor.toggleChecked(blocks, block.key)
            6 -> editor.setType(blocks, block.key, listOf(BlockType.TODO, BlockType.PARAGRAPH, BlockType.HEADING_2).random(random)).blocks
            7 -> editor.backspaceAtStart(blocks, block.key).blocks
            8 -> if (blocks.size > 1) editor.delete(blocks, block.key).blocks else blocks
            else -> editor.insertAfter(blocks, block.key, BlockType.TODO).blocks
        }
    }

    private fun shape(blocks: List<Block>) = blocks.map { Triple(it.type, it.text, it.type == BlockType.TODO && it.checked) }

    private fun shapeOf(page: Page) = page.live().map { b ->
        val type = when (b.type) {
            "to_do" -> BlockType.TODO
            "heading_2" -> BlockType.HEADING_2
            else -> BlockType.PARAGRAPH
        }
        Triple(type, b.text, type == BlockType.TODO && b.checked)
    }

    private fun run(seed: Int) = runBlocking {
        val random = Random(seed)
        val page = Page().apply {
            blocks += Page.B("start-1", "to_do", "Milk", false)
            blocks += Page.B("start-2", "paragraph", "Notes", false)
        }
        serve(page, random)
        val repo = repository(seed)
        repo.refresh("db", userAsked = true)
        var editorBlocks = checkNotNull(repo.fetchContent("page-1")).blocks
        val editor = DocumentEditor()

        repeat(EDITS) {
            editorBlocks = edit(editor, editorBlocks, random)
            repo.saveContent("page-1", editorBlocks)
            delay(random.nextLong(0, 120))
        }
        // Leave the note and let everything go out.
        repo.saveContent("page-1", editorBlocks)
        repeat(20) {
            repo.flush()
            if (repo.pendingCount.first() == 0) return@repeat
            delay(200)
        }
        delay(1_500)
        repo.flush()

        val expected = shape(editorBlocks)
        val actual = synchronized(page) { shapeOf(page) }
        if (startSupported) {
            assertEquals("seed $seed", expected, actual)
        } else {
            // Lines added above the first line may end up at the end, but never twice or lost.
            assertEquals("seed $seed", expected.sortedBy { it.toString() }, actual.sortedBy { it.toString() })
        }
    }

    @Test
    fun `random editing while Notion is slow never duplicates or loses blocks`() {
        (1..SEEDS).forEach { run(it) }
    }

    @Test
    fun `without inserting at the start, still nothing is duplicated or lost`() {
        startSupported = false
        (101..100 + SEEDS).forEach { run(it) }
    }

    private companion object {
        const val SEEDS = 4
        const val EDITS = 40
    }
}
