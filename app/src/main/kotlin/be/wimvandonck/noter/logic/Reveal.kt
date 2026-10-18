package be.wimvandonck.noter.logic

/** Scrolling a line into view without jumping: only as far as needed, not to the top of the list. */
object Reveal {
    /**
     * How far to scroll (positive: the content moves up) so that an item from [top] to [bottom]
     * lies inside the visible part from [viewStart] to [viewEnd], keeping [margin] free at both
     * ends; 0 when it already does. An item taller than the visible part is lined up by its top.
     */
    fun scrollBy(top: Int, bottom: Int, viewStart: Int, viewEnd: Int, margin: Int = 0): Float {
        val from = viewStart + margin
        val to = viewEnd - margin
        return when {
            bottom - top > to - from -> (top - from).toFloat()
            top < from -> (top - from).toFloat()
            bottom > to -> (bottom - to).toFloat()
            else -> 0f
        }
    }
}
