package com.impca201.noter.data.sync

import com.impca201.noter.data.model.Block
import com.impca201.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentDiffTest {
    private fun synced(id: String, text: String, type: BlockType = BlockType.PARAGRAPH, checked: Boolean = false) =
        Block(key = "k$id", notionId = id, type = type, text = text, checked = checked)

    private fun new(key: String, text: String, type: BlockType = BlockType.PARAGRAPH) = Block(key = key, type = type, text = text)

    @Test
    fun `nothing changed means nothing to send`() {
        val base = listOf(synced("1", "a"), synced("2", "b"))
        assertTrue(ContentDiff.plan(base, base).isEmpty())
    }

    @Test
    fun `changed text and checked state are updates`() {
        val base = listOf(synced("1", "a"), synced("2", "milk", BlockType.TODO))
        val current = listOf(synced("1", "a!"), synced("2", "milk", BlockType.TODO, checked = true))
        assertEquals(
            listOf(ContentOp.Update("1", current[0]), ContentOp.Update("2", current[1])),
            ContentDiff.plan(base, current),
        )
    }

    @Test
    fun `removed blocks are deleted`() {
        val base = listOf(synced("1", "a"), synced("2", "b"))
        assertEquals(listOf(ContentOp.Delete("2")), ContentDiff.plan(base, listOf(synced("1", "a"))))
    }

    @Test
    fun `new blocks are inserted after the block before them`() {
        val base = listOf(synced("1", "a"), synced("2", "b"))
        val x = new("x", "x")
        val y = new("y", "y")
        val current = listOf(synced("1", "a"), x, y, synced("2", "b"))
        assertEquals(listOf(ContentOp.Insert("1", atStart = false, blocks = listOf(x, y))), ContentDiff.plan(base, current))
    }

    @Test
    fun `new blocks before all others go to the start`() {
        val base = listOf(synced("1", "a"))
        val x = new("x", "x")
        assertEquals(listOf(ContentOp.Insert(null, atStart = true, blocks = listOf(x))), ContentDiff.plan(base, listOf(x, synced("1", "a"))))
    }

    @Test
    fun `blocks in an empty page are appended`() {
        val x = new("x", "x")
        assertEquals(listOf(ContentOp.Insert(null, atStart = false, blocks = listOf(x))), ContentDiff.plan(emptyList(), listOf(x)))
    }

    @Test
    fun `a changed type is a delete and an insert in the same place`() {
        val base = listOf(synced("1", "a"), synced("2", "b"))
        val changed = synced("2", "b", BlockType.TODO)
        val ops = ContentDiff.plan(base, listOf(synced("1", "a"), changed))
        assertEquals(
            listOf(ContentOp.Delete("2"), ContentOp.Insert("1", atStart = false, blocks = listOf(changed.copy(notionId = null)))),
            ops,
        )
    }

    @Test
    fun `unsupported blocks are never written`() {
        val image = Block("img", "9", BlockType.UNSUPPORTED, "", rawType = "image")
        val base = listOf(image)
        assertTrue(ContentDiff.plan(base, listOf(image.copy(text = "changed"))).isEmpty())
    }
}
