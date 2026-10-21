package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.notion.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

class WorkspaceTokensPlaceTest {
    private val pat = WorkspaceToken("pat", "Wim", "Home", TokenKind.PERSONAL, workspaceId = "ws-home")
    private val signedIn = WorkspaceToken("old", "Wim", "Home", TokenKind.PERSONAL, refresh = "r1", workspaceId = "ws-home")
    private val fresh = WorkspaceToken("new", "Wim", "Home", TokenKind.PERSONAL, refresh = "r2", workspaceId = "ws-home")
    private val connection = WorkspaceToken("conn", "Noter", "Home", TokenKind.CONNECTION, workspaceId = "ws-home")
    private val club = WorkspaceToken("club", "Noter", "Club", TokenKind.CONNECTION, workspaceId = "ws-club")
    private val none = WorkspaceToken("")

    private fun added(placement: Placement) = placement as Placement.Added

    @Test
    fun `a personal access token sees most, then Connect to Notion, then a connection`() {
        assertTrue(WorkspaceTokens.access(pat) > WorkspaceTokens.access(signedIn))
        assertTrue(WorkspaceTokens.access(signedIn) > WorkspaceTokens.access(connection))
        assertTrue(WorkspaceTokens.access(connection) > WorkspaceTokens.access(WorkspaceToken("unknown")))
        assertEquals(
            listOf(WorkspaceWay.PERSONAL, WorkspaceWay.SIGN_IN, WorkspaceWay.CONNECTION, null),
            listOf(pat, signedIn, connection, WorkspaceToken("x")).map(WorkspaceTokens::way),
        )
    }

    @Test
    fun `the first token becomes the main one`() {
        assertEquals(Placement.Added(fresh, emptyList()), WorkspaceTokens.place(none, emptyList(), fresh))
    }

    @Test
    fun `another workspace goes after the others`() {
        assertEquals(Placement.Added(club, listOf(fresh)), WorkspaceTokens.place(club, emptyList(), fresh))
    }

    @Test
    fun `connecting a workspace again replaces its token where it was, keeping its ID`() {
        val placement = added(WorkspaceTokens.place(club, listOf(signedIn, WorkspaceToken("t3")), fresh))
        assertEquals(club, placement.main)
        assertEquals("new", placement.others[0].token)
        assertEquals("r2", placement.others[0].refresh)
        assertEquals(Workspaces.id("old"), placement.others[0].id)
        assertEquals("t3", placement.others[1].token)
        assertNull(placement.replaced)

        val again = added(WorkspaceTokens.place(signedIn, emptyList(), fresh))
        assertEquals("new", again.main.token)
        assertEquals(Workspaces.id("old"), again.main.id)
    }

    @Test
    fun `a way that sees more replaces one that sees less, and says which`() {
        val overConnection = added(WorkspaceTokens.place(connection, listOf(club), fresh))
        assertEquals("new", overConnection.main.token)
        assertEquals(connection, overConnection.replaced)
        val patOverSignIn = added(WorkspaceTokens.place(club, listOf(signedIn), pat))
        assertEquals(listOf("pat"), patOverSignIn.others.map { it.token })
        assertEquals("", patOverSignIn.others[0].refresh)
        assertEquals(signedIn, patOverSignIn.replaced)
    }

    @Test
    fun `a personal access token is never replaced by a way that sees less`() {
        assertEquals(Placement.Refused(pat), WorkspaceTokens.place(pat, emptyList(), fresh))
        assertEquals(Placement.Refused(pat), WorkspaceTokens.place(club, listOf(pat), connection))
        assertEquals(Placement.Refused(signedIn), WorkspaceTokens.place(signedIn, emptyList(), connection))
    }

    @Test
    fun `adding the same token again updates it`() {
        val renamed = pat.copy(workspace = "Home (new)")
        assertEquals(Placement.Added(renamed, listOf(club)), WorkspaceTokens.place(pat, listOf(club), renamed))
    }

    @Test
    fun `a token whose workspace Notion didn't name is a new workspace`() {
        val unknown = WorkspaceToken("u", kind = TokenKind.PERSONAL)
        assertEquals(Placement.Added(pat, listOf(unknown)), WorkspaceTokens.place(pat, emptyList(), unknown))
    }

    @Test
    fun `duplicates from before are cleaned up, the token that sees most staying`() {
        assertEquals(pat to listOf(club), WorkspaceTokens.deduplicate(connection, listOf(club, signedIn, pat)))
        assertEquals(signedIn to listOf(club), WorkspaceTokens.deduplicate(signedIn, listOf(connection, club)))
        val second = pat.copy(token = "pat2")
        assertEquals(pat to emptyList<WorkspaceToken>(), WorkspaceTokens.deduplicate(pat, listOf(second)))
        assertEquals(none to listOf(club), WorkspaceTokens.deduplicate(none, listOf(club)))
    }

    @Test
    fun `tokens whose workspace isn't known yet all stay`() {
        val a = WorkspaceToken("a", kind = TokenKind.PERSONAL)
        val b = WorkspaceToken("b", kind = TokenKind.CONNECTION)
        assertEquals(a to listOf(b), WorkspaceTokens.deduplicate(a, listOf(b)))
    }

    @Test
    fun `a replaced token keeps the ID it already had`() {
        val second = WorkspaceTokens.replaced(signedIn.copy(id = "abc"), fresh)
        assertEquals("abc", second.id)
        assertEquals("ws-home", WorkspaceTokens.replaced(signedIn, fresh.copy(workspaceId = "")).workspaceId)
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

    @Test
    fun `only Connect to Notion is signed in`() {
        assertTrue(signedIn.signedIn)
        assertFalse(pat.signedIn)
    }

    @Test
    fun `what Notion says at start fills in the workspace's ID`() {
        val list = WorkspaceTokens.update(listOf(club.copy(workspaceId = "")), "club", "Noter", "Club", TokenKind.CONNECTION, "ws-club")
        assertEquals("ws-club", list.single().workspaceId)
        assertEquals("ws-club", WorkspaceTokens.update(list, "club", "Noter", "Club", TokenKind.CONNECTION).single().workspaceId)
    }
}
