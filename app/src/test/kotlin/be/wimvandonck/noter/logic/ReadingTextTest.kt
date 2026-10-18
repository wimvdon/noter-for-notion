package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTextTest {
    private fun b(key: String, text: String = "", type: BlockType = BlockType.PARAGRAPH, depth: Int = 0, checked: Boolean = false) =
        Block(key = key, type = type, text = text, depth = depth, checked = checked)

    @Test
    fun `every line becomes text with its list marker, Markdown marks removed`() {
        val lines = ReadingText.lines(
            listOf(
                b("h", "Shopping", BlockType.HEADING_1),
                b("p", "Buy **fresh** milk"),
                b("a", "Milk", BlockType.TODO),
                b("c", "Eggs", BlockType.TODO, checked = true),
                b("d", "", BlockType.DIVIDER),
                b("e", "First", BlockType.NUMBERED),
                b("f", "Second", BlockType.NUMBERED),
                b("g", "Nested", BlockType.BULLETED, depth = 1),
            ),
        )
        assertEquals(listOf("Shopping", "Buy fresh milk", "☐ Milk", "☑ Eggs", "", "1. First", "2. Second", "• Nested"), lines.map { it.text })
        assertEquals(ReadingStyle.HEADING_1, lines[0].style)
        assertEquals(ReadingStyle.DIVIDER, lines[4].style)
        assertEquals(1, lines.last().depth)
    }

    @Test
    fun `code keeps its characters and blocks the editor only shows are left out`() {
        val lines = ReadingText.lines(
            listOf(
                b("k", "val x = **y**", BlockType.CODE),
                b("h4", "Small", BlockType.HEADING_4),
                Block(key = "img", type = BlockType.UNSUPPORTED, rawType = "image"),
            ),
        )
        assertEquals(listOf("val x = **y**", "Small"), lines.map { it.text })
        assertEquals(listOf(ReadingStyle.CODE, ReadingStyle.HEADING_3), lines.map { it.style })
    }
}
