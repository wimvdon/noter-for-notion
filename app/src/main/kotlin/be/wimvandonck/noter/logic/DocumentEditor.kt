package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import java.util.UUID

/** The content after an edit, and where the cursor goes. */
data class EditResult(
    val blocks: List<Block>,
    val focusKey: String? = null,
    val cursor: Int? = null,
)

/**
 * The note editor's rules, on a plain list of [Block]s. They follow Notion's own editor:
 * - Enter splits a block; in a list (bullets, numbers, to-dos) the new line is the same kind,
 *   otherwise it is text. Enter on an empty list item, quote, callout or toggle turns it into
 *   text. In a code block Enter is a new line inside the code.
 * - Markdown at the start of a text line changes its kind, as in Notion: `# `, `## `, `### `, `#### `,
 *   `- `, `* `, `1. `, `[] `, `[ ] `, `[x] `, `> ` (toggle), `" ` (quote), ```` ``` ```` (code)
 *   and `---` (divider).
 * - Backspace at the start turns a block into text, then moves it out of the block it is nested
 *   in, then joins it with the line above.
 * - Nesting: a block's children follow it one level deeper ([Block.depth]). Indent puts a block
 *   (with what is inside it) in the block above it, outdent takes it out. Notion can't move blocks,
 *   so a moved block is added again; that is only allowed when everything moved can be added again
 *   (text and dividers, no content the app doesn't show). Enter on a block with nested blocks
 *   adds the new line as its first nested block.
 */
class DocumentEditor(private val newKey: () -> String = { UUID.randomUUID().toString() }) {

    fun newBlock(type: BlockType = BlockType.PARAGRAPH, text: String = ""): Block =
        Block(key = newKey(), type = type, text = text)

    /** The user typed in block [key]; [newText] is the field's whole text, [cursor] where the cursor is. */
    fun changeText(blocks: List<Block>, key: String, newText: String, cursor: Int = newText.length): EditResult {
        val index = blocks.indexOfFirst { it.key == key }
        if (index < 0) return EditResult(blocks)
        val block = blocks[index]
        if (!block.type.hasText) return EditResult(blocks)

        if (newText.contains('\n') && block.type != BlockType.CODE) return splitLines(blocks, index, newText)

        val kids = hasKids(blocks, index)
        if (block.type == BlockType.PARAGRAPH && !kids && newText == DIVIDER_SHORTCUT) {
            return insertDivider(blocks.removeAtIndex(index), null, at = index, depth = block.depth)
        }
        val shortcut = if (block.type == BlockType.PARAGRAPH && !kids) shortcutFor(newText) else null
        if (shortcut != null) {
            val (type, checked, prefixLength) = shortcut
            val text = newText.substring(prefixLength)
            val updated = block.copy(type = type, checked = checked, text = text)
            return EditResult(blocks.replace(index, updated), key, (cursor - prefixLength).coerceIn(0, text.length))
        }
        return EditResult(blocks.replace(index, block.copy(text = newText)), key, cursor)
    }

    private fun splitLines(blocks: List<Block>, index: Int, newText: String): EditResult {
        val block = blocks[index]
        val kids = hasKids(blocks, index)
        // Enter on an empty list item, quote, … : take it out of its parent, or turn it into text.
        if (newText == "\n" && block.text.isEmpty() && block.type != BlockType.PARAGRAPH && !kids) {
            if (block.depth > 0 && canOutdent(blocks, block.key)) return outdent(blocks, block.key)
            return EditResult(blocks.replace(index, block.copy(type = BlockType.PARAGRAPH, checked = false, toggleable = false)), block.key, 0)
        }
        val lines = newText.split('\n')
        val first = block.copy(text = lines.first())
        // A block with nested blocks gets the new lines as its first nested blocks, as in Notion.
        val nextType = if (kids) continuationType(block.type).takeIf { it.isListItem } ?: BlockType.PARAGRAPH else continuationType(block.type)
        val depth = if (kids) block.depth + 1 else block.depth
        val added = lines.drop(1).map { newBlock(nextType, it).copy(depth = depth) }
        val result = blocks.toMutableList().apply {
            set(index, first)
            addAll(index + 1, added)
        }
        // The cursor goes to the start of the text that moved down (Enter in the middle of a line).
        val last = added.last()
        return EditResult(result, last.key, if (added.size == 1) 0 else last.text.length)
    }

