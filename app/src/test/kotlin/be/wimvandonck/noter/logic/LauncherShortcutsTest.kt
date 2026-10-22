package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PinnedDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShortcutsTest {
    private val books = PinnedDatabase("db1", "Books", PageIcon(emoji = "📚"))
    private val tasks = PinnedDatabase("db2", " Tasks ")
    private val idea = NoteSummary("n1", "db1", "Idea", icon = PageIcon(emoji = "💡"))
    private val plan = NoteSummary("n2", "db1", "Plan")

    @Test
    fun `pinned databases first, then pinned notes in Home's order`() {
        val shortcuts = LauncherShortcuts.plan(listOf(books, tasks), listOf("n2", "n1"), listOf(idea, plan), max = 10)
        assertEquals(
            listOf(
                ShortcutTarget("database:db1", "Books", "📚", isDatabase = true),
                ShortcutTarget("database:db2", "Tasks", null, isDatabase = true),
                ShortcutTarget("note:n2", "Plan", null, isDatabase = false),
                ShortcutTarget("note:n1", "Idea", "💡", isDatabase = false),
            ),
            shortcuts,
        )
    }

    @Test
    fun `as many as the launcher shows, and notes not on the phone wait`() {
        assertEquals(2, LauncherShortcuts.plan(listOf(books, tasks), listOf("n1"), listOf(idea), max = 2).size)
        assertEquals(listOf("database:db1"), LauncherShortcuts.plan(listOf(books), listOf("missing"), listOf(idea), max = 4).map { it.key })
        assertTrue(LauncherShortcuts.plan(listOf(books), emptyList(), emptyList(), max = 0).isEmpty())
    }

    @Test
    fun `only a note or database is opened from outside`() {
        assertTrue(LauncherShortcuts.isTarget("note:n1"))
        assertTrue(LauncherShortcuts.isTarget("database:db1"))
        assertFalse(LauncherShortcuts.isTarget("note:"))
        assertFalse(LauncherShortcuts.isTarget("settings"))
    }
}
