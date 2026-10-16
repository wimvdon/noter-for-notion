package com.impca201.noter.logic

import com.impca201.noter.data.model.DatabaseConfig

/**
 * The top-level destinations: Pinned, Browse, then the user's views. Material's navigation bar
 * holds three to five destinations; with more, all of them move to a navigation drawer.
 */
object Destinations {
    /** Pinned (the key is also where Pinned's own note order is kept). */
    const val PINNED = "home"
    const val BROWSE = "browse"

    /** The most destinations Material's navigation bar holds. */
    const val MAX_BAR_ITEMS = 5

    fun keys(views: List<DatabaseConfig>): List<String> = listOf(PINNED, BROWSE) + views.map { it.id }

    fun useDrawer(views: List<DatabaseConfig>): Boolean = keys(views).size > MAX_BAR_ITEMS

    /** The destination to show: [current] while it exists, else Pinned (a removed view). */
    fun valid(current: String, views: List<DatabaseConfig>): String = if (current in keys(views)) current else PINNED

    /**
     * The filters selected in a view: what the user picked since opening the app, else the
     * view's own filters.
     */
    fun selectedFilters(view: DatabaseConfig?, picked: Set<String>?): Set<String> = picked ?: view?.filters.orEmpty().toSet()
}
