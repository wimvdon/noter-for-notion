package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ChoiceOrderTest {
    private val keys = listOf("a", "b", "c", "d")

    @Test
    fun `when the picker opens, what is selected comes first`() {
        assertEquals(listOf("c", "a", "b", "d"), ChoiceOrder.arrange(keys, setOf("c"), emptyList()))
        assertEquals(listOf("b", "d", "a", "c"), ChoiceOrder.arrange(keys, setOf("d", "b"), emptyList()))
        assertEquals(keys, ChoiceOrder.arrange(keys, emptySet(), emptyList()))
    }

    @Test
    fun `ticking and unticking doesn't move anything`() {
        val opened = ChoiceOrder.arrange(keys, setOf("c"), emptyList())
        val ticked = ChoiceOrder.arrange(keys, setOf("c", "d"), opened)
        assertEquals(opened, ticked)
        val unticked = ChoiceOrder.arrange(keys, setOf("d"), ticked)
        assertEquals(opened, unticked)
    }

    @Test
    fun `new choices go at the end and gone ones are dropped`() {
        val opened = ChoiceOrder.arrange(keys, setOf("c"), emptyList())
        assertEquals(listOf("c", "a", "d", "e"), ChoiceOrder.arrange(listOf("a", "c", "d", "e"), setOf("c", "e"), opened))
    }

    @Test
    fun `choices that load later are ordered once they are there`() {
        val loading = ChoiceOrder.arrange(emptyList(), setOf("b"), emptyList())
        assertEquals(emptyList<String>(), loading)
        assertEquals(listOf("b", "a", "c"), ChoiceOrder.arrange(listOf("a", "b", "c"), setOf("b"), loading))
    }

    @Test
    fun `the same key twice is shown once`() {
        assertEquals(listOf("a", "b"), ChoiceOrder.arrange(listOf("a", "b", "a"), emptySet(), emptyList()))
    }
}
