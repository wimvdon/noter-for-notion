package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.PinnedDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteListsTest {
    private fun pin(id: String) = PinnedDatabase(id, "Database $id")

    @Test
    fun `pinned databases follow the order they were dragged to`() {
        val pins = listOf(pin("a"), pin("b"), pin("c"))
        assertEquals(listOf("c", "a", "b"), NoteLists.reorderDatabases(pins, listOf("c", "a", "b")).map { it.id })
    }

    @Test
    fun `a pinned database that is not in the new order keeps its place after the others`() {
        val pins = listOf(pin("a"), pin("b"), pin("c"))
        assertEquals(listOf("b", "a", "c"), NoteLists.reorderDatabases(pins, listOf("b", "a")).map { it.id })
        assertEquals(listOf("b", "a", "c"), NoteLists.reorderDatabases(pins, listOf("b", "gone", "a")).map { it.id })
    }

    @Test
    fun `IDs are matched with or without dashes`() {
        val pins = listOf(pin("11-22"), pin("33-44"))
        assertEquals(listOf("33-44", "11-22"), NoteLists.reorderDatabases(pins, listOf("3344", "1122")).map { it.id })
    }
}
