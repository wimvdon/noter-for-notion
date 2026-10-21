package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class NewDatabasesTest {
    private fun schema(request: Map<String, Any?>) = (request["initial_data_source"] as Map<*, *>)["properties"] as Map<*, *>

    @Test
    fun `inside a page, with the title property and the properties in their order`() {
        val properties = listOf(NewProperty(PropertyKind.MULTI_SELECT, "Tags"), NewProperty(PropertyKind.NUMBER, "Number"), NewProperty(PropertyKind.URL, "URL"))
        val request = NewDatabases.request(" Books ", "page-1", "Name", properties)
        assertEquals(mapOf("type" to "page_id", "page_id" to "page-1"), request["parent"])
        assertEquals(listOf(mapOf("type" to "text", "text" to mapOf("content" to "Books"))), request["title"])
        val schema = schema(request)
        assertEquals(listOf("Name", "Tags", "Number", "URL"), schema.keys.toList())
        assertEquals(mapOf("title" to emptyMap<String, Any?>()), schema["Name"])
        assertEquals(mapOf("multi_select" to emptyMap<String, Any?>()), schema["Tags"])
        assertEquals(mapOf("number" to mapOf("format" to "number")), schema["Number"])
        assertEquals(mapOf("url" to emptyMap<String, Any?>()), schema["URL"])
    }

    @Test
    fun `at the top of the workspace`() {
        val request = NewDatabases.request("Books", null, "Name", emptyList())
        assertEquals(mapOf("type" to "workspace", "workspace" to true), request["parent"])
    }

    @Test
    fun `several of a kind are numbered as Notion does`() {
        val list = listOf(PropertyKind.TEXT, PropertyKind.TEXT, PropertyKind.CHECKBOX, PropertyKind.TEXT)
            .fold(emptyList<NewProperty>()) { acc, kind -> NewDatabases.add(acc, kind, if (kind == PropertyKind.TEXT) "Text" else "Checkbox", "Name") }
        assertEquals(listOf("Text", "Text 2", "Checkbox", "Text 3"), list.map { it.name })
        assertEquals("name 2", NewDatabases.add(emptyList(), PropertyKind.TEXT, "name", "Name").single().name)
    }

    @Test
    fun `names made alike or blank while editing are still sent once each`() {
        val properties = listOf(NewProperty(PropertyKind.TEXT, "Notes"), NewProperty(PropertyKind.DATE, "notes"), NewProperty(PropertyKind.EMAIL, " "))
        val schema = schema(NewDatabases.request("Books", "p", " ", properties, mapOf(PropertyKind.EMAIL to "Email")))
        assertEquals(listOf("Name", "Notes", "notes 2", "Email"), schema.keys.toList())
    }

    @Test
    fun `a new database starts with Tags, as in Notion`() {
        assertEquals(listOf(PropertyKind.MULTI_SELECT), NewDatabases.DEFAULT_KINDS)
    }
}
