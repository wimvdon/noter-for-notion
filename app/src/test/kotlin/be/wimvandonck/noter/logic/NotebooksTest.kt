package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotebookSort
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.SelectOption
import be.wimvandonck.noter.data.model.SortKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotebooksTest {
    private fun note(id: String, status: String? = null, tag: String? = null, edited: String = "2026-10-0$id", created: String = edited, title: String = id) =
        NoteSummary(
            id, "db", title, createdTime = created, lastEditedTime = edited,
            properties = listOf(
                NoteProperty("Status", "status", PropertyValue.Select(status?.let { SelectOption(it) })),
                NoteProperty("Tag", "select", PropertyValue.Select(tag?.let { SelectOption(it) })),
            ),
        )

    private val schema = DatabaseSchema(
        "db", "Tasks",
        listOf(
            PropertySchema("title", "Name", "title"),
            PropertySchema("st", "Status", "status", listOf(SelectOption("Todo"), SelectOption("Done"))),
            PropertySchema("tg", "Tag", "select", listOf(SelectOption("Home"), SelectOption("Work"))),
        ),
    )

    @Test
    fun `every filtering property must match, any of its values`() {
        val notes = listOf(note("1", "Todo", "Home"), note("2", "Done", "Home"), note("3", "Todo", "Work"))
        val selected = mapOf("Status" to setOf("Todo"), "Tag" to setOf("Home", "Work"))
        assertEquals(listOf("1", "3"), Notebooks.filter(notes, selected).map { it.id })
        assertEquals(listOf("1"), Notebooks.filter(notes, selected + ("Tag" to setOf("Home"))).map { it.id })
    }

    @Test
    fun `sorts by Notion's order, dates, title or the custom order`() {
        val notes = listOf(note("1", title = "b"), note("2", title = "a"), note("3", title = "c"))
        assertEquals(listOf("3", "1", "2"), Notebooks.sorted(notes, NotebookSort(SortKind.NOTION), listOf("3", "1", "2"), emptyList()).map { it.id })
        assertEquals(listOf("3", "2", "1"), Notebooks.sorted(notes, NotebookSort(SortKind.LAST_EDITED), emptyList(), emptyList()).map { it.id })
        assertEquals(listOf("1", "2", "3"), Notebooks.sorted(notes, NotebookSort(SortKind.CREATED, descending = false), emptyList(), emptyList()).map { it.id })
        assertEquals(listOf("2", "1", "3"), Notebooks.sorted(notes, NotebookSort(SortKind.TITLE), emptyList(), emptyList()).map { it.id })
        assertEquals(listOf("2", "3", "1"), Notebooks.sorted(notes, NotebookSort(SortKind.CUSTOM), emptyList(), listOf("2", "3", "1")).map { it.id })
    }

    @Test
    fun `only a page's notebook has Notion's order, a database's starts in the custom order`() {
        assertEquals(SortKind.NOTION, DatabaseConfig("n", "N", databaseId = "page:p", pageId = "p").currentSort.kind)
        assertEquals(SortKind.CUSTOM, DatabaseConfig("n", "N", databaseId = "db").currentSort.kind)
        // A notebook that followed a Notion view before, sorted in its order: the custom order now.
        assertEquals(SortKind.CUSTOM, DatabaseConfig("n", "N", databaseId = "db", sort = NotebookSort(SortKind.NOTION)).currentSort.kind)
        assertEquals(SortKind.TITLE, DatabaseConfig("n", "N", databaseId = "db", sort = NotebookSort(SortKind.TITLE)).currentSort.kind)
    }

    @Test
    fun `content has the quick filters, the filtered notes and pinned ones first`() {
        val notes = listOf(note("1", "Todo"), note("2", "Done"), note("3", "Todo"))
        val config = DatabaseConfig("n", "N", databaseId = "db", quickFilters = listOf("Status"), selected = mapOf("Status" to listOf("Todo")))
        val content = Notebooks.content(notes, schema, config, null, pinned = listOf("3"), customOrder = listOf("1", "3"))
        assertEquals(listOf("Status"), content.groups.map { it.property?.name })
        assertEquals(setOf("Todo"), content.groups.single().selected)
        assertEquals(listOf("3"), content.pinned.map { it.id })
        assertEquals(listOf("1"), content.others.map { it.id })
        assertEquals(1, content.filterCount)
    }

    @Test
    fun `toggling a value adds or removes it, and drops an empty property`() {
        val on = Notebooks.toggle(emptyMap(), "Status", "Todo")
        assertEquals(mapOf("Status" to listOf("Todo")), on)
        assertEquals(emptyMap<String, List<String>>(), Notebooks.toggle(on, "Status", "Todo"))
    }

    @Test
    fun `a new notebook shows all notes with a status property as its first chip row`() {
        val base = DatabaseConfig("n", "Tasks", databaseId = "db", sort = NotebookSort(SortKind.TITLE))
        val new = Notebooks.newNotebook(base, schema)
        assertEquals(listOf("Status"), new.quickFilters)
        assertNull(new.sort)
        val noStatus = schema.copy(properties = schema.properties.filter { it.type != "status" })
        assertEquals(listOf("Tag"), Notebooks.newNotebook(base, noStatus).quickFilters)
        assertEquals(emptyList<String>(), Notebooks.newNotebook(base, null).quickFilters)
    }

    @Test
    fun `chip rows are the quick filters in their order, the other properties after`() {
        val (rows, others) = Notebooks.chipRows(listOf("Status", "Tag", "Owner"), listOf("Tag", "Gone", "Status"))
        assertEquals(listOf("Tag", "Status"), rows)
        assertEquals(listOf("Owner"), others)
    }

    @Test
    fun `a notebook from before quick filters keeps its filter property and filters`() {
        val old = DatabaseConfig("n", "N", databaseId = "db", chipProperty = "Status", filters = listOf("Todo")).normalized()
        assertEquals(listOf("Status"), old.quickFilters)
        assertEquals(mapOf("Status" to listOf("Todo")), old.selected)
        assertNull(old.chipProperty)
    }

    @Test
    fun `a row of chips only shows once a note has a value for it`() {
        val config = DatabaseConfig("n", "N", databaseId = "db", quickFilters = listOf("Status", "Tag"))
        val withoutTags = listOf(note("1", "Todo"), note("2", "Done"))
        assertEquals(listOf("Status"), Notebooks.content(withoutTags, schema, config, null, emptyList(), emptyList()).groups.map { it.property?.name })
        val tagged = withoutTags + note("3", tag = "Home")
        assertEquals(listOf("Status", "Tag"), Notebooks.content(tagged, schema, config, null, emptyList(), emptyList()).groups.map { it.property?.name })
    }
}
