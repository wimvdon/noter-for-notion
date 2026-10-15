package com.impca201.noter.logic

import com.impca201.noter.data.model.Block
import com.impca201.noter.data.model.BlockType
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
    fun `the X removes a checkbox`() {
        val result = editor.delete(listOf(b("a", "Milk", BlockType.TODO), b("b", "Eggs", BlockType.TODO)), "a")
        assertEquals(listOf("b"), result.blocks.map { it.key })
        assertNull(result.focusKey)
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
    fun `notion shortcuts for toggle, quote, code and divider`() {
        fun typed(text: String) = editor.changeText(listOf(b("a")), "a", text).blocks
        assertEquals(BlockType.TOGGLE, typed("> ")[0].type)
        assertEquals(BlockType.QUOTE, typed("\" ")[0].type)
        assertEquals(BlockType.CODE, typed("```")[0].type)
        val divider = typed("---")
        assertEquals(listOf(BlockType.DIVIDER, BlockType.PARAGRAPH), divider.map { it.type })
    }

    @Test
    fun `enter in a code block is a new line in the code`() {
        val result = editor.changeText(listOf(b("a", "x", BlockType.CODE)), "a", "x\ny")
        assertEquals(listOf("x\ny"), result.blocks.map { it.text })
    }

    @Test
    fun `enter after a toggle or quote adds text`() {
        val result = editor.changeText(listOf(b("a", "x", BlockType.QUOTE)), "a", "x\n")
        assertEquals(BlockType.PARAGRAPH, result.blocks[1].type)
    }

    @Test
    fun `backspace on a divider removes it`() {
        val result = editor.backspaceAtStart(listOf(b("x", "a"), b("d", type = BlockType.DIVIDER)), "d")
        assertEquals(listOf("x"), result.blocks.map { it.key })
    }
}
