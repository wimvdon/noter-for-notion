package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.SelectOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SchemaOptionsTest {
    private val schema = DatabaseSchema(
        "db",
        "Notes",
        listOf(
            PropertySchema(name = "Name", type = "title"),
            PropertySchema(name = "Category", type = "select", options = listOf(SelectOption("Work", "blue"))),
            PropertySchema(name = "Tags", type = "multi_select", options = listOf(SelectOption("a"))),
        ),
    )

    private fun note(id: String, vararg properties: NoteProperty) = NoteSummary(id, "db", title = id, properties = properties.toList())

    @Test
    fun `an option a note has that the properties don't know yet is added once`() {
        val notes = listOf(
            note("1", NoteProperty("Category", "select", PropertyValue.Select(SelectOption("Home", "green")))),
            note("2", NoteProperty("Category", "select", PropertyValue.Select(SelectOption("Home", "green")))),
            note("3", NoteProperty("Tags", "multi_select", PropertyValue.MultiSelect(listOf(SelectOption("a"), SelectOption("b"))))),
        )
        val updated = SchemaOptions.withValues(schema, notes)
        assertEquals(listOf("Work", "Home"), updated.property("Category")!!.options.map { it.name })
        assertEquals("green", updated.property("Category")!!.options.last().color)
        assertEquals(listOf("a", "b"), updated.property("Tags")!!.options.map { it.name })
    }

    @Test
    fun `nothing new leaves the properties as they were`() {
        val notes = listOf(note("1", NoteProperty("Category", "select", PropertyValue.Select(SelectOption("Work", "blue")))))
        assertSame(schema, SchemaOptions.withValues(schema, notes))
    }
}
