package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotebookSort
import be.wimvandonck.noter.data.model.NotionView
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.SelectOption
import be.wimvandonck.noter.data.model.SortKind
import be.wimvandonck.noter.data.model.ViewResult
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
    fun `a view shows its notes, and the ones Notion can't know about yet`() {
        val notes = listOf(note("1"), note("2"), note("3", created = "2026-10-10"), NoteSummary("local-x", "db", ""))
        val result = ViewResult(listOf("1"), fetchedAt = "2026-10-09")
        assertEquals(listOf("1", "3", "local-x"), Notebooks.inView(notes, result).map { it.id })
        assertEquals(4, Notebooks.inView(notes, null).size)
    }

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
    fun `a notebook following a view starts in Notion's order, otherwise in the custom order`() {
        assertEquals(SortKind.NOTION, DatabaseConfig("n", "N", databaseId = "db", viewId = "v").currentSort.kind)
        assertEquals(SortKind.CUSTOM, DatabaseConfig("n", "N", databaseId = "db", viewId = "v", followView = false).currentSort.kind)
        assertEquals(SortKind.CUSTOM, DatabaseConfig("n", "N", databaseId = "db").currentSort.kind)
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
    fun `a new notebook takes its quick filters and layout from the Notion view`() {
        val base = DatabaseConfig("n", "Tasks", databaseId = "db")
        val board = NotionView("v", "Board", "board", groupBy = "st", properties = listOf("tg" to false, "st" to true), showsCover = true)
        val fromBoard = Notebooks.fromView(base, board, schema)
        assertEquals(listOf("Status"), fromBoard.quickFilters)
        assertEquals("v", fromBoard.viewId)
        val withQuick = Notebooks.fromView(base, board.copy(quickFilters = listOf("Tag")), schema)
        assertEquals(listOf("Tag"), withQuick.quickFilters)
        val layout = Notebooks.layoutFromView(null, board, schema)!!
        assertEquals(listOf("Tag", "Status"), layout.order)
        assertEquals(listOf("Tag"), layout.hidden)
        assertEquals(true, layout.coverOnCard)
        // Arranged by the user already: kept.
        val own = PropertyLayout(order = listOf("Status"))
        assertEquals(own, Notebooks.layoutFromView(own, board, schema))
        // No view: all notes, a status property as quick filter.
        val all = Notebooks.fromView(base, null, schema)
        assertNull(all.viewId)
        assertEquals(listOf("Status"), all.quickFilters)
    }

    @Test
    fun `a notebook from before quick filters keeps its filter property and filters`() {
        val old = DatabaseConfig("n", "N", databaseId = "db", chipProperty = "Status", filters = listOf("Todo")).normalized()
        assertEquals(listOf("Status"), old.quickFilters)
        assertEquals(mapOf("Status" to listOf("Todo")), old.selected)
        assertNull(old.chipProperty)
    }
}
