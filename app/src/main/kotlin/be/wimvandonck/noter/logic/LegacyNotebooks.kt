package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.SortKind
import be.wimvandonck.noter.data.store.AppSettings

/**
 * Notebooks from before the redesign, in today's terms: each database keeps the filters, rows of
 * chips, sort and cards its notebook had (the first notebook of it, when there were several), and
 * is pinned on Home, in the order of the tabs. A page's notebook becomes a pinned page (its
 * subpages are links in it).
 */
object LegacyNotebooks {
    fun migrate(settings: AppSettings): AppSettings {
        if (settings.databases.isEmpty()) return settings
        val notebooks = settings.databases.map { it.normalized() }
        val setups = notebooks.flatMap { it.leaves }
            .filter { it.pageId == null }
            .distinctBy { plain(it.sourceId) }
            .associate { it.sourceId to setupOf(it) }
        val databases = notebooks.filter { it.pageId == null }.map { PinnedDatabase(it.sourceId, it.name) }
        val pages = notebooks.mapNotNull { it.pageId }
        val pinned = settings.pinned.map(::plain).toMutableSet()
        return settings.copy(
            databases = emptyList(),
            // Set up again since then (an older copy of the settings): the newer setup stays.
            setups = setups.filterKeys { key -> settings.setups.keys.none { plain(it) == plain(key) } } + settings.setups,
            pinnedDatabases = (settings.pinnedDatabases + databases).distinctBy { plain(it.id) },
            pinned = settings.pinned + pages.filter { pinned.add(plain(it)) },
        )
    }

    private fun setupOf(notebook: DatabaseConfig) = DatabaseSetup(
        quickFilters = notebook.quickFilters,
        selected = notebook.selected,
        sort = notebook.sort?.takeIf { it.kind != SortKind.NOTION },
        cards = notebook.cards,
    )

    private fun plain(id: String) = id.replace("-", "")
}
