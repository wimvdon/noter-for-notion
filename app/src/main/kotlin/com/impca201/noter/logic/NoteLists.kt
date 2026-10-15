package com.impca201.noter.logic

import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.model.PropertySchema

/** What one tab shows: chips on top, then pinned notes, then the rest. */
data class NoteListContent(
    val chips: List<FilterChip>,
    val pinned: List<NoteSummary>,
    val others: List<NoteSummary>,
)

/** Builds the Home tab and the database tabs from the notes on this phone. */
object NoteLists {
    /** Home: the pinned notes of all databases; the chips filter by database. */
    fun home(
        databases: List<DatabaseConfig>,
        notesByDatabase: Map<String, List<NoteSummary>>,
        pinned: List<String>,
        order: List<String>,
        selectedDatabases: Set<String>,
    ): NoteListContent {
        val pinnedSet = pinned.toSet()
        val all = databases.flatMap { db -> notesByDatabase[db.id].orEmpty().filter { it.id in pinnedSet } }
        val chips = if (databases.size < 2) {
            emptyList()
        } else {
            databases.map { db -> FilterChip(db.id, db.name, count = all.count { it.databaseId == db.id }) }
                .filter { it.count > 0 }
        }
        val visible = if (selectedDatabases.isEmpty()) all else all.filter { it.databaseId in selectedDatabases }
        // Pinned in the order they were pinned (newest first) unless placed by hand.
        val byPinOrder = visible.sortedBy { pinned.indexOf(it.id) }
        val placed = order.withIndex().associate { (i, id) -> id to i }
        val (inOrder, rest) = byPinOrder.partition { it.id in placed }
        return NoteListContent(chips, pinned = rest + inOrder.sortedBy { placed.getValue(it.id) }, others = emptyList())
    }

    fun database(
        notes: List<NoteSummary>,
        chipProperty: PropertySchema?,
        selectedChips: Set<String>,
        pinned: List<String>,
        order: List<String>,
    ): NoteListContent {
        val chips = ChipFilter.chips(chipProperty, notes)
        val validSelection = selectedChips.filter { key -> chips.any { it.key == key } }.toSet()
        val filtered = ChipFilter.filter(notes, chipProperty?.name, validSelection)
        val arranged = NoteOrdering.arrange(filtered, pinned.toSet(), order)
        return NoteListContent(chips, arranged.pinned, arranged.others)
    }
}
