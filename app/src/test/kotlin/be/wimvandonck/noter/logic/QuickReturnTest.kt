package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickReturnTest {
    @Test
    fun `scrolling down moves the header away, but no further than its height`() {
        assertEquals(-30f, QuickReturn.offset(0f, -30f, 200f))
        assertEquals(-200f, QuickReturn.offset(-180f, -50f, 200f))
    }

    @Test
    fun `scrolling up brings it back, but no further than all shown`() {
        assertEquals(-150f, QuickReturn.offset(-200f, 50f, 200f))
        assertEquals(0f, QuickReturn.offset(-20f, 50f, 200f))
    }

    @Test
    fun `a header that got shorter is clamped to its new height`() {
        assertEquals(-100f, QuickReturn.offset(-180f, 0f, 100f))
        assertEquals(0f, QuickReturn.offset(-10f, -10f, 0f))
    }
}
