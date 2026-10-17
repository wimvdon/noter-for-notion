package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PropertySchema

/** What one tab shows: chips on top, then pinned notes, then the rest. */
data class NoteListContent(
    val chips: List<FilterChip>,
    val pinned: List<NoteSummary>,
    val others: List<NoteSummary>,
)

/** Where notes come from on Pinned: a database or the pages on their own, with its name. */
data class NoteSource(val id: String, val label: String)

/** Builds Pinned and the database views from the notes on this phone. */
object NoteLists {
    /** Pinned: the pinned notes of every source; the filters filter by source. */
    fun home(
        sources: List<NoteSource>,
        notesByDatabase: Map<String, List<NoteSummary>>,
        pinned: List<String>,
        order: List<String>,
        selectedDatabases: Set<String>,
    ): NoteListContent {
        val pinnedSet = pinned.toSet()
        val all = sources.flatMap { db -> notesByDatabase[db.id].orEmpty().filter { it.id in pinnedSet } }
        val chips = sources.map { db -> FilterChip(db.id, db.label, count = all.count { it.databaseId == db.id }) }
            .filter { it.count > 0 }
            .takeIf { it.size >= 2 }
            .orEmpty()
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
