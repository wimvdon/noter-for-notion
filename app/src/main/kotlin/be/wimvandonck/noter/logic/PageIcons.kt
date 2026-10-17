package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.icons.IconCatalog

/** Rules for showing Notion's page icons with what the app has. */
object PageIcons {
    /**
     * The Material Symbol closest to Notion's own icon [name] ("pizza", "book-closed"): the same
     * name, else the best search match for all its words, then for its first word, else null (the
     * app then shows a page icon).
     */
    fun closestSymbol(name: String, catalog: IconCatalog): IconCatalog.Icon? {
        val words = name.lowercase().replace('_', '-')
        return catalog.icon(words)
            ?: catalog.search(words.replace('-', ' ')).firstOrNull()
            ?: catalog.search(words.substringBefore('-')).firstOrNull()
    }
}
