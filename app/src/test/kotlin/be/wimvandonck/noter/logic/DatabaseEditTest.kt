package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DatabaseEditTest {
    private val star = PageIcon(emoji = "⭐")

    @Test
    fun `a new name is sent as Notion's title, trimmed`() {
        val body = DatabaseEdit.rename("  Books ").body()
        assertEquals(listOf(mapOf("type" to "text", "text" to mapOf("content" to "Books"))), body["title"])
        assertFalse("icon" in body)
    }

    @Test
    fun `an icon is sent as an emoji, and removed as null`() {
        assertEquals(mapOf("icon" to mapOf("type" to "emoji", "emoji" to "📚")), DatabaseEdit.icon("📚").body())
        val removed = DatabaseEdit.icon(null).body()
        assertEquals(setOf("icon"), removed.keys)
        assertEquals(null, removed["icon"])
    }

    @Test
    fun `Home's rows of the database and its data source change, others don't`() {
        val items = listOf(
            WorkspaceItem("db-1", isDatabase = true, title = "Old", icon = star),
            WorkspaceItem("src1", isDatabase = true, title = "Old"),
            WorkspaceItem("other", isDatabase = false, title = "Page", icon = star),
        )
        val renamed = DatabaseEdit.rename("New").applyTo(items, setOf("db1", "src1"))
        assertEquals(listOf("New", "New", "Page"), renamed.map { it.title })
        assertEquals(star, renamed[0].icon)

        val noIcon = DatabaseEdit.icon(null).applyTo(items, setOf("db1"))
        assertEquals(listOf(null, null, star), noIcon.map { it.icon })
        assertEquals("Old", noIcon[0].title)
    }

    @Test
    fun `pins and what is kept about the database follow`() {
        val pins = listOf(PinnedDatabase("src-1", "Old"), PinnedDatabase("x", "X", star))
        assertEquals(
            listOf(PinnedDatabase("src-1", "Old", PageIcon(emoji = "📚")), PinnedDatabase("x", "X", star)),
            DatabaseEdit.icon("📚").applyToPins(pins, setOf("src1")),
        )
        assertEquals(DatabaseInfo("d", "New", star), DatabaseEdit.rename("New").applyTo(DatabaseInfo("d", "Old", star)))
    }
}