    /** What a new line after a block of [type] becomes. */
    fun continuationType(type: BlockType): BlockType = if (type.isListItem) type else BlockType.PARAGRAPH

    /** Backspace at the very start of block [key]. */
    fun backspaceAtStart(blocks: List<Block>, key: String): EditResult {
        val index = blocks.indexOfFirst { it.key == key }
        if (index < 0) return EditResult(blocks)
        val block = blocks[index]
        if (!block.type.hasText) return EditResult(blocks)
        val kids = hasKids(blocks, index)
        if (block.type != BlockType.PARAGRAPH && !kids) {
            return EditResult(blocks.replace(index, block.copy(type = BlockType.PARAGRAPH, checked = false, toggleable = false)), key, 0)
        }
        if (block.depth > 0 && canOutdent(blocks, key)) return outdent(blocks, key).copy(cursor = 0)
        if (kids || block.hasChildren) return EditResult(blocks, key, 0)
        val previousIndex = index - 1
        val previous = blocks.getOrNull(previousIndex) ?: return EditResult(blocks, key, 0)
        if (previous.type == BlockType.DIVIDER) {
            // The line above is a divider: remove the divider, as Notion does.
            return EditResult(blocks.removeAtIndex(previousIndex), key, 0)
        }
        if (previous.type == BlockType.UNSUPPORTED) {
            // Can't join into an image or table; only remove this line when it is empty.
            return if (block.text.isEmpty()) EditResult(blocks.removeAtIndex(index), null) else EditResult(blocks, key, 0)
        }
        val joined = previous.copy(text = previous.text + block.text)
        val result = blocks.toMutableList().apply {
            set(previousIndex, joined)
            removeAt(index)
        }
        return EditResult(result, previous.key, previous.text.length)
    }

    fun setType(blocks: List<Block>, key: String, type: BlockType): EditResult {
        val index = blocks.indexOfFirst { it.key == key }
        if (index < 0) return EditResult(blocks)
        val block = blocks[index]
        if (!canChangeType(blocks, key) || !type.hasText) return EditResult(blocks, key)
        val toggleable = block.toggleable && type.isHeading
        // Notion only nests blocks in some kinds of blocks.
        if (hasKids(blocks, index) && !canNest(type, toggleable)) return EditResult(blocks, key)
        val updated = block.copy(type = type, checked = if (type == BlockType.TODO) block.checked else false, toggleable = toggleable)
        return EditResult(blocks.replace(index, updated), key)
    }

    /**
     * Notion can't change a block's kind; the app adds it again, with what is nested in it. That
     * works when everything nested can be added again.
     */
    fun canChangeType(blocks: List<Block>, key: String): Boolean {
        val index = blocks.indexOfFirst { it.key == key }
        if (index < 0) return false
        val block = blocks[index]
        return block.type.hasText && movable(blocks, index)
    }

    /** True when [block] can change its kind (without looking at blocks nested in it). */
    fun canChangeType(block: Block): Boolean = block.type.hasText && !block.hasChildren

    fun toggleChecked(blocks: List<Block>, key: String): List<Block> =
        blocks.map { if (it.key == key && it.type == BlockType.TODO) it.copy(checked = !it.checked) else it }

    /** Deletes block [key] with what is nested in it (as Notion does). */
    fun delete(blocks: List<Block>, key: String): EditResult {
        val index = blocks.indexOfFirst { it.key == key }
        if (index < 0) return EditResult(blocks)
        val end = subtreeEnd(blocks, index)
        return EditResult(blocks.subList(0, index) + blocks.subList(end, blocks.size), null)
    }

    // ---- Nesting ----

    /** True when block [key] can go into the block above it (Notion's Tab). */
    fun canIndent(blocks: List<Block>, key: String): Boolean {
        val index = blocks.indexOfFirst { it.key == key }
        if (index <= 0) return false
        val parent = previousSibling(blocks, index) ?: return false
        return canNest(parent.type, parent.toggleable) && !parent.hasChildren && movable(blocks, index)
    }

    /** Puts block [key] (with what is nested in it) in the block above it, as its last nested block. */
    fun indent(blocks: List<Block>, key: String): EditResult {
        if (!canIndent(blocks, key)) return EditResult(blocks, key)
        val index = blocks.indexOfFirst { it.key == key }
        return EditResult(shiftDepth(blocks, index, subtreeEnd(blocks, index), 1), key)
    }

