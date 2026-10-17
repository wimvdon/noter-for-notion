package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSearchTest {
    private fun note(id: String, title: String, edited: String) = NoteSummary(id, "db", title, lastEditedTime = edited)

    @Test
    fun `every word, any case or accent, newest first`() {
        val notes = listOf(
            note("1", "Café in **Gent**", "2026-10-01"),
            note("2", "Gent to-do", "2026-10-05"),
            note("3", "Brussel", "2026-10-09"),
        )
        assertEquals(listOf("2", "1"), NoteSearch.matches(notes, "gent").map { it.id })
        assertEquals(listOf("1"), NoteSearch.matches(notes, "cafe GENT").map { it.id })
    }

    @Test
    fun `nothing typed finds nothing`() {
        assertEquals(emptyList<NoteSummary>(), NoteSearch.matches(listOf(note("1", "A", "")), "  "))
    }
}
