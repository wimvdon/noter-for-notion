package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTargetsTest {
    @Test
    fun `views come first, each database once, then the other databases from Browse`() {
        val views = listOf(
            DatabaseConfig("v1", "Todo", icon = "checklist", databaseId = "aaaa-1111"),
            DatabaseConfig("v2", "Done", databaseId = "aaaa-1111"),
            DatabaseConfig("v3", "Ideas", databaseId = "bbbb"),
        )
        val browse = listOf(
            WorkspaceItem("aaaa1111", isDatabase = true, title = "Tasks"),
            WorkspaceItem("cccc", isDatabase = true, title = "Recipes", icon = PageIcon(emoji = "🍲")),
            WorkspaceItem("page", isDatabase = false, title = "A page"),
        )

        val targets = NoteTargets.of(views, browse)

        assertEquals(listOf("Todo", "Ideas", "Recipes"), targets.map { it.name })
        assertEquals("checklist", targets.first().iconKey)
        assertEquals("🍲", targets.last().pageIcon?.emoji)
    }

    @Test
    fun `before Browse has loaded only the views count`() {
        assertEquals(listOf("bbbb"), NoteTargets.of(listOf(DatabaseConfig("v", "Ideas", databaseId = "bbbb")), null).map { it.databaseId })
    }
}
