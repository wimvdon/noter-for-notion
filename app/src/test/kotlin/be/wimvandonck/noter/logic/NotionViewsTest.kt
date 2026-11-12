package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NotionView
import be.wimvandonck.noter.data.model.PropertySchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotionViewsTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun view(id: String, type: String = "table", source: String? = "ds-1", filter: String? = null, sorts: String? = null, quick: String? = null) =
        NotionView(id, "View $id", type, source, filter?.let(::json), sorts?.let(::json), quick?.let(::json))

    private val schema = DatabaseSchema(
        "ds-1", "Tasks",
        listOf(
            PropertySchema("title", "Name", "title"),
            PropertySchema("a%3Bb", "Due", "date"),
            PropertySchema("xyz", "Files", "files"),
        ),
    )

    @Test
    fun `only views that list this data source's notes are offered`() {
        val views = listOf(view("1"), view("2", type = "form"), view("3", type = "dashboard", source = null), view("4", source = "ds-2"), view("5", type = "board", source = "ds1"))
        assertEquals(listOf("1", "5"), NotionViews.listed(views, "ds-1").map { it.id })
    }

    @Test
    fun `a database opens on Notion's first view, then on the view used last`() {
        val views = listOf(view("1"), view("2"))
        assertEquals("1", NotionViews.chosen(null, views))
        assertEquals("2", NotionViews.chosen("2", views))
        // A chosen view that is gone or hidden: the first shown one.
        assertEquals("1", NotionViews.chosen("gone", views))
        assertNull(NotionViews.chosen(null, emptyList()))
    }

    @Test
    fun `tabs follow the user's order, new views after it, hidden ones apart`() {
        val views = listOf(view("1"), view("2"), view("3"), view("4"))
        val (shown, hidden) = NotionViews.arranged(views, order = listOf("3", "1", "2"), hidden = listOf("2"))
        assertEquals(listOf("3", "1", "4"), shown.map { it.id })
        assertEquals(listOf("2"), hidden.map { it.id })
    }

    @Test
    fun `when every view is hidden they all show`() {
        val views = listOf(view("1"), view("2"))
        val (shown, hidden) = NotionViews.arranged(views, order = emptyList(), hidden = listOf("1", "2"))
        assertEquals(listOf("1", "2"), shown.map { it.id })
        assertEquals(emptyList<NotionView>(), hidden)
    }

    @Test
    fun `a view's filter and sorts are sent as they are, sorts by property name`() {
        val v = view("1", filter = """{"property":"Done","checkbox":{"equals":false}}""", sorts = """[{"property":"a%3Bb","direction":"ascending"},{"timestamp":"created_time","direction":"descending"}]""")
        val (filter, sorts) = NotionViews.query(v, schema)
        assertEquals(json("""{"property":"Done","checkbox":{"equals":false}}"""), filter)
        assertEquals(json("""[{"property":"Due","direction":"ascending"},{"timestamp":"created_time","direction":"descending"}]"""), sorts)
    }

    @Test
    fun `quick filters with a condition join the view's filter`() {
        val quick = """{"Status":{"status":{"equals":"Doing"}},"Tag":{}}"""
        val alone = NotionViews.query(view("1", quick = quick), schema).first
        assertEquals(json("""{"property":"Status","status":{"equals":"Doing"}}"""), alone)

        val withAnd = NotionViews.query(view("1", filter = """{"and":[{"property":"Done","checkbox":{"equals":false}}]}""", quick = quick), schema).first
        assertEquals(json("""{"and":[{"property":"Done","checkbox":{"equals":false}},{"property":"Status","status":{"equals":"Doing"}}]}"""), withAnd)

        val withOr = NotionViews.query(view("1", filter = """{"or":[{"property":"A","checkbox":{"equals":true}}]}""", quick = quick), schema).first
        assertEquals(json("""{"and":[{"or":[{"property":"A","checkbox":{"equals":true}}]},{"property":"Status","status":{"equals":"Doing"}}]}"""), withOr)

        assertNull(NotionViews.query(view("1", quick = quick), schema, quickFilters = false).first)
    }

    @Test
    fun `no filter and no sorts ask for every note in Notion's order`() {
        assertEquals(null to null, NotionViews.query(view("1", sorts = "[]"), schema))
    }

    @Test
    fun `notes made since the view was asked show first`() {
        val created = mapOf("a" to "2026-11-01T10:00:00.000Z", "new" to "2026-11-12T10:00:05.000Z", "old" to "2026-11-01T09:00:00.000Z")
        assertEquals(listOf("new", "a"), NotionViews.members(listOf("a"), "2026-11-12T10:00:00Z", created))
    }
}
