package com.impca201.noter.logic

import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.model.WorkspaceItem

/** A database a new note can go in: from a view ([iconKey]) or from Browse ([emoji], Notion's icon). */
data class NoteTarget(
    val databaseId: String,
    val name: String,
    val iconKey: String? = null,
    val emoji: String? = null,
)

/** Where "New note" on Pinned and Browse can put a note. */
object NoteTargets {
    /**
     * The views' databases (each once, named after its first view), then the other databases
     * listed in Browse. Notion writes database IDs with or without dashes; they are compared without.
     */
    fun of(views: List<DatabaseConfig>, browse: List<WorkspaceItem>?): List<NoteTarget> {
        val fromViews = views.distinctBy { plain(it.sourceId) }.map { NoteTarget(it.sourceId, it.name, iconKey = it.icon) }
        val known = fromViews.map { plain(it.databaseId) }.toMutableSet()
        val fromBrowse = browse.orEmpty()
            .filter { it.isDatabase && known.add(plain(it.id)) }
            .map { NoteTarget(it.id, it.title, emoji = it.icon) }
        return fromViews + fromBrowse
    }

    private fun plain(id: String) = id.replace("-", "")
}
