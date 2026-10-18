package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import be.wimvandonck.noter.data.notion.InlineMarkdown
import java.net.URI

/** A web page's title and main content, as blocks for a new note (#63). */
data class WebArticle(val title: String, val blocks: List<Block>)

/**
 * Reads the main content of a web page shared to the app: its headings, paragraphs, list items,
 * quotes, code and images, in order, from its `<article>` (else `<main>`, else `<body>`), without
 * scripts, menus, headers, footers and side bars. The page's own preview image comes first. A
 * small reader of its own, since a HTML library would be a new dependency; pages it reads badly
 * still have the link as a bookmark at the top of the note.
 */
object WebArticles {
    /** At most this many blocks (with the bookmark and shared text, Notion takes 100). */
    const val MAX_BLOCKS = 90
    const val MAX_IMAGES = 20

    /** Notion refuses longer links. */
    private const val MAX_URL = 2000

    private val OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val NOISE = Regex("""<!--.*?-->|<(script|style|noscript|svg|template|iframe|canvas|select)\b.*?</\1\s*>""", OPTIONS)
    private val CHROME = Regex("""<(nav|header|footer|aside|form|button|figcaption)\b.*?</\1\s*>""", OPTIONS)
    private val META = Regex("""<meta\b[^>]*>""", OPTIONS)
    private val ATTRIBUTE = Regex("""([a-zA-Z:_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>"']+))""")
    private val TITLE = Regex("""<title\b[^>]*>(.*?)</title\s*>""", OPTIONS)
    private val ELEMENT = Regex("""<(h[1-6]|p|li|blockquote|pre)\b[^>]*>(.*?)</\1\s*>|<img\b([^>]*)>""", OPTIONS)
    private val IMAGE = Regex("""<img\b([^>]*)>""", OPTIONS)
    private val BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<\s*/?\s*([a-zA-Z0-9]*)[^>]*>""")

    /** Tags that separate words; others (links, bold, …) sit inside a word's text. */
    private val BLOCK_TAGS = setOf(
        "p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "section",
        "table", "tr", "td", "th", "figure", "dd", "dt", "hr",
    )
    private val WHITESPACE = Regex("""\s+""")
    private val SPACES = Regex("""[ \t\u00A0]+""")
    private val ENTITY = Regex("""&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);""")
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "copy" to "©",
    )

    /** [html] of the page at [pageUrl] (to resolve relative image links). */
    fun parse(html: String, pageUrl: String): WebArticle {
        val clean = NOISE.replace(html, " ")
        val meta = metaOf(clean)
        val title = text(meta["og:title"] ?: TITLE.find(clean)?.groupValues?.get(1).orEmpty())
        val region = CHROME.replace(regionOf(clean), " ")
        val blocks = mutableListOf<Block>()
        val images = mutableSetOf<String>()

        fun addImage(src: String?) {
            if (src.isNullOrBlank() || images.size >= MAX_IMAGES) return
            val url = resolve(pageUrl, decode(src)) ?: return
            if (images.add(url)) blocks += Block(key = "", type = BlockType.UNSUPPORTED, rawType = SharedNotes.IMAGE, url = url)
        }

        fun addImageTag(attributes: String) {
            val a = attributesOf(attributes)
            // Tracking pixels and spacers.
            if (a["width"] == "1" || a["height"] == "1") return
            addImage(a["src"]?.takeUnless { it.startsWith("data:") } ?: a["data-src"])
        }

        addImage(meta["og:image"])
        for (m in ELEMENT.findAll(region)) {
            if (blocks.size >= MAX_BLOCKS) break
            val tag = m.groupValues[1].lowercase()
            if (tag.isEmpty()) {
                addImageTag(m.groupValues[3])
                continue
            }
            val inner = m.groupValues[2]
            val type = typeOf(tag)
            if (type == BlockType.CODE) {
                val code = code(inner)
                if (code.isNotBlank()) blocks += Block(key = "", type = type, text = code)
            } else {
                val body = text(inner)
                // The page's own title is the note's title already.
                if (body.isNotEmpty() && !(tag == "h1" && body.equals(title, ignoreCase = true))) {
                    blocks += Block(key = "", type = type, text = InlineMarkdown.escape(body))
                }
            }
            IMAGE.findAll(inner).forEach { addImageTag(it.groupValues[1]) }
        }
        // Nothing to read (a page built by scripts): its description, if it has one.
        if (blocks.none { it.type != BlockType.UNSUPPORTED }) {
            val description = text(meta["og:description"] ?: meta["description"].orEmpty())
            if (description.isNotEmpty()) blocks += Block(key = "", text = InlineMarkdown.escape(description))
        }
        return WebArticle(title, blocks.take(MAX_BLOCKS).mapIndexed { i, block -> block.copy(key = "web-$i") })
    }

    private fun typeOf(tag: String): BlockType = when (tag) {
        "h1" -> BlockType.HEADING_1
        "h2" -> BlockType.HEADING_2
        "h3", "h4", "h5", "h6" -> BlockType.HEADING_3
        "li" -> BlockType.BULLETED
        "blockquote" -> BlockType.QUOTE
        "pre" -> BlockType.CODE
        else -> BlockType.PARAGRAPH
    }

    private fun regionOf(html: String): String {
        for (tag in listOf("article", "main", "body")) {
            Regex("""<$tag\b[^>]*>(.*)</$tag\s*>""", OPTIONS).find(html)?.let { return it.groupValues[1] }
        }
        return html
    }

    /** The page's `<meta>` values by property or name (`og:title`, `description`, …), the first of each. */
    private fun metaOf(html: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        META.findAll(html).forEach { m ->
            val a = attributesOf(m.value)
            val name = (a["property"] ?: a["name"])?.lowercase() ?: return@forEach
            val content = a["content"] ?: return@forEach
            if (name !in out) out[name] = decode(content).trim()
        }
        return out
    }

    private fun attributesOf(tag: String): Map<String, String> =
        ATTRIBUTE.findAll(tag).associate { m ->
            m.groupValues[1].lowercase() to (m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value.orEmpty())
        }

    /** Text of a piece of HTML: one line per `<br>`, spaces as a browser shows them. */
    private fun text(html: String): String {
        val lines = stripTags(BREAK.replace(WHITESPACE.replace(html, " "), "\n"))
        return decode(lines).replace(SPACES, " ").lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    /** Text of a `<pre>`, keeping its lines. */
    private fun code(html: String): String = decode(stripTags(BREAK.replace(html, "\n"))).trim('\n', '\r')

    private fun stripTags(html: String): String =
        TAG.replace(html) { if (it.groupValues[1].lowercase() in BLOCK_TAGS) " " else "" }

    private fun decode(text: String): String = ENTITY.replace(text) { m ->
        val name = m.groupValues[1]
        when {
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let { character(it) }
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let { character(it) }
            else -> NAMED[name]
        } ?: m.value
    }

    private fun character(codePoint: Int): String? =
        if (codePoint != 0 && Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else null

    /** [src] as a full web link (relative to [base]), or null when it isn't one Notion can use. */
    private fun resolve(base: String, src: String): String? = runCatching {
        URI(base).resolve(src.trim().replace(" ", "%20")).toString()
    }.getOrNull()?.takeIf { (it.startsWith("https://") || it.startsWith("http://")) && it.length <= MAX_URL }
}
