package be.wimvandonck.noter.logic

/**
 * A header that scrolls away with the content and comes back on the first scroll up: its offset
 * runs from 0 (all shown) to minus its height (all gone).
 */
object QuickReturn {
    /** The header's offset after the content is dragged by [delta] (negative: up). */
    fun offset(current: Float, delta: Float, height: Float): Float {
        val limit = if (height > 0f) -height else 0f
        return (current + delta).coerceIn(limit, 0f)
    }
}
