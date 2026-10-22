package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.WorkspaceItem

/** A page or database right inside a page, from that page's blocks (`child_page`, `child_database`). */
data class ChildRef(val id: String, val isDatabase: Boolean, val lastEditedTime: String = "")

/**
 * Home's two levels complete. Notion's search lists everything newest first, notes in databases
 * included, so it can miss an old page; its top-level pages are asked for what is right inside
 * them, which lists all of it, and what the search didn't find is added.
 */
object HomeLevels {
    /**
     * The pages at the top of Home's tree (not inside another page of [items]; databases hold
     * notes, not subpages), most recently edited first, at most [limit].
     */
    fun levelOne(items: List<WorkspaceItem>, limit: Int): List<String> {
        val ids = items.flatMap { listOfNotNull(it.id, it.databaseId) }.map(::plain).toSet()
        return items
            .filter { !it.isDatabase && it.parentId?.let { parent -> plain(parent) in ids } != true }
            .sortedByDescending { it.lastEditedTime }
            .map { it.id }
            .distinctBy(::plain)
            .take(limit)
    }

    /** The [children] that aren't in [items] yet (a database is there as itself or one of its data sources). */
    fun missing(items: List<WorkspaceItem>, children: List<ChildRef>): List<ChildRef> {
        val known = items.flatMap { listOfNotNull(it.id, it.databaseId) }.map(::plain).toSet()
        return children.filter { plain(it.id) !in known }.distinctBy { plain(it.id) }
    }

    private fun plain(id: String) = id.replace("-", "")
}
