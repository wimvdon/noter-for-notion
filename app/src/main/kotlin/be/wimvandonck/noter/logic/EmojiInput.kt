package be.wimvandonck.noter.logic

import java.text.BreakIterator

/** Notion only takes an emoji as a page's emoji icon: what the user types is checked first. */
object EmojiInput {
    /**
     * The first emoji in [text] (a whole emoji, also one made of several characters such as a
     * flag or a family), or null when [text] doesn't start with one.
     */
    fun first(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val graphemes = BreakIterator.getCharacterInstance().apply { setText(trimmed) }
        val first = trimmed.substring(0, graphemes.next().coerceAtLeast(1))
        return first.takeIf { isEmoji(it.codePointAt(0)) }
    }

    private fun isEmoji(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF || Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()

    /** Offered first in the icon picker: emoji people often give a note. */
    val SUGGESTED = listOf(
        "📝", "📌", "⭐", "✅", "💡", "📚", "📖", "🗒️", "📋", "🗂️", "📁", "📅",
        "🛒", "🍲", "🍳", "☕", "🏠", "🌱", "🌳", "🌍", "✈️", "🚗", "🚲", "🏃",
        "💪", "🧘", "❤️", "🎉", "🎁", "🎨", "🎵", "🎬", "📷", "🎮", "⚽", "🐶",
        "💼", "💰", "📈", "📊", "🧾", "🔧", "💻", "📱", "🔑", "🔒", "📣", "✉️",
        "🧠", "🎯", "🚀", "🔥", "✨", "☀️", "🌙", "⏰", "❓", "❗", "⚠️", "🙂",
    )
}
