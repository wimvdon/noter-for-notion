package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.WorkspaceItem

/** A data source a new note can go in, with the name and icon of its database. */
data class NoteTarget(
    val databaseId: String,
    val name: String,
    val icon: PageIcon? = null,
)

/** Where + on Home can put a new note. */
object NoteTargets {
    /**
     * The pinned databases, then the other databases on Home's list ([recent]). A database of several
     * data sources offers each of them, named "Database · Data source" (from [infos], by database
     * ID, for a pinned one). Notion writes IDs with or without dashes; they are compared without.
     */
    fun of(pinned: List<PinnedDatabase>, recent: List<WorkspaceItem>?, infos: Map<String, DatabaseInfo>): List<NoteTarget> {
        val byPlainId = infos.mapKeys { plain(it.key) }
        val fromPins = pinned.flatMap { pin ->
            val sources = byPlainId[plain(pin.id)]?.sources.orEmpty()
            if (sources.size > 1) {
                sources.map { NoteTarget(it.id, "${pin.title} · ${it.title}", pin.icon) }
            } else {
                listOf(NoteTarget(pin.id, pin.title, pin.icon))
            }
        }
        val fromRecent = recent.orEmpty().filter { it.isDatabase }.flatMap { item ->
            if (item.sources.size > 1) {
                item.sources.map { NoteTarget(it.id, "${item.title} · ${it.title}", item.icon) }
            } else {
                listOf(NoteTarget(item.id, item.title, item.icon))
            }
        }
        return (fromPins + fromRecent).distinctBy { plain(it.databaseId) }
    }

    private fun plain(id: String) = id.replace("-", "")
}
