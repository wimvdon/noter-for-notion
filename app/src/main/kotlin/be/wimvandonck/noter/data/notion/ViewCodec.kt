package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.api.stringOrNull
import be.wimvandonck.noter.data.model.NotionView
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Reads Notion's views (`GET /views/{id}`). */
object ViewCodec {
    /** The view in [json], or null when it isn't one. */
    fun parse(json: JsonObject): NotionView? {
        if (json.stringOrNull("object") != "view") return null
        val id = json.stringOrNull("id") ?: return null
        return NotionView(
            id = id,
            name = json.stringOrNull("name").orEmpty(),
            type = json.stringOrNull("type").orEmpty(),
            dataSourceId = json.stringOrNull("data_source_id"),
            filter = json["filter"].orNull(),
            sorts = json["sorts"].orNull(),
            quickFilters = json["quick_filters"].orNull(),
        )
    }

    private fun JsonElement?.orNull(): JsonElement? = this?.takeUnless { it is JsonNull }
}
