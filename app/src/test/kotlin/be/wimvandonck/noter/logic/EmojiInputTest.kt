package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiInputTest {
    @Test
    fun `takes the first whole emoji`() {
        assertEquals("🥑", EmojiInput.first("🥑"))
        assertEquals("🥑", EmojiInput.first(" 🥑🍕 "))
        assertEquals("👨‍👩‍👧", EmojiInput.first("👨‍👩‍👧 family"))
        assertEquals("🇧🇪", EmojiInput.first("🇧🇪"))
        assertEquals("❤️", EmojiInput.first("❤️"))
    }

    @Test
    fun `letters, digits and nothing are no emoji`() {
        assertNull(EmojiInput.first("A"))
        assertNull(EmojiInput.first("1"))
        assertNull(EmojiInput.first("  "))
    }
}
