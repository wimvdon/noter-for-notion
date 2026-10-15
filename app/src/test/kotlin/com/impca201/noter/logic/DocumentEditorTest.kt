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
    fun `enter on a section title adds a checkbox`() {
        val blocks = listOf(b("t", "Groceries"), b("c", "Milk", BlockType.TODO))
        val result = editor.changeText(blocks, "t", "Groceries\n")
        assertEquals(BlockType.TODO, result.blocks[1].type)
        assertEquals("", result.blocks[1].text)
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
    fun `add item goes to the end of the section`() {
        val blocks = listOf(b("t", "Shop"), b("1", "a", BlockType.TODO), b("2", "b", BlockType.TODO), b("u", "Other"))
        val result = editor.addCheckboxToSection(blocks, "1")
        assertEquals(listOf("t", "1", "2", "n1", "u"), result.blocks.map { it.key })
        assertEquals(BlockType.TODO, result.blocks[3].type)
    }

    @Test
    fun `add checkbox reuses an empty last line`() {
        val result = editor.addCheckboxAtEnd(listOf(b("a", "x"), b("e")))
        assertEquals(listOf(BlockType.PARAGRAPH, BlockType.TODO), result.blocks.map { it.type })
        assertEquals("e", result.focusKey)
    }

    @Test
    fun `blocks with nested content keep their kind`() {
        val block = Block("a", "1", BlockType.BULLETED, "x", hasChildren = true)
        assertFalse(editor.canChangeType(block))
        assertEquals(BlockType.BULLETED, editor.setType(listOf(block), "a", BlockType.TODO).blocks[0].type)
    }

    @Test
    fun `checked items move down within their section`() {
        val blocks = listOf(
            b("t", "Shop"),
            b("1", "a", BlockType.TODO, checked = true, id = "i1"),
            b("2", "b", BlockType.TODO, id = "i2"),
            b("3", "c", BlockType.TODO, checked = true, id = "i3"),
            b("u", "Other"),
        )
        val result = editor.sortCheckedToBottom(blocks)
        assertEquals(listOf("Shop", "b", "a", "c", "Other"), result.map { it.text })
        // "a" moved, so Notion gets it as a new block; "c" stayed in place.
        assertNull(result[2].notionId)
        assertEquals("i3", result[3].notionId)
    }

    @Test
    fun `delete checked removes only checked checkboxes`() {
        val blocks = listOf(b("1", "a", BlockType.TODO, checked = true), b("2", "b", BlockType.TODO), b("3", "c"))
        assertEquals(listOf("2", "3"), editor.deleteChecked(blocks).map { it.key })
    }

    @Test
    fun `numbers restart after a non-numbered line`() {
        val blocks = listOf(
            b("1", type = BlockType.NUMBERED), b("2", type = BlockType.NUMBERED), b("x"), b("3", type = BlockType.NUMBERED),
        )
        assertEquals(mapOf("1" to 1, "2" to 2, "3" to 1), DocumentEditor.numbers(blocks))
    }

    @Test
    fun `sections count their checked items`() {
        val blocks = listOf(
            b("t", "Shop"), b("1", "a", BlockType.TODO, checked = true), b("2", "b", BlockType.TODO),
            b("p", "just text"), b("q", "more text"),
        )
        assertEquals(mapOf("t" to SectionProgress(1, 2)), DocumentEditor.sections(blocks))
        assertEquals(setOf("2"), DocumentEditor.lastCheckboxes(blocks))
    }
}
