package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.CardLayout
import be.wimvandonck.noter.data.model.DatabaseConfig
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
        var cards = CardLayout()
        listOf("D", "A", "B", "C").forEach { cards = PropertyLayouts.toggleOnCard(cards, it) }
        assertEquals(listOf("D", "A", "B"), cards.properties)
        val layout = PropertyLayout(onCard = cards.properties)
        assertEquals(listOf("A", "B", "D"), PropertyLayouts.cardProperties(properties, layout).map { it.name })
        cards = PropertyLayouts.toggleOnCard(cards, "A")
        assertEquals(listOf("D", "B"), cards.properties)
    }

    @Test
    fun `cards show the content by default, the header image only when chosen`() {
        assertTrue(PropertyLayouts.showsContent(null))
        assertFalse(PropertyLayouts.showsCover(null, "https://img"))

        val layout = PropertyLayout(coverOnCard = true, contentOnCard = false)
        assertFalse(PropertyLayouts.showsContent(layout))
        assertTrue(PropertyLayouts.showsCover(layout, "https://img"))
        // A note without a header image has nothing to show.
        assertFalse(PropertyLayouts.showsCover(layout, null))
    }

    @Test
    fun `each notebook has its own cards, starting from what its database's cards showed`() {
        val legacy = PropertyLayout(order = listOf("B", "A"), onCard = listOf("A"), coverOnCard = true)
        val first = DatabaseConfig("n1", "First", databaseId = "db")
        val second = DatabaseConfig("n2", "Second", databaseId = "db", cards = CardLayout(content = false, properties = listOf("B")))
        assertEquals(CardLayout(cover = true, content = true, properties = listOf("A")), PropertyLayouts.cards(first, legacy))
        assertEquals(second.cards, PropertyLayouts.cards(second, legacy))

        val layouts = mapOf("db" to legacy)
        // The notebook shown decides; elsewhere (Pinned) the database's first notebook.
        val shown = PropertyLayouts.cardLayouts(layouts, listOf(first, second), current = second).getValue("db")
        assertEquals(listOf("B"), shown.onCard)
        assertFalse(shown.contentOnCard)
        assertEquals(listOf("B", "A"), shown.order)
        assertEquals(listOf("A"), PropertyLayouts.cardLayouts(layouts, listOf(first, second)).getValue("db").onCard)
        // A notebook of a database that has no layout yet.
        assertEquals(listOf("B"), PropertyLayouts.cardLayouts(emptyMap(), listOf(second)).getValue("db").onCard)
    }
}
