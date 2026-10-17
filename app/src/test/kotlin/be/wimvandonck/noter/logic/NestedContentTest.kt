package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedContentTest {
    private fun block(type: BlockType = BlockType.PARAGRAPH, hasChildren: Boolean = false, rawType: String? = null, depth: Int = 0) =
        Block(key = "k", notionId = "id", type = type, hasChildren = hasChildren, rawType = rawType, depth = depth)

    @Test
    fun `text and columns with nested content wait for it`() {
        assertTrue(NestedContent.isMissing(block(BlockType.TOGGLE, hasChildren = true)))
        assertTrue(NestedContent.isMissing(block(BlockType.UNSUPPORTED, hasChildren = true, rawType = Columns.LIST)))
        assertTrue(NestedContent.isMissing(block(BlockType.UNSUPPORTED, hasChildren = true, rawType = Columns.COLUMN)))
    }

    @Test
    fun `blocks that keep their content in Notion don't wait`() {
        assertFalse(NestedContent.isMissing(block(BlockType.UNSUPPORTED, hasChildren = true, rawType = "synced_block")))
        assertFalse(NestedContent.isMissing(block(BlockType.PARAGRAPH)))
        assertFalse(NestedContent.isMissing(block(BlockType.TOGGLE, hasChildren = true, depth = NotesRepository.MAX_DEPTH)))
    }

    @Test
    fun `state follows the copy and the download`() {
        val partial = listOf(block(), block(BlockType.UNSUPPORTED, hasChildren = true, rawType = Columns.LIST))
        assertEquals(NestedContent.State.LOADING, NestedContent.state(partial, downloading = true))
        assertEquals(NestedContent.State.NOT_LOADED, NestedContent.state(partial, downloading = false))
        assertEquals(NestedContent.State.LOADED, NestedContent.state(listOf(block()), downloading = true))
    }
}
