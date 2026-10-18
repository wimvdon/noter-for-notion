package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTargetsTest {
    @Test
    fun `pinned databases come first, each data source once, then the other databases on Home`() {
        val pinned = listOf(PinnedDatabase("aaaa-1111", "Tasks", PageIcon(emoji = "✅")), PinnedDatabase("bbbb", "Ideas"))
        val recent = listOf(
            WorkspaceItem("aaaa1111", isDatabase = true, title = "Tasks again"),
            WorkspaceItem("cccc", isDatabase = true, title = "Recipes", icon = PageIcon(emoji = "🍲")),
            WorkspaceItem("page", isDatabase = false, title = "A page"),
        )

        val targets = NoteTargets.of(pinned, recent, emptyMap())

        assertEquals(listOf("Tasks", "Ideas", "Recipes"), targets.map { it.name })
        assertEquals("✅", targets.first().icon?.emoji)
        assertEquals("🍲", targets.last().icon?.emoji)
    }

    @Test
    fun `a database of several data sources offers each of them`() {
        val sources = listOf(PageRef("ds-skin", "Skin"), PageRef("ds-food", "Food"))
        val pinned = listOf(PinnedDatabase("db-health", "Health"))
        val recent = listOf(WorkspaceItem("db-home", isDatabase = true, title = "Home", sources = listOf(PageRef("ds-a", "Rooms"), PageRef("ds-b", "Plants"))))

        val targets = NoteTargets.of(pinned, recent, mapOf("db-health" to DatabaseInfo("db-health", "Health", sources = sources)))

        assertEquals(listOf("Health · Skin", "Health · Food", "Home · Rooms", "Home · Plants"), targets.map { it.name })
        assertEquals(listOf("ds-skin", "ds-food", "ds-a", "ds-b"), targets.map { it.databaseId })
    }

    @Test
    fun `before Home's list has loaded only the pins count`() {
        assertEquals(listOf("bbbb"), NoteTargets.of(listOf(PinnedDatabase("bbbb", "Ideas")), null, emptyMap()).map { it.databaseId })
    }
}
