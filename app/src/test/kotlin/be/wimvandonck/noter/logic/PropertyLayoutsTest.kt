package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyLayoutsTest {
    private fun prop(name: String, type: String = "rich_text") = NoteProperty(name, type, PropertyValue.Text(name))

    private val properties = listOf(prop("Name", "title"), prop("A"), prop("B"), prop("C"), prop("D"), prop("E"))
    private val names = listOf("A", "B", "C", "D", "E")

    @Test
    fun `shows every property by default, as Notion does`() {
        val arranged = PropertyLayouts.arrange(properties, null)
        assertEquals(names, arranged.shown.map { it.name })
        assertEquals(emptyList<String>(), arranged.hidden.map { it.name })
    }

    @Test
    fun `follows the user's order, new properties last and shown`() {
        val layout = PropertyLayout(order = listOf("E", "C", "Gone"), hidden = listOf("C"))
        assertEquals(listOf("E", "C", "A", "B", "D"), PropertyLayouts.ordered(names, layout))
        assertEquals(listOf("E", "A", "B", "D"), PropertyLayouts.arrange(properties, layout).shown.map { it.name })
        assertEquals(listOf("C"), PropertyLayouts.arrange(properties, layout).hidden.map { it.name })
    }

    @Test
    fun `arranging keeps the order of both sections`() {
        val layout = PropertyLayouts.arranged(null, shown = listOf("D", "A"), hidden = listOf("E", "B", "C"))
        assertEquals(PropertyLayouts.sections(names, layout), listOf("D", "A") to listOf("E", "B", "C"))
    }

    @Test
    fun `hiding a property puts it on top of the hidden ones, showing it at the end of the shown ones`() {
        val hidden = PropertyLayouts.toggleShown(null, names, "B")
        assertEquals(listOf("A", "C", "D", "E") to listOf("B"), PropertyLayouts.sections(names, hidden))
        val shown = PropertyLayouts.toggleShown(hidden, names, "B")
        assertEquals(listOf("A", "C", "D", "E", "B") to emptyList<String>(), PropertyLayouts.sections(names, shown))
    }

    @Test
    fun `a card shows at most three properties, in order`() {
        var layout: PropertyLayout? = null
        listOf("D", "A", "B", "C").forEach { layout = PropertyLayouts.toggleOnCard(layout, it) }
        assertEquals(listOf("D", "A", "B"), layout!!.onCard)
        assertEquals(listOf("A", "B", "D"), PropertyLayouts.cardProperties(properties, layout).map { it.name })
        layout = PropertyLayouts.toggleOnCard(layout, "A")
        assertEquals(listOf("D", "B"), layout!!.onCard)
    }

    @Test
    fun `cards show the content by default, the header image only when chosen`() {
        assertTrue(PropertyLayouts.showsContent(null))
        assertFalse(PropertyLayouts.showsCover(null, "https://img"))

        val layout = PropertyLayouts.setContentOnCard(PropertyLayouts.setCoverOnCard(null, true), false)
        assertFalse(PropertyLayouts.showsContent(layout))
        assertTrue(PropertyLayouts.showsCover(layout, "https://img"))
        // A note without a header image has nothing to show.
        assertFalse(PropertyLayouts.showsCover(layout, null))
    }
}
