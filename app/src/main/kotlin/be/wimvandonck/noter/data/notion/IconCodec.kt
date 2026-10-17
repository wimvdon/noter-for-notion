package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.api.objOrNull
import be.wimvandonck.noter.data.api.stringOrNull
import be.wimvandonck.noter.data.model.PageIcon
import kotlinx.serialization.json.JsonObject

/** Notion's icon objects (of pages, databases and data sources) ⇄ [PageIcon]. */
object IconCodec {
    /** The icon in [icon] (a page's "icon"), or null when there is none or its kind is unknown. */
    fun parse(icon: JsonObject?): PageIcon? {
        val type = icon?.stringOrNull("type") ?: return null
        val body = icon.objOrNull(type)
        return when (type) {
            "emoji" -> icon.stringOrNull("emoji")?.let { PageIcon(emoji = it) }
            "icon" -> body?.stringOrNull("name")?.let { PageIcon(name = it, color = body.stringOrNull("color") ?: DEFAULT_COLOR) }
            "external", "custom_emoji" -> body?.stringOrNull("url")?.let { PageIcon(url = it) }
            "file" -> body?.stringOrNull("url")?.let { PageIcon(url = it, urlExpiry = body.stringOrNull("expiry_time")) }
            else -> null
        }
    }

    /** Notion's default color for its own icons. */
    const val DEFAULT_COLOR = "gray"
}
