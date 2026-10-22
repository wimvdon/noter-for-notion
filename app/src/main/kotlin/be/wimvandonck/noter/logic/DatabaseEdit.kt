package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.WorkspaceItem

/**
 * A new name or icon for a database (Database › ⋮ › Rename, Add icon), made in Notion at once.
 * [title]: the new name (null: kept). [setsIcon]: the icon changes, to [emoji] (null: removed).
 */
data class DatabaseEdit(val title: String? = null, val emoji: String? = null, val setsIcon: Boolean = false) {
    /** What Notion is sent; a database and a data source take the same. */
    fun body(): Map<String, Any?> = buildMap {
        title?.let { put("title", listOf(mapOf("type" to "text", "text" to mapOf("content" to it)))) }
        if (setsIcon) put("icon", emoji?.let { mapOf("type" to "emoji", "emoji" to it) })
    }

    fun title(old: String): String = title ?: old

    fun icon(old: PageIcon?): PageIcon? = if (setsIcon) emoji?.let { PageIcon(emoji = it) } else old

    /** Home's list and the last search: the database's rows ([ids]: the database and its data source) changed. */
    fun applyTo(items: List<WorkspaceItem>, ids: Set<String>): List<WorkspaceItem> =
        items.map { if (plain(it.id) in ids) it.copy(title = title(it.title), icon = icon(it.icon)) else it }

    /** The pinned databases on Home, likewise. */
    fun applyToPins(pins: List<PinnedDatabase>, ids: Set<String>): List<PinnedDatabase> =
        pins.map { if (plain(it.id) in ids) it.copy(title = title(it.title), icon = icon(it.icon)) else it }

    fun applyTo(info: DatabaseInfo): DatabaseInfo = info.copy(title = title(info.title), icon = icon(info.icon))

    companion object {
        fun rename(title: String) = DatabaseEdit(title = title.trim())

        fun icon(emoji: String?) = DatabaseEdit(emoji = emoji, setsIcon = true)

        /** IDs compared without dashes (Notion gives both). */
        fun plain(id: String): String = id.replace("-", "")
    }
}
