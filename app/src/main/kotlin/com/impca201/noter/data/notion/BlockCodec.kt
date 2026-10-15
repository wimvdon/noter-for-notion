package com.impca201.noter.data.notion

import com.impca201.noter.data.api.arrayOrNull
import com.impca201.noter.data.api.asObjectOrNull
import com.impca201.noter.data.api.boolOrNull
import com.impca201.noter.data.api.objOrNull
import com.impca201.noter.data.api.stringOrNull
import com.impca201.noter.data.model.Block
import com.impca201.noter.data.model.BlockType
import kotlinx.serialization.json.JsonObject

/** Reads Notion blocks into the editor's [Block]s and writes them back. */
object BlockCodec {
    private val typeNames = mapOf(
        "paragraph" to BlockType.PARAGRAPH,
        "heading_1" to BlockType.HEADING_1,
        "heading_2" to BlockType.HEADING_2,
        "heading_3" to BlockType.HEADING_3,
        "bulleted_list_item" to BlockType.BULLETED,
        "numbered_list_item" to BlockType.NUMBERED,
        "to_do" to BlockType.TODO,
    )

    fun notionType(type: BlockType): String =
        typeNames.entries.firstOrNull { it.value == type }?.key ?: error("$type has no Notion type")

    fun parse(block: JsonObject, key: String): Block? {
        val id = block.stringOrNull("id") ?: return null
        val rawType = block.stringOrNull("type") ?: return null
        val hasChildren = block.boolOrNull("has_children") == true
        val body = block.objOrNull(rawType)
        val type = typeNames[rawType]
        // Toggle headings have their content as children; the editor can't show those, so keep them as they are.
        if (type == null || body?.boolOrNull("is_toggleable") == true) {
            return Block(
                key = key,
                notionId = id,
                type = BlockType.UNSUPPORTED,
                text = body?.arrayOrNull("rich_text")?.let { InlineMarkdown.plainText(it) }
                    ?: body?.stringOrNull("url")
                    ?: body?.objOrNull("external")?.stringOrNull("url")
                    ?: body?.stringOrNull("title")
                    ?: "",
                hasChildren = hasChildren,
                rawType = rawType,
            )
        }
        return Block(
            key = key,
            notionId = id,
            type = type,
            text = InlineMarkdown.fromRichText(body?.arrayOrNull("rich_text")),
            checked = type == BlockType.TODO && body?.boolOrNull("checked") == true,
            hasChildren = hasChildren,
        )
    }

    /** The body for creating [block] (append children / create page). */
    fun toCreateRequest(block: Block): Map<String, Any?> {
        val type = notionType(block.type)
        return mapOf("object" to "block", "type" to type, type to content(block))
    }

    /** The body for updating [block] in place (same type). */
    fun toUpdateRequest(block: Block): Map<String, Any?> = mapOf(notionType(block.type) to content(block))

    private fun content(block: Block): Map<String, Any?> = buildMap {
        put("rich_text", InlineMarkdown.toRichText(block.text))
        if (block.type == BlockType.TODO) put("checked", block.checked)
    }
}
