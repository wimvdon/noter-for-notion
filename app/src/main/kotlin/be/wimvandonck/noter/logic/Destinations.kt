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

    enum class Section { PINNED, NOTEBOOKS, SEARCH }

    fun section(key: String): Section = when (key) {
        PINNED -> Section.PINNED
        SEARCH -> Section.SEARCH
        else -> Section.NOTEBOOKS
    }

    /**
     * The destination to show: [current] while it exists; under Notebooks, a notebook that's gone
     * (removed) gives way to the first notebook.
     */
    fun valid(current: String, views: List<DatabaseConfig>): String = when {
        current == PINNED || current == SEARCH -> current
        views.any { it.id == current } -> current
        else -> views.firstOrNull()?.id ?: NOTEBOOKS
    }

    /** What tapping Notebooks shows: the notebook seen last, else the first. */
    fun notebook(last: String?, views: List<DatabaseConfig>): String =
        views.firstOrNull { it.id == last }?.id ?: views.firstOrNull()?.id ?: NOTEBOOKS
}
