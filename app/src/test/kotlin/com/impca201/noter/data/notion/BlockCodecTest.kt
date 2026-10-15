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
}
