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
    fun `the other properties keep the order the user gave them`() {
        val candidates = listOf("Status", "Tag", "Owner", "Due")
        val (rows, others) = DatabaseSetups.chipRows(candidates, listOf("Status"), filterOrder = listOf("Status", "Due", "Gone", "Tag"))
        assertEquals(listOf("Status"), rows)
        // "Due" and "Tag" as placed, then "Owner", which wasn't placed yet.
        assertEquals(listOf("Due", "Tag", "Owner"), others)
    }

    @Test
    fun `arranging the filters keeps the shown ones as rows and the order of all of them`() {
        val setup = DatabaseSetups.arranged(DatabaseSetup(selected = mapOf("Tag" to listOf("Home"))), shown = listOf("Tag", "Status"), hidden = listOf("Due", "Owner"))
        assertEquals(listOf("Tag", "Status"), setup.quickFilters)
        assertEquals(listOf("Tag", "Status", "Due", "Owner"), setup.filterOrder)
        assertEquals(mapOf("Tag" to listOf("Home")), setup.selected)
        val (rows, others) = DatabaseSetups.chipRows(listOf("Status", "Tag", "Owner", "Due"), setup.quickFilters, setup.filterOrder)
        assertEquals(listOf("Tag", "Status"), rows)
        assertEquals(listOf("Due", "Owner"), others)
    }

    @Test
    fun `a row of chips only shows once a note has a value for it`() {
        val setup = DatabaseSetup(quickFilters = listOf("Status", "Tag"))
        val withoutTags = listOf(note("1", "Todo"), note("2", "Done"))
        assertEquals(listOf("Status"), DatabaseSetups.content(withoutTags, schema, setup, emptyList(), emptyList()).groups.map { it.property.name })
        val tagged = withoutTags + note("3", tag = "Home")
        assertEquals(listOf("Status", "Tag"), DatabaseSetups.content(tagged, schema, setup, emptyList(), emptyList()).groups.map { it.property.name })
    }

    private fun noteWith(id: String, vararg properties: NoteProperty) = NoteSummary(id, "db", id, properties = properties.toList())

    @Test
    fun `the sort menu offers every property that can be sorted, not only dates (#104)`() {
        val schema = DatabaseSchema(
            "db", "Household",
            listOf(
                PropertySchema("t", "Name", "title"),
                PropertySchema("d", "Due", "date"),
                PropertySchema("f", "Deadline", "formula"),
                PropertySchema("r", "Next", "rollup"),
                PropertySchema("n", "Cost", "number"),
                PropertySchema("s", "Status", "status"),
                PropertySchema("c", "Done", "checkbox"),
                PropertySchema("e", "Edited", "last_edited_time"),
                PropertySchema("l", "Linked", "relation"),
                PropertySchema("a", "Files", "files"),
                PropertySchema("b", "Do it", "button"),
            ),
        )
        val choices = DatabaseSetups.sortChoices(schema)
        assertEquals(
            listOf(SortKind.CUSTOM, SortKind.LAST_EDITED, SortKind.CREATED, SortKind.TITLE),
            choices.take(4).map { it.kind },
        )
        assertEquals(listOf("Due", "Deadline", "Next", "Cost", "Status", "Done", "Edited"), choices.drop(4).map { it.property })
        assertEquals(setOf(SortKind.PROPERTY), choices.drop(4).map { it.kind }.toSet())
        assertEquals(4, DatabaseSetups.sortChoices(null).size)
    }

    @Test
    fun `a date sort from before reads as a property sort`() {
        val sort = DatabaseSetup(sort = NoteSort(SortKind.DATE, "Due", descending = true)).currentSort
        assertEquals(NoteSort(SortKind.PROPERTY, "Due", descending = true), sort)
    }

    @Test
    fun `sorts by a formula date, notes without a value last in both directions`() {
        fun deadline(id: String, value: String) = noteWith(id, NoteProperty("Deadline", "formula", PropertyValue.ReadOnly(value)))
        val notes = listOf(deadline("1", "2026-11-03"), deadline("2", ""), deadline("3", "2026-11-01T09:00:00.000+01:00"), deadline("4", "2026-12-24"))
        val up = NoteSort(SortKind.PROPERTY, "Deadline", descending = false)
        assertEquals(listOf("3", "1", "4", "2"), DatabaseSetups.sorted(notes, up, emptyList()).map { it.id })
        assertEquals(listOf("4", "1", "3", "2"), DatabaseSetups.sorted(notes, up.copy(descending = true), emptyList()).map { it.id })
        // A sort kept from before still works.
        assertEquals(listOf("3", "1", "4", "2"), DatabaseSetups.sorted(notes, up.copy(kind = SortKind.DATE), emptyList()).map { it.id })
    }

    @Test
    fun `sorts numbers by value, also from a formula`() {
        val notes = listOf(
            noteWith("1", NoteProperty("Cost", "number", PropertyValue.Number(10.0))),
            noteWith("2", NoteProperty("Cost", "number", PropertyValue.Number(9.5))),
            noteWith("3", NoteProperty("Cost", "number", PropertyValue.Number(null))),
            noteWith("4", NoteProperty("Cost", "number", PropertyValue.Number(-1.0))),
        )
        assertEquals(listOf("4", "2", "1", "3"), DatabaseSetups.sorted(notes, NoteSort(SortKind.PROPERTY, "Cost", descending = false), emptyList()).map { it.id })
        val formulas = listOf("10", "9", "100").mapIndexed { i, v -> noteWith("$i", NoteProperty("Days", "formula", PropertyValue.ReadOnly(v))) }
        assertEquals(listOf("1", "0", "2"), DatabaseSetups.sorted(formulas, NoteSort(SortKind.PROPERTY, "Days", descending = false), emptyList()).map { it.id })
    }

    @Test
    fun `sorts options in the database's order, checkboxes unchecked first, text by name`() {
        val notes = listOf(note("1", "Done"), note("2", "Todo"), note("3", null))
        assertEquals(listOf("2", "1", "3"), DatabaseSetups.sorted(notes, NoteSort(SortKind.PROPERTY, "Status", descending = false), emptyList(), schema).map { it.id })
        val boxes = listOf(true, false).mapIndexed { i, v -> noteWith("$i", NoteProperty("Done", "checkbox", PropertyValue.Checkbox(v))) }
        assertEquals(listOf("1", "0"), DatabaseSetups.sorted(boxes, NoteSort(SortKind.PROPERTY, "Done", descending = false), emptyList()).map { it.id })
        val texts = listOf("banana", "Apple", "").mapIndexed { i, v -> noteWith("$i", NoteProperty("Note", "rich_text", PropertyValue.Text(v))) }
        assertEquals(listOf("1", "0", "2"), DatabaseSetups.sorted(texts, NoteSort(SortKind.PROPERTY, "Note", descending = false), emptyList()).map { it.id })
    }
}
