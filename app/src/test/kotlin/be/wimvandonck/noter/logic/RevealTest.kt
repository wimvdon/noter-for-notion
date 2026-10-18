package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class RevealTest {
    @Test
    fun `an item that is fully in view doesn't scroll`() {
        assertEquals(0f, Reveal.scrollBy(top = 100, bottom = 160, viewStart = 0, viewEnd = 800), 0f)
        // Just the last visible line (the old code jumped here).
        assertEquals(0f, Reveal.scrollBy(top = 740, bottom = 800, viewStart = 0, viewEnd = 800), 0f)
    }

    @Test
    fun `an item below the view scrolls up just enough`() {
        assertEquals(60f, Reveal.scrollBy(top = 800, bottom = 860, viewStart = 0, viewEnd = 800), 0f)
        assertEquals(76f, Reveal.scrollBy(top = 800, bottom = 860, viewStart = 0, viewEnd = 800, margin = 16), 0f)
    }

    @Test
    fun `an item above the view scrolls down just enough`() {
        assertEquals(-40f, Reveal.scrollBy(top = -40, bottom = 20, viewStart = 0, viewEnd = 800), 0f)
    }

    @Test
    fun `an item taller than the view is lined up by its top`() {
        assertEquals(300f, Reveal.scrollBy(top = 300, bottom = 1300, viewStart = 0, viewEnd = 800), 0f)
    }
}
