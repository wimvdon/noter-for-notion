package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class NewDatabasesTest {
    private val names = mapOf(
        StarterProperty.TAGS to "Tags",
        StarterProperty.DATE to "Date",
        StarterProperty.DONE to "Done",
        StarterProperty.LINK to "Link",
    )

    @Test
    fun `inside a page, with the title property and the starters picked, in a fixed order`() {
        val request = NewDatabases.request(" Books ", "page-1", "Name", listOf(StarterProperty.LINK, StarterProperty.TAGS), names)
        assertEquals(mapOf("type" to "page_id", "page_id" to "page-1"), request["parent"])
        assertEquals(listOf(mapOf("type" to "text", "text" to mapOf("content" to "Books"))), request["title"])
        val properties = (request["initial_data_source"] as Map<*, *>)["properties"] as Map<*, *>
        assertEquals(listOf("Name", "Tags", "Link"), properties.keys.toList())
        assertEquals(mapOf("title" to emptyMap<String, Any?>()), properties["Name"])
        assertEquals(mapOf("multi_select" to emptyMap<String, Any?>()), properties["Tags"])
        assertEquals(mapOf("url" to emptyMap<String, Any?>()), properties["Link"])
    }

    @Test
    fun `at the top of the workspace`() {
        val request = NewDatabases.request("Books", null, "Name", emptyList(), names)
        assertEquals(mapOf("type" to "workspace", "workspace" to true), request["parent"])
    }

    @Test
    fun `a property named like another is added once, and a blank title name falls back`() {
        val request = NewDatabases.request("Books", "p", " ", listOf(StarterProperty.TAGS, StarterProperty.DATE), names + (StarterProperty.DATE to "tags"))
        val properties = (request["initial_data_source"] as Map<*, *>)["properties"] as Map<*, *>
        assertEquals(listOf("Name", "Tags"), properties.keys.toList())
    }
}
