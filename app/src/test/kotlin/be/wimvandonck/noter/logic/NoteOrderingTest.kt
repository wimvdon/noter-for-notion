package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteOrderingTest {
    private fun note(id: String, edited: String = "2026-01-01T00:00:00.000Z") = NoteSummary(id, "db", id, lastEditedTime = edited)

    @Test
    fun `without a custom order the most recently edited comes first`() {
        val notes = listOf(note("a", "2026-01-01T00:00:00.000Z"), note("b", "2026-02-01T00:00:00.000Z"))
        assertEquals(listOf("b", "a"), NoteOrdering.sort(notes, emptyList()).map { it.id })
    }

    @Test
    fun `placed notes keep their place and new ones come first`() {
        val notes = listOf(note("a"), note("b"), note("c"), note("new"))
        assertEquals(listOf("new", "c", "a", "b"), NoteOrdering.sort(notes, listOf("c", "a", "b")).map { it.id })
    }

    @Test
    fun `pinned notes go on top`() {
        val arranged = NoteOrdering.arrange(listOf(note("a"), note("b"), note("c")), setOf("c"), listOf("a", "b", "c"))
        assertEquals(listOf("c"), arranged.pinned.map { it.id })
        assertEquals(listOf("a", "b"), arranged.others.map { it.id })
    }

    @Test
    fun `move puts an item at a new position`() {
        assertEquals(listOf("b", "c", "a"), NoteOrdering.move(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("a", "b"), NoteOrdering.move(listOf("a", "b"), 0, 5))
        // Also for notebooks dragged in Manage notebooks.
        assertEquals(listOf(3, 1, 2), NoteOrdering.move(listOf(1, 2, 3), 2, 0))
    }

    @Test
    fun `reordering a filtered list keeps the hidden notes in place`() {
        // b and d are visible (filter on); the user puts d before b.
        assertEquals(listOf("a", "d", "c", "b"), NoteOrdering.applySubsetOrder(listOf("a", "b", "c", "d"), listOf("d", "b")))
    }

    @Test
    fun `reordering notes that were never placed adds them`() {
        assertEquals(listOf("y", "x", "a"), NoteOrdering.applySubsetOrder(listOf("a"), listOf("y", "x")))
    }

    @Test
    fun `rename replaces a local ID`() {
        assertEquals(listOf("a", "n1"), NoteOrdering.rename(listOf("a", "local-1"), "local-1", "n1"))
    }
}
