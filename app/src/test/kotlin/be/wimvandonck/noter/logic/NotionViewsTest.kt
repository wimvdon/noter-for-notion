package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteSort
import be.wimvandonck.noter.data.model.NotionView
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.SortKind
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
    fun `a database opens on Notion's first view the first time`() {
        val views = listOf(view("1"), view("2"))
        assertEquals("1", NotionViews.chosen(emptyMap(), emptyMap(), "ds-1", views))
    }

    @Test
    fun `after that it opens on the view used last`() {
        val views = listOf(view("1"), view("2"))
        assertEquals("2", NotionViews.chosen(mapOf("ds1" to "2"), emptyMap(), "ds-1", views))
        assertEquals(NotionViews.NOTER, NotionViews.chosen(mapOf("ds-1" to NotionViews.NOTER), emptyMap(), "ds-1", views))
    }

    @Test
    fun `a database set up before Notion's views came opens on the Noter view`() {
        val views = listOf(view("1"))
        assertEquals(NotionViews.NOTER, NotionViews.chosen(emptyMap(), mapOf("ds-1" to DatabaseSetup()), "ds-1", views))
    }

    @Test
    fun `a chosen view that is gone gives Notion's first, and no views the Noter view`() {
        assertEquals("1", NotionViews.chosen(mapOf("ds-1" to "gone"), mapOf("ds-1" to DatabaseSetup()), "ds-1", listOf(view("1"))))
        assertEquals(NotionViews.NOTER, NotionViews.chosen(emptyMap(), emptyMap(), "ds-1", emptyList()))
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
    fun `a view's first sort becomes Noter's`() {
        assertEquals(NoteSort(SortKind.PROPERTY, "Due", descending = false), NotionViews.noterSort(view("1", sorts = """[{"property":"a%3Bb","direction":"ascending"}]"""), schema))
        assertEquals(NoteSort(SortKind.PROPERTY, "Due", descending = true), NotionViews.noterSort(view("1", sorts = """[{"property":"Due","direction":"descending"}]"""), schema))
        assertEquals(NoteSort(SortKind.TITLE, descending = false), NotionViews.noterSort(view("1", sorts = """[{"property":"Name","direction":"ascending"}]"""), schema))
        assertEquals(NoteSort(SortKind.CREATED, descending = true), NotionViews.noterSort(view("1", sorts = """[{"timestamp":"created_time","direction":"descending"}]"""), schema))
        assertEquals(NoteSort(SortKind.LAST_EDITED, descending = false), NotionViews.noterSort(view("1", sorts = """[{"timestamp":"last_edited_time","direction":"ascending"}]"""), schema))
    }

    @Test
    fun `a sort Noter can't follow is the custom order`() {
        assertEquals(NoteSort(SortKind.CUSTOM), NotionViews.noterSort(view("1"), schema))
        assertEquals(NoteSort(SortKind.CUSTOM), NotionViews.noterSort(view("1", sorts = """[{"property":"Files","direction":"ascending"}]"""), schema))
        assertEquals(NoteSort(SortKind.CUSTOM), NotionViews.noterSort(view("1", sorts = """[{"property":"Gone","direction":"ascending"}]"""), schema))
    }

    @Test
    fun `a change in a Notion view starts the Noter view from it`() {
        val setup = DatabaseSetup(quickFilters = listOf("Status"), selected = mapOf("Status" to listOf("Done")), sort = NoteSort(SortKind.TITLE))
        val copied = NotionViews.copy(setup, view("1", sorts = """[{"timestamp":"created_time","direction":"ascending"}]"""), schema)
        assertEquals("1", copied.baseView)
        assertEquals("View 1", copied.baseViewName)
        assertEquals(emptyMap<String, List<String>>(), copied.selected)
        assertEquals(NoteSort(SortKind.CREATED, descending = false), copied.sort)
        assertEquals(listOf("Status"), copied.quickFilters)
    }

    @Test
    fun `notes made since the view was asked show first`() {
        val created = mapOf("a" to "2026-11-01T10:00:00.000Z", "new" to "2026-11-12T10:00:05.000Z", "old" to "2026-11-01T09:00:00.000Z")
        assertEquals(listOf("new", "a"), NotionViews.members(listOf("a"), "2026-11-12T10:00:00Z", created))
    }
}
