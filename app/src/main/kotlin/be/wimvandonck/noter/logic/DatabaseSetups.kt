package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NoteSort
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.SortKind
import be.wimvandonck.noter.data.notion.InlineMarkdown
import java.time.LocalDate

/** One property's filter chips above the notes, with the ones selected. */
data class ChipGroup(
    val property: PropertySchema,
    val chips: List<FilterChip>,
    val selected: Set<String>,
)

/** What a database shows: its quick filters, then pinned notes, then the rest. */
data class SetupContent(
    val groups: List<ChipGroup>,
    val pinned: List<NoteSummary>,
    val others: List<NoteSummary>,
    /** How many properties filter the notes now (for the Filters button). */
    val filterCount: Int,
)

/**
 * A database's notes as its setup shows them: all its notes, filtered by the values selected per
 * property (a note shows when it has one of the selected values of every filtering property),
 * sorted, pinned ones first. Everything is worked out on this phone, from the notes it has, so it
 * works offline.
 */
object DatabaseSetups {
    /** [notes] that have one of the selected values of every property in [selected]. */
    fun filter(notes: List<NoteSummary>, selected: Map<String, Set<String>>, today: LocalDate = LocalDate.now()): List<NoteSummary> {
        val active = selected.filterValues { it.isNotEmpty() }
        if (active.isEmpty()) return notes
        return notes.filter { note -> active.all { (property, values) -> ChipFilter.keysOf(note, property, today).any { it in values } } }
    }

    /** [notes] in the order [sort] asks for; [customOrder]: the dragged one. */
    fun sorted(notes: List<NoteSummary>, sort: NoteSort, customOrder: List<String>): List<NoteSummary> =
        when (sort.kind) {
            SortKind.CUSTOM, SortKind.NOTION -> NoteOrdering.sort(notes, customOrder)
            SortKind.LAST_EDITED -> notes.sortedBy { it.lastEditedTime }.let { if (sort.descending) it.reversed() else it }
            SortKind.CREATED -> notes.sortedBy { it.createdTime }.let { if (sort.descending) it.reversed() else it }
            SortKind.TITLE -> notes.sortedBy { InlineMarkdown.stripped(it.title).lowercase() }.let { if (sort.descending) it.reversed() else it }
            SortKind.DATE -> {
                val (dated, undated) = notes.partition { dateOf(it, sort.property) != null }
                val ordered = dated.sortedBy { dateOf(it, sort.property) }.let { if (sort.descending) it.reversed() else it }
                ordered + undated
            }
        }

    private fun dateOf(note: NoteSummary, property: String?): String? =
        (property?.let { note.property(it)?.value } as? PropertyValue.Date)?.start

    /** The selected values, keeping only properties that still exist. */
    fun selection(setup: DatabaseSetup, schema: DatabaseSchema?): Map<String, Set<String>> =
        setup.selected.filterKeys { schema == null || schema.property(it) != null }.mapValues { it.value.toSet() }

    fun content(
        notes: List<NoteSummary>,
        schema: DatabaseSchema?,
        setup: DatabaseSetup,
        pinned: List<String>,
        customOrder: List<String>,
        today: LocalDate = LocalDate.now(),
    ): SetupContent {
        // A value that no note has any more (an option deleted in Notion) doesn't hide every note.
        val selected = selection(setup, schema).mapValues { (name, values) ->
            val property = schema?.property(name) ?: return@mapValues values
            val keys = ChipFilter.chips(property, notes, today).map { it.key }.toSet()
            values.filter { it in keys }.toSet()
        }
        // A row only shows once a note has a value for its property.
        val groups = setup.quickFilters.mapNotNull { name ->
            val property = schema?.property(name) ?: return@mapNotNull null
            ChipGroup(property, ChipFilter.chips(property, notes, today), selected[name].orEmpty())
        }.filter { ChipFilter.anyValue(it.chips) }
        val filtered = filter(notes, selected, today)
        val sortedNotes = sorted(filtered, setup.currentSort, customOrder)
        val pinnedSet = pinned.toSet()
        val (top, rest) = sortedNotes.partition { it.id in pinnedSet }
        return SetupContent(groups, top, rest, selected.count { it.value.isNotEmpty() })
    }

    /** [selected] with [key] of [property] switched on or off. */
    fun toggle(selected: Map<String, List<String>>, property: String, key: String): Map<String, List<String>> {
        val values = selected[property].orEmpty()
        val next = if (key in values) values - key else values + key
        return if (next.isEmpty()) selected - property else selected + (property to next)
    }

    /**
     * How a database opened for the first time shows its notes: all of them, in the custom order,
     * with a status (else a select) property as its first row of filter chips.
     */
    fun initial(schema: DatabaseSchema?): DatabaseSetup =
        DatabaseSetup(
            quickFilters = listOfNotNull(
                schema?.properties?.firstOrNull { it.type == "status" }?.name ?: schema?.properties?.firstOrNull { it.type == "select" }?.name,
            ),
        )

    /** The setup of data source [sourceId]: the one kept here, else the [initial] one. */
    fun of(setups: Map<String, DatabaseSetup>, sourceId: String, schema: DatabaseSchema?): DatabaseSetup =
        setups[sourceId] ?: setups.entries.firstOrNull { plain(it.key) == plain(sourceId) }?.value ?: initial(schema)

    /**
     * The properties a database can filter by, split into the ones with a row of chips above the
     * notes (in their order) and the others (in the database's order).
     */
    fun chipRows(candidates: List<String>, quickFilters: List<String>, filterOrder: List<String> = emptyList()): Pair<List<String>, List<String>> {
        val rows = quickFilters.filter { it in candidates }
        val rest = candidates.filterNot { it in rows }
        // The others keep the order the user gave them; properties not placed yet follow in the database's order.
        val placed = filterOrder.filter { it in rest }
        return rows to (placed + rest.filterNot { it in placed })
    }

    /** [setup] after the user arranged the Filters panel: [shown] have a row of chips, [hidden] don't; both keep their order. */
    fun arranged(setup: DatabaseSetup, shown: List<String>, hidden: List<String>): DatabaseSetup =
        setup.copy(quickFilters = shown, filterOrder = shown + hidden)

    private fun plain(id: String) = id.replace("-", "")
}
