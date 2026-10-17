package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentEditorTest {
    private var counter = 0
    private val editor = DocumentEditor { "n${++counter}" }

    private fun b(key: String, text: String = "", type: BlockType = BlockType.PARAGRAPH, checked: Boolean = false, id: String? = null) =
        Block(key, id, type, text, checked)

    @Test
    fun `typing changes the text`() {
        val result = editor.changeText(listOf(b("a", "hi")), "a", "hi!")
        assertEquals("hi!", result.blocks[0].text)
    }

    @Test
    fun `enter in a checklist adds a checkbox`() {
        val result = editor.changeText(listOf(b("a", "Milk", BlockType.TODO)), "a", "Milk\n")
        assertEquals(listOf(b("a", "Milk", BlockType.TODO), b("n1", "", BlockType.TODO)), result.blocks)
        assertEquals("n1", result.focusKey)
        assertEquals(0, result.cursor)
    }

    @Test
    fun `enter in the middle moves the rest to the new line`() {
        val result = editor.changeText(listOf(b("a", "MilkEggs", BlockType.BULLETED)), "a", "Milk\nEggs")
        assertEquals(listOf("Milk", "Eggs"), result.blocks.map { it.text })
        assertEquals(BlockType.BULLETED, result.blocks[1].type)
    }

    @Test
    fun `enter on an empty list item ends the list`() {
        val result = editor.changeText(listOf(b("a", "", BlockType.TODO)), "a", "\n")
        assertEquals(listOf(b("a", "", BlockType.PARAGRAPH)), result.blocks)
    }

    @Test
    fun `enter on a heading adds text`() {
        val result = editor.changeText(listOf(b("h", "Title", BlockType.HEADING_1)), "h", "Title\n")
        assertEquals(BlockType.PARAGRAPH, result.blocks[1].type)
    }

    @Test
    fun `pasting several lines makes several blocks`() {
        val result = editor.changeText(listOf(b("a", "", BlockType.TODO)), "a", "a\nb\nc")
        assertEquals(listOf("a", "b", "c"), result.blocks.map { it.text })
        assertEquals(listOf(BlockType.TODO, BlockType.TODO, BlockType.TODO), result.blocks.map { it.type })
    }

    @Test
    fun `markdown at the start changes the kind of line`() {
        fun typed(text: String) = editor.changeText(listOf(b("a")), "a", text).blocks[0]
        assertEquals(BlockType.HEADING_1, typed("# ").type)
        assertEquals(BlockType.HEADING_2, typed("## ").type)
        assertEquals(BlockType.HEADING_3, typed("### Hi").type)
        assertEquals(BlockType.HEADING_4, typed("#### Hi").type)
        assertEquals("Hi", typed("#### Hi").text)
        assertEquals("Hi", typed("### Hi").text)
        assertEquals(BlockType.BULLETED, typed("- ").type)
        assertEquals(BlockType.BULLETED, typed("* ").type)
        assertEquals(BlockType.NUMBERED, typed("1. ").type)
        assertEquals(BlockType.TODO, typed("[] ").type)
        assertEquals(BlockType.TODO, typed("[ ] ").type)
        val checked = typed("[x] ")
        assertEquals(BlockType.TODO, checked.type)
        assertEquals(true, checked.checked)
        assertEquals(BlockType.PARAGRAPH, typed("#hashtag").type)
    }

    @Test
    fun `markdown in a list item is just text`() {
        val result = editor.changeText(listOf(b("a", "", BlockType.BULLETED)), "a", "# ")
        assertEquals(BlockType.BULLETED, result.blocks[0].type)
    }

    @Test
    fun `backspace at the start of a list item makes it text first`() {
        val result = editor.backspaceAtStart(listOf(b("x"), b("a", "Milk", BlockType.TODO)), "a")
        assertEquals(BlockType.PARAGRAPH, result.blocks[1].type)
        assertEquals(2, result.blocks.size)
    }

    @Test
    fun `backspace at the start of text joins it with the line above`() {
        val result = editor.backspaceAtStart(listOf(b("x", "Hello "), b("a", "world")), "a")
        assertEquals(listOf(b("x", "Hello world")), result.blocks)
        assertEquals("x", result.focusKey)
        assertEquals(6, result.cursor)
    }

    @Test
    fun `blocks with nested content keep their kind`() {
        val block = Block("a", "1", BlockType.BULLETED, "x", hasChildren = true)
        assertFalse(editor.canChangeType(block))
        assertEquals(BlockType.BULLETED, editor.setType(listOf(block), "a", BlockType.TODO).blocks[0].type)
    }

    @Test
    fun `numbers restart after a non-numbered line`() {
        val blocks = listOf(
            b("1", type = BlockType.NUMBERED), b("2", type = BlockType.NUMBERED), b("x"), b("3", type = BlockType.NUMBERED),
        )
        assertEquals(mapOf("1" to 1, "2" to 2, "3" to 1), DocumentEditor.numbers(blocks))
    }

    @Test
    fun `enter after text with to-dos below stays text`() {
        val blocks = listOf(b("t", "Groceries"), b("c", "Milk", BlockType.TODO))
        val result = editor.changeText(blocks, "t", "Groceries\n")
        assertEquals(BlockType.PARAGRAPH, result.blocks[1].type)
    }

    @Test
    fun `delete removes a line`() {
        val result = editor.delete(listOf(b("a", "Milk", BlockType.TODO), b("b", "Eggs", BlockType.TODO)), "a")
        assertEquals(listOf("b"), result.blocks.map { it.key })
        assertNull(result.focusKey)
    }

    @Test
    fun `notion shortcuts for toggles, quotes, code and dividers`() {
        fun typed(text: String) = editor.changeText(listOf(b("a")), "a", text).blocks
        assertEquals(BlockType.TOGGLE, typed("> ")[0].type)
        assertEquals(BlockType.QUOTE, typed("\" ")[0].type)
        assertEquals(BlockType.CODE, typed("```")[0].type)
        val divider = editor.changeText(listOf(b("x", "above"), b("a")), "a", "---")
        assertEquals(listOf(BlockType.PARAGRAPH, BlockType.DIVIDER, BlockType.PARAGRAPH), divider.blocks.map { it.type })
        assertEquals(divider.blocks[2].key, divider.focusKey)
    }

    @Test
    fun `enter in code is a new line inside the code`() {
        val result = editor.changeText(listOf(b("c", "val a = 1", BlockType.CODE)), "c", "val a = 1\nval b = 2")
        assertEquals(1, result.blocks.size)
        assertEquals("val a = 1\nval b = 2", result.blocks[0].text)
    }

    @Test
    fun `enter after a quote, callout or toggle continues with text`() {
        for (type in listOf(BlockType.QUOTE, BlockType.CALLOUT, BlockType.TOGGLE)) {
            val result = editor.changeText(listOf(b("q", "Hi", type)), "q", "Hi\n")
            assertEquals(BlockType.PARAGRAPH, result.blocks[1].type)
        }
    }

    @Test
    fun `backspace below a divider removes the divider`() {
        val blocks = listOf(b("x", "above"), b("d", type = BlockType.DIVIDER), b("a", "below"))
        val result = editor.backspaceAtStart(blocks, "a")
        assertEquals(listOf("x", "a"), result.blocks.map { it.key })
        assertEquals("a", result.focusKey)
    }

    @Test
    fun `dividers can't become text and text can't become a divider`() {
        val divider = b("d", type = BlockType.DIVIDER)
        assertFalse(editor.canChangeType(divider))
        assertEquals(BlockType.PARAGRAPH, editor.setType(listOf(b("a")), "a", BlockType.DIVIDER).blocks[0].type)
    }

    @Test
    fun `tapping below the content reuses an empty last line`() {
        val reuse = editor.addLineAtEnd(listOf(b("a", "x"), b("e")))
        assertEquals(2, reuse.blocks.size)
        assertEquals("e", reuse.focusKey)
        val added = editor.addLineAtEnd(listOf(b("a", "x", BlockType.TODO)))
        assertEquals(listOf(BlockType.TODO, BlockType.PARAGRAPH), added.blocks.map { it.type })
    }

    // ---- Nesting ----

    private fun n(key: String, depth: Int, type: BlockType = BlockType.BULLETED, text: String = key) =
        Block(key, type = type, text = text, depth = depth)

    @Test
    fun `indent puts a block and what is in it in the block above`() {
        val blocks = listOf(n("a", 0), n("b", 0), n("c", 1), n("d", 0))
        val result = editor.indent(blocks, "b")
        assertEquals(listOf(0, 1, 2, 0), result.blocks.map { it.depth })
        // The first block has nothing above it to go into.
        assertFalse(editor.canIndent(blocks, "a"))
    }

    @Test
    fun `outdent takes a block out, and the blocks after it go into it`() {
        val blocks = listOf(n("a", 0), n("b", 1), n("c", 1), n("d", 0))
        val result = editor.outdent(blocks, "b")
        assertEquals(listOf(0, 0, 1, 0), result.blocks.map { it.depth })
        assertFalse(editor.canOutdent(blocks, "a"))
    }

    @Test
    fun `blocks that can't be added again are never moved`() {
        val image = Block("img", "9", BlockType.UNSUPPORTED, depth = 1, rawType = "image")
        val blocks = listOf(n("a", 0), n("b", 0), image)
        assertFalse(editor.canIndent(blocks, "b"))
        assertFalse(editor.canChangeType(blocks, "b"))
        // Code blocks and dividers can't hold nested blocks.
        assertFalse(editor.canIndent(listOf(n("a", 0, BlockType.CODE), n("b", 0)), "b"))
    }

    @Test
    fun `enter on a block with nested blocks adds its first nested block`() {
        val blocks = listOf(n("a", 0, BlockType.TOGGLE, "Toggle"), n("b", 1, BlockType.PARAGRAPH))
        val result = editor.changeText(blocks, "a", "Toggle\n")
        assertEquals(listOf("a", "n1", "b"), result.blocks.map { it.key })
        assertEquals(1, result.blocks[1].depth)
        assertEquals(BlockType.PARAGRAPH, result.blocks[1].type)
    }

    @Test
    fun `enter on an empty nested list item takes it out one level`() {
        val blocks = listOf(n("a", 0), n("b", 1, text = ""))
        val result = editor.changeText(blocks, "b", "\n")
        assertEquals(listOf(0, 0), result.blocks.map { it.depth })
        assertEquals(BlockType.BULLETED, result.blocks[1].type)
    }

    @Test
    fun `backspace at the start of nested text takes it out first`() {
        val blocks = listOf(n("a", 0), n("b", 1, BlockType.PARAGRAPH))
        val result = editor.backspaceAtStart(blocks, "b")
        assertEquals(listOf(0, 0), result.blocks.map { it.depth })
        assertEquals(2, result.blocks.size)
    }

    @Test
    fun `deleting a block deletes what is nested in it`() {
        val blocks = listOf(n("a", 0), n("b", 1), n("c", 2), n("d", 0))
        assertEquals(listOf("d"), editor.delete(blocks, "a").blocks.map { it.key })
    }

    @Test
    fun `a block with nested blocks only becomes a kind that can hold them`() {
        val blocks = listOf(n("a", 0, BlockType.TODO), n("b", 1))
        assertEquals(BlockType.TOGGLE, editor.setType(blocks, "a", BlockType.TOGGLE).blocks[0].type)
        assertEquals(BlockType.TODO, editor.setType(blocks, "a", BlockType.CODE).blocks[0].type)
    }

    @Test
    fun `numbers count per level`() {
        val blocks = listOf(
            n("a", 0, BlockType.NUMBERED), n("b", 1, BlockType.NUMBERED), n("c", 1, BlockType.NUMBERED),
            n("d", 0, BlockType.NUMBERED), n("e", 1, BlockType.NUMBERED),
        )
        assertEquals(mapOf("a" to 1, "b" to 1, "c" to 2, "d" to 2, "e" to 1), DocumentEditor.numbers(blocks))
    }
}
