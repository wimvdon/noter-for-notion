package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.notion.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacesTest {
    private val work = Workspace("w1", "Work", TokenKind.PERSONAL)
    private val club = Workspace("w2", "Club", TokenKind.CONNECTION)
    private val owners = mapOf("page1" to "w1", "db2" to "w2", "gone" to "removed")

    @Test
    fun `a token's id is short, stable and not the token`() {
        val id = Workspaces.id("ntn_secret")
        assertEquals(16, id.length)
        assertEquals(id, Workspaces.id("ntn_secret"))
        assertNotEquals(id, Workspaces.id("ntn_other"))
        assertFalse(id.contains("secret"))
    }

    @Test
    fun `workspaces are named after the workspace, else the person, each once`() {
        val list = Workspaces.of(
            listOf(
                WorkspaceToken("a", "Wim", "Work", TokenKind.PERSONAL),
                WorkspaceToken("b", "Club bot", ""),
                WorkspaceToken("a", "Wim", "Work"),
                WorkspaceToken(""),
            ),
        )
        assertEquals(listOf("Work", "Club bot"), list.map { it.name })
        assertEquals(listOf(Workspaces.id("a"), Workspaces.id("b")), list.map { it.id })
        assertEquals("Wim", list.first().person)
    }

    @Test
    fun `the owner is the first id known to be in a workspace there is`() {
        val spaces = listOf(work, club)
        assertEquals(work, Workspaces.ownerOf(listOf("unknown", "PAGE-1"), owners, spaces))
        assertEquals(club, Workspaces.ownerOf(listOf(null, "db2"), owners, spaces))
        assertNull(Workspaces.ownerOf(listOf("gone"), owners, spaces))
    }

    @Test
    fun `groups follow the workspaces' order, the unknown last, and leave out empty ones`() {
        val groups = Workspaces.group(listOf("db2", "x", "page1"), owners, listOf(work, club, Workspace("w3", "Empty"))) { listOf(it) }
        assertEquals(listOf(work, club, null), groups.map { it.workspace })
        assertEquals(listOf(listOf("page1"), listOf("db2"), listOf("x")), groups.map { it.items })
    }

    @Test
    fun `only a connection can't add pages at the top`() {
        assertTrue(work.canAddTopLevel)
        assertFalse(club.canAddTopLevel)
        assertTrue(Workspace("w3", "Not known yet").canAddTopLevel)
    }
}
