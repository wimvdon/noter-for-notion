package be.wimvandonck.noter.ui.common

/**
 * Whether a sheet may close on a swipe, a tap beside it or Back. While the keyboard is open, and
 * for a moment after it opens or closes, the sheet is still moving to its new height: a close then
 * is not what the user asked for. The keyboard closes instead, and the sheet stays. Buttons in the
 * sheet always close it. Plain Kotlin, unit tested.
 */
class SheetDismissGuard(
    private val settleMillis: Long = SETTLE_MILLIS,
) {
    private var changedAt = Long.MIN_VALUE / 2

    /** The keyboard opened or closed at [now]. */
    fun changed(now: Long) {
        changedAt = now
    }

    /** True when a close at [now] is the user's: no keyboard, and the sheet has settled. */
    fun allowsDismiss(now: Long, keyboardOpen: Boolean): Boolean = !keyboardOpen && now - changedAt >= settleMillis

    companion object {
        /** About as long as the keyboard and the sheet take to move. */
        const val SETTLE_MILLIS = 600L
    }
}

/**
 * Keeps a scroll in a sheet's list from closing the sheet. A finger that scrolled the list and
 * then reaches its top (or a fling that runs into the top) stops there: what is left of that
 * gesture does not pull the sheet down. Only a gesture that starts with the list at its top (or on
 * the handle or the title) moves the sheet, so closing it is always on purpose. Plain Kotlin, unit
 * tested; [NoterSheet] feeds it the nested-scroll events of the sheet's content.
 */
class SheetScrollGuard {
    private var scrolledContent = false

    /**
     * After the list took [consumedY] of a drag and left [availableY] (positive is downward, toward
     * closing). Returns how much of [availableY] to keep from the sheet.
     */
    fun afterDrag(consumedY: Float, availableY: Float): Float {
        if (consumedY != 0f) scrolledContent = true
        return if (scrolledContent && availableY > 0f) availableY else 0f
    }

    /**
     * The finger lifted, and the list's fling left [availableVelocityY]. Returns how much of it to
     * keep from the sheet. The next gesture starts afresh.
     */
    fun afterFling(availableVelocityY: Float): Float {
        val keep = scrolledContent && availableVelocityY > 0f
        scrolledContent = false
        return if (keep) availableVelocityY else 0f
    }
}
