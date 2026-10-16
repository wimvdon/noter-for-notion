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
        data class B(
            val id: String,
            var type: String,
            var text: String,
            var checked: Boolean,
            var archived: Boolean = false,
            /** The parent block (null: the page). */
            val parent: String? = null,
        )

        /** All blocks of the page, each parent's children in their order. */
        val blocks = mutableListOf<B>()
        private var next = 0

        private fun gone(b: B): Boolean = b.archived || (b.parent != null && blocks.first { it.id == b.parent }.let(::gone))

        fun children(parent: String?) = blocks.filter { it.parent == parent && !gone(it) }

        /** The page as the editor shows it: each block followed by its children, with their depth. */
        fun live(parent: String? = null, depth: Int = 0): List<Pair<B, Int>> =
            children(parent).flatMap { listOf(it to depth) + live(it.id, depth + 1) }

        fun json(b: B) =
            """{"object":"block","id":"${b.id}","type":"${b.type}","has_children":${children(b.id).isNotEmpty()},
               "${b.type}":{"checked":${b.checked},"rich_text":[{"type":"text","plain_text":${Json.encodeToString(b.text)},"text":{"content":${Json.encodeToString(b.text)}},"annotations":{}}]}}"""

        fun append(parent: String?, body: JsonObject): String {
            val children = body["children"]!!.jsonArray.map { it.jsonObject }
            val after = body["after"]?.jsonPrimitive?.content
            val atStart = body["position"]?.jsonObject?.get("type")?.jsonPrimitive?.content == "start"
            val added = children.map { child ->
                val type = child["type"]!!.jsonPrimitive.content
                val content = child[type]?.jsonObject
                B("b${++next}", type, richText(content?.get("rich_text") as? JsonArray), content?.get("checked")?.jsonPrimitive?.boolean ?: false, parent = parent)
            }
            val siblings = blocks.filter { it.parent == parent }
            val index = when {
                after != null -> blocks.indexOfFirst { it.id == after } + 1
                atStart -> siblings.firstOrNull()?.let { blocks.indexOf(it) } ?: blocks.size
                else -> blocks.size
            }
            blocks.addAll(index, added)
            // Like Notion with an anchor: the new blocks, then the ones after them.
            val answer = if (after == null && !atStart) added else blocks.drop(index).filter { it.parent == parent && !it.archived }
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
            Thread.sleep(random.nextLong(LATENCY_MIN, LATENCY_MAX))
            synchronized(page) {
                val body = if (sent.body.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(sent.body).jsonObject
                when {
                    sent.path.startsWith("/data_sources/db/query") -> 200 to FakeNotion.list(FakeNotion.page("page-1", "db", "List"))
                    sent.path.startsWith("/data_sources/db") -> 200 to FakeNotion.database("db")
                    sent.path == "/pages/page-1" -> 200 to FakeNotion.page("page-1", "db", "List")
                    sent.path.contains("/children") && sent.method == "GET" -> {
                        val id = sent.path.removePrefix("/blocks/").substringBefore("/children")
                        200 to FakeNotion.list(*page.children(id.takeIf { it != "page-1" }).map(page::json).toTypedArray())
                    }
                    sent.path.endsWith("/children") && sent.method == "PATCH" -> {
                        val id = sent.path.removePrefix("/blocks/").removeSuffix("/children")
                        if (body.containsKey("position") && !startSupported) {
                            400 to """{"object":"error","status":400,"message":"position is not supported"}"""
                        } else {
                            200 to page.append(id.takeIf { it != "page-1" }, body)
                        }
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
        return when (random.nextInt(13)) {
            0, 1, 2 -> editor.changeText(blocks, block.key, block.text + ('a' + random.nextInt(26))).blocks
            3, 4 -> editor.changeText(blocks, block.key, block.text + "\n").blocks
            5 -> editor.toggleChecked(blocks, block.key)
            6 -> editor.setType(blocks, block.key, listOf(BlockType.TODO, BlockType.PARAGRAPH, BlockType.HEADING_2).random(random)).blocks
            7 -> editor.backspaceAtStart(blocks, block.key).blocks
            8 -> if (blocks.size > 1) editor.delete(blocks, block.key).blocks else blocks
            9 -> editor.insertAfter(blocks, block.key, BlockType.TODO).blocks
            10, 11 -> editor.indent(blocks, block.key).blocks
            else -> editor.outdent(blocks, block.key).blocks
        }
    }

    private fun shape(blocks: List<Block>) = blocks.map { "${it.depth}:${it.type}:${it.text}:${it.type == BlockType.TODO && it.checked}" }

    private fun shapeOf(page: Page) = page.live().map { (b, depth) ->
        val type = when (b.type) {
            "to_do" -> BlockType.TODO
            "heading_2" -> BlockType.HEADING_2
            else -> BlockType.PARAGRAPH
        }
        "$depth:$type:${b.text}:${type == BlockType.TODO && b.checked}"
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
            assertEquals("seed $seed", expected.sorted(), actual.sorted())
        }
    }

    @Test
    fun `random editing while Notion is slow never duplicates or loses blocks`() {
        val failures = (1..SEEDS).mapNotNull { seed -> runCatching { run(seed) }.exceptionOrNull()?.message }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun `ticking a nested item only ticks it, and taking it out moves it out`() = runBlocking {
        val page = Page().apply { blocks += Page.B("milk", "to_do", "Milk", false) }
        serve(page, Random(0))
        val repo = repository(0)
        repo.refresh("db", userAsked = true)
        val editor = DocumentEditor()
        var blocks = checkNotNull(repo.fetchContent("page-1")).blocks

        suspend fun step(next: List<Block>) {
            blocks = next
            repo.saveContent("page-1", blocks)
            repo.flush()
        }
        step(editor.insertAfter(blocks, "milk", BlockType.TODO).blocks)
        step(editor.indent(blocks, blocks.last().key).blocks)
        val before = notion.requests.size
        step(editor.toggleChecked(blocks, blocks.last().key))
        // Ticking only ticks: the item stays where it is (it isn't deleted and added again).
        assertEquals(listOf("PATCH"), notion.requests.drop(before).map { it.method })
        step(editor.outdent(blocks, blocks.last().key).blocks)

        assertEquals(shape(blocks), synchronized(page) { shapeOf(page) })
    }

    @Test
    fun `without inserting at the start, still nothing is duplicated or lost`() {
        startSupported = false
        (101..100 + SEEDS).forEach { run(it) }
    }

    private companion object {
        const val SEEDS = 5
        const val EDITS = 40
        /** How long Notion takes to answer (ms): wide, so sends and edits interleave in many ways. */
        const val LATENCY_MIN = 1L
        const val LATENCY_MAX = 250L
    }
}
