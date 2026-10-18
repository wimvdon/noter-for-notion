package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextTest {
    @Test
    fun `a browser's share is a title and a link`() {
        assertEquals(
            SharedContent("A good read", "", "https://example.com/story"),
            SharedText.of("A good read", "https://example.com/story"),
        )
    }

    @Test
    fun `a title and a link in the text become title and link`() {
        assertEquals(
            SharedContent("Look at this", "", "https://example.com/a?b=1"),
            SharedText.of(null, "Look at this https://example.com/a?b=1."),
        )
    }

    @Test
    fun `longer text stays text, with the link taken out`() {
        val text = "First line\nSecond line\nhttps://example.com"
        assertEquals(SharedContent("", "First line\nSecond line", "https://example.com"), SharedText.of(null, text))
    }

    @Test
    fun `text without a link`() {
        assertEquals(SharedContent("Idea", "Buy milk", null), SharedText.of("Idea", "Buy milk"))
        assertEquals(SharedContent("Same", "", null), SharedText.of("Same", "Same"))
    }

    @Test
    fun `nothing shared is nothing`() {
        assertNull(SharedText.of(null, "  "))
        assertNull(SharedText.of("", null))
    }
}
