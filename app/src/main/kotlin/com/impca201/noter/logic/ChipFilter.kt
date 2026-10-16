package com.impca201.noter.logic

import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.model.PropertySchema
import com.impca201.noter.data.model.PropertyValue

/**
 * A filter chip: [key] is an option name, a user ID for person properties, or [ChipFilter.CHECKED]
 * / [ChipFilter.UNCHECKED] for checkbox properties (shown with their own words, see [isCheckbox]).
 */
data class FilterChip(
    val key: String,
    val label: String,
    val color: String = "default",
    val count: Int = 0,
) {
    val isCheckbox: Boolean get() = key == ChipFilter.CHECKED || key == ChipFilter.UNCHECKED
}

/** Filter chips from a select, status, multi-select, person or checkbox property. */
object ChipFilter {
    const val CHECKED = "__checked"
    const val UNCHECKED = "__unchecked"

    fun chips(property: PropertySchema?, notes: List<NoteSummary>): List<FilterChip> {
        property ?: return emptyList()
        val keysPerNote = notes.map { keysOf(it, property.name) }
        fun count(key: String) = keysPerNote.count { key in it }
        return when (property.type) {
            "select", "status", "multi_select" -> {
                val known = property.options.map { FilterChip(it.name, it.name, it.color, count(it.name)) }
                // Options only seen on notes (the schema may be older than the notes).
                val knownNames = known.map { it.key }.toSet()
                val extra = notes.flatMap { note ->
                    when (val value = note.property(property.name)?.value) {
                        is PropertyValue.Select -> listOfNotNull(value.option)
                        is PropertyValue.MultiSelect -> value.options
                        else -> emptyList()
                    }
                }.distinctBy { it.name }.filter { it.name !in knownNames }
                    .map { FilterChip(it.name, it.name, it.color, count(it.name)) }
                known + extra
            }
            "people" -> notes.flatMap { (it.property(property.name)?.value as? PropertyValue.People)?.users.orEmpty() }
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase() }
                .map { FilterChip(it.id, it.name.ifBlank { "?" }, count = count(it.id)) }
            "checkbox" -> listOf(FilterChip(CHECKED, "", count = count(CHECKED)), FilterChip(UNCHECKED, "", count = count(UNCHECKED)))
            else -> emptyList()
        }
    }

    /** The chip keys a note has for [propertyName]. */
    fun keysOf(note: NoteSummary, propertyName: String): Set<String> =
        when (val value = note.property(propertyName)?.value) {
            is PropertyValue.Select -> setOfNotNull(value.option?.name)
            is PropertyValue.MultiSelect -> value.options.map { it.name }.toSet()
            is PropertyValue.People -> value.users.map { it.id }.toSet()
            is PropertyValue.Checkbox -> setOf(if (value.checked) CHECKED else UNCHECKED)
            else -> emptySet()
        }

    /** Notes that have at least one of the [selected] chips; all notes when nothing is selected. */
    fun filter(notes: List<NoteSummary>, propertyName: String?, selected: Set<String>): List<NoteSummary> {
        if (propertyName == null || selected.isEmpty()) return notes
        return notes.filter { note -> keysOf(note, propertyName).any { it in selected } }
    }
}
