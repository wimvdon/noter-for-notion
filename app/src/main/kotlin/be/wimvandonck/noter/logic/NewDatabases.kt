package be.wimvandonck.noter.logic

/** A property a new database can start with, besides its title (#71). */
enum class StarterProperty(val type: String) {
    TAGS("multi_select"),
    DATE("date"),
    DONE("checkbox"),
    LINK("url"),
}

/**
 * A new database as Notion's `POST /databases` wants it (#71): a title, its title property and the
 * [StarterProperty]s picked, inside a page or at the top of the workspace. The properties are the
 * kinds the app edits itself; more can be added in Notion. Notion makes the first data source and
 * a table view with it.
 */
object NewDatabases {
    /**
     * The request for database [title] inside page [parentPageId] (null: at the top of the
     * workspace). [names] are the names of the title property ([titleName]) and of each starter
     * property, in the user's language; a starter property named like another isn't added twice.
     */
    fun request(
        title: String,
        parentPageId: String?,
        titleName: String,
        starters: List<StarterProperty>,
        names: Map<StarterProperty, String>,
    ): Map<String, Any?> {
        val properties = linkedMapOf<String, Any?>(titleName.trim().ifBlank { "Name" } to mapOf("title" to emptyMap<String, Any?>()))
        StarterProperty.entries.filter { it in starters }.forEach { starter ->
            val name = names[starter]?.trim().orEmpty()
            if (name.isNotEmpty() && properties.keys.none { it.equals(name, ignoreCase = true) }) {
                properties[name] = mapOf(starter.type to emptyMap<String, Any?>())
            }
        }
        return mapOf(
            "parent" to if (parentPageId != null) mapOf("type" to "page_id", "page_id" to parentPageId) else mapOf("type" to "workspace", "workspace" to true),
            "title" to listOf(mapOf("type" to "text", "text" to mapOf("content" to title.trim()))),
            "initial_data_source" to mapOf("properties" to properties),
        )
    }
}
