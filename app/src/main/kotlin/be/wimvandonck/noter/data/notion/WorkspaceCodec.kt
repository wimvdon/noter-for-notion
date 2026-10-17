package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.api.arrayOrNull
import be.wimvandonck.noter.data.api.objOrNull
import be.wimvandonck.noter.data.api.stringOrNull
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.WorkspaceItem
import kotlinx.serialization.json.JsonObject

/** Reads Notion's search results into the Browse list. */
object WorkspaceCodec {
    /**
     * The data source a page belongs to (a database's notes; older answers name the database), or
     * null for a page on its own (in a page or the workspace).
     */
    fun parentDatabase(page: JsonObject): String? {
        val parent = page.objOrNull("parent") ?: return null
        return when (parent.stringOrNull("type")) {
            "data_source_id" -> parent.stringOrNull("data_source_id")
            "database_id" -> parent.stringOrNull("database_id")
            else -> null
        }
    }

    /** A database's data sources (ID and name), in Notion's order. */
    fun dataSources(database: JsonObject): List<PageRef> =
        database.arrayOrNull("data_sources").orEmpty().mapNotNull { element ->
            val source = element as? JsonObject ?: return@mapNotNull null
            val id = source.stringOrNull("id") ?: return@mapNotNull null
            PageRef(id, source.stringOrNull("name").orEmpty())
        }

    /**
     * A subpage from its block on the parent page (Notion's `child_page`), until the page itself is
     * downloaded: its title and times. Kept under [key].
     */
    fun childPage(block: JsonObject, key: String): NoteSummary? {
        val id = block.stringOrNull("id") ?: return null
        val title = block.objOrNull("child_page")?.stringOrNull("title").orEmpty()
        return NoteSummary(
            id = id,
            databaseId = key,
            title = title,
            createdTime = block.stringOrNull("created_time").orEmpty(),
            lastEditedTime = block.stringOrNull("last_edited_time").orEmpty(),
            properties = listOf(NoteProperty("title", "title", PropertyValue.Text(title))),
        )
    }

    /**
     * Search results as the Browse list. A database whose data sources are among the results is
     * left out: it is only their container (opening it as a notebook doesn't work), and each of
     * its data sources is listed on its own.
     */
    fun parseAll(results: List<JsonObject>, includeNotes: Boolean, pagesKey: String): List<WorkspaceItem> {
        val containers = results.filter { it.stringOrNull("object") == "data_source" }
            .mapNotNull { it.objOrNull("parent")?.stringOrNull("database_id")?.replace("-", "") }
            .toSet()
        return results
            .filterNot { it.stringOrNull("object") == "database" && it.stringOrNull("id")?.replace("-", "") in containers }
            .mapNotNull { parse(it, includeNotes, pagesKey) }
    }

    /**
     * One search result. Pages of a database (the notes) are left out unless [includeNotes]: the
     * list is about pages and databases, a database's notes are one tap further. [pagesKey] is the
     * key under which pages on their own are kept.
     */
    fun parse(json: JsonObject, includeNotes: Boolean, pagesKey: String): WorkspaceItem? {
        if (PropertyCodec.isTrashed(json)) return null
        val id = json.stringOrNull("id") ?: return null
        val icon = IconCodec.parse(json.objOrNull("icon"))
        val edited = json.stringOrNull("last_edited_time").orEmpty()
        return when (json.stringOrNull("object")) {
            // A data source is a database's table of notes; a database with several shows each.
            "data_source", "database" -> WorkspaceItem(
                id = id,
                isDatabase = true,
                title = InlineMarkdown.plainText(json.arrayOrNull("title")).trim(),
                icon = icon,
                lastEditedTime = edited,
            )
            "page" -> {
                val database = parentDatabase(json)
                if (database != null && !includeNotes) return null
                val note = PropertyCodec.parsePage(json, database ?: pagesKey) ?: return null
                WorkspaceItem(id, isDatabase = false, title = InlineMarkdown.stripped(note.title), icon = icon, lastEditedTime = edited, note = note)
            }
            else -> null
        }
    }
}
