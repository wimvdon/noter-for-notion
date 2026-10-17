package be.wimvandonck.noter.data.notion

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
        assertEquals("📚", db.icon)
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
}
