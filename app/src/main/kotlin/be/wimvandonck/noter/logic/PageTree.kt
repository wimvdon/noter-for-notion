package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.WorkspaceItem

/** A database or page at the top of Home's tree, with the databases and pages right inside it. */
data class TreeNode(val item: WorkspaceItem, val children: List<WorkspaceItem>)

/**
 * Home's databases and pages as a tree of two levels (#64), in the manner of Notion's sidebar.
 * The top level is what isn't inside another page of the list (at the top of the workspace, or in
 * a page the connection can't see); below each, the databases and pages right inside it. Deeper
 * pages are one tap further (they are links in their page's content). Notion's API doesn't give
 * the sidebar's own order, so both levels are by title.
 */
object PageTree {
    /** How many entries of the top level Home shows at first, and how many more each "Show more" adds. */
    const val PAGE_SIZE = 30

    /** [items] as a tree. Notion writes IDs with or without dashes; they are compared without. */
    fun build(items: List<WorkspaceItem>): List<TreeNode> {
        val unique = items.distinctBy { plain(it.id) }
        val ids = unique.map { plain(it.id) }.toSet()
        fun parentIn(item: WorkspaceItem): String? = item.parentId?.let { plain(it) }?.takeIf { it in ids && it != plain(item.id) }
        val children = unique.filter { parentIn(it) != null }.groupBy { parentIn(it)!! }
        return unique
            .filter { parentIn(it) == null }
            .sortedWith(BY_TITLE)
            .map { TreeNode(it, children[plain(it.id)].orEmpty().sortedWith(BY_TITLE)) }
    }

    /** Untitled ones last, the rest by title without minding capitals. */
    private val BY_TITLE: Comparator<WorkspaceItem> =
        compareBy<WorkspaceItem> { it.title.isBlank() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }

    private fun plain(id: String) = id.replace("-", "")
}
