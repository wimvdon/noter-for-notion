package be.wimvandonck.noter.logic

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenRefresherTest {
    private var stored = listOf(
        WorkspaceToken("a1", "Wim", "Home", refresh = "r1", workspaceId = "ws"),
        WorkspaceToken("pat", "Wim", "Work"),
    )
    private val asked = mutableListOf<String>()
    private var time = 0L

    private fun refresher(answer: (String) -> SignInGrant? = { SignInGrant("a2", "r2", "ws", "Home", "") }) = TokenRefresher(
        find = { token -> stored.firstOrNull { it.token == token } },
        refresh = { refresh ->
            asked += refresh
            delay(10)
            answer(refresh)
        },
        save = { old, new -> stored = stored.map { if (it.token == old.token) new else it } },
        now = { time },
    )

    @Test
    fun `a refused token is refreshed and replaced, keeping its workspace's ID`() = runBlocking {
        val id = Workspaces.id("a1")
        assertEquals("a2", refresher().renew("a1"))
        assertEquals(listOf("r1"), asked)
        assertEquals(WorkspaceToken("a2", "Wim", "Home", refresh = "r2", workspaceId = "ws", id = id), stored[0])
        assertEquals(id, Workspaces.id("a2"))
    }

    @Test
    fun `requests refused at the same time share one refresh`() = runBlocking {
        val refresher = refresher()
        val results = (1..3).map { async { refresher.renew("a1") } }.awaitAll()
        assertEquals(listOf("a2", "a2", "a2"), results)
        assertEquals(listOf("r1"), asked)
    }

    @Test
    fun `a pasted token isn't refreshed`() = runBlocking {
        assertNull(refresher().renew("pat"))
        assertNull(refresher().renew("unknown"))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `a refused refresh leaves the token as it was`() = runBlocking {
        val before = stored
        assertNull(refresher { null }.renew("a1"))
        assertEquals(before, stored)
    }

    @Test
    fun `a new token refused at once isn't refreshed again within a minute`() = runBlocking {
        var n = 1
        val refresher = refresher { n++; SignInGrant("a${n}", "r${n}", "", "", "") }
        assertEquals("a2", refresher.renew("a1"))
        assertNull(refresher.renew("a2"))
        time += 61_000
        assertEquals("a3", refresher.renew("a2"))
        assertEquals(listOf("r1", "r2"), asked)
        assertEquals("ws", stored[0].workspaceId)
    }

    @Test
    fun `Notion not sending a new refresh token keeps the old one`() = runBlocking {
        refresher { SignInGrant("a2", "", "ws", "Home", "") }.renew("a1")
        assertEquals("r1", stored[0].refresh)
    }
}
