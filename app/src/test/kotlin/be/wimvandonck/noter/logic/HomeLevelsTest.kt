package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLevelsTest {
    private fun page(id: String, parent: String? = null, edited: String = "2026-01-01") =
        WorkspaceItem(id, isDatabase = false, title = id, lastEditedTime = edited, parentId = parent)

    private fun source(id: String, database: String, parent: String? = null) =
        WorkspaceItem(id, isDatabase = true, title = id, databaseId = database, parentId = parent)

    @Test
    fun `level one is the pages not inside another page of the list, newest first`() {
        val items = listOf(
            page("a", edited = "2026-01-01"),
            page("b", edited = "2026-03-01"),
            page("a1", parent = "a"),
            page("c", parent = "elsewhere", edited = "2026-02-01"),
            source("src", database = "db"),
        )
        assertEquals(listOf("b", "c", "a"), HomeLevels.levelOne(items, limit = 10))
        assertEquals(listOf("b", "c"), HomeLevels.levelOne(items, limit = 2))
    }

    @Test
    fun `IDs are compared without dashes`() {
        val items = listOf(page("aa-bb"), page("child", parent = "aabb"))
        assertEquals(listOf("aa-bb"), HomeLevels.levelOne(items, limit = 10))
    }

    @Test
    fun `only children the search didn't find are missing`() {
        val items = listOf(page("a"), page("a-1", parent = "a"), source("src", database = "db-1"))
        val children = listOf(
            ChildRef("a1", isDatabase = false),
            ChildRef("a2", isDatabase = false),
            ChildRef("db1", isDatabase = true),
            ChildRef("db2", isDatabase = true),
            ChildRef("a-2", isDatabase = false),
        )
        assertEquals(listOf(ChildRef("a2", false), ChildRef("db2", true)), HomeLevels.missing(items, children))
    }
}
