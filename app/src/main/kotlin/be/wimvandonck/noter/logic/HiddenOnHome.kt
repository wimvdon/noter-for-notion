package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.HiddenItem
import be.wimvandonck.noter.data.model.WorkspaceItem

/**
 * Databases and pages hidden from Home's "Databases and pages" (#108). A long press on a row there
 * offers Hide (after a confirmation); Settings lists what is hidden, each with Show. Only Home's
 * list leaves them out: Search, pins and + are as before. Notion writes IDs with or without
 * dashes; they are compared without.
 */
object HiddenOnHome {
    /** [hidden] with [item] added on top (once). */
    fun hide(hidden: List<HiddenItem>, item: WorkspaceItem): List<HiddenItem> =
        listOf(HiddenItem(item.id, item.title, item.icon, item.isDatabase)) + show(hidden, item.id)

    /** [hidden] without [id]. */
    fun show(hidden: List<HiddenItem>, id: String): List<HiddenItem> = hidden.filterNot { plain(it.id) == plain(id) }

    /** The IDs of [hidden], without dashes, as [PageTree.build] takes them. */
    fun ids(hidden: List<HiddenItem>): Set<String> = hidden.map { plain(it.id) }.toSet()

    private fun plain(id: String) = id.replace("-", "")
}
