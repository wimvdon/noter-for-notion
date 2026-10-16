package com.impca201.noter.data.notion

import com.impca201.noter.data.model.Block
import com.impca201.noter.data.model.BlockType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockCodecTest {
    private fun parse(json: String) = BlockCodec.parse(Json.parseToJsonElement(json).jsonObject, "k")

    @Test
    fun `reads a checked to-do`() {
        val block = parse("""{"id":"b1","type":"to_do","has_children":false,"to_do":{"rich_text":[{"plain_text":"Milk"}],"checked":true}}""")
        assertEquals(Block("k", "b1", BlockType.TODO, "Milk", checked = true), block)
    }

    @Test
    fun `reads headings and lists`() {
        assertEquals(BlockType.HEADING_2, parse("""{"id":"b","type":"heading_2","heading_2":{"rich_text":[]}}""")!!.type)
        assertEquals(BlockType.NUMBERED, parse("""{"id":"b","type":"numbered_list_item","numbered_list_item":{"rich_text":[]}}""")!!.type)
    }

    @Test
    fun `other blocks are kept but not editable`() {
        val image = parse("""{"id":"b","type":"image","has_children":false,"image":{"type":"external","external":{"url":"https://x/y.png"}}}""")!!
        assertEquals(BlockType.UNSUPPORTED, image.type)
        assertEquals("image", image.rawType)
    }

    @Test
    fun `reads toggles, quotes, callouts, code and dividers`() {
        val toggleHeading = parse("""{"id":"b","type":"heading_1","has_children":true,"heading_1":{"rich_text":[],"is_toggleable":true}}""")!!
        assertEquals(BlockType.HEADING_1, toggleHeading.type)
        assertEquals(true, toggleHeading.toggleable)
        assertEquals(BlockType.TOGGLE, parse("""{"id":"b","type":"toggle","toggle":{"rich_text":[]}}""")!!.type)
        assertEquals(BlockType.QUOTE, parse("""{"id":"b","type":"quote","quote":{"rich_text":[]}}""")!!.type)
        assertEquals(BlockType.DIVIDER, parse("""{"id":"b","type":"divider","divider":{}}""")!!.type)
        val callout = parse("""{"id":"b","type":"callout","callout":{"rich_text":[],"icon":{"type":"emoji","emoji":"🔥"}}}""")!!
        assertEquals("🔥", callout.icon)
        val code = parse("""{"id":"b","type":"code","code":{"rich_text":[{"plain_text":"a*b*c"}],"language":"kotlin"}}""")!!
        assertEquals("a*b*c", code.text)
        assertEquals("kotlin", code.language)
    }

    @Test
    fun `new code, callouts and dividers get what Notion requires`() {
        @Suppress("UNCHECKED_CAST")
        fun body(block: Block) = BlockCodec.toCreateRequest(block)[BlockCodec.notionType(block.type)] as Map<String, Any?>
        assertEquals("plain text", body(Block("k", type = BlockType.CODE, text = "x"))["language"])
        assertEquals(mapOf("type" to "emoji", "emoji" to "💡"), body(Block("k", type = BlockType.CALLOUT))["icon"])
        assertEquals(emptyMap<String, Any?>(), body(Block("k", type = BlockType.DIVIDER)))
        // Updates send only the text, so Notion keeps a callout's icon and a code block's language.
        val update = BlockCodec.toUpdateRequest(Block("k", type = BlockType.CODE, text = "**x**", language = "kotlin"))
        @Suppress("UNCHECKED_CAST")
        val richText = (update["code"] as Map<String, Any?>)["rich_text"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        assertEquals("**x**", (richText[0]["text"] as Map<String, Any?>)["content"])
    }

    @Test
    fun `writes a to-do with its checked state`() {
        val request = BlockCodec.toCreateRequest(Block("k", type = BlockType.TODO, text = "Eggs", checked = true))
        assertEquals("to_do", request["type"])
        @Suppress("UNCHECKED_CAST")
        val body = request["to_do"] as Map<String, Any?>
        assertEquals(true, body["checked"])
    }
}
