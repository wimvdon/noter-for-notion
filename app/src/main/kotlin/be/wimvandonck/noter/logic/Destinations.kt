package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig

/**
 * The top-level destinations: Pinned, then the user's notebooks. The navigation bar at the bottom
 * holds Pinned, the notebooks pinned to it (as many as fit on the screen) and Notebooks, which
 * opens a sheet with all of them. Searching Notion is a screen of its own (the search button),
 * not a destination.
 */
object Destinations {
    /** Pinned (the key is also where Pinned's own note order is kept). */
    const val PINNED = "home"

    /** Width (dp) a navigation bar item needs: Material's items are at least 80dp wide here. */
    const val BAR_ITEM_WIDTH_DP = 80

    /** Pinned and Notebooks are always in the bar. */
    private const val FIXED = 2

    fun keys(views: List<DatabaseConfig>): List<String> = listOf(PINNED) + views.map { it.id }

    /** How many items fit in the bar on a window [widthDp] wide (at least Pinned, one notebook and Notebooks). */
    fun barCapacity(widthDp: Int): Int = (widthDp / BAR_ITEM_WIDTH_DP).coerceAtLeast(FIXED + 1)

    /** How many notebooks fit in the bar, next to Pinned and Notebooks. */
    fun barNotebooks(widthDp: Int): Int = barCapacity(widthDp) - FIXED

    /** The notebooks in the bar: the pinned ones, in their order, as many as fit. */
    fun barViews(views: List<DatabaseConfig>, widthDp: Int): List<DatabaseConfig> =
        views.filter { it.inBar }.take(barNotebooks(widthDp))

    /** The views that can still be pinned to the bar on a window [widthDp] wide. */
    fun barRoom(views: List<DatabaseConfig>, widthDp: Int): Int =
        (barNotebooks(widthDp) - views.count { it.inBar }).coerceAtLeast(0)

    /** The destination to show: [current] while it exists, else Pinned (a removed view). */
    fun valid(current: String, views: List<DatabaseConfig>): String = if (current in keys(views)) current else PINNED
}
