package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteSummary

/** What a database shows: pinned notes, then the rest, each in the Notion view's order. */
data class SetupContent(
    val pinned: List<NoteSummary>,
    val others: List<NoteSummary>,
)

/**
 * A database's notes as its Notion view shows them (see NotionViews), pinned ones first, from
 * the notes kept on this phone, so it works offline.
 */
object DatabaseSetups {
    /**
     * The notes of [members] (a Notion view's, by ID, in its order; see NotionViews.members) that
     * are on this phone, pinned ones first.
     */
    fun content(notes: List<NoteSummary>, members: List<String>, pinned: List<String>): SetupContent {
        val byId = notes.associateBy { it.id }
        val shown = members.distinct().mapNotNull { byId[it] }
        val pinnedSet = pinned.toSet()
        val (top, rest) = shown.partition { it.id in pinnedSet }
        return SetupContent(top, rest)
    }

    /**
     * The property the cards show when none were chosen for them: the first row of chips from
     * before, else a status (or else a select) property.
     */
    fun cardProperty(setup: DatabaseSetup, schema: DatabaseSchema?): String? =
        setup.quickFilters.firstOrNull { schema == null || schema.property(it) != null }
            ?: schema?.properties?.firstOrNull { it.type == "status" }?.name
            ?: schema?.properties?.firstOrNull { it.type == "select" }?.name

    /** The setup of data source [sourceId]: the one kept here, else an empty one. */
    fun of(setups: Map<String, DatabaseSetup>, sourceId: String): DatabaseSetup =
        setups[sourceId] ?: setups.entries.firstOrNull { plain(it.key) == plain(sourceId) }?.value ?: DatabaseSetup()

    private fun plain(id: String) = id.replace("-", "")
}
