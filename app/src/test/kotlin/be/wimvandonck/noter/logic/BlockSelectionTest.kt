package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockSelectionTest {
    private fun b(key: String, text: String = "", type: BlockType = BlockType.PARAGRAPH, depth: Int = 0, checked: Boolean = false) =
        Block(key = key, type = type, text = text, depth = depth, checked = checked)

    private val blocks = listOf(
        b("h", "Shopping", BlockType.HEADING_1),
        b("a", "Milk", BlockType.TODO),
        b("c", "Eggs", BlockType.TODO, checked = true),
        b("d", "", BlockType.DIVIDER),
        b("e", "First", BlockType.NUMBERED),
        b("f", "Second", BlockType.NUMBERED),
        b("g", "Nested", BlockType.BULLETED, depth = 1),
        Block(key = "img", type = BlockType.UNSUPPORTED, rawType = "image"),
    )

    @Test
    fun `text blocks and dividers can be selected, blocks that are only shown can't`() {
        assertTrue(BlockSelection.selectable(blocks[0]))
        assertTrue(BlockSelection.selectable(blocks[3]))
        assertFalse(BlockSelection.selectable(blocks.last()))
        assertEquals(setOf("h", "a", "c", "d", "e", "f", "g"), BlockSelection.all(blocks))
    }

    @Test
    fun `toggling a line adds or removes it`() {
        val on = BlockSelection.toggle(emptySet(), "a")
        assertEquals(setOf("a"), on)
        assertEquals(emptySet<String>(), BlockSelection.toggle(on, "a"))
    }

    @Test
    fun `copying gives the selected lines as Markdown, in the order of the note`() {
        val text = BlockSelection.asText(blocks, setOf("g", "c", "h", "f", "d", "a"))
        assertEquals("# Shopping\n- [ ] Milk\n- [x] Eggs\n---\n2. Second\n  - Nested", text)
    }

    @Test
    fun `code is copied in a fence without indenting`() {
        val code = listOf(b("a", "val x = 1", BlockType.CODE, depth = 1))
        assertEquals("```\nval x = 1\n```", BlockSelection.asText(code, setOf("a")))
    }
}
