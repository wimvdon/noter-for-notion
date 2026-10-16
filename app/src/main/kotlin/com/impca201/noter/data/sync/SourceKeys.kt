package com.impca201.noter.data.sync

import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.store.AppSettings

/**
 * The app keeps a database's notes under the ID of its data source (a database can have several;
 * each is shown as a database of its own). Versions before data sources kept them under the
 * database's ID; once Notion says which data source that is, the old key is renamed everywhere.
 */
object SourceKeys {
    /** [settings] with database [old] renamed to data source [new]: views, note order and property layouts. */
    fun renameInSettings(settings: AppSettings, old: String, new: String): AppSettings {
        if (old == new) return settings
        return settings.copy(
            databases = settings.databases.map { if (it.sourceId == old) it.copy(databaseId = new) else it },
            order = renameKey(settings.order, old, new),
            propertyLayouts = renameKey(settings.propertyLayouts, old, new),
        )
    }

    /** [queue] with notes waiting to be created in [old] created in [new] instead. */
    fun renameInOutbox(queue: List<PendingOp>, old: String, new: String): List<PendingOp> =
        queue.map { if (it is PendingOp.CreatePage && it.databaseId == old) it.copy(databaseId = new) else it }

    /** The notes of [old] and [new] together, as notes of [new] (the newer copy of a note wins). */
    fun mergeNotes(old: List<NoteSummary>, new: List<NoteSummary>, newKey: String): List<NoteSummary> {
        val ids = new.map { it.id }.toSet()
        return new + old.filter { it.id !in ids }.map { it.copy(databaseId = newKey) }
    }

    private fun <V> renameKey(map: Map<String, V>, old: String, new: String): Map<String, V> {
        val value = map[old] ?: return map
        return (map - old) + (new to (map[new] ?: value))
    }
}
