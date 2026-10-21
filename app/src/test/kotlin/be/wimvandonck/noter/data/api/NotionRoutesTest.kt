package be.wimvandonck.noter.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class NotionRoutesTest {
    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `the subject is the id in the path`() {
        assertEquals("p1", NotionRoutes.subjectId("/pages/p1", null))
        assertEquals("b1", NotionRoutes.subjectId("/blocks/b1/children?page_size=100", null))
        assertEquals("d1", NotionRoutes.subjectId("/data_sources/d1/query", emptyMap<String, Any?>()))
        assertEquals("db1", NotionRoutes.subjectId("/databases/db1", null))
    }

    @Test
    fun `a new page's subject is its parent`() {
        val inSource = mapOf("parent" to mapOf("type" to "data_source_id", "data_source_id" to "d1"))
        val inPage = mapOf("parent" to mapOf("type" to "page_id", "page_id" to "p1"))
        assertEquals("d1", NotionRoutes.subjectId("/pages", inSource))
        assertEquals("p1", NotionRoutes.subjectId("/pages", inPage))
    }

    @Test
    fun `workspace-wide requests have no subject`() {
        assertNull(NotionRoutes.subjectId("/search", mapOf("query" to "x")))
        assertNull(NotionRoutes.subjectId("/users?page_size=100", null))
        assertNull(NotionRoutes.subjectId("/pages", mapOf("parent" to mapOf("type" to "workspace", "workspace" to true))))
    }

    @Test
    fun `object ids come from the object, its results and its data sources`() {
        assertEquals(listOf("p1"), NotionRoutes.objectIds(obj("""{"object":"page","id":"p1"}""")))
        assertEquals(
            listOf("b1", "b2"),
            NotionRoutes.objectIds(obj("""{"object":"list","results":[{"id":"b1"},{"id":"b2"},null]}""")),
        )
        assertEquals(
            listOf("db1", "d1"),
            NotionRoutes.objectIds(obj("""{"object":"database","id":"db1","data_sources":[{"id":"d1","name":"A"}]}""")),
        )
    }

    @Test
    fun `blocks and people are told apart from pages and databases`() {
        val list = obj("""{"object":"list","results":[{"object":"block","id":"b1"},{"object":"page","id":"p1"},{"object":"user","id":"u1"}]}""")
        assertEquals(listOf("p1"), NotionRoutes.objectIds(list))
        assertEquals(listOf("b1"), NotionRoutes.objectIds(list, blocks = true))
        assertTrue(NotionRoutes.isBlock("/blocks/b1/children"))
        assertFalse(NotionRoutes.isBlock("/pages/p1"))
    }

    @Test
    fun `results from several workspaces are merged newest first`() {
        val older = obj("""{"id":"a","last_edited_time":"2026-10-01T10:00:00.000Z"}""")
        val newer = obj("""{"id":"b","last_edited_time":"2026-10-02T10:00:00.000Z"}""")
        val newest = obj("""{"id":"c","last_edited_time":"2026-10-03T10:00:00.000Z"}""")
        assertEquals(listOf(newest, newer, older), NotionRoutes.newestFirst(listOf(listOf(newest, older), listOf(newer))))
        assertEquals(listOf(newest, newer), NotionRoutes.newestFirst(listOf(listOf(newest, older), listOf(newer)), limit = 2))
    }

    @Test
    fun `one workspace keeps Notion's order`() {
        val a = obj("""{"id":"a","last_edited_time":"2026-10-01T10:00:00.000Z"}""")
        val b = obj("""{"id":"b","last_edited_time":"2026-10-02T10:00:00.000Z"}""")
        assertEquals(listOf(a, b), NotionRoutes.newestFirst(listOf(listOf(a, b))))
    }

    @Test
    fun `a page two tokens of the same workspace found is listed once`() {
        val a = obj("""{"id":"a1b2","last_edited_time":"2026-10-02T10:00:00.000Z"}""")
        val same = obj("""{"id":"A1-B2","last_edited_time":"2026-10-02T10:00:00.000Z"}""")
        val b = obj("""{"id":"b","last_edited_time":"2026-10-01T10:00:00.000Z"}""")
        assertEquals(listOf(a, b), NotionRoutes.newestFirst(listOf(listOf(a, b), listOf(same))))
    }
}
