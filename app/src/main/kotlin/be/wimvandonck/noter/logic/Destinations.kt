package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig

/**
 * The top-level destinations, in the navigation bar: Pinned, Notebooks and Search. The notebooks
 * are peers inside Notebooks, as tabs (Material 3: the bar for top-level destinations, tabs for
 * peer views within one). A destination key is [PINNED], [SEARCH], a notebook's ID, or
 * [NOTEBOOKS] while there is no notebook to show.
 */
object Destinations {
    /** Pinned (the key is also where Pinned's own note order is kept). */
    const val PINNED = "home"

    /** Notebooks without a notebook (there are none yet). */
    const val NOTEBOOKS = "notebooks"

    const val SEARCH = "search"

    /** Where the app opens: the first destination of the navigation bar (see [start]). */
    const val START = "start"

    enum class Section { PINNED, NOTEBOOKS, SEARCH }

    /**
     * The navigation bar, in the order the user [saved] (names of [Section]). Search is always
     * last: it opens the keyboard, so it is never where the app opens.
     */
    fun bar(saved: List<String>): List<Section> {
        val chosen = saved.mapNotNull { name -> Section.entries.firstOrNull { it.name == name } }.filter { it != Section.SEARCH }
        return (chosen + Section.PINNED + Section.NOTEBOOKS).distinct() + Section.SEARCH
    }

    /** The order to save after moving [section] to place [to] in the bar; Search can't move. */
    fun moved(saved: List<String>, section: Section, to: Int): List<String> {
        val movable = bar(saved).dropLast(1).toMutableList()
        val from = movable.indexOf(section)
        if (from >= 0 && to in movable.indices) movable.add(to, movable.removeAt(from))
        return movable.map { it.name }
    }

    /** The destination the app opens on: Pinned, or the first notebook when Notebooks comes first. */
    fun start(saved: List<String>, views: List<DatabaseConfig>): String =
        if (bar(saved).first() == Section.NOTEBOOKS) notebook(null, views) else PINNED

    fun section(key: String): Section = when (key) {
        PINNED -> Section.PINNED
        SEARCH -> Section.SEARCH
        else -> Section.NOTEBOOKS
    }

    /**
     * The destination to show: [current] while it exists ([START]: the bar's first, in the order
     * [saved]); under Notebooks, a notebook that's gone (removed) gives way to the first notebook.
     */
    fun valid(current: String, views: List<DatabaseConfig>, saved: List<String> = emptyList()): String = when {
        current == START -> start(saved, views)
        current == PINNED || current == SEARCH -> current
        views.any { it.id == current } -> current
        else -> views.firstOrNull()?.id ?: NOTEBOOKS
    }

    /** What tapping Notebooks shows: the notebook seen last, else the first. */
    fun notebook(last: String?, views: List<DatabaseConfig>): String =
        views.firstOrNull { it.id == last }?.id ?: views.firstOrNull()?.id ?: NOTEBOOKS
}
