package be.wimvandonck.noter.data.sync

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentDiffTest {
    private fun synced(
        id: String,
        text: String,
        type: BlockType = BlockType.PARAGRAPH,
        checked: Boolean = false,
        depth: Int = 0,
        parent: String? = null,
    ) = Block(key = "k$id", notionId = id, type = type, text = text, checked = checked, depth = depth, parentId = parent)

    private fun new(key: String, text: String, type: BlockType = BlockType.PARAGRAPH, depth: Int = 0) =
        Block(key = key, type = type, text = text, depth = depth)

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
        assertEquals(listOf(ContentOp.Insert(null, BlockRef("k1", "1"), atStart = false, blocks = listOf(x, y))), ContentDiff.plan(base, current))
    }

    @Test
    fun `new blocks before all others go to the start`() {
        val base = listOf(synced("1", "a"))
        val x = new("x", "x")
        assertEquals(listOf(ContentOp.Insert(null, null, atStart = true, blocks = listOf(x))), ContentDiff.plan(base, listOf(x, synced("1", "a"))))
    }

    @Test
    fun `blocks in an empty page are appended`() {
        val x = new("x", "x")
        assertEquals(listOf(ContentOp.Insert(null, null, atStart = false, blocks = listOf(x))), ContentDiff.plan(emptyList(), listOf(x)))
    }

    @Test
    fun `a changed type is a delete and an insert in the same place`() {
        val base = listOf(synced("1", "a"), synced("2", "b"))
        val changed = synced("2", "b", BlockType.TODO)
        val ops = ContentDiff.plan(base, listOf(synced("1", "a"), changed))
        assertEquals(
            listOf(ContentOp.Delete("2"), ContentOp.Insert(null, BlockRef("k1", "1"), atStart = false, blocks = listOf(changed.copy(notionId = null)))),
            ops,
        )
    }

    @Test
    fun `unsupported blocks are never written`() {
        val image = Block("img", "9", BlockType.UNSUPPORTED, "", rawType = "image")
        val base = listOf(image)
        assertTrue(ContentDiff.plan(base, listOf(image.copy(text = "changed"))).isEmpty())
    }

    @Test
    fun `nested blocks find their parent`() {
        val blocks = listOf(new("a", "a"), new("b", "b", depth = 1), new("c", "c", depth = 2), new("d", "d", depth = 1), new("e", "e"))
        assertEquals(listOf(null, 0, 1, 0, null), ContentDiff.parentIndexes(blocks))
    }

    @Test
    fun `a new item inside a toggle goes into the toggle`() {
        val toggle = synced("t", "Toggle", BlockType.TOGGLE)
        val child = synced("c", "Inside", depth = 1, parent = "t")
        val x = new("x", "New", depth = 1)
        val ops = ContentDiff.plan(listOf(toggle, child), listOf(toggle, child, x))
        assertEquals(listOf(ContentOp.Insert(BlockRef("kt", "t"), BlockRef("kc", "c"), atStart = false, blocks = listOf(x))), ops)
    }

    @Test
    fun `the first new item of a parent with items goes to its start`() {
        val list = synced("l", "List", BlockType.BULLETED)
        val child = synced("c", "Sub", BlockType.BULLETED, depth = 1, parent = "l")
        val x = new("x", "First", BlockType.BULLETED, depth = 1)
        val ops = ContentDiff.plan(listOf(list, child), listOf(list, x, child))
        assertEquals(listOf(ContentOp.Insert(BlockRef("kl", "l"), null, atStart = true, blocks = listOf(x))), ops)
    }

    @Test
    fun `new blocks inside new blocks come after their parent`() {
        val a = new("a", "A", BlockType.TODO)
        val b = new("b", "B", BlockType.TODO, depth = 1)
        val c = new("c", "C", BlockType.TODO)
        val ops = ContentDiff.plan(emptyList(), listOf(a, b, c))
        assertEquals(
            listOf(
                ContentOp.Insert(null, null, atStart = false, blocks = listOf(a, c)),
                ContentOp.Insert(BlockRef("a", null), null, atStart = false, blocks = listOf(b)),
            ),
            ops,
        )
    }

    @Test
    fun `an indented block is recreated in its new parent, with what is inside it`() {
        val first = synced("1", "One", BlockType.BULLETED)
        val second = synced("2", "Two", BlockType.BULLETED)
        val inside = synced("3", "Three", BlockType.BULLETED, depth = 1, parent = "2")
        val current = listOf(first, second.copy(depth = 1), inside.copy(depth = 2))
        val ops = ContentDiff.plan(listOf(first, second, inside), current)
        assertEquals(
            listOf(
                // Deleting "Two" in Notion also deletes "Three".
                ContentOp.Delete("2"),
                ContentOp.Insert(BlockRef("k1", "1"), null, atStart = false, blocks = listOf(current[1].copy(notionId = null))),
                ContentOp.Insert(BlockRef("k2", null), null, atStart = false, blocks = listOf(current[2].copy(notionId = null))),
            ),
            ops,
        )
    }

    @Test
    fun `deleting a parent deletes only the parent`() {
        val toggle = synced("t", "Toggle", BlockType.TOGGLE)
        val child = synced("c", "Inside", depth = 1, parent = "t")
        assertEquals(listOf(ContentOp.Delete("t")), ContentDiff.plan(listOf(toggle, child), emptyList()))
    }
}
