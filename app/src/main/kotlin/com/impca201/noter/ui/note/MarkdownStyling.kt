package com.impca201.noter.ui.note

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

/**
 * Shows inline Markdown while typing: the text between `**`, `*`, `~~` and `` ` `` gets its
 * style and the markers are dimmed. The text itself is unchanged (the markers stay visible and
 * editable), so cursor positions need no mapping.
 */
class MarkdownStyling(private val markerColor: Color, private val linkColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.none { it == '*' || it == '~' || it == '`' || it == '[' }) return TransformedText(text, OffsetMapping.Identity)
        val builder = AnnotatedString.Builder(text)
        val marker = SpanStyle(color = markerColor)
        fun style(regex: Regex, open: Int, close: Int, span: SpanStyle) {
            regex.findAll(raw).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                builder.addStyle(marker, start, start + open)
                builder.addStyle(span, start + open, end - close)
                builder.addStyle(marker, end - close, end)
            }
        }
        style(BOLD, 2, 2, SpanStyle(fontWeight = FontWeight.Bold))
        style(ITALIC, 1, 1, SpanStyle(fontStyle = FontStyle.Italic))
        style(STRIKE, 2, 2, SpanStyle(textDecoration = TextDecoration.LineThrough))
        style(CODE, 1, 1, SpanStyle(fontFamily = FontFamily.Monospace))
        LINK.findAll(raw).forEach { match ->
            val label = match.groups[1]!!.range
            builder.addStyle(marker, match.range.first, label.first)
            builder.addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), label.first, label.last + 1)
            builder.addStyle(marker, label.last + 1, match.range.last + 1)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean =
        other is MarkdownStyling && other.markerColor == markerColor && other.linkColor == linkColor

    override fun hashCode(): Int = markerColor.hashCode() * 31 + linkColor.hashCode()

    private companion object {
        val BOLD = Regex("""\*\*(?=\S)(.+?)\*\*""")
        val ITALIC = Regex("""(?<![*\\])\*(?=[^\s*])(.+?)(?<![\\*])\*(?!\*)""")
        val STRIKE = Regex("""~~(?=\S)(.+?)~~""")
        val CODE = Regex("""`[^`\n]+`""")
        val LINK = Regex("""\[([^\]\n]+)]\(\S+?\)""")
    }
}
