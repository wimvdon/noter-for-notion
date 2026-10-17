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
    fun `shows the first three by default and collapses the rest`() {
        val arranged = PropertyLayouts.arrange(properties, null)
        assertEquals(listOf("A", "B", "C"), arranged.shown.map { it.name })
        assertEquals(listOf("D", "E"), arranged.hidden.map { it.name })
    }

    @Test
    fun `follows the user's order, new properties last`() {
        val layout = PropertyLayout(order = listOf("E", "C", "Gone"))
        assertEquals(listOf("E", "C", "A", "B", "D"), PropertyLayouts.ordered(names, layout))
        assertEquals(listOf("E", "C", "A"), PropertyLayouts.arrange(properties, layout).shown.map { it.name })
    }

    @Test
    fun `showing or collapsing a property keeps the others as they were`() {
        val hidden = PropertyLayouts.toggleShown(null, names, "B")
        assertEquals(listOf("A", "C"), hidden.shown)
        val shown = PropertyLayouts.toggleShown(hidden, names, "E")
        assertEquals(listOf("A", "C", "E"), shown.shown)
        assertEquals(listOf("B", "D"), PropertyLayouts.arrange(properties, shown).hidden.map { it.name })
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
