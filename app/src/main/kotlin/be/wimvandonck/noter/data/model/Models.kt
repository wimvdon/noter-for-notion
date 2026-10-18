package be.wimvandonck.noter.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How one data source's notes are shown, kept on this phone per data source (Notion isn't
 * changed): the values filtering them, the properties with a row of chips above them, their order
 * and what their cards show. Changed in the database itself; there is nothing to save.
 */
@Serializable
data class DatabaseSetup(
    /** Properties whose values are filter chips above the notes (quick filters), one row each, in this order. */
    val quickFilters: List<String> = emptyList(),
    /** The selected filter values per property: a note shows when it has one of each property's. */
    val selected: Map<String, List<String>> = emptyMap(),
    /** The order of the notes (null: the custom order). */
    val sort: NoteSort? = null,
    /** What the cards show (null: what the database's cards showed before, see PropertyLayouts.cards). */
    val cards: CardLayout? = null,
) {
    /** The order the notes are in now ([SortKind.NOTION] from before reads as the custom order). */
    val currentSort: NoteSort
        get() = sort?.takeIf { it.kind != SortKind.NOTION } ?: NoteSort(SortKind.CUSTOM)
}

/**
 * A database pinned on Home: [id] is what opens it (a data source, or a database of several data
 * sources), with its name and icon as last seen, for Home to show without asking Notion.
 */
@Serializable
data class PinnedDatabase(
    val id: String,
    val title: String,
    val icon: PageIcon? = null,
)

/**
 * A notebook from before the redesign: a database (or a page's subpages) set up as a tab, with its
 * own filters and sort. Only read once, to turn it into a [DatabaseSetup] and a pin (see
 * LegacyNotebooks). Notebooks saved before notebooks existed have the database's ID as [id] and no
 * [databaseId].
 */
@Serializable
data class DatabaseConfig(
    val id: String,
    val name: String,
    val icon: String = DEFAULT_ICON,
    /** Before quick filters: the one property whose values were the filters (see [normalized]). */
    val chipProperty: String? = null,
    /** The database shown; null for views saved before views existed ([id] is then the database). */
    val databaseId: String? = null,
    /** Before [selected]: the options of [chipProperty] selected when the view opened. */
    val filters: List<String> = emptyList(),
    val quickFilters: List<String> = emptyList(),
    val selected: Map<String, List<String>> = emptyMap(),
    val sort: NoteSort? = null,
    /** A page's notebook: its notes were this page's subpages. */
    val pageId: String? = null,
    val cards: CardLayout? = null,
    /** A database with several data sources: one sub-notebook per data source. */
    val children: List<DatabaseConfig> = emptyList(),
) {
    /** The notebooks that showed notes: the sub-notebooks, else this one. */
    val leaves: List<DatabaseConfig> get() = children.ifEmpty { listOf(this) }

    /** The database (or data source) this notebook showed. */
    val sourceId: String get() = databaseId ?: id

    /** The notebook in today's terms: a filter property from before becomes a quick filter. */
    fun normalized(): DatabaseConfig {
        if (children.isNotEmpty()) return copy(children = children.map { it.normalized() })
        val old = chipProperty ?: return this
        return copy(
            chipProperty = null,
            filters = emptyList(),
            quickFilters = quickFilters.ifEmpty { listOf(old) },
            selected = if (filters.isEmpty() || old in selected) selected else selected + (old to filters),
        )
    }

    companion object {
        const val DEFAULT_ICON = "note"
    }
}

/** How a database's notes are sorted (pinned notes always come first). */
@Serializable
enum class SortKind {
    /** From before: a page's notebook in the page's order (read as [CUSTOM] now). */
    NOTION,

    /** As placed by dragging on this phone; new notes on top. */
    CUSTOM,
    LAST_EDITED,
    CREATED,
    TITLE,

    /** By a date property ([NoteSort.property]); notes without a date last. */
    DATE,
}

@Serializable
data class NoteSort(
    val kind: SortKind,
    val property: String? = null,
    /** Newest (or Z) first; for [SortKind.NOTION] and [SortKind.CUSTOM] it doesn't apply. */
    val descending: Boolean = kind != SortKind.TITLE,
)

/** What a database's cards show. */
@Serializable
data class CardLayout(
    /** The note's header image (Notion's cover) on top of its card. */
    val cover: Boolean = false,
    /** The start of the note's content on its card. */
    val content: Boolean = true,
    /** Properties shown on the card (at most three), in the database's order of properties. */
    val properties: List<String> = emptyList(),
)

/**
 * How a database's properties are shown, chosen by the user (kept on this phone, backed up). As in
 * Notion, every property is shown until the user hides it; ones added in Notion later come last.
 */
