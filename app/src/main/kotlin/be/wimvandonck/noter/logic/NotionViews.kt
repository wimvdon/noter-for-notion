package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteSort
import be.wimvandonck.noter.data.model.NotionView
import be.wimvandonck.noter.data.model.SortKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A database shows one of its Notion views, read from Notion and never changed here, or the Noter
 * view: the filters, sort and rows of chips kept on this phone ([DatabaseSetup]). A change made
 * while a Notion view shows turns it into the Noter view ([copy]), so Notion's views stay as they are.
 */
object NotionViews {
    /** The Noter view, in AppSettings.views. */
    const val NOTER = "noter"

    /** View types that list a data source's pages; forms, charts and dashboards don't. */
    val LISTED_TYPES = setOf("table", "board", "list", "calendar", "timeline", "gallery", "map")

    /** The views of [views] that list the notes of data source [sourceId], in Notion's order. */
    fun listed(views: List<NotionView>, sourceId: String): List<NotionView> =
        views.filter { it.type in LISTED_TYPES && it.dataSourceId != null && plain(it.dataSourceId) == plain(sourceId) }

    /**
     * The view data source [sourceId] shows, of its [views]: the one chosen last ([choices]); else
     * the Noter view for a database set up before Notion's views came ([setups]); else Notion's
     * first view. A chosen view that is gone gives Notion's first view; no views give the Noter view.
     */
    fun chosen(choices: Map<String, String>, setups: Map<String, DatabaseSetup>, sourceId: String, views: List<NotionView>): String {
        val choice = choices[sourceId] ?: choices.entries.firstOrNull { plain(it.key) == plain(sourceId) }?.value
        if (choice == NOTER) return NOTER
        if (choice != null) views.firstOrNull { plain(it.id) == plain(choice) }?.let { return it.id }
        if (choice == null && setups.keys.any { plain(it) == plain(sourceId) }) return NOTER
        return views.firstOrNull()?.id ?: NOTER
    }

    /**
     * What Notion is asked for [view]'s notes: its filter, with its quick filters that have a
     * condition when [quickFilters] (added to an "and" it already has, so it nests no deeper), and
     * its sorts. A sort names its property by name, as Notion's query asks ([schema] gives the name
     * of a property named by ID).
     */
    fun query(view: NotionView, schema: DatabaseSchema?, quickFilters: Boolean = true): Pair<JsonElement?, JsonElement?> {
        val quick = if (quickFilters) quickConditions(view) else emptyList()
        val filter = view.filter as? JsonObject
        val combined = when {
            quick.isEmpty() -> filter
            filter == null -> if (quick.size == 1) quick.single() else JsonObject(mapOf("and" to JsonArray(quick)))
            filter.keys == setOf("and") && filter["and"] is JsonArray -> JsonObject(mapOf("and" to JsonArray((filter["and"] as JsonArray) + quick)))
            else -> JsonObject(mapOf("and" to JsonArray(listOf(filter) + quick)))
        }
        val sorts = (view.sorts as? JsonArray)?.map { sort ->
            val obj = sort as? JsonObject ?: return@map sort
            val property = obj.string("property") ?: return@map sort
            val name = schema?.properties?.firstOrNull { it.id == property }?.name ?: return@map sort
            JsonObject(obj + ("property" to JsonPrimitive(name)))
        }?.let(::JsonArray)?.takeIf { it.isNotEmpty() }
        return combined to sorts
    }

    /** [view]'s quick filters that filter now, as filters of the property they are on. */
    private fun quickConditions(view: NotionView): List<JsonObject> =
        (view.quickFilters as? JsonObject).orEmpty().mapNotNull { (property, condition) ->
            val obj = condition as? JsonObject ?: return@mapNotNull null
            if (obj.isEmpty()) return@mapNotNull null
            JsonObject(mapOf("property" to JsonPrimitive(property)) + obj)
        }

    /**
     * [view]'s first sort as Noter's: by created or edited time, title or a property it can sort
     * by. Anything else (no sort, a property that is gone) is the custom order.
     */
    fun noterSort(view: NotionView, schema: DatabaseSchema?): NoteSort {
        val first = (view.sorts as? JsonArray)?.firstOrNull() as? JsonObject ?: return NoteSort(SortKind.CUSTOM)
        val descending = first.string("direction") == "descending"
        first.string("timestamp")?.let { timestamp ->
            return when (timestamp) {
                "created_time" -> NoteSort(SortKind.CREATED, descending = descending)
                "last_edited_time" -> NoteSort(SortKind.LAST_EDITED, descending = descending)
                else -> NoteSort(SortKind.CUSTOM)
            }
        }
        val name = first.string("property") ?: return NoteSort(SortKind.CUSTOM)
        val property = schema?.properties?.firstOrNull { it.name == name } ?: schema?.properties?.firstOrNull { it.id == name }
            ?: return NoteSort(SortKind.CUSTOM)
        return when {
            property.type == "title" -> NoteSort(SortKind.TITLE, descending = descending)
            property.isSortable -> NoteSort(SortKind.PROPERTY, property.name, descending)
            else -> NoteSort(SortKind.CUSTOM)
        }
    }

    /**
     * The Noter view a change in Notion view [view] starts from: [setup] (its rows of chips, cards
     * and quick actions) showing only [view]'s notes, nothing selected, sorted as [view] is.
     */
    fun copy(setup: DatabaseSetup, view: NotionView, schema: DatabaseSchema?): DatabaseSetup =
        setup.copy(baseView = view.id, baseViewName = view.name, selected = emptyMap(), sort = noterSort(view, schema))

    /** [setup] as a Notion view shows it: its rows of chips with nothing selected, sorted as [view] is. */
    fun shownAs(setup: DatabaseSetup, view: NotionView, schema: DatabaseSchema?): DatabaseSetup =
        setup.copy(selected = emptyMap(), sort = noterSort(view, schema))

    /**
     * The IDs a view shows, in its order ([ids], as Notion listed them when asked at [fetchedAt],
     * ISO 8601), with the notes made since ([createdTimes], by ID: they can't be in it yet) first.
     */
    fun members(ids: List<String>, fetchedAt: String, createdTimes: Map<String, String>): List<String> {
        val known = ids.toSet()
        val newer = createdTimes.filter { (id, created) -> id !in known && created > fetchedAt }
            .entries.sortedByDescending { it.value }.map { it.key }
        return newer + ids
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

    private fun plain(id: String) = id.replace("-", "")
}
