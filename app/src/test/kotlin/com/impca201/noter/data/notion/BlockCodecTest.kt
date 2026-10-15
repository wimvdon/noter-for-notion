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
        val toggleHeading = parse("""{"id":"b","type":"heading_1","has_children":true,"heading_1":{"rich_text":[],"is_toggleable":true}}""")!!
        assertEquals(BlockType.UNSUPPORTED, toggleHeading.type)
    }

    @Test
    fun `writes a to-do with its checked state`() {
        val request = BlockCodec.toCreateRequest(Block("k", type = BlockType.TODO, text = "Eggs", checked = true))
        assertEquals("to_do", request["type"])
        @Suppress("UNCHECKED_CAST")
        val body = request["to_do"] as Map<String, Any?>
        assertEquals(true, body["checked"])
    }

    @Test
    fun `reads code, quote, toggle, callout and divider`() {
        val code = parse("""{"id":"b","type":"code","code":{"rich_text":[{"plain_text":"a **b**","annotations":{"bold":false}}],"language":"kotlin"}}""")!!
        assertEquals(BlockType.CODE, code.type)
        assertEquals("a **b**", code.text)
        assertEquals("kotlin", code.language)
        assertEquals(BlockType.QUOTE, parse("""{"id":"b","type":"quote","quote":{"rich_text":[]}}""")!!.type)
        assertEquals(BlockType.TOGGLE, parse("""{"id":"b","type":"toggle","has_children":true,"toggle":{"rich_text":[]}}""")!!.type)
        assertEquals(BlockType.CALLOUT, parse("""{"id":"b","type":"callout","callout":{"rich_text":[]}}""")!!.type)
        assertEquals(BlockType.DIVIDER, parse("""{"id":"b","type":"divider","divider":{}}""")!!.type)
    }

    @Test
    fun `writes code without markdown and a divider without text`() {
        val code = BlockCodec.toCreateRequest(Block("k", type = BlockType.CODE, text = "**x**", language = "kotlin"))
        @Suppress("UNCHECKED_CAST")
        val body = code["code"] as Map<String, Any?>
        assertEquals("kotlin", body["language"])
        @Suppress("UNCHECKED_CAST")
        val text = (body["rich_text"] as List<Map<String, Any?>>)[0]["text"] as Map<String, Any?>
        assertEquals("**x**", text["content"])
        assertEquals(mapOf("object" to "block", "type" to "divider", "divider" to emptyMap<String, Any?>()), BlockCodec.toCreateRequest(Block("k", type = BlockType.DIVIDER)))
    }
}
