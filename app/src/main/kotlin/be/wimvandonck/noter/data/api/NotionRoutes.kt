package be.wimvandonck.noter.data.api

import be.wimvandonck.noter.logic.KeyRouter
import kotlinx.serialization.json.JsonObject

/** How a Notion request is matched to a token when there are several (#58, see [NotionClient]). */
object NotionRoutes {
    private val subject = Regex("^/(pages|blocks|data_sources|databases)/([^/?]+)")

    /**
     * The object a request is about: the ID in the path, or for a new page the parent's ID.
     * Null for requests about the whole workspace (search, users, a page at the top).
     */
    fun subjectId(endpoint: String, body: Any?): String? {
        subject.find(endpoint)?.let { return it.groupValues[2] }
        val parent = (body as? Map<*, *>)?.get("parent") as? Map<*, *> ?: return null
        return (parent["page_id"] ?: parent["data_source_id"] ?: parent["database_id"]) as? String
    }

    /** A request about this object is about a block (only remembered while the app runs, see KeyRouter). */
    fun isBlock(endpoint: String): Boolean = subject.find(endpoint)?.groupValues?.get(1) == "blocks"

    /**
     * The IDs of the objects in a successful answer (the object itself, a list's results and a
     * database's data sources): later requests about them go to the same workspace. [blocks]:
     * the blocks among them, else the pages, databases and data sources (people are in every
     * workspace they were invited to, so they aren't anyone's).
     */
    fun objectIds(data: JsonObject, blocks: Boolean = false): List<String> = buildList {
        fun take(obj: JsonObject) {
            val kind = obj.stringOrNull("object")
            val wanted = if (blocks) kind == "block" else kind != "block" && kind != "user"
            if (wanted) obj.stringOrNull("id")?.let(::add)
        }
        take(data)
        for (key in listOf("results", "data_sources")) {
            data.arrayOrNull(key)?.forEach { el -> el.asObjectOrNull()?.let(::take) }
        }
    }

    /**
     * Search results from several workspaces in one list, most recently edited first (Notion's
     * times are ISO 8601 in UTC, so they sort as text). One list is returned as Notion sorted it.
     */
    fun newestFirst(lists: List<List<JsonObject>>, limit: Int = Int.MAX_VALUE): List<JsonObject> =
        if (lists.size == 1) {
            lists.first().take(limit)
        } else {
            // Two tokens for the same workspace both find its pages: each once.
            lists.flatten().distinctBy { it.stringOrNull("id")?.let(KeyRouter::normalize) ?: it.toString() }
                .sortedByDescending { it.stringOrNull("last_edited_time").orEmpty() }.take(limit)
        }
}
