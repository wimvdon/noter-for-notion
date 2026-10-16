package com.impca201.noter.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A database the user added in Settings. Its notes get their own tab. */
@Serializable
data class DatabaseConfig(
    val id: String,
    val name: String,
    val icon: String = DEFAULT_ICON,
    /** Property whose options become the filter chips (select, status, multi-select or person). */
    val chipProperty: String? = null,
) {
    companion object {
        const val DEFAULT_ICON = "note"
    }
}

/** A colored option of a select, multi-select or status property. */
@Serializable
data class SelectOption(
    val name: String,
    val color: String = "default",
    val id: String? = null,
)

/** A Notion user, for person properties. */
@Serializable
data class NotionUser(
    val id: String,
    val name: String,
)

/** A linked page of a relation property, or a page of the related database. */
@Serializable
data class PageRef(
    val id: String,
    val title: String,
)

@Serializable
data class FileRef(
    val name: String,
    val url: String? = null,
)

/** One property of a database, as its schema describes it. */
@Serializable
data class PropertySchema(
    val id: String = "",
    val name: String,
    val type: String,
    val options: List<SelectOption> = emptyList(),
    val relationDatabaseId: String? = null,
    val numberFormat: String? = null,
) {
    val isChipCandidate: Boolean get() = type in CHIP_TYPES

    companion object {
        val CHIP_TYPES = setOf("select", "status", "multi_select", "people")
    }
}

@Serializable
data class DatabaseSchema(
    val id: String,
    val title: String,
    val properties: List<PropertySchema>,
) {
    val titleProperty: String? get() = properties.firstOrNull { it.type == "title" }?.name

    fun property(name: String): PropertySchema? = properties.firstOrNull { it.name == name }
}

/**
 * The value of one page property. Types the app can edit have their own class; everything Notion
 * computes (formulas, rollups, timestamps, IDs, …) is [ReadOnly] and shown as text.
 */
@Serializable
sealed interface PropertyValue {
    /** Title or text: inline Markdown (see InlineMarkdown). */
    @Serializable
    @SerialName("text")
    data class Text(val markdown: String) : PropertyValue

    @Serializable
    @SerialName("number")
    data class Number(val value: Double?) : PropertyValue

    /** Select or status: at most one option. */
    @Serializable
    @SerialName("select")
    data class Select(val option: SelectOption?) : PropertyValue

    @Serializable
    @SerialName("multi_select")
    data class MultiSelect(val options: List<SelectOption>) : PropertyValue

    /** ISO dates as Notion sends them: "2026-10-15" or "2026-10-15T09:30:00.000+02:00". */
    @Serializable
    @SerialName("date")
    data class Date(val start: String?, val end: String? = null) : PropertyValue

    @Serializable
    @SerialName("people")
    data class People(val users: List<NotionUser>) : PropertyValue

    @Serializable
    @SerialName("checkbox")
    data class Checkbox(val checked: Boolean) : PropertyValue

    /** URL, e-mail or phone number. */
    @Serializable
    @SerialName("plain")
    data class Plain(val value: String?) : PropertyValue

    /** [hasMore]: Notion only sent the first 25 links, so the value must not be written back. */
    @Serializable
    @SerialName("relation")
    data class Relation(val ids: List<String>, val hasMore: Boolean = false) : PropertyValue

    @Serializable
    @SerialName("files")
    data class Files(val files: List<FileRef>) : PropertyValue

    @Serializable
    @SerialName("read_only")
    data class ReadOnly(val display: String) : PropertyValue
}

@Serializable
data class NoteProperty(
    val name: String,
    val type: String,
    val value: PropertyValue,
) {
    /** True when the app can change this property and write it to Notion. */
    val isEditable: Boolean
        get() = when (value) {
            is PropertyValue.ReadOnly, is PropertyValue.Files -> false
            is PropertyValue.Relation -> !value.hasMore
            else -> type in EDITABLE_TYPES
        }

    val isEmpty: Boolean
        get() = when (value) {
            is PropertyValue.Text -> value.markdown.isBlank()
            is PropertyValue.Number -> value.value == null
            is PropertyValue.Select -> value.option == null
            is PropertyValue.MultiSelect -> value.options.isEmpty()
            is PropertyValue.Date -> value.start == null
            is PropertyValue.People -> value.users.isEmpty()
            is PropertyValue.Checkbox -> false
            is PropertyValue.Plain -> value.value.isNullOrBlank()
            is PropertyValue.Relation -> value.ids.isEmpty()
            is PropertyValue.Files -> value.files.isEmpty()
            is PropertyValue.ReadOnly -> value.display.isBlank()
        }

    companion object {
        val EDITABLE_TYPES = setOf(
            "title", "rich_text", "number", "select", "status", "multi_select", "date",
            "people", "checkbox", "url", "email", "phone_number", "relation",
        )
    }
}

/** A note: one page of a database, as shown in lists (without its content). */
@Serializable
data class NoteSummary(
    val id: String,
    val databaseId: String,
    val title: String,
    val icon: String? = null,
    val url: String? = null,
    val createdTime: String = "",
    val lastEditedTime: String = "",
    val properties: List<NoteProperty> = emptyList(),
) {
    val isLocal: Boolean get() = id.startsWith(LOCAL_PREFIX)

    fun property(name: String): NoteProperty? = properties.firstOrNull { it.name == name }

    companion object {
        /** Notes created on this phone that Notion doesn't have yet. */
        const val LOCAL_PREFIX = "local-"
    }
}

enum class BlockType {
    PARAGRAPH, HEADING_1, HEADING_2, HEADING_3, BULLETED, NUMBERED, TODO,
    TOGGLE, QUOTE, CALLOUT, CODE,

    /** A horizontal line; it has no text. */
    DIVIDER,

    /** A block the editor can't change (image, table, embed, …). Shown, kept, never written. */
    UNSUPPORTED,
    ;

    val isListItem: Boolean get() = this == BULLETED || this == NUMBERED || this == TODO
    val isHeading: Boolean get() = this == HEADING_1 || this == HEADING_2 || this == HEADING_3

    /** Blocks with text the user types in (all but dividers and unsupported blocks). */
    val hasText: Boolean get() = this != DIVIDER && this != UNSUPPORTED
}

/**
 * A block of a note's content. [key] is stable on this phone (also for blocks Notion doesn't have
 * yet); [notionId] is set once Notion has the block.
 */
@Serializable
data class Block(
    val key: String,
    val notionId: String? = null,
    val type: BlockType = BlockType.PARAGRAPH,
    val text: String = "",
    val checked: Boolean = false,
    val hasChildren: Boolean = false,
    /** Notion's type name, for [BlockType.UNSUPPORTED] blocks. */
    val rawType: String? = null,
    /** A heading that folds its nested content away (Notion's toggle heading). */
    val toggleable: Boolean = false,
    /** A code block's language, as Notion names it ("plain text", "kotlin", …). */
    val language: String? = null,
    /** A callout's emoji. */
    val icon: String? = null,
) {
    /** Same content as [other], ignoring identity. */
    fun sameContentAs(other: Block): Boolean =
        type == other.type && text == other.text && checked == other.checked
}

/**
 * A note's content on this phone. [base] is what Notion had after the last sync, so local edits
 * can be sent as a diff; [dirty] means [blocks] has changes Notion doesn't have yet.
 */
@Serializable
data class PageContent(
    val pageId: String,
    val blocks: List<Block>,
    val base: List<Block> = blocks,
    val dirty: Boolean = false,
    val remoteEditedTime: String? = null,
    /** True when Notion had more content than the editor shows (nested blocks are kept as is). */
    val complete: Boolean = true,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }
