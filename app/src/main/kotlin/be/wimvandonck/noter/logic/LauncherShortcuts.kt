package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PinnedDatabase

/** A launcher shortcut (long press on the app icon) to a pinned database or note. */
data class ShortcutTarget(val key: String, val title: String, val emoji: String?, val isDatabase: Boolean)

/**
 * The launcher's shortcuts (#76): what is pinned on Home, in Home's order (the pinned databases,
 * then the pinned notes and pages), as many as the launcher shows. A note not on this phone yet
 * waits until it is. [key] is the screen it opens, as the app's navigation names it.
 */
object LauncherShortcuts {
    const val NOTE = "note:"
    const val DATABASE = "database:"

    fun plan(databases: List<PinnedDatabase>, pinnedNotes: List<String>, notes: List<NoteSummary>, max: Int): List<ShortcutTarget> {
        val byId = notes.associateBy { it.id }
        val forDatabases = databases.map { ShortcutTarget(DATABASE + it.id, it.title.trim(), it.icon?.emoji, isDatabase = true) }
        val forNotes = pinnedNotes.mapNotNull { id ->
            val note = byId[id] ?: return@mapNotNull null
            ShortcutTarget(NOTE + id, note.title.trim(), note.icon?.emoji, isDatabase = false)
        }
        return (forDatabases + forNotes).distinctBy { it.key }.take(max.coerceAtLeast(0))
    }

    /** Whether [key] is a screen a shortcut may open (anything else from outside is ignored). */
    fun isTarget(key: String): Boolean =
        (key.startsWith(NOTE) && key.length > NOTE.length) || (key.startsWith(DATABASE) && key.length > DATABASE.length)
}
