package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.CardLayout
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteSort
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.SortKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaEditsTest {
    private val title = PropertySchema("title", "Name", "title")
    private val tags = PropertySchema("a1", "Tags", "multi_select")
    private val due = PropertySchema("b2", "Due", "date")
    private val original = listOf(tags, title, due)

    @Test
    fun `starts with the title property first`() {
        val start = SchemaEdits.start(original)
        assertEquals(listOf("Name", "Tags", "Due"), start.map { it.name })
        assertFalse(start[0].removable)
        assertTrue(start[1].removable)
    }

    @Test
    fun `nothing changed sends nothing`() {
        assertTrue(SchemaEdits.change(original, SchemaEdits.start(original)).isEmpty)
    }

    @Test
    fun `a new name renames the property by its ID`() {
        val edited = SchemaEdits.start(original).map { if (it.id == "a1") it.copy(name = " Labels ") else it }
        val change = SchemaEdits.change(original, edited)
        assertEquals(mapOf("a1" to mapOf("name" to "Labels")), change.properties)
        assertEquals(mapOf("Tags" to "Labels"), change.renamed)
    }

    @Test
    fun `a blank name keeps the old one`() {
        val edited = SchemaEdits.start(original).map { if (it.id == "a1") it.copy(name = "  ") else it }
        assertTrue(SchemaEdits.change(original, edited).isEmpty)
    }

    @Test
    fun `a property left out is removed`() {
        val edited = SchemaEdits.start(original).filter { it.id != "b2" }
        val change = SchemaEdits.change(original, edited)
        assertEquals(mapOf("b2" to null), change.properties)
        assertEquals(setOf("Due"), change.removed)
    }

    @Test
    fun `a new property is added with its kind`() {
        val edited = SchemaEdits.add(SchemaEdits.start(original), PropertyKind.NUMBER, "Number")
        assertEquals(mapOf("Number" to mapOf("number" to mapOf("format" to "number"))), SchemaEdits.change(original, edited).properties)
    }

    @Test
    fun `a new property gets a number when its name is taken`() {
        val edited = SchemaEdits.add(SchemaEdits.start(original), PropertyKind.DATE, "Due")
        assertEquals("Due 2", edited.last().name)
        // Typed back to a taken name: still made unique.
        val typed = edited.dropLast(1) + edited.last().copy(name = "due")
        assertEquals(setOf("due 2"), SchemaEdits.change(original, typed).properties.keys)
    }

    @Test
    fun `a rename to a name another keeps gets a number`() {
        val edited = SchemaEdits.start(original).map { if (it.id == "b2") it.copy(name = "Tags") else it }
        assertEquals(mapOf("b2" to mapOf("name" to "Tags 2")), SchemaEdits.change(original, edited).properties)
    }

    @Test
    fun `a removed property's name is free for a rename`() {
        val edited = SchemaEdits.start(original).filter { it.id != "a1" }.map { if (it.id == "b2") it.copy(name = "Tags") else it }
        assertEquals(mapOf("a1" to null, "b2" to mapOf("name" to "Tags")), SchemaEdits.change(original, edited).properties)
    }

    @Test
    fun `the setup follows renamed and removed properties`() {
        val change = SchemaChange(mapOf("x" to null), renamed = mapOf("Tags" to "Labels"), removed = setOf("Due"))
        val setup = DatabaseSetup(
            quickFilters = listOf("Tags", "Due"),
            filterOrder = listOf("Due", "Tags", "Status"),
            selected = mapOf("Tags" to listOf("a"), "Due" to listOf("today")),
            sort = NoteSort(SortKind.DATE, property = "Due"),
            cards = CardLayout(properties = listOf("Tags", "Due")),
        )
        val changed = change.applyTo(setup)
        assertEquals(listOf("Labels"), changed.quickFilters)
        assertEquals(listOf("Labels", "Status"), changed.filterOrder)
        assertEquals(mapOf("Labels" to listOf("a")), changed.selected)
        assertNull(changed.sort)
        assertEquals(listOf("Labels"), changed.cards?.properties)
        assertEquals("Labels", change.applyTo(setup.copy(sort = NoteSort(SortKind.DATE, property = "Tags"))).sort?.property)
    }

    @Test
    fun `the note's layout follows renamed and removed properties`() {
        val change = SchemaChange(mapOf("x" to null), renamed = mapOf("Tags" to "Labels"), removed = setOf("Due"))
        val layout = PropertyLayout(order = listOf("Due", "Tags"), hidden = listOf("Tags"), onCard = listOf("Due"))
        assertEquals(PropertyLayout(order = listOf("Labels"), hidden = listOf("Labels"), onCard = emptyList()), change.applyTo(layout))
    }
}
