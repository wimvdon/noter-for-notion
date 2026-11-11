package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.QuickAction
import be.wimvandonck.noter.data.model.SelectOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class QuickActionsTest {
    private val status = PropertySchema("st", "Status", "status", listOf(SelectOption("Todo", "red"), SelectOption("Done")))
    private val tags = PropertySchema("tg", "Tags", "multi_select", listOf(SelectOption("Home"), SelectOption("Work", "blue")))
    private val owner = PropertySchema("ow", "Owner", "people")
    private val done = PropertySchema("dn", "Done", "checkbox")
    private val due = PropertySchema("du", "Due", "date")
    private val project = PropertySchema("pr", "Project", "relation")
    private val formula = PropertySchema("fo", "Score", "formula")

    private fun group(schema: PropertySchema, vararg chips: FilterChip) = ChipGroup(schema, chips.toList(), selected = setOf("x"))

    private val groups = listOf(
        group(status, FilterChip("Todo", "Todo"), FilterChip("Done", "Done")),
        group(tags, FilterChip("Home", "Home"), FilterChip("Work", "Work")),
        group(owner, FilterChip("u1", "Ann"), FilterChip("u2", "Bob")),
        group(done, FilterChip(ChipFilter.CHECKED, ""), FilterChip(ChipFilter.UNCHECKED, "")),
        group(due, *ChipFilter.DATE_KEYS.map { FilterChip(it, "") }.toTypedArray()),
        group(project, FilterChip("page-1", "")),
        group(formula, FilterChip("3", "3")),
    )

    @Test
    fun `choices are the values a new note can get, nothing selected`() {
        val choices = QuickActions.choices(groups)
        assertEquals(listOf("Status", "Tags", "Owner", "Done", "Due", "Project"), choices.map { it.property.name })
        assertEquals(listOf(ChipFilter.TODAY), choices.first { it.property.name == "Due" }.chips.map { it.key })
        assertEquals(true, choices.all { it.selected.isEmpty() })
    }

    @Test
    fun `one-value kinds swap their value, several-value kinds collect them`() {
        var values = QuickActions.toggle(emptyMap(), "Status", "status", "Todo")
        values = QuickActions.toggle(values, "Status", "status", "Done")
        assertEquals(listOf("Done"), values["Status"])
        values = QuickActions.toggle(values, "Tags", "multi_select", "Home")
        values = QuickActions.toggle(values, "Tags", "multi_select", "Work")
        assertEquals(listOf("Home", "Work"), values["Tags"])
        // Tapped again: no longer chosen, and an empty property goes.
        values = QuickActions.toggle(values, "Status", "status", "Done")
        assertEquals(setOf("Tags"), values.keys)
    }

    @Test
    fun `a quick action gives a new note its values`() {
        val action = QuickAction(
            "a", "Work todo",
            mapOf(
                "Status" to listOf("Todo"),
                "Tags" to listOf("Work", "Home"),
                "Owner" to listOf("u2"),
                "Done" to listOf(ChipFilter.UNCHECKED),
                "Due" to listOf(ChipFilter.TODAY),
                "Project" to listOf("page-1"),
            ),
        )
        val presets = QuickActions.presets(action, groups, LocalDate.of(2026, 11, 11))
        assertEquals(
            listOf(
                NoteProperty("Status", "status", PropertyValue.Select(SelectOption("Todo", "red"))),
                NoteProperty("Tags", "multi_select", PropertyValue.MultiSelect(listOf(SelectOption("Work", "blue"), SelectOption("Home")))),
                NoteProperty("Owner", "people", PropertyValue.People(listOf(NotionUser("u2", "Bob")))),
                NoteProperty("Done", "checkbox", PropertyValue.Checkbox(false)),
                NoteProperty("Due", "date", PropertyValue.Date("2026-11-11")),
                NoteProperty("Project", "relation", PropertyValue.Relation(listOf("page-1"))),
            ),
            presets,
        )
    }

    @Test
    fun `values the database no longer has are left out`() {
        val action = QuickAction("a", "Old", mapOf("Status" to listOf("Gone"), "Renamed" to listOf("Todo"), "Tags" to listOf("Gone", "Home")))
        assertEquals(
            listOf(NoteProperty("Tags", "multi_select", PropertyValue.MultiSelect(listOf(SelectOption("Home"))))),
            QuickActions.presets(action, groups),
        )
    }

    @Test
    fun `saving adds at the end or replaces in place, never with a blank name`() {
        val a = QuickAction("a", "A")
        val b = QuickAction("b", "B")
        val list = QuickActions.save(QuickActions.save(emptyList(), a), b)
        assertEquals(listOf(a, b), list)
        assertEquals(listOf(a.copy(name = "A2"), b), QuickActions.save(list, a.copy(name = "  A2 ")))
        assertSame(list, QuickActions.save(list, QuickAction("c", "  ")))
    }

    @Test
    fun `a removed quick action comes back in its place`() {
        val a = QuickAction("a", "A")
        val b = QuickAction("b", "B")
        val c = QuickAction("c", "C")
        val removed = QuickActions.remove(listOf(a, b, c), "b")
        assertEquals(listOf(a, c), removed)
        assertEquals(listOf(a, b, c), QuickActions.restore(removed, b, 1))
        assertEquals(listOf(b), QuickActions.restore(emptyList(), b, 5))
        assertEquals(listOf(a, b, c), QuickActions.restore(listOf(a, b, c), b, 0))
    }
}