@Serializable
data class PropertyLayout(
    /** The user's order; properties not in it follow in their usual order. */
    val order: List<String> = emptyList(),
    /** Properties hidden in the note (folded away under "N more properties"). */
    val hidden: List<String> = emptyList(),
    /**
     * What the cards show: stored here before cards were set per database ([CardLayout]); the
     * lists fill these in from each database's setup (PropertyLayouts.cardLayouts).
     */
    val onCard: List<String> = emptyList(),
    val coverOnCard: Boolean = false,
    val contentOnCard: Boolean = true,
)

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

/**
 * A page's or database's icon, as Notion has it: an emoji, an image ([url]: uploaded, a link, or
 * a custom emoji of the workspace), or one of Notion's own icons ([name] in [color]).
 */
@Serializable
data class PageIcon(
    val emoji: String? = null,
    val url: String? = null,
    /** When [url] stops working, for a file Notion hosts. */
    val urlExpiry: String? = null,
    val name: String? = null,
    val color: String? = null,
)

/** Something shared with the integration, for Search and Home's list (kept on this phone for Home). */
@Serializable
data class WorkspaceItem(
    val id: String,
    val isDatabase: Boolean,
    val title: String,
    val icon: PageIcon? = null,
    val lastEditedTime: String = "",
    /** A page: the note as the editor needs it (in its database, or among the pages). */
    val note: NoteSummary? = null,
    /** A data source: the database it is in. */
    val databaseId: String? = null,
    /** A database with several data sources ([id] is the database's): those data sources. */
    val sources: List<PageRef> = emptyList(),
)

/** A database as Notion describes it: its name, icon and data sources (see WorkspaceCodec.group). */
@Serializable
data class DatabaseInfo(
    val id: String,
    val title: String,
    val icon: PageIcon? = null,
    val sources: List<PageRef> = emptyList(),
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
        val CHIP_TYPES = setOf(
            "select", "status", "multi_select", "people", "checkbox",
            "relation", "created_by", "last_edited_by", "date", "formula",
        )
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
    val icon: PageIcon? = null,
    val url: String? = null,
    val createdTime: String = "",
    val lastEditedTime: String = "",
    val properties: List<NoteProperty> = emptyList(),
    /** The page's header image (Notion's "cover"). */
    val cover: String? = null,
    /** When [cover] stops working, for a file Notion hosts. */
    val coverExpiry: String? = null,
) {
    val isLocal: Boolean get() = id.startsWith(LOCAL_PREFIX)

    fun property(name: String): NoteProperty? = properties.firstOrNull { it.name == name }

    companion object {
        /** Notes created on this phone that Notion doesn't have yet. */
        const val LOCAL_PREFIX = "local-"
    }
}

enum class BlockType {
    PARAGRAPH, HEADING_1, HEADING_2, HEADING_3, HEADING_4, BULLETED, NUMBERED, TODO,
    TOGGLE, QUOTE, CALLOUT, CODE,

    /** A horizontal line; it has no text. */
    DIVIDER,

    /**
     * A block the editor can't change (image, table, bookmark, …), told apart by [Block.rawType].
     * Shown read-only, kept, never written.
     */
    UNSUPPORTED,
    ;

    val isListItem: Boolean get() = this == BULLETED || this == NUMBERED || this == TODO
    val isHeading: Boolean get() = this == HEADING_1 || this == HEADING_2 || this == HEADING_3 || this == HEADING_4

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
    /**
     * Read-only blocks: the file, image or link (image, video, file, pdf, audio, bookmark, embed,
     * link preview), or the linked page's ID (child page or database, link to page).
     */
    val url: String? = null,
    /** When a file Notion hosts stops working ([url] is then signed for about an hour). */
    val urlExpiry: String? = null,
    /** A link to a page ([url]) that is a database, not a page. */
    val linksDatabase: Boolean = false,
    /** A table's cells, row by row, as plain text. */
    val rows: List<List<String>>? = null,
    /** The table's first row is its header. */
    val headerRow: Boolean = false,
    /**
     * How far the block is nested: 0 on the page, 1 inside the block above it with depth 0, … The
     * content is one flat list; a block's parent is the nearest block above it one level less deep.
     */
    val depth: Int = 0,
    /** The Notion ID of the block's parent (null: the page), as Notion had it. Set in the synced copy. */
    val parentId: String? = null,
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
    /**
     * True when the nested blocks were downloaded too. Copies made for the overview's previews
     * (and copies from before nested blocks were shown) have only the top level; opening the note
     * downloads the rest.
     */
    val nestedLoaded: Boolean = false,
    /**
     * True when sending new blocks failed in a way that leaves open whether Notion added them
     * (a timeout, a server error). The content is checked against Notion before it is sent again,
     * so the blocks aren't added twice (see BlockMatch).
     */
    val uncertain: Boolean = false,
) {
    /** True when a file of the content that Notion hosts has expired (see [NotionFiles]). */
    fun hasExpiredFiles(nowMs: Long): Boolean = blocks.any { NotionFiles.isExpired(it.urlExpiry, nowMs) }
}

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }
