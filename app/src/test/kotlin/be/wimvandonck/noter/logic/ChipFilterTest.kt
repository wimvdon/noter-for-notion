package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.SelectOption
import org.junit.Assert.assertEquals
import org.junit.Test

class ChipFilterTest {
    private val status = PropertySchema(name = "Status", type = "status", options = listOf(SelectOption("Todo", "gray"), SelectOption("Done", "green")))

    private fun note(id: String, status: String?, db: String = "db") =
        NoteSummary(id, db, id, properties = listOf(NoteProperty("Status", "status", PropertyValue.Select(status?.let { SelectOption(it) }))))

    private val notes = listOf(note("a", "Todo"), note("b", "Done"), note("c", null), note("d", "Todo"))

    @Test
    fun `chips are the property's options with counts`() {
        val chips = ChipFilter.chips(status, notes)
        assertEquals(listOf("Todo" to 2, "Done" to 1), chips.map { it.label to it.count })
        assertEquals("green", chips[1].color)
    }

    @Test
    fun `selected chips filter the notes, nothing selected shows all`() {
        assertEquals(listOf("a", "d"), ChipFilter.filter(notes, "Status", setOf("Todo")).map { it.id })
        assertEquals(listOf("a", "b", "d"), ChipFilter.filter(notes, "Status", setOf("Todo", "Done")).map { it.id })
        assertEquals(4, ChipFilter.filter(notes, "Status", emptySet()).size)
    }

    @Test
    fun `person chips come from the notes`() {
        val people = PropertySchema(name = "Who", type = "people")
        val withPeople = listOf(
            NoteSummary("a", "db", "a", properties = listOf(NoteProperty("Who", "people", PropertyValue.People(listOf(NotionUser("u2", "Zoe"), NotionUser("u1", "Ann")))))),
        )
        assertEquals(listOf("Ann", "Zoe"), ChipFilter.chips(people, withPeople).map { it.label })
        assertEquals(1, ChipFilter.filter(withPeople, "Who", setOf("u2")).size)
    }

    private fun notebook(selected: List<String>) =
        be.wimvandonck.noter.data.model.DatabaseConfig("n", "N", databaseId = "db", quickFilters = listOf("Status"), selected = mapOf("Status" to selected))

    private val schema = be.wimvandonck.noter.data.model.DatabaseSchema("db", "Tasks", listOf(status))

    @Test
    fun `a notebook puts pinned notes first and applies the chips`() {
        val content = Notebooks.content(notes, schema, notebook(listOf("Todo")), null, pinned = listOf("d"), customOrder = emptyList())
        assertEquals(listOf("d"), content.pinned.map { it.id })
        assertEquals(listOf("a"), content.others.map { it.id })
    }

    @Test
    fun `a chip that no longer exists is ignored`() {
        val content = Notebooks.content(notes, schema, notebook(listOf("Gone")), null, pinned = emptyList(), customOrder = emptyList())
        assertEquals(4, content.others.size)
        assertEquals(0, content.filterCount)
    }

    @Test
    fun `home shows pinned notes of all databases, newest pin first`() {
        val dbs = listOf(NoteSource("db", "One"), NoteSource("db2", "Two"))
        val byDb = mapOf("db" to notes, "db2" to listOf(note("x", null, "db2")))
        val content = NoteLists.home(dbs, byDb, pinned = listOf("x", "a"), order = emptyList(), selectedDatabases = emptySet())
        assertEquals(listOf("x", "a"), content.pinned.map { it.id })
        assertEquals(listOf("db", "db2"), content.chips.map { it.key })
        val filtered = NoteLists.home(dbs, byDb, pinned = listOf("x", "a"), order = emptyList(), selectedDatabases = setOf("db2"))
        assertEquals(listOf("x"), filtered.pinned.map { it.id })
    }

