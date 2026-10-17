package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.api.arrayOrNull
import be.wimvandonck.noter.data.api.boolOrNull
import be.wimvandonck.noter.data.api.objOrNull
import be.wimvandonck.noter.data.api.stringOrNull
import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import be.wimvandonck.noter.logic.Columns
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Reads Notion blocks into the editor's [Block]s and writes them back. */
object BlockCodec {
    private val typeNames = mapOf(
        "paragraph" to BlockType.PARAGRAPH,
        "heading_1" to BlockType.HEADING_1,
        "heading_2" to BlockType.HEADING_2,
        "heading_3" to BlockType.HEADING_3,
        "heading_4" to BlockType.HEADING_4,
        "bulleted_list_item" to BlockType.BULLETED,
        "numbered_list_item" to BlockType.NUMBERED,
        "to_do" to BlockType.TODO,
        "toggle" to BlockType.TOGGLE,
        "quote" to BlockType.QUOTE,
        "callout" to BlockType.CALLOUT,
        "code" to BlockType.CODE,
        "divider" to BlockType.DIVIDER,
    )

    /** Notion's language for a new code block. */
    const val DEFAULT_CODE_LANGUAGE = "plain text"

    /** The emoji of a new callout (Notion's own default). */
    const val DEFAULT_CALLOUT_ICON = "💡"

    fun notionType(type: BlockType): String =
        typeNames.entries.firstOrNull { it.value == type }?.key ?: error("$type has no Notion type")

    fun parse(block: JsonObject, key: String): Block? {
        val id = block.stringOrNull("id") ?: return null
        val rawType = block.stringOrNull("type") ?: return null
        val hasChildren = block.boolOrNull("has_children") == true
        val body = block.objOrNull(rawType)
        val type = typeNames[rawType] ?: return readOnly(key, id, rawType, body, hasChildren)
        return Block(
            key = key,
            notionId = id,
            type = type,
            // Code keeps its text exactly (no inline Markdown: asterisks are part of the code).
            text = if (type == BlockType.CODE) {
                InlineMarkdown.plainText(body?.arrayOrNull("rich_text"))
            } else {
                InlineMarkdown.fromRichText(body?.arrayOrNull("rich_text"))
            },
            checked = type == BlockType.TODO && body?.boolOrNull("checked") == true,
            hasChildren = hasChildren,
            toggleable = type.isHeading && body?.boolOrNull("is_toggleable") == true,
            language = if (type == BlockType.CODE) body?.stringOrNull("language") else null,
            // A callout's emoji; a text line can have one too when it is a tab's name.
            icon = if (type == BlockType.CALLOUT || type == BlockType.PARAGRAPH) body?.objOrNull("icon")?.stringOrNull("emoji") else null,
        )
    }

    /**
     * A text line directly in a tab block is a tab: its text is the tab's name and the tab's
     * blocks are nested in it. It is shown as a label, not edited (Notion only allows text lines
     * there, so it can't change kind or move).
     */
    fun asTab(block: Block): Block =
        if (block.type == BlockType.PARAGRAPH) block.copy(type = BlockType.UNSUPPORTED, rawType = Columns.TAB) else block

    /** Notion's file blocks: the link is in "external" or, for uploads, in "file" with an expiry. */
    private val fileTypes = setOf("image", "video", "file", "pdf", "audio")

    private fun readOnly(key: String, id: String, rawType: String, body: JsonObject?, hasChildren: Boolean): Block {
        val caption = body?.arrayOrNull("caption")?.let { InlineMarkdown.plainText(it) }?.trim().orEmpty()
        val base = Block(key = key, notionId = id, type = BlockType.UNSUPPORTED, hasChildren = hasChildren, rawType = rawType)
        return when (rawType) {
            in fileTypes -> {
                val file = fileLink(body)
                base.copy(text = caption.ifEmpty { body?.stringOrNull("name").orEmpty() }, url = file?.first, urlExpiry = file?.second)
            }
            "bookmark", "embed", "link_preview" -> base.copy(text = caption, url = body?.stringOrNull("url"))
            "equation" -> base.copy(text = body?.stringOrNull("expression").orEmpty())
            "child_page" -> base.copy(text = body?.stringOrNull("title").orEmpty(), url = id)
            "child_database" -> base.copy(text = body?.stringOrNull("title").orEmpty(), url = id, linksDatabase = true)
            "link_to_page" -> body?.stringOrNull("database_id")?.let { base.copy(url = it, linksDatabase = true) }
                ?: base.copy(url = body?.stringOrNull("page_id"))
            "table" -> base.copy(headerRow = body?.boolOrNull("has_column_header") == true)
            else -> base.copy(
                text = body?.arrayOrNull("rich_text")?.let { InlineMarkdown.plainText(it) } ?: caption.ifEmpty { body?.stringOrNull("url").orEmpty() },
            )
        }
    }

    /** The link of a file block or cover, and when it expires (only for files Notion hosts). */
    fun fileLink(body: JsonObject?): Pair<String, String?>? {
        body ?: return null
        body.objOrNull("external")?.stringOrNull("url")?.let { return it to null }
        val file = body.objOrNull("file") ?: return null
        val url = file.stringOrNull("url") ?: return null
        return url to file.stringOrNull("expiry_time")
    }

    /** The cells of a table's rows (Notion's "table_row" children), as plain text. */
    fun tableRows(children: List<JsonObject>): List<List<String>> =
        children.filter { it.stringOrNull("type") == "table_row" }.map { row ->
            row.objOrNull("table_row")?.arrayOrNull("cells").orEmpty().map { cell ->
                (cell as? JsonArray)?.let { InlineMarkdown.plainText(it) }.orEmpty()
            }
        }

    /** The body for creating [block] (append children / create page). */
    fun toCreateRequest(block: Block): Map<String, Any?> {
        val type = notionType(block.type)
        val content = when (block.type) {
            BlockType.DIVIDER -> emptyMap()
            BlockType.CODE -> content(block) + ("language" to (block.language ?: DEFAULT_CODE_LANGUAGE))
            BlockType.CALLOUT -> content(block) + ("icon" to mapOf("type" to "emoji", "emoji" to (block.icon ?: DEFAULT_CALLOUT_ICON)))
            else -> if (block.toggleable) content(block) + ("is_toggleable" to true) else content(block)
        }
        return mapOf("object" to "block", "type" to type, type to content)
    }

    /**
     * The body for updating [block] in place (same type). Only the text and checked state are
     * sent, so what the app doesn't edit (a callout's icon, a code block's language, colours)
     * stays as it is in Notion.
     */
    fun toUpdateRequest(block: Block): Map<String, Any?> = mapOf(notionType(block.type) to content(block))

    private fun content(block: Block): Map<String, Any?> = buildMap {
        put(
            "rich_text",
            if (block.type == BlockType.CODE) InlineMarkdown.plainRichText(block.text) else InlineMarkdown.toRichText(block.text),
        )
        if (block.type == BlockType.TODO) put("checked", block.checked)
    }
}
