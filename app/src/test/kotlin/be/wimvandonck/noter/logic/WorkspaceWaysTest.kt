package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspaceWaysTest {
    @Test
    fun `Advanced offers the two tokens, a personal one first and chosen`() {
        assertEquals(listOf(WorkspaceWay.PERSONAL, WorkspaceWay.CONNECTION), WorkspaceWays.advanced)
        assertEquals(WorkspaceWay.PERSONAL, WorkspaceWays.default)
    }

    @Test
    fun `Connect to Notion is not under Advanced`() {
        assertFalse(WorkspaceWay.SIGN_IN in WorkspaceWays.advanced)
    }
}
