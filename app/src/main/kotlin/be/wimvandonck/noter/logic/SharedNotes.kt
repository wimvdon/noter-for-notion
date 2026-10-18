package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.notion.InlineMarkdown
import java.net.URI

/** The title and content of a new note made from a share (#63). */
object SharedNotes {
    /** Notion takes at most 100 blocks with a new page. */
    const val MAX_BLOCKS = 100

    /** Read-only block kinds a share adds (Notion's names; see BlockCodec.createRequestOrNull). */
    const val IMAGE = "image"
    const val BOOKMARK = "bookmark"

    private val PARAGRAPHS = Regex("""\n\s*\n""")

    /**
     * The shared title, else the page's title ([article]), else the link's site, else the first
     * line of the text; at most [SharedText.MAX_TITLE] characters.
     */
    fun title(shared: SharedContent, article: WebArticle?): String =
        listOf(shared.title, article?.title.orEmpty(), siteOf(shared.url), shared.text.lineSequence().firstOrNull().orEmpty())
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(SharedText.MAX_TITLE)
            .orEmpty()

    /** The link as a bookmark (Notion shows its preview), the shared text, then the page's content. */
    fun blocks(shared: SharedContent, article: WebArticle?): List<Block> {
        val out = mutableListOf<Block>()
        shared.url?.let { out += Block(key = "", type = BlockType.UNSUPPORTED, rawType = BOOKMARK, url = it) }
        shared.text.split(PARAGRAPHS).map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            out += Block(key = "", text = InlineMarkdown.escape(it))
        }
        article?.blocks?.let { out += it }
        return out.take(MAX_BLOCKS).mapIndexed { i, block -> block.copy(key = "shared-$i") }
    }

    /** Names a URL property is likely to have when a database has more than one (#68). */
    private val LINK_NAMES = listOf("url", "link", "source", "website", "web")

    /**
     * The shared link as the value of the database's URL property (#68), or null when there is
     * no link or no URL property. With several URL properties, the one named like a link
     * ("URL", "Link", "Source", ...) is used, else the first.
     */
    fun urlProperty(schema: DatabaseSchema?, url: String?): NoteProperty? {
        if (url.isNullOrBlank() || schema == null) return null
        val candidates = schema.properties.filter { it.type == "url" }
        val target = LINK_NAMES.firstNotNullOfOrNull { name -> candidates.firstOrNull { it.name.trim().equals(name, ignoreCase = true) } }
            ?: candidates.firstOrNull()
            ?: return null
        return NoteProperty(target.name, "url", PropertyValue.Plain(url))
    }

    /** The link's site without "www." ("example.com"), or "" when there is none. */
    fun siteOf(url: String?): String =
        url?.let { runCatching { URI(it).host }.getOrNull() }?.removePrefix("www.").orEmpty()
}
