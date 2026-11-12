package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.EditMode
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PropertyEdit
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.QuickAction
import be.wimvandonck.noter.data.model.SelectOption
import be.wimvandonck.noter.data.notion.InlineMarkdown
import be.wimvandonck.noter.data.notion.PropertyCodec
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Quick actions (#119), the app's own Notion buttons: Notion's can't be read or pressed through
 * its API. Made per database (⋮ › Quick actions), each is a name and changes to a note's
 * properties, as Notion's button step "Edit property" makes them; a button under a note's
 * properties runs it on that note. Kept on this phone with the database's setup.
 */
object QuickActions {
    /** Property kinds a quick action can change. */
    val EDITABLE = setOf(
        "title", "rich_text", "number", "select", "status", "multi_select", "date",
        "people", "checkbox", "url", "email", "phone_number", "relation",
    )

    /** Kinds that hold several values (options, people, pages). */
    private val SEVERAL = setOf("multi_select", "people", "relation")

    /** What a quick action can do with a property of kind [type], in the order the editor offers it. */
    fun modes(type: String): List<EditMode> = when (type) {
        in SEVERAL -> listOf(EditMode.ADD, EditMode.REMOVE, EditMode.SET, EditMode.CLEAR)
        "number" -> listOf(EditMode.SET, EditMode.ADD, EditMode.REMOVE, EditMode.CLEAR)
        "date" -> listOf(EditMode.SET, EditMode.ADD, EditMode.CLEAR)
        "checkbox" -> listOf(EditMode.SET, EditMode.TOGGLE)
        // A note always has a title: it can be set, not emptied.
        "title" -> listOf(EditMode.SET)
        in EDITABLE -> listOf(EditMode.SET, EditMode.CLEAR)
        else -> emptyList()
    }

    /** The properties of [schema] a quick action can change, in its order. */
    fun properties(schema: DatabaseSchema?): List<PropertySchema> = schema?.properties.orEmpty().filter { it.type in EDITABLE }

    /** A first change for [property]: what it can do first; a checkbox gets checked. */
    fun newEdit(property: PropertySchema): PropertyEdit {
        val mode = modes(property.type).firstOrNull() ?: EditMode.SET
        return PropertyEdit(property.name, mode, values = if (property.type == "checkbox") listOf(PropertyEdit.CHECKED) else emptyList())
    }

    /** [edit] in [mode], keeping what still fits (one option for a single choice). */
    fun withMode(edit: PropertyEdit, type: String, mode: EditMode): PropertyEdit =
        edit.copy(mode = mode, values = if (type in SEVERAL || type == "checkbox") edit.values else edit.values.take(1))

    /** [edit] with option or page [key] chosen or no longer chosen; a single choice swaps its value. */
    fun toggleValue(edit: PropertyEdit, type: String, key: String): PropertyEdit {
        val next = when {
            key in edit.values -> edit.values - key
            type in SEVERAL -> edit.values + key
            else -> listOf(key)
        }
        return edit.copy(values = next)
    }

    /** [edit] with [user] chosen or no longer chosen. */
    fun togglePerson(edit: PropertyEdit, user: NotionUser): PropertyEdit =
        if (edit.people.any { it.id == user.id }) edit.copy(people = edit.people.filterNot { it.id == user.id }) else edit.copy(people = edit.people + user)

    /** Whether [edit] does something: emptying, toggling, or with what to set, add or take away. */
    fun isComplete(edit: PropertyEdit, type: String): Boolean = when {
        edit.mode == EditMode.CLEAR || edit.mode == EditMode.TOGGLE -> true
        type == "date" -> true
        type == "checkbox" -> edit.values.isNotEmpty()
        type == "people" -> edit.people.isNotEmpty() || edit.mode == EditMode.SET
        type in SEVERAL -> edit.values.isNotEmpty() || edit.mode == EditMode.SET
        type in setOf("select", "status") -> edit.values.isNotEmpty()
        type == "number" -> number(edit.text) != null
        type == "title" -> edit.text.isNotBlank()
        else -> true
    }

    /**
     * [action]'s changes. A quick action of version 2.6.14 kept values a new note gets: they read
     * as changes that set them, for the properties [schema] still has.
     */
    fun editsOf(action: QuickAction, schema: DatabaseSchema?): List<PropertyEdit> {
        if (action.edits.isNotEmpty() || action.values.isEmpty()) return action.edits
        return action.values.mapNotNull { (name, keys) ->
            val type = schema?.property(name)?.type ?: return@mapNotNull null
            when (type) {
                "select", "status", "multi_select", "relation" -> PropertyEdit(name, EditMode.SET, values = keys)
                "people" -> PropertyEdit(name, EditMode.SET, people = keys.map { NotionUser(it, "") })
                "checkbox" -> PropertyEdit(name, EditMode.SET, values = listOf(if (ChipFilter.CHECKED in keys) PropertyEdit.CHECKED else PropertyEdit.UNCHECKED))
                "date" -> if (ChipFilter.TODAY in keys) PropertyEdit(name, EditMode.SET) else null
                else -> null
            }
        }
    }

    /**
     * The properties of [note] that running [action] changes, with their new values, in the order
     * of the note. Changes run in order, so two on one property both count. A person "me" is [me]
     * (left out when unknown); properties the note can't change (more than 25 links) are skipped.
     */
    fun apply(
        action: QuickAction,
        note: NoteSummary,
        schema: DatabaseSchema?,
        me: NotionUser?,
        today: LocalDate = LocalDate.now(),
    ): List<NoteProperty> {
        val working = LinkedHashMap<String, NoteProperty>()
        editsOf(action, schema).forEach { edit ->
            val propertySchema = schema?.property(edit.property)
            val current = working[edit.property] ?: note.property(edit.property) ?: propertySchema?.let { PropertyCodec.emptyValue(it) } ?: return@forEach
            if (!current.isEditable || current.type !in EDITABLE) return@forEach
            val value = changed(current, edit, propertySchema, me, today) ?: return@forEach
            working[edit.property] = current.copy(value = value)
        }
        return working.values.filter { it != note.property(it.name) && !(note.property(it.name) == null && it.isEmpty) }
    }

    private fun changed(current: NoteProperty, edit: PropertyEdit, schema: PropertySchema?, me: NotionUser?, today: LocalDate): PropertyValue? {
        val value = current.value
        return when (current.type) {
            "title", "rich_text" -> when (edit.mode) {
                EditMode.CLEAR -> if (current.type == "title") null else PropertyValue.Text("")
                else -> PropertyValue.Text(InlineMarkdown.escape(edit.text))
            }
            "url", "email", "phone_number" -> PropertyValue.Plain(if (edit.mode == EditMode.CLEAR) null else edit.text.trim().ifEmpty { null })
            "number" -> {
                if (edit.mode == EditMode.CLEAR) return PropertyValue.Number(null)
                val amount = number(edit.text) ?: return null
                val now = (value as? PropertyValue.Number)?.value ?: 0.0
                PropertyValue.Number(
                    when (edit.mode) {
                        EditMode.ADD -> now + amount
                        EditMode.REMOVE -> now - amount
                        else -> amount
                    },
                )
            }
            "select", "status" -> when (edit.mode) {
                EditMode.CLEAR -> PropertyValue.Select(null)
                else -> PropertyValue.Select(option(schema, edit.values.firstOrNull() ?: return null))
            }
            "multi_select" -> {
                val now = (value as? PropertyValue.MultiSelect)?.options.orEmpty()
                val chosen = edit.values.map { option(schema, it) }
                PropertyValue.MultiSelect(
                    when (edit.mode) {
                        EditMode.SET -> chosen
                        EditMode.ADD -> now + chosen.filter { c -> now.none { it.name == c.name } }
                        EditMode.REMOVE -> now.filter { it.name !in edit.values }
                        else -> emptyList()
                    },
                )
            }
            "people" -> {
                val now = (value as? PropertyValue.People)?.users.orEmpty()
                val chosen = edit.people.mapNotNull { if (it.id == PropertyEdit.ME) me else it }.distinctBy { it.id }
                PropertyValue.People(
                    when (edit.mode) {
                        EditMode.SET -> chosen
                        EditMode.ADD -> now + chosen.filter { c -> now.none { it.id == c.id } }
                        EditMode.REMOVE -> now.filter { u -> chosen.none { it.id == u.id } }
                        else -> emptyList()
                    },
                )
            }
            "relation" -> {
                val now = (value as? PropertyValue.Relation)?.ids.orEmpty()
                PropertyValue.Relation(
                    when (edit.mode) {
                        EditMode.SET -> edit.values
                        EditMode.ADD -> now + edit.values.filter { it !in now }
                        EditMode.REMOVE -> now.filter { it !in edit.values }
                        else -> emptyList()
                    },
                )
            }
            "checkbox" -> when (edit.mode) {
                EditMode.TOGGLE -> PropertyValue.Checkbox((value as? PropertyValue.Checkbox)?.checked != true)
                else -> PropertyValue.Checkbox(edit.values.firstOrNull() != PropertyEdit.UNCHECKED)
            }
            "date" -> {
                val now = value as? PropertyValue.Date
                when (edit.mode) {
                    EditMode.CLEAR -> PropertyValue.Date(null)
                    EditMode.ADD -> {
                        val start = now?.start
                        if (start == null) {
                            PropertyValue.Date(today.plusDays(edit.days.toLong()).toString())
                        } else {
                            PropertyValue.Date(shift(start, edit.days) ?: return null, now.end?.let { shift(it, edit.days) })
                        }
                    }
                    else -> PropertyValue.Date(today.plusDays(edit.days.toLong()).toString())
                }
            }
            else -> null
        }
    }

    /** The schema's option named [name], or a new one (Notion adds an option it doesn't know yet). */
    private fun option(schema: PropertySchema?, name: String): SelectOption =
        schema?.options?.firstOrNull { it.name == name } ?: SelectOption(name)

    /** A number typed in the editor, with a point or a comma. */
    fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    /** A Notion date moved by [days], a moment keeping its time and offset. */
    fun shift(iso: String, days: Int): String? = runCatching {
        if (NotionDates.hasTime(iso)) {
            OffsetDateTime.parse(iso).plusDays(days.toLong()).format(MOMENT)
        } else {
            LocalDate.parse(iso).plusDays(days.toLong()).toString()
        }
    }.getOrNull()

    private val MOMENT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

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
