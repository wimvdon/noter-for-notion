package com.impca201.noter.data.notion

import com.impca201.noter.data.api.arrayOrNull
import com.impca201.noter.data.api.asObjectOrNull
import com.impca201.noter.data.api.boolOrNull
import com.impca201.noter.data.api.objOrNull
import com.impca201.noter.data.api.stringOrNull
import kotlinx.serialization.json.JsonArray

/** A piece of text with one set of formatting. */
data class TextRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
) {
    fun sameStyle(other: TextRun): Boolean =
        bold == other.bold && italic == other.italic && strike == other.strike && code == other.code && link == other.link
}

/**
 * Converts Notion rich text to inline Markdown and back:
 * `**bold**`, `*italic*`, `~~strikethrough~~`, `` `code` `` and `[text](url)`.
 *
 * Markdown is only produced where the text has that formatting, so plain notes stay plain. A
 * marker only counts when it opens right before a non-space character and is closed later, so
 * "5 * 3" stays as typed. Notion's colors and underline are not kept when a text is changed.
 */
object InlineMarkdown {
    /** Notion accepts at most 2000 characters per rich text item. */
    private const val MAX_ITEM_LENGTH = 2000

    // ---- Notion JSON -> runs -> Markdown ----

    fun runsFromRichText(richText: JsonArray?): List<TextRun> =
        richText.orEmpty().mapNotNull { element ->
            val item = element.asObjectOrNull() ?: return@mapNotNull null
            val text = item.stringOrNull("plain_text")
                ?: item.objOrNull("text")?.stringOrNull("content")
                ?: return@mapNotNull null
            val annotations = item.objOrNull("annotations")
            val link = if (item.stringOrNull("type") == "text") {
                item.objOrNull("text")?.objOrNull("link")?.stringOrNull("url")
            } else {
                null
            }
            TextRun(
                text = text,
                bold = annotations?.boolOrNull("bold") == true,
                italic = annotations?.boolOrNull("italic") == true,
                strike = annotations?.boolOrNull("strikethrough") == true,
                code = annotations?.boolOrNull("code") == true,
                link = link,
            )
        }

    fun fromRichText(richText: JsonArray?): String = toMarkdown(runsFromRichText(richText))

    /** Plain text of a rich text array, without any formatting. */
    fun plainText(richText: JsonArray?): String =
        richText.orEmpty().joinToString("") { it.asObjectOrNull()?.stringOrNull("plain_text").orEmpty() }

    fun toMarkdown(runs: List<TextRun>): String {
        val merged = merge(runs)
        val plain = render(merged, escape = false)
        // Escape only when the text would otherwise read back differently (it contains markers).
        return if (merge(parse(plain)) == merged) plain else render(merged, escape = true)
    }

    private fun render(runs: List<TextRun>, escape: Boolean): String {
        val out = StringBuilder()
        for (run in runs) {
            var body = if (run.code) run.text.replace("`", "'") else if (escape) escapeText(run.text) else run.text
            if (run.code) body = "`$body`"
            if (run.strike) body = "~~$body~~"
            if (run.italic) body = "*$body*"
            if (run.bold) body = "**$body**"
            if (run.link != null) body = "[$body](${run.link})"
            out.append(body)
        }
        return out.toString()
    }

    private fun escapeText(text: String): String =
        buildString { text.forEach { if (it in SPECIAL) append('\\'); append(it) } }

    private const val SPECIAL = "\\*~`[]"

    // ---- Markdown -> runs -> Notion JSON ----

    fun parse(markdown: String): List<TextRun> {
        val runs = mutableListOf<TextRun>()
        parseInto(markdown, TextRun(""), runs)
        return merge(runs)
    }

    private fun parseInto(source: String, style: TextRun, out: MutableList<TextRun>) {
        var bold = style.bold
        var italic = style.italic
        var strike = style.strike
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) {
                out += TextRun(buffer.toString(), bold, italic, strike, code = false, link = style.link)
                buffer.clear()
            }
        }
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '\\' && i + 1 < source.length && source[i + 1] in SPECIAL -> {
                    buffer.append(source[i + 1]); i += 2
                }
                c == '`' && opens(source, i, "`") -> {
                    val end = source.indexOf('`', i + 1)
                    flush()
                    out += TextRun(source.substring(i + 1, end), bold, italic, strike, code = true, link = style.link)
                    i = end + 1
                }
                source.startsWith("**", i) && (bold || opens(source, i, "**")) -> {
                    flush(); bold = !bold; i += 2
                }
                source.startsWith("~~", i) && (strike || opens(source, i, "~~")) -> {
                    flush(); strike = !strike; i += 2
                }
                c == '*' && (italic || opens(source, i, "*")) -> {
                    flush(); italic = !italic; i += 1
                }
                c == '[' && style.link == null && linkAt(source, i) != null -> {
                    val (textEnd, urlEnd) = linkAt(source, i)!!
                    flush()
                    val url = source.substring(textEnd + 2, urlEnd)
                    parseInto(source.substring(i + 1, textEnd), TextRun("", bold, italic, strike, link = url), out)
                    i = urlEnd + 1
                }
                else -> {
                    buffer.append(c); i += 1
                }
            }
        }
        flush()
    }

    /** A marker opens formatting when a non-space follows and the same marker comes later. */
    private fun opens(source: String, at: Int, marker: String): Boolean {
        val next = at + marker.length
        if (next >= source.length || source[next].isWhitespace()) return false
        if (marker == "*" && source[next] == '*') return false
        return source.indexOf(marker, next + 1) > next
    }

    /** For "[text](url)" at [at]: the index of "]" and of ")". */
    private fun linkAt(source: String, at: Int): Pair<Int, Int>? {
        val textEnd = source.indexOf("](", at + 1).takeIf { it > at + 1 } ?: return null
        if (source.substring(at + 1, textEnd).contains('\n')) return null
        val urlEnd = source.indexOf(')', textEnd + 2).takeIf { it > textEnd + 2 } ?: return null
        val url = source.substring(textEnd + 2, urlEnd)
        if (url.any { it.isWhitespace() }) return null
        return textEnd to urlEnd
    }

    /** Notion rich text items for [markdown] (an empty list for empty text). */
    fun toRichText(markdown: String): List<Map<String, Any?>> =
        parse(markdown).flatMap { run ->
            run.text.chunked(MAX_ITEM_LENGTH).map { chunk ->
                mapOf(
                    "type" to "text",
                    "text" to mapOf("content" to chunk, "link" to run.link?.let { mapOf("url" to it) }),
                    "annotations" to mapOf(
                        "bold" to run.bold,
                        "italic" to run.italic,
                        "strikethrough" to run.strike,
                        "code" to run.code,
                    ),
                )
            }
        }

    /** Notion rich text items for [text] taken literally (code blocks: no Markdown). */
    fun plainRichText(text: String): List<Map<String, Any?>> =
        text.chunked(MAX_ITEM_LENGTH).map { chunk -> mapOf("type" to "text", "text" to mapOf("content" to chunk)) }

    /** Text without Markdown markers, for previews and sorting. */
    fun stripped(markdown: String): String = parse(markdown).joinToString("") { it.text }

    private fun merge(runs: List<TextRun>): List<TextRun> {
        val out = mutableListOf<TextRun>()
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.sameStyle(run)) out[out.size - 1] = last.copy(text = last.text + run.text) else out += run
        }
        return out
    }
}
