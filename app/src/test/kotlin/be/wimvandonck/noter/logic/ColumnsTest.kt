package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnsTest {
    private fun container(key: String, rawType: String, depth: Int) =
        Block(key, "id-$key", BlockType.UNSUPPORTED, rawType = rawType, depth = depth)

    private fun text(key: String, depth: Int, type: BlockType = BlockType.BULLETED) = Block(key, "id-$key", type, key, depth = depth)

    // Before the columns, a column list with two columns (the second has a nested item), then text.
    private val blocks = listOf(
        text("intro", 0, BlockType.PARAGRAPH),
        container("list", Columns.LIST, 0),
        container("col1", Columns.COLUMN, 1),
        text("a", 2),
        container("col2", Columns.COLUMN, 1),
        text("b", 2),
        text("b1", 3),
        text("after", 0, BlockType.PARAGRAPH),
    )

    @Test
    fun `columns don't indent what is in them`() {
        // The column blocks and their content line up with the text around them; only "b1",
        // nested in "b", is indented.
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1, 0), Columns.visualDepths(blocks))
    }

    @Test
    fun `a block stays in its column, but can nest inside it`() {
        val editor = DocumentEditor()
        assertFalse(editor.canOutdent(blocks, "a"))
        assertTrue(editor.canOutdent(blocks, "b1"))
        assertFalse(editor.canIndent(blocks, "a"))
        // A second item in column 2 can go into the item above it.
        val withB2 = blocks.subList(0, 7) + text("b2", 2) + blocks.subList(7, blocks.size)
        assertTrue(editor.canIndent(withB2, "b2"))
    }
}