    /**
     * True when block [key] can come out of the block it is in (Notion's Shift+Tab). The blocks after
     * it in the same parent become nested in it, so they must be movable too.
     */
    fun canOutdent(blocks: List<Block>, key: String): Boolean {
        val index = blocks.indexOfFirst { it.key == key }
        if (index < 0 || blocks[index].depth == 0) return false
        val block = blocks[index]
        // A block in a column stays in it: out of it, it would sit directly in the column list.
        if (Columns.parentOf(blocks, index)?.let(Columns::isContainer) == true) return false
        val end = siblingsEnd(blocks, index)
        val takesSiblings = end > subtreeEnd(blocks, index)
        if (takesSiblings && (!canNest(block.type, block.toggleable) || block.hasChildren)) return false
        return (index until end).all { blocks[it].movableAlone() }
    }

    /** Takes block [key] out of the block it is in; the blocks after it in that block go into it. */
    fun outdent(blocks: List<Block>, key: String): EditResult {
        if (!canOutdent(blocks, key)) return EditResult(blocks, key)
        val index = blocks.indexOfFirst { it.key == key }
        return EditResult(shiftDepth(blocks, index, subtreeEnd(blocks, index), -1), key)
    }

    private fun shiftDepth(blocks: List<Block>, from: Int, to: Int, by: Int): List<Block> =
        blocks.mapIndexed { i, b -> if (i in from until to) b.copy(depth = (b.depth + by).coerceAtLeast(0)) else b }

    /** The index after block [index] and everything nested in it. */
    private fun subtreeEnd(blocks: List<Block>, index: Int): Int {
        val depth = blocks[index].depth
        var end = index + 1
        while (end < blocks.size && blocks[end].depth > depth) end++
        return end
    }

    /** The index after the last block of [index]'s parent (its later siblings and their nested blocks). */
    private fun siblingsEnd(blocks: List<Block>, index: Int): Int {
        val depth = blocks[index].depth
        var end = index + 1
        while (end < blocks.size && blocks[end].depth >= depth) end++
        return end
    }

    /** The block before [index] in the same parent, if any. */
    private fun previousSibling(blocks: List<Block>, index: Int): Block? {
        val depth = blocks[index].depth
        for (i in index - 1 downTo 0) {
            if (blocks[i].depth == depth) return blocks[i]
            if (blocks[i].depth < depth) return null
        }
        return null
    }

    /** True when block [index] has nested blocks (shown here, or only in Notion). */
    fun hasKids(blocks: List<Block>, index: Int): Boolean =
        blocks[index].hasChildren || blocks.getOrNull(index + 1)?.let { it.depth > blocks[index].depth } == true

    /** True when block [index] and everything nested in it can be added again. */
    private fun movable(blocks: List<Block>, index: Int): Boolean = (index until subtreeEnd(blocks, index)).all { blocks[it].movableAlone() }

    private fun Block.movableAlone(): Boolean = (type.hasText || type == BlockType.DIVIDER) && !hasChildren

    /**
     * Adds an empty block of [type] after block [afterKey] (after what is nested in it, at its
     * level), or at the end when it is null.
     */
    fun insertAfter(blocks: List<Block>, afterKey: String?, type: BlockType): EditResult {
        if (type == BlockType.DIVIDER) return insertDivider(blocks, afterKey)
        val index = afterKey?.let { k -> blocks.indexOfFirst { it.key == k } } ?: -1
        val block = newBlock(type).copy(depth = if (index < 0) 0 else blocks[index].depth)
        val at = if (index < 0) blocks.size else subtreeEnd(blocks, index)
        val result = blocks.toMutableList().apply { add(at, block) }
        return EditResult(result, block.key, 0)
    }

    /**
     * Adds a divider after block [afterKey] (or at position [at], or at the end) with an empty
     * text line below it to carry on typing.
     */
    fun insertDivider(blocks: List<Block>, afterKey: String?, at: Int? = null, depth: Int? = null): EditResult {
        val afterIndex = afterKey?.let { k -> blocks.indexOfFirst { it.key == k } }?.takeIf { it >= 0 }
        val level = depth ?: afterIndex?.let { blocks[it].depth } ?: 0
        val divider = newBlock(BlockType.DIVIDER).copy(depth = level)
        val line = newBlock().copy(depth = level)
        val index = when {
            afterIndex != null -> subtreeEnd(blocks, afterIndex)
            at != null -> at.coerceIn(0, blocks.size)
            else -> blocks.size
        }
        val result = blocks.toMutableList().apply { addAll(index, listOf(divider, line)) }
        return EditResult(result, line.key, 0)
    }

