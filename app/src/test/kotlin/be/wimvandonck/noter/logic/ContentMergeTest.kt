package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentMergeTest {
    private fun b(key: String, text: String, depth: Int = 0, id: String? = "n-$key") = Block(key, id, BlockType.PARAGRAPH, text, depth = depth)

    private fun texts(blocks: List<Block>) = blocks.map { "  ".repeat(it.depth) + it.text }

    private val start = listOf(b("a", "A"), b("b", "B"), b("c", "C"))

    @Test
    fun `nothing changed in Notion, the edits stay as they are`() {
        val local = listOf(b("a", "A!"), b("x", "X", id = null), b("c", "C"))
        assertEquals(local, ContentMerge.merge(start, local, start))
    }

    @Test
    fun `blocks added in Notion meanwhile are kept`() {
        val local = listOf(b("a", "A edited"), b("b", "B"), b("c", "C"))
        val remote = listOf(b("a", "A"), b("b", "B"), b("r", "Added in Notion"), b("c", "C"))
        assertEquals(listOf("A edited", "B", "Added in Notion", "C"), texts(ContentMerge.merge(start, local, remote)))
    }

    @Test
    fun `blocks changed in Notion and not here keep Notion's text`() {
        val local = listOf(b("a", "A edited"), b("b", "B"), b("c", "C"))
        val remote = listOf(b("a", "A"), b("b", "B from Notion"), b("c", "C"))
        assertEquals(listOf("A edited", "B from Notion", "C"), texts(ContentMerge.merge(start, local, remote)))
    }

    @Test
    fun `blocks deleted here go, blocks deleted in Notion stay gone`() {
        val local = listOf(b("a", "A"), b("c", "C"))
        val remote = listOf(b("a", "A"), b("b", "B"))
        assertEquals(listOf("A"), texts(ContentMerge.merge(start, local, remote)))
    }

    @Test
    fun `a block changed here but deleted in Notion comes back as a new block`() {
        val local = listOf(b("a", "A"), b("b", "B edited"), b("c", "C"))
        val remote = listOf(b("a", "A"), b("c", "C"))
        val merged = ContentMerge.merge(start, local, remote)
        assertEquals(listOf("A", "B edited", "C"), texts(merged))
        assertEquals(null, merged[1].notionId)
    }

    @Test
    fun `new blocks go after the block before them here`() {
        val local = listOf(b("x", "First", id = null), b("a", "A"), b("b", "B"), b("y", "After B", id = null), b("c", "C"))
        val remote = listOf(b("r", "Notion's first"), b("a", "A"), b("b", "B"), b("c", "C"))
        assertEquals(listOf("First", "Notion's first", "A", "B", "After B", "C"), texts(ContentMerge.merge(start, local, remote)))
    }

    @Test
    fun `a new block doesn't slip in between a block and what Notion nested in it`() {
        val local = listOf(b("a", "A"), b("x", "New", id = null), b("b", "B"), b("c", "C"))
        val remote = listOf(b("a", "A"), b("child", "Nested in Notion", depth = 1), b("b", "B"), b("c", "C"))
        assertEquals(listOf("A", "  Nested in Notion", "New", "B", "C"), texts(ContentMerge.merge(start, local, remote)))
    }

    @Test
    fun `an indent made here is kept`() {
        val local = listOf(b("a", "A"), b("b", "B", depth = 1), b("c", "C"))
        assertEquals(listOf("A", "  B", "C"), texts(ContentMerge.merge(start, local, start)))
    }
}
