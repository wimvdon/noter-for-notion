package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.notion.TokenKind
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
    fun `what Notion says now replaces the names and kind, in place`() {
        val updated = WorkspaceTokens.update(listOf(first, second), "t1", "Wim V", "Work 2", TokenKind.CONNECTION)
        assertEquals(listOf(WorkspaceToken("t1", "Wim V", "Work 2", TokenKind.CONNECTION), second), updated)
    }

    @Test
    fun `stored and read back`() {
        val list = listOf(first, second.copy(kind = TokenKind.PERSONAL))
        assertEquals(list, WorkspaceTokens.decode(WorkspaceTokens.encode(list)))
    }

    @Test
    fun `nothing stored or unreadable reads as no tokens`() {
        assertTrue(WorkspaceTokens.decode(null).isEmpty())
        assertTrue(WorkspaceTokens.decode("").isEmpty())
        assertTrue(WorkspaceTokens.decode("not json").isEmpty())
    }
}

class WorkspaceTokensSignInTest {
    private val pat = WorkspaceToken("pat", "Wim", "Work", TokenKind.PERSONAL)
    private val signedIn = WorkspaceToken("old", "Wim", "Home", TokenKind.PERSONAL, refresh = "r1", workspaceId = "ws-home")
    private val fresh = WorkspaceToken("new", "Wim", "Home", refresh = "r2", workspaceId = "ws-home")

    @Test
    fun `the first token becomes the main one`() {
        val (main, others) = WorkspaceTokens.signIn(WorkspaceToken(""), emptyList(), fresh)
        assertEquals(fresh, main)
        assertEquals(emptyList<WorkspaceToken>(), others)
    }

    @Test
    fun `another workspace goes after the others`() {
        val (main, others) = WorkspaceTokens.signIn(pat, emptyList(), fresh)
        assertEquals(pat, main)
        assertEquals(listOf(fresh), others)
    }

    @Test
    fun `connecting a workspace again replaces its token where it was, keeping its ID`() {
        val (main, others) = WorkspaceTokens.signIn(pat, listOf(signedIn, WorkspaceToken("t3")), fresh)
        assertEquals(pat, main)
        assertEquals("new", others[0].token)
        assertEquals("r2", others[0].refresh)
        assertEquals(Workspaces.id("old"), others[0].id)
        assertEquals(TokenKind.PERSONAL, others[0].kind)
        assertEquals("t3", others[1].token)

        val (again, _) = WorkspaceTokens.signIn(signedIn, emptyList(), fresh)
        assertEquals("new", again.token)
        assertEquals(Workspaces.id("old"), again.id)
    }

    @Test
    fun `a replaced token keeps the ID it already had`() {
        val second = WorkspaceTokens.replaced(signedIn.copy(id = "abc"), fresh)
        assertEquals("abc", second.id)
        assertEquals("ws-home", WorkspaceTokens.replaced(signedIn, fresh.copy(workspaceId = "")).workspaceId)
    }

    @Test
    fun `a pasted token for the same workspace isn't replaced`() {
        val (main, others) = WorkspaceTokens.signIn(pat.copy(workspace = "Home"), emptyList(), fresh)
        assertEquals("pat", main.token)
        assertEquals(listOf(fresh), others)
    }

    @Test
    fun `the new fields are stored and old lists still read`() {
        val list = listOf(signedIn.copy(id = "abc"))
        assertEquals(list, WorkspaceTokens.decode(WorkspaceTokens.encode(list)))
        assertEquals(listOf(WorkspaceToken("t", "n", "w")), WorkspaceTokens.decode("""[{"token":"t","name":"n","workspace":"w"}]"""))
    }

    @Test
    fun `a kept ID is used for the new token`() {
        Workspaces.keep("refreshed-token", "abc123")
        assertEquals("abc123", Workspaces.id("refreshed-token"))
        assertEquals("abc123", Workspaces.of(listOf(WorkspaceToken("other-new", id = "abc123", refresh = "r"))).single().id)
        assertTrue(Workspaces.of(listOf(WorkspaceToken("other-new", id = "abc123", refresh = "r"))).single().signedIn)
    }
}
