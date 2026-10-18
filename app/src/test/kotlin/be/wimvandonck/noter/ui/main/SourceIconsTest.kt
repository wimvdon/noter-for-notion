package be.wimvandonck.noter.ui.main

import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceIconsTest {
    private val tasks = PageIcon(emoji = "✅")
    private val health = PageIcon(emoji = "❤️")
    private val books = PageIcon(emoji = "📚")

    @Test
    fun `a pinned note's card shows its database's icon, also for a data source of a database of several`() {
        val pinned = listOf(PinnedDatabase("ds-tasks", "Tasks", tasks), PinnedDatabase("db-health", "Health", health), PinnedDatabase("ds-plain", "Plain"))
        val recent = listOf(
            WorkspaceItem("db-books", isDatabase = true, title = "Books", icon = books, sources = listOf(PageRef("ds-read", "Read"))),
            WorkspaceItem("page", isDatabase = false, title = "A page", icon = PageIcon(emoji = "📄")),
        )
        val infos = mapOf("db-health" to DatabaseInfo("db-health", "Health", sources = listOf(PageRef("ds-skin", "Skin"), PageRef("ds-food", "Food"))))

        val icons = sourceIcons(pinned, recent, infos)

        assertEquals(tasks, icons["ds-tasks"])
        assertEquals(health, icons["ds-skin"])
        assertEquals(health, icons["ds-food"])
        assertEquals(books, icons["ds-read"])
        // Pages aren't databases, and a database without an icon has none.
        assertNull(icons["page"])
        assertNull(icons["ds-plain"])
    }
}
