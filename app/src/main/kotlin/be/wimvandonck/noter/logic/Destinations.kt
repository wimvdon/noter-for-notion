package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig

/**
 * The top-level destinations: Pinned, then the user's notebooks. All of them are in the
 * navigation drawer (behind the menu button); the navigation bar at the bottom holds Pinned and
 * the notebooks pinned to it, as many as fit on the screen. Searching Notion is a screen of its
 * own (the search button), not a destination.
 */
object Destinations {
    /** Pinned (the key is also where Pinned's own note order is kept). */
    const val PINNED = "home"

    /** Width (dp) a navigation bar item needs: Material's items are at least 80dp wide here. */
    const val BAR_ITEM_WIDTH_DP = 80

    /** Pinned is always in the bar. */
    private const val FIXED = 1

    fun keys(views: List<DatabaseConfig>): List<String> = listOf(PINNED) + views.map { it.id }

    /** How many destinations fit in the bar on a window [widthDp] wide (at least Pinned and one notebook). */
    fun barCapacity(widthDp: Int): Int = (widthDp / BAR_ITEM_WIDTH_DP).coerceAtLeast(FIXED + 1)

    /** The notebooks in the bar: the pinned ones, in their order, as many as fit next to Pinned. */
    fun barViews(views: List<DatabaseConfig>, widthDp: Int): List<DatabaseConfig> =
        views.filter { it.inBar }.take(barCapacity(widthDp) - FIXED)

    /** The views that can still be pinned to the bar on a window [widthDp] wide. */
    fun barRoom(views: List<DatabaseConfig>, widthDp: Int): Int =
        (barCapacity(widthDp) - FIXED - views.count { it.inBar }).coerceAtLeast(0)

    /** The destination to show: [current] while it exists, else Pinned (a removed view). */
    fun valid(current: String, views: List<DatabaseConfig>): String = if (current in keys(views)) current else PINNED

    /**
     * The filters selected in a view: what the user picked since opening the app, else the
     * view's own filters.
     */
    fun selectedFilters(view: DatabaseConfig?, picked: Set<String>?): Set<String> = picked ?: view?.filters.orEmpty().toSet()
}
