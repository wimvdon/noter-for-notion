package be.wimvandonck.noter.data.sync

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockMatchTest {
    private fun todo(key: String, text: String, id: String? = null) = Block(key, id, BlockType.TODO, text)

    @Test
    fun `blocks without an ID take the IDs of matching new blocks in Notion, in order`() {
        val local = listOf(todo("a", "Milk", "n1"), todo("b", "Eggs"), todo("c", "Bread"))
        val remote = listOf(todo("x", "Milk", "n1"), todo("y", "Eggs", "n2"), todo("z", "Bread", "n3"))

        val adopted = BlockMatch.adopt(local, remote, knownIds = setOf("n1"))

        assertEquals(listOf("n1", "n2", "n3"), adopted.map { it.notionId })
        assertEquals(listOf("a", "b", "c"), adopted.map { it.key })
    }

    @Test
    fun `known blocks and blocks with other text are never taken`() {
        val local = listOf(todo("b", "Eggs"))
        val remote = listOf(todo("x", "Eggs", "known"), todo("y", "Eggs!", "n2"))

        assertEquals(listOf(null), BlockMatch.adopt(local, remote, knownIds = setOf("known")).map { it.notionId })
    }

    @Test
    fun `each block in Notion is taken once`() {
        val local = listOf(todo("a", "Item"), todo("b", "Item"))
        val remote = listOf(todo("x", "Item", "n1"))

        assertEquals(listOf("n1", null), BlockMatch.adopt(local, remote, emptySet()).map { it.notionId })
    }

    @Test
    fun `downloaded blocks keep the keys they have on this phone`() {
        val previous = listOf(todo("a", "Milk", "n1"), todo("b", "Eggs"))
        val remote = listOf(todo("n1", "Milk", "n1"), todo("n2", "Eggs", "n2"), todo("n3", "Other", "n3"))

        assertEquals(mapOf("n1" to "a", "n2" to "b"), BlockMatch.keysFor(previous, remote))
    }

    @Test
    fun `the editor's blocks keep the IDs Notion gave them`() {
        val stored = listOf(todo("a", "Milk", "n1"), todo("b", "Eggs", "n2"))
        val fromEditor = listOf(todo("a", "Milk"), todo("b", "Eggs and ham"), todo("c", "New"))

        assertEquals(listOf("n1", "n2", null), BlockMatch.keepIds(fromEditor, stored).map { it.notionId })
    }

    @Test
    fun `a block added again with a new kind takes its new ID`() {
        val stored = listOf(Block("a", "new-id", BlockType.TODO, "Milk"))
        val fromEditor = listOf(Block("a", "old-id", BlockType.TODO, "Milk"))

        assertEquals("new-id", BlockMatch.keepIds(fromEditor, stored).single().notionId)
    }
}
