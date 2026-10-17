package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import java.time.LocalDate

/**
 * A filter chip. [key] is an option name, a user ID (person), a page ID (relation), a person's
 * name (created by, last edited by), a formula's text, or one of [ChipFilter]'s fixed keys
 * (checked / not checked, date ranges), which the screen shows in its own words. [label] is empty
 * when the screen names the chip (fixed keys, related pages by their title).
 */
data class FilterChip(
    val key: String,
    val label: String,
    val color: String = "default",
    val count: Int = 0,
) {
    val isCheckbox: Boolean get() = key == ChipFilter.CHECKED || key == ChipFilter.UNCHECKED
    val isDate: Boolean get() = key in ChipFilter.DATE_KEYS
}

/**
 * Filter chips from one property: select, status, multi-select, person, checkbox, relation,
 * created by, last edited by, date or formula.
 */
object ChipFilter {
    const val CHECKED = "__checked"
    const val UNCHECKED = "__unchecked"

    const val OVERDUE = "__date_overdue"
    const val TODAY = "__date_today"
    const val NEXT_7_DAYS = "__date_next7"
    const val LATER = "__date_later"
    const val NO_DATE = "__date_none"
    val DATE_KEYS = listOf(OVERDUE, TODAY, NEXT_7_DAYS, LATER, NO_DATE)

    /** At most this many chips for a formula's different texts (a formula can give anything). */
    const val MAX_FORMULA_CHIPS = 30

    fun chips(property: PropertySchema?, notes: List<NoteSummary>, today: LocalDate = LocalDate.now()): List<FilterChip> {
        property ?: return emptyList()
        val keysPerNote = notes.map { keysOf(it, property.name, today) }
        fun count(key: String) = keysPerNote.count { key in it }
        fun fixed(vararg keys: String) = keys.map { FilterChip(it, "", count = count(it)) }
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
            "checkbox" -> fixed(CHECKED, UNCHECKED)
            "date" -> fixed(*DATE_KEYS.toTypedArray())
            // Linked pages, named by the screen (it knows their titles).
            "relation" -> keysPerNote.flatten().distinct().map { FilterChip(it, "", count = count(it)) }
            "created_by", "last_edited_by" -> keysPerNote.flatten().distinct().sortedWith(NATURAL)
                .map { FilterChip(it, it, count = count(it)) }
            "formula" -> {
                val values = keysPerNote.flatten().distinct()
                if (values.isNotEmpty() && values.all { it == CHECKED || it == UNCHECKED }) {
                    fixed(CHECKED, UNCHECKED)
                } else {
                    values.filter { it != CHECKED && it != UNCHECKED }.sortedWith(NATURAL).take(MAX_FORMULA_CHIPS)
                        .map { FilterChip(it, it, count = count(it)) }
                }
            }
            else -> emptyList()
        }
    }

    /** True when some note has a value among [chips] ("No date" isn't one). */
    fun anyValue(chips: List<FilterChip>): Boolean = chips.any { it.count > 0 && it.key != NO_DATE }

    /** Texts in the order people expect: ignoring case, and numbers by their value (2 before 10). */
    val NATURAL: Comparator<String> = Comparator { a, b ->
        val x = chunks(a.lowercase())
        val y = chunks(b.lowercase())
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val byValue = if (p[0] in '0'..'9' && q[0] in '0'..'9') p.toBigInteger().compareTo(q.toBigInteger()) else p.compareTo(q)
            if (byValue != 0) return@Comparator byValue
        }
        x.size.compareTo(y.size)
    }

    private fun chunks(text: String): List<String> = Regex("\\d+|\\D+").findAll(text).map { it.value }.toList()

    /** The chip keys a note has for [propertyName]. */
    fun keysOf(note: NoteSummary, propertyName: String, today: LocalDate = LocalDate.now()): Set<String> =
        when (val value = note.property(propertyName)?.value) {
            is PropertyValue.Select -> setOfNotNull(value.option?.name)
            is PropertyValue.MultiSelect -> value.options.map { it.name }.toSet()
            is PropertyValue.People -> value.users.map { it.id }.toSet()
            is PropertyValue.Checkbox -> setOf(if (value.checked) CHECKED else UNCHECKED)
            is PropertyValue.Relation -> value.ids.toSet()
            is PropertyValue.Date -> setOf(dateKey(value, today))
            // Computed values (created by, formulas) as Notion shows them; a yes/no formula as checked or not.
            is PropertyValue.ReadOnly -> when (value.display) {
                "true" -> setOf(CHECKED)
                "false" -> setOf(UNCHECKED)
                "" -> emptySet()
                else -> setOf(value.display)
            }
            else -> emptySet()
        }

    /**
     * Which date range a date falls in: before today (overdue; for a range, when it ended), today
     * (or a range that includes today), the next seven days, later, or no date.
     */
    fun dateKey(value: PropertyValue.Date, today: LocalDate): String {
        val start = parseDay(value.start) ?: return NO_DATE
        val end = parseDay(value.end) ?: start
        return when {
            end < today -> OVERDUE
            start <= today -> TODAY
            start <= today.plusDays(7) -> NEXT_7_DAYS
            else -> LATER
        }
    }

    private fun parseDay(iso: String?): LocalDate? = iso?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Notes that have at least one of the [selected] chips; all notes when nothing is selected. */
    fun filter(notes: List<NoteSummary>, propertyName: String?, selected: Set<String>, today: LocalDate = LocalDate.now()): List<NoteSummary> {
        if (propertyName == null || selected.isEmpty()) return notes
        return notes.filter { note -> keysOf(note, propertyName, today).any { it in selected } }
    }
}
