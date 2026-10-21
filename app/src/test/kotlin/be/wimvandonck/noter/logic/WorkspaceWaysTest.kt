package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceWaysTest {
    @Test
    fun `Connect to Notion comes first when the build has it`() {
        assertEquals(listOf(WorkspaceWay.SIGN_IN, WorkspaceWay.PERSONAL, WorkspaceWay.CONNECTION), WorkspaceWays.available(canSignIn = true))
        assertEquals(WorkspaceWay.SIGN_IN, WorkspaceWays.default(canSignIn = true))
    }

    @Test
    fun `without it, the two tokens are offered and a personal one is chosen`() {
        assertEquals(listOf(WorkspaceWay.PERSONAL, WorkspaceWay.CONNECTION), WorkspaceWays.available(canSignIn = false))
        assertEquals(WorkspaceWay.PERSONAL, WorkspaceWays.default(canSignIn = false))
    }

    @Test
    fun `only the tokens are pasted`() {
        assertFalse(WorkspaceWays.needsToken(WorkspaceWay.SIGN_IN))
        assertTrue(WorkspaceWays.needsToken(WorkspaceWay.PERSONAL))
        assertTrue(WorkspaceWays.needsToken(WorkspaceWay.CONNECTION))
    }
}
