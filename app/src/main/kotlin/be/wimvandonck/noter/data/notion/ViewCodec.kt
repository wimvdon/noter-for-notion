package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.api.arrayOrNull
import be.wimvandonck.noter.data.api.asObjectOrNull
import be.wimvandonck.noter.data.api.boolOrNull
import be.wimvandonck.noter.data.api.objOrNull
import be.wimvandonck.noter.data.api.stringOrNull
import be.wimvandonck.noter.data.model.NotionView
import kotlinx.serialization.json.JsonObject

/** Notion's view objects (`GET /views/{id}`) ⇄ [NotionView]: only what a notebook starts from. */
object ViewCodec {
    fun parse(view: JsonObject): NotionView? {
        val id = view.stringOrNull("id") ?: return null
        val type = view.stringOrNull("type").orEmpty()
        val configuration = view.objOrNull("configuration")
        return NotionView(
            id = id,
            name = view.stringOrNull("name").orEmpty(),
            type = type,
            quickFilters = view.objOrNull("quick_filters")?.keys?.toList().orEmpty(),
            groupBy = configuration?.objOrNull("group_by")?.stringOrNull("property_id"),
            properties = configuration?.arrayOrNull("properties").orEmpty().mapNotNull { element ->
                val property = element.asObjectOrNull() ?: return@mapNotNull null
                val propertyId = property.stringOrNull("property_id") ?: return@mapNotNull null
                propertyId to (property.boolOrNull("visible") != false)
            },
            showsCover = configuration?.objOrNull("cover")?.stringOrNull("type") == "page_cover",
        )
    }

    /** Views a notebook can follow: they show pages of one data source (not forms, charts or dashboards). */
    fun isNotebookKind(view: NotionView): Boolean = view.type !in setOf("form", "chart", "dashboard")
}
