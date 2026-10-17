package be.wimvandonck.noter.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveTimingTest {
    private val timing = SaveTiming(quietMs = 250, maxWaitMs = 1_000)

    @Test
    fun `waits for a short pause after the first edit`() {
        assertEquals(250, timing.delayMs(nowMs = 0, firstPendingMs = 0))
    }

    @Test
    fun `keeps waiting for a pause while typing within the maximum`() {
        assertEquals(250, timing.delayMs(nowMs = 500, firstPendingMs = 0))
    }

    @Test
    fun `saves while typing once the maximum wait is near`() {
        assertEquals(100, timing.delayMs(nowMs = 900, firstPendingMs = 0))
    }

    @Test
    fun `saves at once when the maximum wait has passed`() {
        assertEquals(0, timing.delayMs(nowMs = 1_500, firstPendingMs = 0))
    }

    @Test
    fun `continuous typing reaches Notion at most about once a second`() {
        // Each Notion round sends one request per changed block; typing in one block stays far
        // below Notion's three requests per second.
        assertTrue(SaveTiming.NOTION.maxWaitMs >= 1_000)
        assertTrue(SaveTiming.LOCAL.maxWaitMs <= SaveTiming.NOTION.maxWaitMs)
    }
}
