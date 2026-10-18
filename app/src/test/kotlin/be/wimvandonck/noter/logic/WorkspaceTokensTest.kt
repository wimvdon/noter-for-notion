package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTokensTest {
    private val first = WorkspaceToken("t1", "Wim", "Work")
    private val second = WorkspaceToken("t2", "Wim", "Client")

    @Test
    fun `adds a token at the end`() {
        assertEquals(listOf(first, second), WorkspaceTokens.add(listOf(first), "main", second))
    }

    @Test
    fun `the main token and a blank one are not added`() {
        assertEquals(listOf(first), WorkspaceTokens.add(listOf(first), "main", WorkspaceToken("main")))
        assertEquals(listOf(first), WorkspaceTokens.add(listOf(first), "main", WorkspaceToken(" ")))
    }

    @Test
    fun `adding a token again replaces it`() {
        val renamed = first.copy(workspace = "Work (new)")
        assertEquals(listOf(second, renamed), WorkspaceTokens.add(listOf(first, second), "main", renamed))
    }

    @Test
    fun `removes a token`() {
        assertEquals(listOf(second), WorkspaceTokens.remove(listOf(first, second), "t1"))
        assertEquals(listOf(first, second), WorkspaceTokens.remove(listOf(first, second), "other"))
    }

    @Test
    fun `the label is the workspace, else the person`() {
        assertEquals("Work", WorkspaceTokens.label(first))
        assertEquals("Wim", WorkspaceTokens.label(WorkspaceToken("t3", "Wim", "")))
    }

    @Test
    fun `stored and read back`() {
        val list = listOf(first, second)
        assertEquals(list, WorkspaceTokens.decode(WorkspaceTokens.encode(list)))
    }

    @Test
    fun `nothing stored or unreadable reads as no tokens`() {
        assertTrue(WorkspaceTokens.decode(null).isEmpty())
        assertTrue(WorkspaceTokens.decode("").isEmpty())
        assertTrue(WorkspaceTokens.decode("not json").isEmpty())
    }
}