    /**
     * Puts the cursor on a new text line at the end of the note (tapping below the content). An
     * empty text line at the end is reused, so tapping twice doesn't leave empty lines behind.
     */
    fun addLineAtEnd(blocks: List<Block>): EditResult {
        val last = blocks.lastOrNull()
        if (last != null && last.type == BlockType.PARAGRAPH && last.text.isEmpty() && !last.hasChildren && last.depth == 0) {
            return EditResult(blocks, last.key, 0)
        }
        return insertAfter(blocks, null, BlockType.PARAGRAPH)
    }

    companion object {
        private val NUMBERED_PREFIX = Regex("""^\d+[.)] """)
        const val DIVIDER_SHORTCUT = "---"

        /** (type, checked, prefix length) for Markdown typed at the start of a line. */
        fun shortcutFor(text: String): Triple<BlockType, Boolean, Int>? = when {
            text.startsWith("#### ") -> Triple(BlockType.HEADING_4, false, 5)
            text.startsWith("### ") -> Triple(BlockType.HEADING_3, false, 4)
            text.startsWith("## ") -> Triple(BlockType.HEADING_2, false, 3)
            text.startsWith("# ") -> Triple(BlockType.HEADING_1, false, 2)
            text.startsWith("[] ") -> Triple(BlockType.TODO, false, 3)
            text.startsWith("[ ] ") -> Triple(BlockType.TODO, false, 4)
            text.startsWith("[x] ") || text.startsWith("[X] ") -> Triple(BlockType.TODO, true, 4)
            text.startsWith("- ") || text.startsWith("* ") || text.startsWith("• ") -> Triple(BlockType.BULLETED, false, 2)
            text.startsWith("> ") -> Triple(BlockType.TOGGLE, false, 2)
            text.startsWith("\" ") || text.startsWith("“ ") -> Triple(BlockType.QUOTE, false, 2)
            text.startsWith("```") -> Triple(BlockType.CODE, false, 3)
            NUMBERED_PREFIX.containsMatchIn(text) -> Triple(BlockType.NUMBERED, false, NUMBERED_PREFIX.find(text)!!.value.length)
            else -> null
        }

        /** Kinds of blocks Notion can nest blocks in (headings only when they fold). */
        fun canNest(type: BlockType, toggleable: Boolean = false): Boolean = when (type) {
            BlockType.PARAGRAPH, BlockType.BULLETED, BlockType.NUMBERED, BlockType.TODO,
            BlockType.TOGGLE, BlockType.QUOTE, BlockType.CALLOUT -> true
            BlockType.HEADING_1, BlockType.HEADING_2, BlockType.HEADING_3, BlockType.HEADING_4 -> toggleable
            else -> false
        }

        /** The number shown in front of each numbered list item (counted per level). */
        fun numbers(blocks: List<Block>): Map<String, Int> {
            val result = mutableMapOf<String, Int>()
            val counters = mutableListOf<Int>()
            blocks.forEach { block ->
                val d = block.depth
                while (counters.size <= d) counters += 0
                // A block at this level ends the lists nested deeper.
                for (i in d + 1 until counters.size) counters[i] = 0
                if (block.type == BlockType.NUMBERED) {
                    counters[d]++
                    result[block.key] = counters[d]
                } else {
                    counters[d] = 0
                }
            }
            return result
        }

        /** A short preview of a note's content for lists. */
        fun preview(blocks: List<Block>, maxLines: Int = 3): String =
            blocks.asSequence()
                .filter { it.type.hasText && it.text.isNotBlank() }
                .take(maxLines)
                .joinToString("\n") { block ->
                    val text = if (block.type == BlockType.CODE) block.text else be.wimvandonck.noter.data.notion.InlineMarkdown.stripped(block.text)
                    when (block.type) {
                        BlockType.TODO -> (if (block.checked) "☑ " else "☐ ") + text
                        BlockType.BULLETED -> "• $text"
                        else -> text
                    }
                }
    }
}

private fun <T> List<T>.replace(index: Int, item: T): List<T> = toMutableList().apply { set(index, item) }

private fun <T> List<T>.removeAtIndex(index: Int): List<T> = toMutableList().apply { removeAt(index) }
