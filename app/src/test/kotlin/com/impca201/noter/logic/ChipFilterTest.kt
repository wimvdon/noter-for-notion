package com.impca201.noter.logic

import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.model.NotionUser
import com.impca201.noter.data.model.PropertySchema
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.data.model.SelectOption
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

    @Test
    fun `the database tab puts pinned notes first and applies the chips`() {
        val content = NoteLists.database(notes, status, setOf("Todo"), pinned = listOf("d"), order = emptyList())
        assertEquals(listOf("d"), content.pinned.map { it.id })
        assertEquals(listOf("a"), content.others.map { it.id })
    }

    @Test
    fun `a chip that no longer exists is ignored`() {
        val content = NoteLists.database(notes, status, setOf("Gone"), pinned = emptyList(), order = emptyList())
        assertEquals(4, content.others.size)
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
}
