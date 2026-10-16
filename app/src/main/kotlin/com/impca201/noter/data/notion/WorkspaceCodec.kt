package com.impca201.noter.data.notion

import com.impca201.noter.data.api.arrayOrNull
import com.impca201.noter.data.api.objOrNull
import com.impca201.noter.data.api.stringOrNull
import com.impca201.noter.data.model.WorkspaceItem
import kotlinx.serialization.json.JsonObject

/** Reads Notion's search results into the Browse list. */
object WorkspaceCodec {
    /** The database a page belongs to, or null for a page on its own (in a page or the workspace). */
    fun parentDatabase(page: JsonObject): String? =
        page.objOrNull("parent")?.takeIf { it.stringOrNull("type") == "database_id" }?.stringOrNull("database_id")

    /**
     * One search result. Pages of a database (the notes) are left out unless [includeNotes]: the
     * list is about pages and databases, a database's notes are one tap further. [pagesKey] is the
     * key under which pages on their own are kept.
     */
    fun parse(json: JsonObject, includeNotes: Boolean, pagesKey: String): WorkspaceItem? {
        if (PropertyCodec.isTrashed(json)) return null
        val id = json.stringOrNull("id") ?: return null
        val icon = json.objOrNull("icon")?.takeIf { it.stringOrNull("type") == "emoji" }?.stringOrNull("emoji")
        val edited = json.stringOrNull("last_edited_time").orEmpty()
        return when (json.stringOrNull("object")) {
            "database" -> WorkspaceItem(
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
