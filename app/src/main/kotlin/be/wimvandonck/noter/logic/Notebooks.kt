package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotebookSort
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.SortKind
import be.wimvandonck.noter.data.model.ViewResult
import be.wimvandonck.noter.data.notion.InlineMarkdown
import java.time.LocalDate

/** One property's filter chips above the notes, with the ones selected. */
data class ChipGroup(
    /** The property (null on Pinned, whose chips are the sources). */
    val property: PropertySchema?,
    val chips: List<FilterChip>,
    val selected: Set<String>,
)

/** What a notebook shows: its quick filters, then pinned notes, then the rest. */
data class NotebookContent(
    val groups: List<ChipGroup>,
    val pinned: List<NoteSummary>,
    val others: List<NoteSummary>,
    /** How many properties filter the notes now (for the Filters button). */
    val filterCount: Int,
)

/**
 * A notebook's notes: all notes of its database, filtered by the values selected per property (a
 * note shows when it has one of the selected values of every filtering property), sorted, pinned
 * ones first. Everything is worked out on this phone, from the notes it has, so it works offline.
 */
object Notebooks {
    /** [notes] that have one of the selected values of every property in [selected]. */
    fun filter(notes: List<NoteSummary>, selected: Map<String, Set<String>>, today: LocalDate = LocalDate.now()): List<NoteSummary> {
        val active = selected.filterValues { it.isNotEmpty() }
        if (active.isEmpty()) return notes
        return notes.filter { note -> active.all { (property, values) -> ChipFilter.keysOf(note, property, today).any { it in values } } }
    }

    /** [notes] in the order [sort] asks for. [viewOrder]: the page's order of its subpages; [customOrder]: the dragged one. */
    fun sorted(notes: List<NoteSummary>, sort: NotebookSort, viewOrder: List<String>, customOrder: List<String>): List<NoteSummary> =
        when (sort.kind) {
            SortKind.CUSTOM -> NoteOrdering.sort(notes, customOrder)
            SortKind.NOTION -> {
                // Subpages Notion didn't list yet (new ones) on top, as in the custom order.
                val position = viewOrder.withIndex().associate { (i, id) -> plain(id) to i }
                val (known, new) = notes.partition { plain(it.id) in position }
                new.sortedByDescending { it.lastEditedTime } + known.sortedBy { position.getValue(plain(it.id)) }
            }
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
    fun selection(config: DatabaseConfig, schema: DatabaseSchema?): Map<String, Set<String>> =
        config.selected.filterKeys { schema == null || schema.property(it) != null }.mapValues { it.value.toSet() }

    fun content(
        notes: List<NoteSummary>,
        schema: DatabaseSchema?,
        config: DatabaseConfig,
        pageOrder: ViewResult?,
        pinned: List<String>,
        customOrder: List<String>,
        today: LocalDate = LocalDate.now(),
    ): NotebookContent {
        // A value that no note has any more (an option deleted in Notion) doesn't hide every note.
        val selected = selection(config, schema).mapValues { (name, values) ->
            val property = schema?.property(name) ?: return@mapValues values
            val keys = ChipFilter.chips(property, notes, today).map { it.key }.toSet()
            values.filter { it in keys }.toSet()
        }
        // A row only shows once a note has a value for its property.
        val groups = config.quickFilters.mapNotNull { name ->
            val property = schema?.property(name) ?: return@mapNotNull null
            ChipGroup(property, ChipFilter.chips(property, notes, today), selected[name].orEmpty())
        }.filter { ChipFilter.anyValue(it.chips) }
        val filtered = filter(notes, selected, today)
        val sort = config.currentSort
        val sortedNotes = sorted(filtered, sort, pageOrder?.ids.orEmpty(), customOrder)
        val pinnedSet = pinned.toSet()
        val (top, rest) = sortedNotes.partition { it.id in pinnedSet }
        return NotebookContent(groups, top, rest, selected.count { it.value.isNotEmpty() })
    }

    /** [selected] with [key] of [property] switched on or off. */
    fun toggle(selected: Map<String, List<String>>, property: String, key: String): Map<String, List<String>> {
        val values = selected[property].orEmpty()
        val next = if (key in values) values - key else values + key
        return if (next.isEmpty()) selected - property else selected + (property to next)
    }

    /**
     * A new notebook of a database: all its notes, in the custom order, with a status (else a
     * select) property as its first row of filter chips.
     */
    fun newNotebook(base: DatabaseConfig, schema: DatabaseSchema?): DatabaseConfig {
        val quick = listOfNotNull(
            schema?.properties?.firstOrNull { it.type == "status" }?.name ?: schema?.properties?.firstOrNull { it.type == "select" }?.name,
        )
        return base.copy(quickFilters = quick, selected = emptyMap(), sort = null)
    }

    /**
     * The properties a notebook can filter by, split into the ones with a row of chips above the
     * notes (in their order) and the others (in the database's order).
     */
    fun chipRows(candidates: List<String>, quickFilters: List<String>): Pair<List<String>, List<String>> =
        quickFilters.filter { it in candidates } to candidates.filterNot { it in quickFilters }

    private fun plain(id: String) = id.replace("-", "")
}
