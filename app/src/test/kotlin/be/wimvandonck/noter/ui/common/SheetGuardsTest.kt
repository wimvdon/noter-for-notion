package be.wimvandonck.noter.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetGuardsTest {
    @Test
    fun `a sheet that has settled closes`() {
        assertTrue(SheetDismissGuard().allowsDismiss(now = 0L, keyboardOpen = false))
    }

    @Test
    fun `with the keyboard open the sheet stays`() {
        assertFalse(SheetDismissGuard().allowsDismiss(now = 10_000L, keyboardOpen = true))
    }

    @Test
    fun `just after the keyboard changed the sheet stays, then closes`() {
        val guard = SheetDismissGuard(settleMillis = 600L)
        guard.changed(1_000L)
        assertFalse(guard.allowsDismiss(1_200L, keyboardOpen = false))
        assertFalse(guard.allowsDismiss(1_599L, keyboardOpen = false))
        assertTrue(guard.allowsDismiss(1_600L, keyboardOpen = false))
    }

    @Test
    fun `a swipe down that starts with the list at its top moves the sheet`() {
        val guard = SheetScrollGuard()
        assertEquals(0f, guard.afterDrag(consumedY = 0f, availableY = 30f))
        assertEquals(0f, guard.afterDrag(consumedY = 0f, availableY = 40f))
        assertEquals(0f, guard.afterFling(availableVelocityY = 2_000f))
    }

    @Test
    fun `scrolling the list up to its top doesn't pull the sheet down`() {
        val guard = SheetScrollGuard()
        assertEquals(0f, guard.afterDrag(consumedY = 50f, availableY = 0f))
        assertEquals(20f, guard.afterDrag(consumedY = 10f, availableY = 20f))
        assertEquals(35f, guard.afterDrag(consumedY = 0f, availableY = 35f))
        assertEquals(1_500f, guard.afterFling(availableVelocityY = 1_500f))
    }

    @Test
    fun `scrolling down the list leaves the sheet alone, and the next gesture starts afresh`() {
        val guard = SheetScrollGuard()
        assertEquals(0f, guard.afterDrag(consumedY = -50f, availableY = 0f))
        assertEquals(0f, guard.afterDrag(consumedY = 0f, availableY = -10f))
        assertEquals(0f, guard.afterFling(availableVelocityY = -800f))
        assertEquals(0f, guard.afterDrag(consumedY = 0f, availableY = 30f))
        assertEquals(0f, guard.afterFling(availableVelocityY = 900f))
    }
}
