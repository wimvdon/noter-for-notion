package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTreeTest {
    private fun page(id: String, title: String, parent: String? = null, edited: String = "") =
        WorkspaceItem(id, isDatabase = false, title = title, parentId = parent, lastEditedTime = edited)
    private fun database(id: String, title: String, parent: String? = null, edited: String = "") =
        WorkspaceItem(id, isDatabase = true, title = title, parentId = parent, lastEditedTime = edited)

    @Test
    fun `the top level is what isn't in another page of the list, by title when times are the same`() {
        val tree = PageTree.build(
            listOf(
                page("b", "Work"),
                page("a", "home"),
                // In a page the connection can't see: at the top too.
                page("c", "Shared with me", parent = "hidden"),
                page("d", ""),
            ),
        )
        assertEquals(listOf("a", "c", "b", "d"), tree.map { it.item.id })
        assertTrue(tree.all { it.children.isEmpty() })
    }

    @Test
    fun `a page shows the databases and pages right inside it, deeper ones not`() {
        val tree = PageTree.build(
            listOf(
                page("child-2", "Recipes", parent = "home"),
                page("home", "Home"),
                database("db", "Books", parent = "home"),
                page("grandchild", "Soups", parent = "child-2"),
            ),
        )
        assertEquals(listOf("home"), tree.map { it.item.id })
        assertEquals(listOf("db", "child-2"), tree.single().children.map { it.id })
    }

    @Test
    fun `the most recently edited come first, a page counting what is right inside it`() {
        val tree = PageTree.build(
            listOf(
                page("old", "Old", edited = "2026-10-01T10:00:00.000Z"),
                page("new", "New", edited = "2026-10-05T10:00:00.000Z"),
                page("quiet", "Quiet parent", edited = "2026-09-01T10:00:00.000Z"),
                page("busy", "Busy child", parent = "quiet", edited = "2026-10-09T10:00:00.000Z"),
                database("db", "Older child", parent = "quiet", edited = "2026-09-02T10:00:00.000Z"),
            ),
        )
        assertEquals(listOf("quiet", "new", "old"), tree.map { it.item.id })
        assertEquals(listOf("busy", "db"), tree.first().children.map { it.id })
    }

    @Test
    fun `IDs match with or without dashes, and each item shows once`() {
        val tree = PageTree.build(
            listOf(
                page("aaaa-bbbb", "Parent"),
                page("cccc", "Child", parent = "aaaabbbb"),
                page("aaaabbbb", "Parent again"),
            ),
        )
        assertEquals(listOf("aaaa-bbbb"), tree.map { it.item.id })
        assertEquals(listOf("cccc"), tree.single().children.map { it.id })
    }

    @Test
    fun `nothing gives an empty tree`() {
        assertTrue(PageTree.build(emptyList()).isEmpty())
    }
}
