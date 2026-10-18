package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
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

    /** The link's site without "www." ("example.com"), or "" when there is none. */
    fun siteOf(url: String?): String =
        url?.let { runCatching { URI(it).host }.getOrNull() }?.removePrefix("www.").orEmpty()
}
