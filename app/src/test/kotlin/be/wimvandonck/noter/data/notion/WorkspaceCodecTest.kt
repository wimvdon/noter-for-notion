package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.PageRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCodecTest {
    private fun obj(json: String) = Json.parseToJsonElement(json).jsonObject

    private val database = obj(
        """{"object":"database","id":"db1","icon":{"type":"emoji","emoji":"📚"},"last_edited_time":"2026-10-16T10:00:00.000Z",
           "title":[{"plain_text":"Reading list"}]}""",
    )
    private val page = obj(
        """{"object":"page","id":"p1","parent":{"type":"page_id","page_id":"p0"},"last_edited_time":"2026-10-16T09:00:00.000Z",
           "properties":{"title":{"id":"title","type":"title","title":[{"plain_text":"Ideas","text":{"content":"Ideas"},"annotations":{}}]}}}""",
    )
    private val note = obj(
        """{"object":"page","id":"n1","parent":{"type":"database_id","database_id":"db1"},
           "properties":{"Name":{"id":"title","type":"title","title":[{"plain_text":"A note","text":{"content":"A note"},"annotations":{}}]}}}""",
    )

    @Test
    fun `reads databases and pages`() {
        val db = WorkspaceCodec.parse(database, includeNotes = false, pagesKey = "pages")!!
        assertTrue(db.isDatabase)
        assertEquals("Reading list", db.title)
        assertEquals("📚", db.icon?.emoji)
        val p = WorkspaceCodec.parse(page, includeNotes = false, pagesKey = "pages")!!
        assertEquals("Ideas", p.title)
        assertEquals("pages", p.note!!.databaseId)
    }

    @Test
    fun `a database's notes only show up when searching`() {
        assertNull(WorkspaceCodec.parse(note, includeNotes = false, pagesKey = "pages"))
        assertEquals("db1", WorkspaceCodec.parse(note, includeNotes = true, pagesKey = "pages")!!.note!!.databaseId)
    }

    @Test
    fun `pages in the trash are left out`() {
        assertNull(WorkspaceCodec.parse(obj("""{"object":"page","id":"x","archived":true,"properties":{}}"""), includeNotes = true, pagesKey = "pages"))
    }

    @Test
    fun `data sources are listed like databases, and their pages belong to them`() {
        val source = obj("""{"object":"data_source","id":"ds1","title":[{"plain_text":"Archive"}],"parent":{"type":"database_id","database_id":"db1"}}""")
        val item = WorkspaceCodec.parse(source, includeNotes = false, pagesKey = "pages")!!
        assertTrue(item.isDatabase)
        assertEquals("ds1", item.id)
        assertEquals("Archive", item.title)

        val inSource = obj("""{"object":"page","id":"n2","parent":{"type":"data_source_id","data_source_id":"ds1","database_id":"db1"},"properties":{}}""")
        assertEquals("ds1", WorkspaceCodec.parentDatabase(inSource))
        assertEquals("db1", WorkspaceCodec.parentDatabase(note))
    }

    @Test
    fun `a database lists its data sources`() {
        val db = obj("""{"object":"database","id":"db1","data_sources":[{"id":"ds1","name":"Notes"},{"id":"ds2","name":"Archive"}]}""")
        assertEquals(listOf("ds1" to "Notes", "ds2" to "Archive"), WorkspaceCodec.dataSources(db).map { it.id to it.title })
    }

    @Test
    fun `a database with several data sources is listed once, an unreadable data source not at all`() {
        val ds = { id: String, title: String, db: String ->
            WorkspaceCodec.parse(obj("""{"object":"data_source","id":"$id","title":[{"plain_text":"$title"}],"parent":{"type":"database_id","database_id":"$db"}}"""), false, "pages")!!
        }
        // Health has Skin and Food; Notion's search also shows a "Health" data source that Health doesn't list.
        val items = listOf(ds("ghost", "Health", "db-health"), ds("skin", "Skin", "db-health"), ds("notes", "Notes", "db-notes"), ds("food", "Food", "db-health"), ds("x", "Unknown", "db-x"))
        assertEquals("db-health", items[1].databaseId)
        val infos = mapOf(
            "dbhealth" to DatabaseInfo("db-health", "Health", sources = listOf(PageRef("skin", "Skin"), PageRef("food", "Food"))),
            "db-notes" to DatabaseInfo("db-notes", "Notes", sources = listOf(PageRef("notes", "Notes"))),
        )
        val grouped = WorkspaceCodec.group(items, infos)
        assertEquals(listOf("db-health", "notes", "x"), grouped.map { it.id })
        assertEquals(listOf("skin", "food"), grouped[0].sources.map { it.id })
        assertEquals("Health", grouped[0].title)
        // Nothing known yet: as Notion's search gives them.
        assertEquals(items, WorkspaceCodec.group(items, emptyMap()))

        val info = WorkspaceCodec.databaseInfo(obj("""{"object":"database","id":"db1","title":[{"plain_text":"Health"}],"data_sources":[{"id":"ds1","name":"Skin"}]}"""))!!
        assertEquals("Health", info.title)
        assertEquals(listOf(PageRef("ds1", "Skin")), info.sources)
    }

    @Test
    fun `pages and databases know the page they are in, for Home's tree`() {
        assertEquals("p0", WorkspaceCodec.parse(page, includeNotes = false, pagesKey = "pages")!!.parentId)
        val top = obj("""{"object":"page","id":"p2","parent":{"type":"workspace","workspace":true},"properties":{}}""")
        assertNull(WorkspaceCodec.parse(top, includeNotes = false, pagesKey = "pages")!!.parentId)
        val inBlock = obj("""{"object":"page","id":"p3","parent":{"type":"block_id","block_id":"b1"},"properties":{}}""")
        assertEquals("b1", WorkspaceCodec.parse(inBlock, includeNotes = false, pagesKey = "pages")!!.parentId)
        // A note is in its database, not in a page.
        assertNull(WorkspaceCodec.parse(note, includeNotes = true, pagesKey = "pages")!!.parentId)

        // A data source is placed where its database is.
        val info = WorkspaceCodec.databaseInfo(
            obj("""{"object":"database","id":"db1","parent":{"type":"page_id","page_id":"p0"},"title":[{"plain_text":"Books"}],"data_sources":[{"id":"ds1","name":"Books"}]}"""),
        )!!
        assertEquals("p0", info.parentId)
        val source = WorkspaceCodec.parse(
            obj("""{"object":"data_source","id":"ds1","title":[{"plain_text":"Books"}],"parent":{"type":"database_id","database_id":"db1"}}"""),
            includeNotes = false,
            pagesKey = "pages",
        )!!
        assertNull(source.parentId)
        assertEquals("p0", WorkspaceCodec.group(listOf(source), mapOf("db1" to info)).single().parentId)
        val several = info.copy(sources = listOf(PageRef("ds1", "Books"), PageRef("ds2", "Loans")))
        assertEquals("p0", WorkspaceCodec.group(listOf(source), mapOf("db1" to several)).single().parentId)
    }
}
