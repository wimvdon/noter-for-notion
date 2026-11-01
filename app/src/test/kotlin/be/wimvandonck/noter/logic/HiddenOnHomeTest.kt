package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.HiddenItem
import be.wimvandonck.noter.data.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenOnHomeTest {
    private val page = WorkspaceItem("aaaa-bbbb", isDatabase = false, title = "Journal")
    private val database = WorkspaceItem("cccc", isDatabase = true, title = "Books")

    @Test
    fun `hiding keeps the name and kind, newest on top`() {
        val hidden = HiddenOnHome.hide(HiddenOnHome.hide(emptyList(), page), database)
        assertEquals(
            listOf(HiddenItem("cccc", "Books", isDatabase = true), HiddenItem("aaaa-bbbb", "Journal", isDatabase = false)),
            hidden,
        )
    }

    @Test
    fun `hiding twice keeps one, even when the ID is written without dashes`() {
        val again = page.copy(id = "aaaabbbb", title = "Journal 2026")
        val hidden = HiddenOnHome.hide(HiddenOnHome.hide(emptyList(), page), again)
        assertEquals(listOf(HiddenItem("aaaabbbb", "Journal 2026")), hidden)
    }

    @Test
    fun `showing again removes it, with or without dashes`() {
        val hidden = HiddenOnHome.hide(HiddenOnHome.hide(emptyList(), page), database)
        assertEquals(listOf("cccc"), HiddenOnHome.show(hidden, "aaaabbbb").map { it.id })
        assertEquals(hidden, HiddenOnHome.show(hidden, "unknown"))
    }

    @Test
    fun `ids are compared without dashes`() {
        val hidden = HiddenOnHome.hide(emptyList(), page)
        assertEquals(setOf("aaaabbbb"), HiddenOnHome.ids(hidden))
    }
}
