package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.api.arrayOrNull
import be.wimvandonck.noter.data.api.objOrNull
import be.wimvandonck.noter.data.api.stringOrNull
import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.logic.ChildRef
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

    /**
     * The page (or block on a page) a page or database is in, for Home's tree; null at the top of
     * the workspace and for what is in a database (a note, or a data source).
     */
    fun parentPage(json: JsonObject): String? {
        val parent = json.objOrNull("parent") ?: return null
        return when (parent.stringOrNull("type")) {
            "page_id" -> parent.stringOrNull("page_id")
            "block_id" -> parent.stringOrNull("block_id")
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

    /** The pages and databases right inside a page, from its blocks (other blocks are left out). */
    fun childRefs(blocks: List<JsonObject>): List<ChildRef> = blocks.mapNotNull { block ->
        val id = block.stringOrNull("id") ?: return@mapNotNull null
        val edited = block.stringOrNull("last_edited_time").orEmpty()
        when (block.stringOrNull("type")) {
            "child_page" -> ChildRef(id, isDatabase = false, lastEditedTime = edited)
            "child_database" -> ChildRef(id, isDatabase = true, lastEditedTime = edited)
            else -> null
        }
    }

    /** Search results as the Browse list (see [parse]). */
    fun parseAll(results: List<JsonObject>, includeNotes: Boolean, pagesKey: String, includePeople: Boolean = false): List<WorkspaceItem> =
        results.mapNotNull { parse(it, includeNotes, pagesKey, includePeople) }

    /** A database's name, icon and data sources, from `GET /databases/{id}`. */
    fun databaseInfo(database: JsonObject): DatabaseInfo? {
        val id = database.stringOrNull("id") ?: return null
        return DatabaseInfo(
            id,
            InlineMarkdown.plainText(database.arrayOrNull("title")).trim(),
            IconCodec.parse(database.objOrNull("icon")),
            dataSources(database),
            parentPage(database),
        )
    }

    /**
     * The Browse list with what is known about the databases the data sources are in ([infos], by
     * database ID): a data source its database doesn't list is left out (Notion's search can show
     * one the connection can't read), and the data sources of a database with several are shown as
     * that database, once, where the first of them was.
     */
    fun group(items: List<WorkspaceItem>, infos: Map<String, DatabaseInfo>): List<WorkspaceItem> {
        val byPlainId = infos.mapKeys { plain(it.key) }
        val shown = mutableSetOf<String>()
        return items.mapNotNull { item ->
            val info = item.databaseId?.let { byPlainId[plain(it)] } ?: return@mapNotNull item
            when {
                info.sources.none { plain(it.id) == plain(item.id) } -> null
                // A data source's own parent is its database: the tree places it where the database is.
                info.sources.size == 1 -> item.copy(parentId = item.parentId ?: info.parentId)
                !shown.add(plain(info.id)) -> null
                else -> WorkspaceItem(
                    id = info.id,
                    isDatabase = true,
                    title = info.title,
                    icon = info.icon ?: item.icon,
                    lastEditedTime = item.lastEditedTime,
                    sources = info.sources,
                    parentId = info.parentId,
                )
            }
        }
    }

    private fun plain(id: String) = id.replace("-", "")

    /**
     * One search result. Pages of a database (the notes) are left out unless [includeNotes]: the
     * list is about pages and databases, a database's notes are one tap further. [pagesKey] is the
     * key under which pages on their own are kept. Notion's People directory and its profiles only
     * with [includePeople] (Search).
     */
    fun parse(json: JsonObject, includeNotes: Boolean, pagesKey: String, includePeople: Boolean = false): WorkspaceItem? {
        if (PropertyCodec.isTrashed(json)) return null
        // Notion's People directory and its profiles aren't notes: only Search shows them ([includePeople], #92).
        if (!includePeople && (PeopleDirectory.isDirectory(json) || PeopleDirectory.isProfile(json))) return null
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
                databaseId = json.objOrNull("parent")?.stringOrNull("database_id"),
                parentId = parentPage(json),
            )
            "page" -> {
                val database = parentDatabase(json)
                if (database != null && !includeNotes) return null
                val note = PropertyCodec.parsePage(json, database ?: pagesKey) ?: return null
                WorkspaceItem(
                    id,
                    isDatabase = false,
                    title = InlineMarkdown.stripped(note.title),
                    icon = icon,
                    lastEditedTime = edited,
                    note = note,
                    parentId = parentPage(json),
                )
            }
            else -> null
        }
    }
}
