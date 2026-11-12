package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.EditMode
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PropertyEdit
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.QuickAction
import be.wimvandonck.noter.data.model.SelectOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuickActionsTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val ann = NotionUser("u1", "Ann")
    private val bob = NotionUser("u2", "Bob")
    private val me = NotionUser("u9", "Me")

    private val schema = DatabaseSchema(
        "db", "Tasks",
        listOf(
            PropertySchema("t", "Name", "title"),
            PropertySchema("st", "Status", "status", listOf(SelectOption("Todo", "red"), SelectOption("Done", "green"))),
            PropertySchema("tg", "Tags", "multi_select", listOf(SelectOption("Home"), SelectOption("Work", "blue"))),
            PropertySchema("ow", "Assignee", "people"),
            PropertySchema("dn", "Done", "checkbox"),
            PropertySchema("du", "Due", "date"),
            PropertySchema("pr", "Project", "relation"),
            PropertySchema("es", "Estimate", "number"),
            PropertySchema("no", "Notes", "rich_text"),
            PropertySchema("ur", "Link", "url"),
            PropertySchema("fo", "Score", "formula"),
        ),
    )

    private fun note(vararg properties: NoteProperty) = NoteSummary("p1", "db", "Task", properties = properties.toList())

    private fun run(vararg edits: PropertyEdit, on: NoteSummary = note()) =
        QuickActions.apply(QuickAction("a", "Action", edits = edits.toList()), on, schema, me, today).associate { it.name to it.value }

    @Test
    fun `what each kind of property can do`() {
        assertEquals(listOf(EditMode.ADD, EditMode.REMOVE, EditMode.SET, EditMode.CLEAR), QuickActions.modes("people"))
        assertEquals(listOf(EditMode.SET, EditMode.ADD, EditMode.REMOVE, EditMode.CLEAR), QuickActions.modes("number"))
        assertEquals(listOf(EditMode.SET, EditMode.ADD, EditMode.CLEAR), QuickActions.modes("date"))
        assertEquals(listOf(EditMode.SET, EditMode.TOGGLE), QuickActions.modes("checkbox"))
        assertEquals(listOf(EditMode.SET), QuickActions.modes("title"))
        assertEquals(emptyList<EditMode>(), QuickActions.modes("formula"))
        assertFalse(QuickActions.properties(schema).any { it.name == "Score" })
        assertEquals(11 - 1, QuickActions.properties(schema).size)
    }

    @Test
    fun `sets options, text, numbers and links`() {
        val changed = run(
            PropertyEdit("Status", values = listOf("Done")),
            PropertyEdit("Tags", EditMode.SET, values = listOf("Work")),
            PropertyEdit("Notes", text = "Called *back*"),
            PropertyEdit("Estimate", text = "2,5"),
            PropertyEdit("Link", text = " https://example.com "),
            PropertyEdit("Name", text = "Renamed"),
        )
        assertEquals(PropertyValue.Select(SelectOption("Done", "green")), changed["Status"])
        assertEquals(PropertyValue.MultiSelect(listOf(SelectOption("Work", "blue"))), changed["Tags"])
        assertEquals(PropertyValue.Text("Called \\*back\\*"), changed["Notes"])
        assertEquals(PropertyValue.Number(2.5), changed["Estimate"])
        assertEquals(PropertyValue.Plain("https://example.com"), changed["Link"])
        assertEquals(PropertyValue.Text("Renamed"), changed["Name"])
    }

    @Test
    fun `adds and takes away options, people and pages`() {
        val on = note(
            NoteProperty("Tags", "multi_select", PropertyValue.MultiSelect(listOf(SelectOption("Home")))),
            NoteProperty("Assignee", "people", PropertyValue.People(listOf(ann))),
            NoteProperty("Project", "relation", PropertyValue.Relation(listOf("x", "y"))),
        )
        val added = run(
            PropertyEdit("Tags", EditMode.ADD, values = listOf("Work", "Home")),
            PropertyEdit("Assignee", EditMode.ADD, people = listOf(NotionUser(PropertyEdit.ME, ""), ann)),
            PropertyEdit("Project", EditMode.REMOVE, values = listOf("x")),
            on = on,
        )
        assertEquals(PropertyValue.MultiSelect(listOf(SelectOption("Home"), SelectOption("Work", "blue"))), added["Tags"])
        assertEquals(PropertyValue.People(listOf(ann, me)), added["Assignee"])
        assertEquals(PropertyValue.Relation(listOf("y")), added["Project"])
        val replaced = run(PropertyEdit("Assignee", EditMode.SET, people = listOf(bob)), PropertyEdit("Tags", EditMode.REMOVE, values = listOf("Home")), on = on)
        assertEquals(PropertyValue.People(listOf(bob)), replaced["Assignee"])
        assertEquals(PropertyValue.MultiSelect(emptyList()), replaced["Tags"])
    }

    @Test
    fun `numbers go up and down`() {
        val on = note(NoteProperty("Estimate", "number", PropertyValue.Number(3.0)))
        assertEquals(PropertyValue.Number(4.0), run(PropertyEdit("Estimate", EditMode.ADD, text = "1"), on = on)["Estimate"])
        assertEquals(PropertyValue.Number(1.5), run(PropertyEdit("Estimate", EditMode.REMOVE, text = "1.5"), on = on)["Estimate"])
        // Up from nothing counts from zero; not a number: no change.
        assertEquals(PropertyValue.Number(2.0), run(PropertyEdit("Estimate", EditMode.ADD, text = "2"))["Estimate"])
        assertEquals(emptyMap<String, PropertyValue>(), run(PropertyEdit("Estimate", EditMode.SET, text = "lots"), on = on))
    }

    @Test
    fun `dates are today plus some days, or move the date the note has`() {
        assertEquals(PropertyValue.Date("2026-11-19"), run(PropertyEdit("Due", EditMode.SET, days = 7))["Due"])
        assertEquals(PropertyValue.Date("2026-11-11"), run(PropertyEdit("Due", EditMode.SET, days = -1))["Due"])
        val due = note(NoteProperty("Due", "date", PropertyValue.Date("2026-12-30", "2027-01-02")))
        assertEquals(PropertyValue.Date("2027-01-06", "2027-01-09"), run(PropertyEdit("Due", EditMode.ADD, days = 7), on = due)["Due"])
        val moment = note(NoteProperty("Due", "date", PropertyValue.Date("2026-11-12T09:30:00.000+01:00")))
        assertEquals(PropertyValue.Date("2026-11-15T09:30:00.000+01:00"), run(PropertyEdit("Due", EditMode.ADD, days = 3), on = moment)["Due"])
        // Nothing to move: from today.
        assertEquals(PropertyValue.Date("2026-11-14"), run(PropertyEdit("Due", EditMode.ADD, days = 2))["Due"])
        assertEquals(PropertyValue.Date(null), run(PropertyEdit("Due", EditMode.CLEAR), on = due)["Due"])
    }

    @Test
    fun `checkboxes are set or toggled`() {
        val checked = note(NoteProperty("Done", "checkbox", PropertyValue.Checkbox(true)))
        assertEquals(PropertyValue.Checkbox(false), run(PropertyEdit("Done", EditMode.TOGGLE), on = checked)["Done"])
        assertEquals(PropertyValue.Checkbox(true), run(PropertyEdit("Done", EditMode.TOGGLE))["Done"])
        assertEquals(PropertyValue.Checkbox(false), run(PropertyEdit("Done", values = listOf(PropertyEdit.UNCHECKED)), on = checked)["Done"])
    }

    @Test
    fun `only what changes is returned, and what can't change is skipped`() {
        val on = note(
            NoteProperty("Status", "status", PropertyValue.Select(SelectOption("Done", "green"))),
            NoteProperty("Project", "relation", PropertyValue.Relation(listOf("x"), hasMore = true)),
        )
        val changed = run(
            PropertyEdit("Status", values = listOf("Done")),
            PropertyEdit("Project", EditMode.ADD, values = listOf("y")),
            PropertyEdit("Score", text = "1"),
            PropertyEdit("Gone", text = "1"),
            on = on,
        )
        assertEquals(emptyMap<String, PropertyValue>(), changed)
        // "Me" unknown: left out.
        val noMe = QuickActions.apply(
            QuickAction("a", "A", edits = listOf(PropertyEdit("Assignee", EditMode.ADD, people = listOf(NotionUser(PropertyEdit.ME, ""))))),
            note(), schema, me = null, today = today,
        )
        assertEquals(emptyList<NoteProperty>(), noMe)
    }

    @Test
    fun `changes run in order`() {
        val changed = run(
            PropertyEdit("Tags", EditMode.ADD, values = listOf("Home")),
            PropertyEdit("Tags", EditMode.ADD, values = listOf("Work")),
            PropertyEdit("Estimate", text = "1"),
            PropertyEdit("Estimate", EditMode.ADD, text = "2"),
        )
        assertEquals(PropertyValue.MultiSelect(listOf(SelectOption("Home"), SelectOption("Work", "blue"))), changed["Tags"])
        assertEquals(PropertyValue.Number(3.0), changed["Estimate"])
    }

    @Test
    fun `quick actions of version 2_6_14 read as changes that set their values`() {
        val old = QuickAction(
            "a", "Old",
            values = mapOf(
                "Status" to listOf("Todo"),
                "Assignee" to listOf("u2"),
                "Done" to listOf(ChipFilter.UNCHECKED),
                "Due" to listOf(ChipFilter.TODAY),
                "Gone" to listOf("x"),
            ),
        )
        assertEquals(
            listOf(
                PropertyEdit("Status", EditMode.SET, values = listOf("Todo")),
                PropertyEdit("Assignee", EditMode.SET, people = listOf(NotionUser("u2", ""))),
                PropertyEdit("Done", EditMode.SET, values = listOf(PropertyEdit.UNCHECKED)),
                PropertyEdit("Due", EditMode.SET),
            ),
            QuickActions.editsOf(old, schema),
        )
        val edits = listOf(PropertyEdit("Status", values = listOf("Done")))
        assertSame(edits, QuickActions.editsOf(old.copy(edits = edits), schema))
    }

    @Test
    fun `editing a change`() {
        val tags = schema.property("Tags")!!
        var edit = QuickActions.newEdit(tags)
        assertEquals(EditMode.ADD, edit.mode)
        assertFalse(QuickActions.isComplete(edit, "multi_select"))
        edit = QuickActions.toggleValue(QuickActions.toggleValue(edit, "multi_select", "Home"), "multi_select", "Work")
        assertEquals(listOf("Home", "Work"), edit.values)
        assertTrue(QuickActions.isComplete(edit, "multi_select"))
        // A single choice swaps; switching a select to Set keeps one.
        val status = QuickActions.toggleValue(QuickActions.toggleValue(PropertyEdit("Status"), "status", "Todo"), "status", "Done")
        assertEquals(listOf("Done"), status.values)
        assertEquals(listOf(PropertyEdit.CHECKED), QuickActions.newEdit(schema.property("Done")!!).values)
        val person = QuickActions.togglePerson(PropertyEdit("Assignee"), ann)
        assertEquals(listOf(ann), person.people)
        assertEquals(emptyList<NotionUser>(), QuickActions.togglePerson(person, ann).people)
        assertFalse(QuickActions.isComplete(PropertyEdit("Estimate", text = "x"), "number"))
        assertTrue(QuickActions.isComplete(PropertyEdit("Estimate", EditMode.CLEAR), "number"))
        assertTrue(QuickActions.isComplete(PropertyEdit("Due", days = 3), "date"))
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
