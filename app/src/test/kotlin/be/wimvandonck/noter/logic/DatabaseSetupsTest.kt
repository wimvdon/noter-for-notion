package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NoteSort
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.SelectOption
import be.wimvandonck.noter.data.model.SortKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseSetupsTest {
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
        assertEquals(listOf("1", "3"), DatabaseSetups.filter(notes, selected).map { it.id })
        assertEquals(listOf("1"), DatabaseSetups.filter(notes, selected + ("Tag" to setOf("Home"))).map { it.id })
    }

    @Test
    fun `sorts by dates, title or the custom order`() {
        val notes = listOf(note("1", title = "b"), note("2", title = "a"), note("3", title = "c"))
        assertEquals(listOf("3", "2", "1"), DatabaseSetups.sorted(notes, NoteSort(SortKind.LAST_EDITED), emptyList()).map { it.id })
        assertEquals(listOf("1", "2", "3"), DatabaseSetups.sorted(notes, NoteSort(SortKind.CREATED, descending = false), emptyList()).map { it.id })
        assertEquals(listOf("2", "1", "3"), DatabaseSetups.sorted(notes, NoteSort(SortKind.TITLE), emptyList()).map { it.id })
        assertEquals(listOf("2", "3", "1"), DatabaseSetups.sorted(notes, NoteSort(SortKind.CUSTOM), listOf("2", "3", "1")).map { it.id })
    }

    @Test
    fun `a database starts in the custom order, a page's order from before reads as the custom order`() {
        assertEquals(SortKind.CUSTOM, DatabaseSetup().currentSort.kind)
        assertEquals(SortKind.CUSTOM, DatabaseSetup(sort = NoteSort(SortKind.NOTION)).currentSort.kind)
        assertEquals(SortKind.TITLE, DatabaseSetup(sort = NoteSort(SortKind.TITLE)).currentSort.kind)
        val notes = listOf(note("1"), note("2"))
        assertEquals(listOf("2", "1"), DatabaseSetups.sorted(notes, NoteSort(SortKind.NOTION), listOf("2", "1")).map { it.id })
    }

    @Test
    fun `content has the quick filters, the filtered notes and pinned ones first`() {
        val notes = listOf(note("1", "Todo"), note("2", "Done"), note("3", "Todo"))
        val setup = DatabaseSetup(quickFilters = listOf("Status"), selected = mapOf("Status" to listOf("Todo")))
        val content = DatabaseSetups.content(notes, schema, setup, pinned = listOf("3"), customOrder = listOf("1", "3"))
        assertEquals(listOf("Status"), content.groups.map { it.property.name })
        assertEquals(setOf("Todo"), content.groups.single().selected)
        assertEquals(listOf("3"), content.pinned.map { it.id })
        assertEquals(listOf("1"), content.others.map { it.id })
        assertEquals(1, content.filterCount)
    }

    @Test
    fun `toggling a value adds or removes it, and drops an empty property`() {
        val on = DatabaseSetups.toggle(emptyMap(), "Status", "Todo")
        assertEquals(mapOf("Status" to listOf("Todo")), on)
        assertEquals(emptyMap<String, List<String>>(), DatabaseSetups.toggle(on, "Status", "Todo"))
    }

    @Test
    fun `a database opened for the first time shows all notes with a status property as its first chip row`() {
        val initial = DatabaseSetups.initial(schema)
        assertEquals(listOf("Status"), initial.quickFilters)
        assertEquals(SortKind.CUSTOM, initial.currentSort.kind)
        val noStatus = schema.copy(properties = schema.properties.filter { it.type != "status" })
        assertEquals(listOf("Tag"), DatabaseSetups.initial(noStatus).quickFilters)
        assertEquals(emptyList<String>(), DatabaseSetups.initial(null).quickFilters)
    }

    @Test
    fun `a database's setup is the one kept here, found with or without dashes, else the initial one`() {
        val kept = DatabaseSetup(quickFilters = listOf("Tag"), sort = NoteSort(SortKind.TITLE))
        val setups = mapOf("aaaa-1111" to kept)
        assertEquals(kept, DatabaseSetups.of(setups, "aaaa-1111", schema))
        assertEquals(kept, DatabaseSetups.of(setups, "aaaa1111", schema))
        assertEquals(listOf("Status"), DatabaseSetups.of(setups, "other", schema).quickFilters)
    }

    @Test
    fun `chip rows are the quick filters in their order, the other properties after`() {
        val (rows, others) = DatabaseSetups.chipRows(listOf("Status", "Tag", "Owner"), listOf("Tag", "Gone", "Status"))
        assertEquals(listOf("Tag", "Status"), rows)
        assertEquals(listOf("Owner"), others)
    }

    @Test
    fun `a row of chips only shows once a note has a value for it`() {
        val setup = DatabaseSetup(quickFilters = listOf("Status", "Tag"))
        val withoutTags = listOf(note("1", "Todo"), note("2", "Done"))
        assertEquals(listOf("Status"), DatabaseSetups.content(withoutTags, schema, setup, emptyList(), emptyList()).groups.map { it.property.name })
        val tagged = withoutTags + note("3", tag = "Home")
        assertEquals(listOf("Status", "Tag"), DatabaseSetups.content(tagged, schema, setup, emptyList(), emptyList()).groups.map { it.property.name })
    }
}
