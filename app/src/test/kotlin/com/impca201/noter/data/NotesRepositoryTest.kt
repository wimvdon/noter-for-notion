package com.impca201.noter.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.impca201.noter.data.api.NotionClient
import com.impca201.noter.data.model.Block
import com.impca201.noter.data.model.BlockType
import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.data.store.CacheStore
import com.impca201.noter.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections

class NotesRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val notion = FakeNotion()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
        notion.close()
    }

    private fun repository(): NotesRepository {
        val settings = SettingsStore(PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb").also { it.delete() } })
        return NotesRepository(
            client = NotionClient(apiKey = { "secret_test" }, baseUrl = notion.baseUrl),
            hasKey = { true },
            settings = settings,
            cache = CacheStore(folder.newFolder("cache")),
            scope = scope,
        ).also { repo ->
            runBlocking { settings.update { it.copy(databases = listOf(DatabaseConfig("db", "Notes"))) } }
            repo.start()
        }
    }

    @Test
    fun `a new note stays open while Notion creates it`() = runBlocking {
        notion.respond = { sent ->
            when {
                sent.path.startsWith("/databases/db/query") -> 200 to FakeNotion.list()
                sent.path.startsWith("/databases/db") -> 200 to FakeNotion.database("db")
                sent.method == "POST" && sent.path == "/pages" -> 200 to FakeNotion.page("page-1", "db")
                else -> 200 to "{}"
            }
        }
        val repo = repository()
        val localId = checkNotNull(repo.createNote("db"))
        val seen = Collections.synchronizedList(mutableListOf<String?>())
        val watcher = scope.launch { repo.note(localId).collect { seen += it?.id } }

        repo.flush()

        val note = withTimeout(5_000) { repo.note(localId).first { it?.id == "page-1" } }
        assertEquals("page-1", note?.id)
        assertEquals("page-1", repo.resolve(localId))
        watcher.cancel()
        // The editor never saw the note disappear.
        assertFalse(seen.toString(), seen.contains(null))
    }

    @Test
    fun `edits to a note in the trash are not sent`() = runBlocking {
        notion.respond = { sent ->
            when {
                sent.path.startsWith("/databases/db/query") -> 200 to FakeNotion.list(FakeNotion.page("page-1", "db", "Old"))
                sent.path.startsWith("/databases/db") -> 200 to FakeNotion.database("db")
                sent.method == "PATCH" && sent.path == "/pages/page-1" -> 200 to FakeNotion.page("page-1", "db", archived = true)
                else -> 200 to "{}"
            }
        }
        val repo = repository()
        repo.refresh("db", userAsked = true)
        assertEquals(listOf("page-1"), repo.databases.value["db"]?.notes?.map { it.id })

        repo.trashNote("page-1")
        // An editor that is closing hands over what was still waiting.
        repo.updateProperty("page-1", NoteProperty("Name", "title", PropertyValue.Text("New")))
        repo.saveContent("page-1", emptyList())
        repo.flush()

        val patches = notion.requests.filter { it.method == "PATCH" }
        assertEquals(1, patches.size)
        assertTrue(patches.single().body, patches.single().body.contains("\"archived\":true"))
    }

    @Test
    fun `saving a note that was trashed in Notion fails quietly`() = runBlocking {
        notion.respond = { sent ->
            when {
                sent.path.startsWith("/databases/db/query") -> 200 to FakeNotion.list(FakeNotion.page("page-1", "db", "Old"))
                sent.path.startsWith("/databases/db") -> 200 to FakeNotion.database("db")
                sent.method == "PATCH" -> 400 to FakeNotion.ARCHIVED
                else -> 200 to "{}"
            }
        }
        val repo = repository()
        repo.refresh("db", userAsked = true)
        val events = Collections.synchronizedList(mutableListOf<RepoEvent>())
        val collector = scope.launch { repo.events.collect { events += it } }

        repo.updateProperty("page-1", NoteProperty("Name", "title", PropertyValue.Text("New")))
        repo.flush()

        collector.cancel()
        assertTrue(events.toString(), events.isEmpty())
        assertEquals(0, repo.pendingCount.first())
    }

    @Test
    fun `tables come with their rows, and expired images are downloaded again`() = runBlocking {
        var signature = 1
        notion.respond = { sent ->
            when {
                sent.path.startsWith("/databases/db/query") -> 200 to FakeNotion.list(FakeNotion.page("page-1", "db", "Old"))
                sent.path.startsWith("/databases/db") -> 200 to FakeNotion.database("db")
                sent.path == "/pages/page-1" -> 200 to FakeNotion.page("page-1", "db", "Old")
                sent.path.startsWith("/blocks/page-1/children") -> 200 to FakeNotion.list(
                    """{"id":"img","type":"image","image":{"type":"file","caption":[],
                        "file":{"url":"https://s3/cat.png?sig=${signature++}","expiry_time":"2000-01-01T00:00:00.000Z"}}}""",
                    """{"id":"tbl","type":"table","has_children":true,"table":{"table_width":1,"has_column_header":false}}""",
                )
                sent.path.startsWith("/blocks/tbl/children") ->
                    200 to FakeNotion.list("""{"type":"table_row","table_row":{"cells":[[{"plain_text":"A1"}]]}}""")
                else -> 200 to "{}"
            }
        }
        val repo = repository()
        repo.refresh("db", userAsked = true)

        val first = checkNotNull(repo.fetchContent("page-1"))
        assertEquals(listOf(listOf("A1")), first.blocks.single { it.rawType == "table" }.rows)
        // The image's link has expired: opening the note again gets a fresh one.
        val second = checkNotNull(repo.fetchContent("page-1"))
        assertEquals("https://s3/cat.png?sig=2", second.blocks.single { it.rawType == "image" }.url)
        // Blocks keep their keys, so the editor doesn't redraw everything.
        assertEquals(first.blocks.map { it.key }, second.blocks.map { it.key })
    }

    @Test
    fun `pages from Browse and linked pages open in the editor and can be pinned`() = runBlocking {
        notion.respond = { sent ->
            when {
                sent.path.startsWith("/databases/db/query") -> 200 to FakeNotion.list()
                sent.path.startsWith("/databases/db") -> 200 to FakeNotion.database("db")
                sent.path == "/search" -> 200 to FakeNotion.list(
                    """{"object":"page","id":"top","parent":{"type":"workspace","workspace":true},
                        "properties":{"title":{"id":"title","type":"title","title":[]}}}""",
                    FakeNotion.page("row", "db", "A note"),
                )
                sent.path == "/pages/linked" ->
                    200 to """{"object":"page","id":"linked","parent":{"type":"page_id","page_id":"top"},"properties":{"title":{"id":"title","type":"title","title":[]}}}"""
                else -> 200 to "{}"
            }
        }
        val repo = repository()
        val items = repo.browse("")
        // Without a search, a database's notes are left out.
        assertEquals(listOf("top"), items.map { it.id })

        val id = repo.openNote(items.single().note!!)
        assertEquals("top", repo.note(id).first()?.id)
        assertEquals("linked", repo.openPage("linked"))
        assertEquals(NotesRepository.PAGES, repo.note("linked").first()?.databaseId)

        repo.togglePin("linked")
        repo.updateProperty("linked", NoteProperty("title", "title", PropertyValue.Text("Renamed")))
        assertEquals("Renamed", repo.note("linked").first()?.title)
    }

    /** Notion with one page whose content is [blocks]; appended blocks get IDs new-1, new-2, … */
    private fun notionWithContent(blocks: MutableList<String>, onAppend: (Sent) -> Pair<Int, String>? = { null }) {
        var next = 0
        notion.respond = { sent ->
            when {
                sent.path.startsWith("/databases/db/query") -> 200 to FakeNotion.list(FakeNotion.page("page-1", "db", "List"))
                sent.path.startsWith("/databases/db") -> 200 to FakeNotion.database("db")
                sent.path == "/pages/page-1" -> 200 to FakeNotion.page("page-1", "db", "List")
                sent.method == "GET" && sent.path.startsWith("/blocks/page-1/children") -> 200 to FakeNotion.list(*blocks.toTypedArray())
                sent.method == "PATCH" && sent.path == "/blocks/page-1/children" -> {
                    val texts = Regex("\"content\":\"([^\"]*)\"").findAll(sent.body).map { it.groupValues[1] }.toList()
                    val added = texts.map { FakeNotion.todo("new-${++next}", it) }
                    blocks += added
                    onAppend(sent) ?: (200 to FakeNotion.list(*added.toTypedArray()))
                }
                else -> 200 to "{}"
            }
        }
    }

    @Test
    fun `new to-dos are added once, also when the editor saves again without their IDs`() = runBlocking {
        val blocks = mutableListOf(FakeNotion.todo("t1", "Milk"))
        notionWithContent(blocks)
        val repo = repository()
        repo.refresh("db", userAsked = true)
        val loaded = checkNotNull(repo.fetchContent("page-1")).blocks
        val eggs = Block("eggs", type = BlockType.TODO, text = "Eggs")

        repo.saveContent("page-1", loaded + eggs)
        repo.flush()
        // The editor still has its own copy, without the ID Notion gave the new to-do.
        repo.saveContent("page-1", loaded + eggs.copy(checked = true))
        repo.flush()

        val appends = notion.requests.filter { it.method == "PATCH" && it.path == "/blocks/page-1/children" }
        assertEquals(1, appends.size)
        assertTrue(notion.requests.none { it.method == "DELETE" })
        assertTrue(notion.requests.any { it.method == "PATCH" && it.path == "/blocks/new-1" && it.body.contains("\"checked\":true") })
        assertEquals(0, repo.pendingCount.first())
    }

    @Test
    fun `a to-do Notion added without answering is not added again`() = runBlocking {
        val blocks = mutableListOf(FakeNotion.todo("t1", "Milk"))
        var fail = true
        // Notion adds the block, but the answer is a server error (once).
        notionWithContent(blocks) { if (fail) { fail = false; 502 to "{}" } else null }
        val repo = repository()
        repo.refresh("db", userAsked = true)
        val loaded = checkNotNull(repo.fetchContent("page-1")).blocks

        repo.saveContent("page-1", loaded + Block("eggs", type = BlockType.TODO, text = "Eggs"))
        repo.flush()
        assertEquals(1, repo.pendingCount.first())
        repo.flush()

        val appends = notion.requests.filter { it.method == "PATCH" && it.path == "/blocks/page-1/children" }
        assertEquals(1, appends.size)
        assertEquals(2, blocks.size)
        assertEquals(0, repo.pendingCount.first())
        assertEquals("new-1", repo.cachedContent("page-1")?.blocks?.last()?.notionId)
    }
}
