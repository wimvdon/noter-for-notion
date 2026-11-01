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

    /**
     * What the Sort menu offers: the custom order, last edited, created, title, then every property
     * of [schema] that can be sorted ([PropertySchema.isSortable]), in the database's order.
     */
    fun sortChoices(schema: DatabaseSchema?): List<NoteSort> =
        listOf(NoteSort(SortKind.CUSTOM), NoteSort(SortKind.LAST_EDITED), NoteSort(SortKind.CREATED), NoteSort(SortKind.TITLE)) +
            schema?.properties.orEmpty().filter { it.isSortable }.map { NoteSort(SortKind.PROPERTY, it.name, descending = false) }

    /** [notes] in the order [sort] asks for; [customOrder]: the dragged one; [schema]: for the order of options. */
    fun sorted(notes: List<NoteSummary>, sort: NoteSort, customOrder: List<String>, schema: DatabaseSchema? = null): List<NoteSummary> =
        when (sort.kind) {
            SortKind.CUSTOM, SortKind.NOTION -> NoteOrdering.sort(notes, customOrder)
            SortKind.LAST_EDITED -> notes.sortedBy { it.lastEditedTime }.let { if (sort.descending) it.reversed() else it }
            SortKind.CREATED -> notes.sortedBy { it.createdTime }.let { if (sort.descending) it.reversed() else it }
            SortKind.TITLE -> notes.sortedBy { InlineMarkdown.stripped(it.title).lowercase() }.let { if (sort.descending) it.reversed() else it }
            SortKind.DATE, SortKind.PROPERTY -> {
                val property = sort.property
                val propertySchema = property?.let { schema?.property(it) }
                val keyed = notes.map { note -> note to property?.let { note.property(it)?.value }?.let { sortKey(it, propertySchema) } }
                val (valued, empty) = keyed.partition { it.second != null }
                val byKey = compareBy<Pair<NoteSummary, SortKey?>> { it.second }
                val ordered = valued.sortedWith(if (sort.descending) byKey.reversed() else byKey)
                (ordered + empty).map { it.first }
            }
        }

    /**
     * What a note is sorted on: a number (numbers, checkboxes, options in the database's order,
     * formulas that give a number) or else a text (dates as Notion's ISO text sort in time order).
     * Null when the note has no value, so it goes last.
     */
    internal data class SortKey(val number: Double?, val text: String) : Comparable<SortKey> {
        override fun compareTo(other: SortKey): Int = when {
            number != null && other.number != null -> number.compareTo(other.number).takeIf { it != 0 } ?: text.compareTo(other.text)
            number != null -> -1
            other.number != null -> 1
            else -> text.compareTo(other.text)
        }
    }

    internal fun sortKey(value: PropertyValue, property: PropertySchema?): SortKey? = when (value) {
        is PropertyValue.Text -> textKey(InlineMarkdown.stripped(value.markdown))
        is PropertyValue.Number -> value.value?.let { SortKey(it, "") }
        is PropertyValue.Select -> value.option?.let { optionKey(it.name, property) }
        is PropertyValue.MultiSelect -> value.options.firstOrNull()?.let { optionKey(it.name, property) }
        is PropertyValue.Date -> value.start?.takeIf { it.isNotBlank() }?.let { SortKey(null, it) }
        is PropertyValue.People -> textKey(value.users.joinToString(", ") { it.name })
        is PropertyValue.Checkbox -> SortKey(if (value.checked) 1.0 else 0.0, "")
        is PropertyValue.Plain -> textKey(value.value.orEmpty())
        is PropertyValue.Relation, is PropertyValue.Files -> null
        // Formulas, rollups, timestamps, IDs: their text, as a number when it is one.
        is PropertyValue.ReadOnly -> value.display.trim().takeIf { it.isNotEmpty() }?.let { SortKey(it.toDoubleOrNull(), it.lowercase()) }
    }

    private fun textKey(text: String): SortKey? = text.trim().takeIf { it.isNotEmpty() }?.let { SortKey(null, it.lowercase()) }

    /** As in Notion, options sort in the order the database lists them; unknown ones after, by name. */
    private fun optionKey(name: String, property: PropertySchema?): SortKey {
        val index = property?.options.orEmpty().indexOfFirst { it.name == name }
        return if (index >= 0) SortKey(index.toDouble(), name.lowercase()) else SortKey(null, name.lowercase())
    }

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
        val sortedNotes = sorted(filtered, setup.currentSort, customOrder, schema)
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
