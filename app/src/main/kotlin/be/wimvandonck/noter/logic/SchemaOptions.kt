package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.SelectOption

/**
 * Keeps the options a picker offers in step with the notes: an option a note has (added on this
 * phone, or in Notion since the database's properties were last downloaded) is added to its
 * property, so every note's picker shows it at once.
 */
object SchemaOptions {
    fun withValues(schema: DatabaseSchema, notes: List<NoteSummary>): DatabaseSchema {
        var changed = false
        val properties = schema.properties.map { property ->
            if (property.type !in OPTION_TYPES) return@map property
            val used = notes.flatMap { note ->
                when (val value = note.properties.firstOrNull { it.name == property.name }?.value) {
                    is PropertyValue.Select -> listOfNotNull(value.option)
                    is PropertyValue.MultiSelect -> value.options
                    else -> emptyList<SelectOption>()
                }
            }
            val missing = used.filter { option -> property.options.none { it.name == option.name } }.distinctBy { it.name }
            if (missing.isEmpty()) property else property.copy(options = property.options + missing).also { changed = true }
        }
        return if (changed) schema.copy(properties = properties) else schema
    }

    private val OPTION_TYPES = setOf("select", "multi_select", "status")
}