    @Test
    fun `a checkbox property filters checked and not checked notes`() {
        val done = PropertySchema(name = "Done", type = "checkbox")
        fun task(id: String, checked: Boolean) = NoteSummary(id, "db", id, properties = listOf(NoteProperty("Done", "checkbox", PropertyValue.Checkbox(checked))))
        val tasks = listOf(task("a", true), task("b", false), task("c", false))

        val chips = ChipFilter.chips(done, tasks)
        assertEquals(listOf(ChipFilter.CHECKED to 1, ChipFilter.UNCHECKED to 2), chips.map { it.key to it.count })
        assertEquals(listOf("b", "c"), ChipFilter.filter(tasks, "Done", setOf(ChipFilter.UNCHECKED)).map { it.id })
    }

    private val today = java.time.LocalDate.of(2026, 10, 17)

    private fun with(id: String, name: String, type: String, value: PropertyValue) =
        NoteSummary(id, "db", id, properties = listOf(NoteProperty(name, type, value)))

    @Test
    fun `dates fall in overdue, today, next 7 days, later or no date`() {
        val due = PropertySchema(name = "Due", type = "date")
        val notes = listOf(
            with("past", "Due", "date", PropertyValue.Date("2026-10-10")),
            with("now", "Due", "date", PropertyValue.Date("2026-10-17T09:00:00.000+02:00")),
            with("range", "Due", "date", PropertyValue.Date("2026-10-15", "2026-10-20")),
            with("soon", "Due", "date", PropertyValue.Date("2026-10-24")),
            with("far", "Due", "date", PropertyValue.Date("2026-11-30")),
            with("none", "Due", "date", PropertyValue.Date(null)),
        )
        val chips = ChipFilter.chips(due, notes, today)
        assertEquals(ChipFilter.DATE_KEYS.map { it to (if (it == ChipFilter.TODAY) 2 else 1) }, chips.map { it.key to it.count })
        assertEquals(listOf("now", "range"), ChipFilter.filter(notes, "Due", setOf(ChipFilter.TODAY), today).map { it.id })
    }

    @Test
    fun `relations, created by and formulas make chips from the notes' values`() {
        val related = listOf(
            with("a", "Project", "relation", PropertyValue.Relation(listOf("p1", "p2"))),
            with("b", "Project", "relation", PropertyValue.Relation(listOf("p1"))),
        )
        assertEquals(listOf("p1" to 2, "p2" to 1), ChipFilter.chips(PropertySchema(name = "Project", type = "relation"), related, today).map { it.key to it.count })

        val authors = listOf(with("a", "By", "created_by", PropertyValue.ReadOnly("Wim")), with("b", "By", "created_by", PropertyValue.ReadOnly("Ann")))
        assertEquals(listOf("Ann", "Wim"), ChipFilter.chips(PropertySchema(name = "By", type = "created_by"), authors, today).map { it.label })

        val done = listOf(with("a", "Done?", "formula", PropertyValue.ReadOnly("true")), with("b", "Done?", "formula", PropertyValue.ReadOnly("false")))
        assertEquals(listOf(ChipFilter.CHECKED, ChipFilter.UNCHECKED), ChipFilter.chips(PropertySchema(name = "Done?", type = "formula"), done, today).map { it.key })
        assertEquals(listOf("b"), ChipFilter.filter(done, "Done?", setOf(ChipFilter.UNCHECKED), today).map { it.id })

        val texts = listOf(with("a", "Kind", "formula", PropertyValue.ReadOnly("Big")), with("b", "Kind", "formula", PropertyValue.ReadOnly("Small")))
        assertEquals(listOf("Big", "Small"), ChipFilter.chips(PropertySchema(name = "Kind", type = "formula"), texts, today).map { it.key })
    }

    @Test
    fun `numbers among a formula's values are in order of their value`() {
        val scores = listOf("10", "2", "0", "5", "1").mapIndexed { i, v -> with("n$i", "Score", "formula", PropertyValue.ReadOnly(v)) }
        assertEquals(listOf("0", "1", "2", "5", "10"), ChipFilter.chips(PropertySchema(name = "Score", type = "formula"), scores, today).map { it.key })
        assertEquals(listOf("Week 2", "week 9", "Week 10"), listOf("Week 10", "week 9", "Week 2").sortedWith(ChipFilter.NATURAL))
    }
}
