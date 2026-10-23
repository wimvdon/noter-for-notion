package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertySchema

/**
 * A property in Edit database: one the data source has ([id], its Notion [type]) or one being
 * added ([id] null, [kind] set). [name] is what the user typed.
 */
data class EditedProperty(val id: String?, val name: String, val type: String, val kind: PropertyKind? = null) {
    /** The title property can be renamed, not removed (Notion doesn't allow it). */
    val removable: Boolean get() = type != "title"
}

/**
 * What Edit database changes in the data source's properties: Notion's `PATCH /data_sources`
 * [properties] (empty: nothing), and the names that changed ([renamed], old to new) or went
 * ([removed]), for what this phone keeps about them by name.
 */
data class SchemaChange(
    val properties: Map<String, Any?>,
    val renamed: Map<String, String>,
    val removed: Set<String>,
) {
    val isEmpty: Boolean get() = properties.isEmpty()

    /** A database's filters, sort and cards with the renamed properties' new names, without the removed ones. */
    fun applyTo(setup: DatabaseSetup): DatabaseSetup = setup.copy(
        quickFilters = names(setup.quickFilters),
        filterOrder = names(setup.filterOrder),
        selected = setup.selected.filterKeys { it !in removed }.mapKeys { rename(it.key) },
        sort = setup.sort?.let { sort ->
            when (val property = sort.property) {
                null -> sort
                in removed -> null
                else -> sort.copy(property = rename(property))
            }
        },
        cards = setup.cards?.let { it.copy(properties = names(it.properties)) },
    )

    /** How the note shows its properties (order, hidden ones), likewise. */
    fun applyTo(layout: PropertyLayout): PropertyLayout = layout.copy(
        order = names(layout.order),
        hidden = names(layout.hidden),
        onCard = names(layout.onCard),
    )

    private fun rename(name: String) = renamed[name] ?: name

    private fun names(list: List<String>) = list.filter { it !in removed }.map(::rename)
}

object SchemaEdits {
    /** The data source's properties as Edit database starts: the title property first, then Notion's order. */
    fun start(properties: List<PropertySchema>): List<EditedProperty> =
        properties.sortedBy { it.type != "title" }.map { EditedProperty(it.id, it.name, it.type) }

    /** [list] with a new property of [kind] at the end, named [defaultName] (numbered when taken, as Notion does). */
    fun add(list: List<EditedProperty>, kind: PropertyKind, defaultName: String): List<EditedProperty> =
        list + EditedProperty(null, NewDatabases.uniqueName(defaultName, list.map { it.name }), kind.type, kind)

    /**
     * What turns [original] into [edited]. A property left out is removed, a new name renames it
     * (a blank one keeps the old), a property without an ID is added (a blank name becomes
     * [fallbackNames]' name for its kind). Names stay unique: one that clashes gets a number.
     */
    fun change(original: List<PropertySchema>, edited: List<EditedProperty>, fallbackNames: Map<PropertyKind, String> = emptyMap()): SchemaChange {
        val byId = original.associateBy { it.id }
        val kept = edited.filter { it.id != null && it.id in byId }
        val removed = original.filter { o -> kept.none { it.id == o.id } }
        // Unchanged names are taken first, so a renamed or new property never pushes them aside.
        val wanted = edited.map { property ->
            val old = property.id?.let(byId::get)
            property to when {
                old != null -> property.name.trim().ifBlank { old.name }
                else -> property.name.trim().ifBlank { property.kind?.let { fallbackNames[it] }.orEmpty().ifBlank { property.type } }
            }
        }
        val taken = wanted.filter { (p, name) -> p.id != null && byId[p.id]?.name == name }.map { it.second }.toMutableList()
        val body = linkedMapOf<String, Any?>()
        val renamed = linkedMapOf<String, String>()
        removed.forEach { body[it.id] = null }
        wanted.forEach { (property, name) ->
            val old = property.id?.let(byId::get)
            if (old != null && old.name == name) return@forEach
            val final = NewDatabases.uniqueName(name, taken)
            taken += final
            when {
                old != null -> if (final != old.name) {
                    body[old.id] = mapOf("name" to final)
                    renamed[old.name] = final
                }
                property.kind != null -> body[final] = mapOf(property.kind.type to NewDatabases.config(property.kind))
            }
        }
        return SchemaChange(body, renamed, removed.map { it.name }.toSet())
    }
}

