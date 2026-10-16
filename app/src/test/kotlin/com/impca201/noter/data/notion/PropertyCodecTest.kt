package com.impca201.noter.data.notion

import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.NotionUser
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.data.model.SelectOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyCodecTest {
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val page = obj(
        """
        {"id":"p1","created_time":"2026-10-01T10:00:00.000Z","last_edited_time":"2026-10-02T10:00:00.000Z",
         "url":"https://www.notion.so/p1","icon":{"type":"emoji","emoji":"🛒"},
         "properties":{
           "Name":{"id":"title","type":"title","title":[{"type":"text","plain_text":"Groceries","text":{"content":"Groceries","link":null},"annotations":{"bold":false}}]},
           "Status":{"id":"s","type":"status","status":{"id":"1","name":"Doing","color":"blue"}},
           "Tags":{"id":"t","type":"multi_select","multi_select":[{"name":"home","color":"green"},{"name":"food","color":"red"}]},
           "Empty select":{"id":"e","type":"select","select":null},
           "Due":{"id":"d","type":"date","date":{"start":"2026-10-15","end":null}},
           "Done":{"id":"c","type":"checkbox","checkbox":true},
           "Count":{"id":"n","type":"number","number":3},
           "Who":{"id":"w","type":"people","people":[{"object":"user","id":"u1","name":"Wim"}]},
           "Link":{"id":"l","type":"url","url":null},
           "Related":{"id":"r","type":"relation","relation":[{"id":"x1"}],"has_more":false},
           "Many":{"id":"m","type":"relation","relation":[{"id":"x1"}],"has_more":true},
           "Score":{"id":"f","type":"formula","formula":{"type":"number","number":42}},
           "Id":{"id":"u","type":"unique_id","unique_id":{"prefix":"NOTE","number":7}},
           "Rolled":{"id":"ro","type":"rollup","rollup":{"type":"array","array":[{"type":"title","title":[{"plain_text":"A"}]},{"type":"title","title":[{"plain_text":"B"}]}]}},
           "Edited":{"id":"le","type":"last_edited_time","last_edited_time":"2026-10-02T10:00:00.000Z"}
         }}
        """,
    )

    @Test
    fun `reads a page with every kind of property`() {
        val note = PropertyCodec.parsePage(page, "db")!!
        assertEquals("Groceries", note.title)
        assertEquals("🛒", note.icon)
        assertEquals("db", note.databaseId)
        assertEquals(PropertyValue.Select(SelectOption("Doing", "blue", "1")), note.property("Status")!!.value)
        assertEquals(listOf("home", "food"), (note.property("Tags")!!.value as PropertyValue.MultiSelect).options.map { it.name })
        assertEquals(PropertyValue.Select(null), note.property("Empty select")!!.value)
        assertEquals(PropertyValue.Date("2026-10-15", null), note.property("Due")!!.value)
        assertEquals(PropertyValue.Checkbox(true), note.property("Done")!!.value)
        assertEquals(PropertyValue.Number(3.0), note.property("Count")!!.value)
        assertEquals(PropertyValue.People(listOf(NotionUser("u1", "Wim"))), note.property("Who")!!.value)
        assertEquals(PropertyValue.Plain(null), note.property("Link")!!.value)
        assertEquals(PropertyValue.ReadOnly("42"), note.property("Score")!!.value)
        assertEquals(PropertyValue.ReadOnly("NOTE-7"), note.property("Id")!!.value)
        assertEquals(PropertyValue.ReadOnly("A, B"), note.property("Rolled")!!.value)
        assertEquals("title", note.properties.first().type)
    }

    @Test
    fun `computed properties and truncated relations are read-only`() {
        val note = PropertyCodec.parsePage(page, "db")!!
        assertTrue(note.property("Status")!!.isEditable)
        assertTrue(note.property("Related")!!.isEditable)
        assertFalse(note.property("Many")!!.isEditable)
        assertFalse(note.property("Score")!!.isEditable)
        assertFalse(note.property("Edited")!!.isEditable)
        assertNull(PropertyCodec.toRequest(note.property("Score")!!))
    }

    @Test
    fun `writes values the way Notion expects them`() {
        assertEquals(
            mapOf("status" to mapOf("name" to "Done")),
            PropertyCodec.toRequest(NoteProperty("Status", "status", PropertyValue.Select(SelectOption("Done")))),
        )
        assertEquals(
            mapOf("select" to null),
            PropertyCodec.toRequest(NoteProperty("S", "select", PropertyValue.Select(null))),
        )
        assertEquals(
            mapOf("date" to null),
            PropertyCodec.toRequest(NoteProperty("D", "date", PropertyValue.Date(null))),
        )
        assertEquals(
            mapOf("url" to null),
            PropertyCodec.toRequest(NoteProperty("U", "url", PropertyValue.Plain("  "))),
        )
        assertEquals(
            mapOf("people" to listOf(mapOf("object" to "user", "id" to "u1"))),
            PropertyCodec.toRequest(NoteProperty("P", "people", PropertyValue.People(listOf(NotionUser("u1", "Wim"))))),
        )
        assertEquals(
            mapOf("relation" to listOf(mapOf("id" to "x"))),
            PropertyCodec.toRequest(NoteProperty("R", "relation", PropertyValue.Relation(listOf("x")))),
        )
    }

    @Test
    fun `create request leaves out empty values but keeps the title`() {
        val request = PropertyCodec.toCreateRequest(
            listOf(
                NoteProperty("Name", "title", PropertyValue.Text("")),
                NoteProperty("Status", "status", PropertyValue.Select(null)),
                NoteProperty("Tags", "multi_select", PropertyValue.MultiSelect(listOf(SelectOption("a")))),
            ),
        )
        assertEquals(setOf("Name", "Tags"), request.keys)
    }

    @Test
    fun `reads a database schema with options and relation target`() {
        val schema = PropertyCodec.parseSchema(
            obj(
                """{"id":"db","title":[{"plain_text":"Notes"}],"properties":{
                  "Name":{"id":"title","name":"Name","type":"title","title":{}},
                  "Status":{"id":"s","name":"Status","type":"status","status":{"options":[{"id":"1","name":"Todo","color":"gray"}]}},
                  "Project":{"id":"p","name":"Project","type":"relation","relation":{"database_id":"other"}},
                  "Created":{"id":"c","name":"Created","type":"created_time","created_time":{}}}}""",
            ),
        )
        assertEquals("Notes", schema.title)
        assertEquals("Name", schema.titleProperty)
        assertEquals(listOf("Todo"), schema.property("Status")!!.options.map { it.name })
        assertEquals("other", schema.property("Project")!!.relationDatabaseId)
        assertEquals("Created", schema.properties.last().name)
        assertTrue(schema.property("Status")!!.isChipCandidate)
        assertFalse(schema.property("Project")!!.isChipCandidate)
    }

    @Test
    fun `trashed pages are recognised in old and new API versions`() {
        assertTrue(PropertyCodec.isTrashed(obj("""{"archived":true}""")))
        assertTrue(PropertyCodec.isTrashed(obj("""{"in_trash":true}""")))
        assertFalse(PropertyCodec.isTrashed(obj("""{"archived":false}""")))
    }

    @Test
    fun `reads the page's header image`() {
        val note = PropertyCodec.parsePage(
            obj("""{"id":"p","cover":{"type":"file","file":{"url":"https://s3/c.jpg?sig","expiry_time":"2026-10-16T11:00:00.000Z"}},"properties":{}}"""),
            "db",
        )!!
        assertEquals("https://s3/c.jpg?sig", note.cover)
        assertEquals("2026-10-16T11:00:00.000Z", note.coverExpiry)
        val external = PropertyCodec.parsePage(obj("""{"id":"p","cover":{"type":"external","external":{"url":"https://x/c.jpg"}},"properties":{}}"""), "db")!!
        assertEquals("https://x/c.jpg", external.cover)
        assertNull(PropertyCodec.parsePage(obj("""{"id":"p","cover":null,"properties":{}}"""), "db")!!.cover)
    }
}
