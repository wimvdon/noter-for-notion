package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotebookSort
import be.wimvandonck.noter.data.model.NotionView
import be.wimvandonck.noter.data.model.PropertyLayout
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
 * A notebook's notes: those its Notion view shows (when it follows one), filtered by the values
 * selected per property (a note shows when it has one of the selected values of every filtering
 * property), sorted, pinned ones first. Everything is worked out on this phone, from the notes it
 * has, so it works offline.
 */
object Notebooks {
    /**
     * The notes the Notion view showed when last asked ([result]), plus the ones Notion couldn't
     * know about yet (made on this phone, or made after that). Without a result: all of them.
     */
    fun inView(notes: List<NoteSummary>, result: ViewResult?): List<NoteSummary> {
        result ?: return notes
        val ids = result.ids.map(::plain).toSet()
        return notes.filter { it.isLocal || plain(it.id) in ids || it.createdTime > result.fetchedAt }
    }

    /** [notes] that have one of the selected values of every property in [selected]. */
    fun filter(notes: List<NoteSummary>, selected: Map<String, Set<String>>, today: LocalDate = LocalDate.now()): List<NoteSummary> {
        val active = selected.filterValues { it.isNotEmpty() }
        if (active.isEmpty()) return notes
        return notes.filter { note -> active.all { (property, values) -> ChipFilter.keysOf(note, property, today).any { it in values } } }
    }

    /** [notes] in the order [sort] asks for. [viewOrder]: the Notion view's; [customOrder]: the dragged one. */
    fun sorted(notes: List<NoteSummary>, sort: NotebookSort, viewOrder: List<String>, customOrder: List<String>): List<NoteSummary> =
        when (sort.kind) {
            SortKind.CUSTOM -> NoteOrdering.sort(notes, customOrder)
            SortKind.NOTION -> {
                // Notes Notion doesn't have in the view yet (new ones) on top, as in the custom order.
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
        viewResult: ViewResult?,
        pinned: List<String>,
        customOrder: List<String>,
        today: LocalDate = LocalDate.now(),
    ): NotebookContent {
        val shown = if (config.viewId != null && config.followView) inView(notes, viewResult) else notes
        // A value that no note has any more (an option deleted in Notion) doesn't hide every note.
        val selected = selection(config, schema).mapValues { (name, values) ->
            val property = schema?.property(name) ?: return@mapValues values
            val keys = ChipFilter.chips(property, shown, today).map { it.key }.toSet()
            values.filter { it in keys }.toSet()
        }
        val groups = config.quickFilters.mapNotNull { name ->
            val property = schema?.property(name) ?: return@mapNotNull null
            ChipGroup(property, ChipFilter.chips(property, shown, today), selected[name].orEmpty())
        }
        val filtered = filter(shown, selected, today)
        val sort = config.currentSort
        val sortedNotes = sorted(filtered, sort, viewResult?.ids.orEmpty(), customOrder)
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
     * A new notebook of a database, set up like the Notion [view] it starts from (null: all
     * notes): its quick filters (else what a board is grouped by, else a status or select
     * property) and Notion's order.
     */
    fun fromView(base: DatabaseConfig, view: NotionView?, schema: DatabaseSchema?): DatabaseConfig {
        fun name(key: String): String? = schema?.properties?.firstOrNull { it.name == key || it.id == key }?.name
        val quick = view?.quickFilters.orEmpty().mapNotNull(::name)
            .ifEmpty { listOfNotNull(view?.groupBy?.let(::name)) }
            .ifEmpty {
                listOfNotNull(
                    schema?.properties?.firstOrNull { it.type == "status" }?.name ?: schema?.properties?.firstOrNull { it.type == "select" }?.name,
                )
            }
            .filter { schema?.property(it)?.isChipCandidate != false }
        return base.copy(
            viewId = view?.id,
            viewName = view?.name,
            followView = view != null,
            quickFilters = quick,
            selected = emptyMap(),
            sort = null,
        )
    }

    /**
     * How the notes show the database's properties, from the Notion [view] (its order and which
     * are hidden), when the user hasn't arranged them yet ([current] null).
     */
    fun layoutFromView(current: PropertyLayout?, view: NotionView?, schema: DatabaseSchema?): PropertyLayout? {
        if (view == null || schema == null) return current
        val byId = schema.properties.associateBy { it.id }
        val listed = view.properties.mapNotNull { (id, visible) -> byId[id]?.takeIf { it.type != "title" }?.let { it.name to visible } }
        val base = current ?: PropertyLayout(order = listed.map { it.first }, hidden = listed.filterNot { it.second }.map { it.first })
        return if (view.showsCover && current == null) base.copy(coverOnCard = true) else base
    }

    private fun plain(id: String) = id.replace("-", "")
}
