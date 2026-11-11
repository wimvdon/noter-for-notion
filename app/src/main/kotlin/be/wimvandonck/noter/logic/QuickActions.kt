package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.QuickAction
import java.time.LocalDate

/**
 * Quick actions (#119): Notion's buttons can't be read through its API, so the user makes their
 * own in a database (⋮ › Quick actions). Each one is a name and the values a new note gets; the
 * + button offers them next to New note. Kept on this phone with the database's setup.
 */
object QuickActions {
    /** Property kinds that can hold several values; the others take one. */
    private val SEVERAL = setOf("multi_select", "people", "relation")

    /** Property kinds a quick action can fill (computed ones, like formulas, can't be). */
    private val FILLABLE = setOf("select", "status", "multi_select", "people", "checkbox", "relation", "date")

    /**
     * What a quick action can choose from: the database's filter values ([groups]) a new note can
     * get. A date can only be today (the other date chips are ranges, not one date).
     */
    fun choices(groups: List<ChipGroup>): List<ChipGroup> =
        groups.filter { it.property.type in FILLABLE }
            .map { group ->
                if (group.property.type == "date") group.copy(chips = group.chips.filter { it.key == ChipFilter.TODAY }) else group
            }
            .filter { it.chips.isNotEmpty() }
            .map { it.copy(selected = emptySet()) }

    /** [values] with [key] of [property] chosen or no longer chosen; a one-value kind swaps its value. */
    fun toggle(values: Map<String, List<String>>, property: String, type: String, key: String): Map<String, List<String>> {
        val now = values[property].orEmpty()
        val next = when {
            key in now -> now - key
            type in SEVERAL -> now + key
            else -> listOf(key)
        }
        return if (next.isEmpty()) values - property else values + (property to next)
    }

    /** The values [action] gives a new note, for the properties [groups] still has. */
    fun presets(action: QuickAction, groups: List<ChipGroup>, today: LocalDate = LocalDate.now()): List<NoteProperty> =
        groups.mapNotNull { group ->
            val schema = group.property
            val keys = action.values[schema.name].orEmpty()
            val chips = keys.mapNotNull { key -> group.chips.firstOrNull { it.key == key } }
            if (chips.isEmpty()) return@mapNotNull null
            when (schema.type) {
                "multi_select" -> {
                    val options = chips.mapNotNull { chip -> schema.options.firstOrNull { it.name == chip.key } }
                    if (options.isEmpty()) null else NoteProperty(schema.name, schema.type, PropertyValue.MultiSelect(options))
                }
                "people" -> NoteProperty(schema.name, schema.type, PropertyValue.People(chips.map { NotionUser(it.key, it.label) }))
                "relation" -> NoteProperty(schema.name, schema.type, PropertyValue.Relation(chips.map { it.key }))
                else -> presetForChip(schema, chips.first(), today)
            }
        }

    /** [actions] with [action] saved: in its place when it was there, else at the end. A blank name isn't saved. */
    fun save(actions: List<QuickAction>, action: QuickAction): List<QuickAction> {
        val named = action.copy(name = action.name.trim())
        if (named.name.isEmpty()) return actions
        val at = actions.indexOfFirst { it.id == named.id }
        return if (at < 0) actions + named else actions.toMutableList().also { it[at] = named }
    }

    fun remove(actions: List<QuickAction>, id: String): List<QuickAction> = actions.filterNot { it.id == id }

    /** Undo for [remove]: [action] back at [index] (or at the end, when the list got shorter). */
    fun restore(actions: List<QuickAction>, action: QuickAction, index: Int): List<QuickAction> {
        if (actions.any { it.id == action.id }) return actions
        return actions.toMutableList().also { it.add(index.coerceIn(0, it.size), action) }
    }
}

/** One chosen value of a one-value kind as a new note's value. */
private fun presetForChip(schema: PropertySchema, chip: FilterChip, today: LocalDate): NoteProperty? {
    val value = when (schema.type) {
        "select", "status" -> PropertyValue.Select(schema.options.firstOrNull { it.name == chip.key } ?: return null)
        "checkbox" -> PropertyValue.Checkbox(chip.key == ChipFilter.CHECKED)
        "date" -> if (chip.key == ChipFilter.TODAY) PropertyValue.Date(today.toString()) else return null
        else -> return null
    }
    return NoteProperty(schema.name, schema.type, value)
}
