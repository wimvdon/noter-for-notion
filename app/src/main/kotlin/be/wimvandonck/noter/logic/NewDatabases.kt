package be.wimvandonck.noter.logic

/**
 * A kind of property a new database can have (#71): the ones Notion's API makes with nothing more
 * to fill in. Not status (the API can't make one), formulas, relations and rollups (they need more
 * than a name), nor buttons and IDs.
 */
enum class PropertyKind(val type: String) {
    TEXT("rich_text"),
    NUMBER("number"),
    SELECT("select"),
    MULTI_SELECT("multi_select"),
    DATE("date"),
    PERSON("people"),
    FILES("files"),
    CHECKBOX("checkbox"),
    URL("url"),
    EMAIL("email"),
    PHONE("phone_number"),
    CREATED_TIME("created_time"),
    CREATED_BY("created_by"),
    LAST_EDITED_TIME("last_edited_time"),
    LAST_EDITED_BY("last_edited_by"),
}

/** A property of a new database: its kind and name. */
data class NewProperty(val kind: PropertyKind, val name: String)

/**
 * A new database as Notion's `POST /databases` wants it (#71): a title, its title property and the
 * properties chosen, inside a page or at the top of the workspace. Notion makes the first data
 * source and a table view with it; anything else is changed in Notion.
 */
object NewDatabases {
    /** What a new database has besides its title property, as in Notion: Tags. */
    val DEFAULT_KINDS = listOf(PropertyKind.MULTI_SELECT)

    /**
     * [properties] with a property of [kind] added at the end, named [defaultName] as Notion names
     * a new one of that kind, numbered when the name is taken ("Text 2"), as Notion does.
     */
    fun add(properties: List<NewProperty>, kind: PropertyKind, defaultName: String, titleName: String = ""): List<NewProperty> =
        properties + NewProperty(kind, uniqueName(defaultName, properties.map { it.name } + titleName))

    /** [name], or with the first number from 2 that makes it unlike every one of [taken] (in any case). */
    fun uniqueName(name: String, taken: List<String>): String {
        val base = name.trim()
        val used = taken.map { it.trim().lowercase() }.toSet()
        if (base.lowercase() !in used) return base
        return generateSequence(2) { it + 1 }.map { "$base $it" }.first { it.lowercase() !in used }
    }

    /**
     * The request for database [title] inside page [parentPageId] (null: at the top of the
     * workspace), with title property [titleName] and [properties] in their order. A blank name
     * becomes [fallbackNames]' name for its kind; names are made unique as in [add].
     */
    fun request(
        title: String,
        parentPageId: String?,
        titleName: String,
        properties: List<NewProperty>,
        fallbackNames: Map<PropertyKind, String> = emptyMap(),
    ): Map<String, Any?> {
        val titleProperty = titleName.trim().ifBlank { "Name" }
        val schema = linkedMapOf<String, Any?>(titleProperty to mapOf("title" to emptyMap<String, Any?>()))
        properties.forEach { property ->
            val wanted = property.name.trim().ifBlank { fallbackNames[property.kind].orEmpty().ifBlank { property.kind.type } }
            schema[uniqueName(wanted, schema.keys.toList())] = mapOf(property.kind.type to config(property.kind))
        }
        return mapOf(
            "parent" to if (parentPageId != null) mapOf("type" to "page_id", "page_id" to parentPageId) else mapOf("type" to "workspace", "workspace" to true),
            "title" to listOf(mapOf("type" to "text", "text" to mapOf("content" to title.trim()))),
            "initial_data_source" to mapOf("properties" to schema),
        )
    }

    /** What Notion needs to know about a new property of [kind]: nothing, but a number's format. */
    fun config(kind: PropertyKind): Map<String, Any?> =
        if (kind == PropertyKind.NUMBER) mapOf("format" to "number") else emptyMap()
}
