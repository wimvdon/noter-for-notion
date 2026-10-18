package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTreeTest {
    private fun page(id: String, title: String, parent: String? = null) = WorkspaceItem(id, isDatabase = false, title = title, parentId = parent)
    private fun database(id: String, title: String, parent: String? = null) = WorkspaceItem(id, isDatabase = true, title = title, parentId = parent)

    @Test
    fun `the top level is what isn't in another page of the list, by title`() {
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
