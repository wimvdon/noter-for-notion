package com.impca201.noter.data.notion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineMarkdownTest {
    private fun richText(json: String): JsonArray = Json.parseToJsonElement(json).jsonArray

    private fun item(text: String, bold: Boolean = false, italic: Boolean = false, code: Boolean = false, link: String? = null) =
        """{"type":"text","plain_text":"$text","text":{"content":"$text","link":${link?.let { "{\"url\":\"$it\"}" } ?: "null"}},
           "annotations":{"bold":$bold,"italic":$italic,"strikethrough":false,"underline":false,"code":$code,"color":"default"}}"""

    @Test
    fun `plain text stays plain`() {
        assertEquals("Buy milk", InlineMarkdown.fromRichText(richText("[${item("Buy milk")}]")))
    }

    @Test
    fun `formatting becomes markdown`() {
        val md = InlineMarkdown.fromRichText(
            richText("[${item("Buy ")},${item("milk", bold = true)},${item(" and ")},${item("eggs", italic = true)},${item(" x", code = true)}]"),
        )
        assertEquals("Buy **milk** and *eggs*` x`", md)
    }

    @Test
    fun `links become markdown links`() {
        assertEquals("[site](https://a.b)", InlineMarkdown.fromRichText(richText("[${item("site", link = "https://a.b")}]")))
    }

    @Test
    fun `markdown parses back to the same runs`() {
        val runs = InlineMarkdown.parse("a **b** *c* ~~d~~ `e` [f](https://x.y)")
        assertEquals(
            listOf(
                TextRun("a "), TextRun("b", bold = true), TextRun(" "), TextRun("c", italic = true), TextRun(" "),
                TextRun("d", strike = true), TextRun(" "), TextRun("e", code = true), TextRun(" "), TextRun("f", link = "https://x.y"),
            ),
            runs,
        )
    }

    @Test
    fun `a lone star is not formatting`() {
        assertEquals(listOf(TextRun("5 * 3 = 15")), InlineMarkdown.parse("5 * 3 = 15"))
        assertEquals(listOf(TextRun("2*3")), InlineMarkdown.parse("2*3"))
    }

    @Test
    fun `text with markers is escaped so it reads back the same`() {
        val md = InlineMarkdown.toMarkdown(listOf(TextRun("*not italic*")))
        assertEquals("\\*not italic\\*", md)
        assertEquals("*not italic*", InlineMarkdown.stripped(md))
    }

    @Test
    fun `rich text request keeps formatting`() {
        val items = InlineMarkdown.toRichText("hi **there**")
        assertEquals(2, items.size)
        @Suppress("UNCHECKED_CAST")
        val annotations = items[1]["annotations"] as Map<String, Any?>
        assertEquals(true, annotations["bold"])
    }

    @Test
    fun `long text is split into items of at most 2000 characters`() {
        val items = InlineMarkdown.toRichText("x".repeat(4500))
        assertEquals(3, items.size)
    }

    @Test
    fun `empty text gives no rich text items`() {
        assertEquals(emptyList<Any>(), InlineMarkdown.toRichText(""))
    }
}
