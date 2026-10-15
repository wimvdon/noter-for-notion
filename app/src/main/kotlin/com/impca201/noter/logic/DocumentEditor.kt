package com.impca201.noter.logic

import com.impca201.noter.data.model.Block
import com.impca201.noter.data.model.BlockType
import java.util.UUID

/** The content after an edit, and where the cursor goes. */
data class EditResult(
    val blocks: List<Block>,
    val focusKey: String? = null,
    val cursor: Int? = null,
)

/**
 * The note editor's rules, on a plain list of [Block]s, following Notion's own editor:
 * - Enter splits a block; in a list (bullets, numbers, checkboxes) the new line is the same kind.
 *   Enter on an empty list item ends the list. In a code block Enter is a new line in the code.
 * - Notion's Markdown shortcuts at the start of a text block change its kind: `# `, `## `, `### `,
 *   `- `, `* `, `1. `, `[] `, `[x] `, `> ` (toggle), `" ` (quote), ```` ``` ```` (code), `---` (divider).
 * - Backspace at the start turns a block into text, then joins it with the line above.
 */
class DocumentEditor(private val newKey: () -> String = { UUID.randomUUID().toString() }) {

    fun newBlock(type: BlockType = BlockType.PARAGRAPH, text: String = ""): Block =
        Block(key = newKey(), type = type, text = text)

    /** The user typed in block [key]; [newText] is the field's whole text, [cursor] where the cursor is. */
    fun changeText(blocks: List<Block>, key: String, newText: String, cursor: Int = newText.length): EditResult {
        val index = blocks.indexOfFirst { it.key == key }
        if (index < 0) return EditResult(blocks)
        val block = blocks[index]
        if (block.type == BlockType.UNSUPPORTED) return EditResult(blocks)

        if (block.type == BlockType.CODE) return EditResult(blocks.replace(index, block.copy(text = newText)), key, cursor)
        if (newText.contains('\n')) return splitLines(blocks, index, newText)

        val shortcut = if (block.type == BlockType.PARAGRAPH && !block.hasChildren) shortcutFor(newText) else null
        if (shortcut != null) {
            val (type, checked, prefixLength) = shortcut
            val text = newText.substring(prefixLength)
            if (type == BlockType.DIVIDER) {
                // A divider has no text: what was typed after it goes on a new line below.
                val next = newBlock(BlockType.PARAGRAPH, text)
                val result = blocks.toMutableList().apply {
                    set(index, block.copy(type = BlockType.DIVIDER, text = ""))
                    add(index + 1, next)
                }
                return EditResult(result, next.key, 0)
            }
            val updated = block.copy(type = type, checked = checked, text = text, language = if (type == BlockType.CODE) "plain text" else null)
            return EditResult(blocks.replace(index, updated), key, (cursor - prefixLength).coerceIn(0, text.length))
        }
        return EditResult(blocks.replace(index, block.copy(text = newText)), key, cursor)
    }

    private fun splitLines(blocks: List<Block>, index: Int, newText: String): EditResult {
        val block = blocks[index]
        // Enter on an empty list item or heading: turn it into a text line instead of adding one.
        if (newText == "\n" && block.text.isEmpty() && block.type != BlockType.PARAGRAPH && !block.hasChildren) {
            return EditResult(blocks.replace(index, block.copy(type = BlockType.PARAGRAPH, checked = false)), block.key, 0)
        }
        val lines = newText.split('\n')
        val nextType = continuationType(blocks, index)
        val first = block.copy(text = lines.first())
        val added = lines.drop(1).map { newBlock(nextType, it) }
        val result = blocks.toMutableList().apply {
            set(index, first)
            addAll(index + 1, added)
        }
        // The cursor goes to the start of the text that moved down (Enter in the middle of a line).
        val last = added.last()
        return EditResult(result, last.key, if (added.size == 1) 0 else last.text.length)
    }

    /** What a new line after [index] becomes. */
    fun continuationType(blocks: List<Block>, index: Int): BlockType {
        val block = blocks[index]
        return if (block.type.isListItem) block.type else BlockType.PARAGRAPH
    }

    /** Backspace at the very start of block [key]. */
    fun backspaceAtStart(blocks: List<Block>, key: String): EditResult {
        val index = blocks.indexOfFirst { it.key == key }
        if (index < 0) return EditResult(blocks)
        val block = blocks[index]
        if (block.type == BlockType.UNSUPPORTED) return EditResult(blocks)
        if (block.type == BlockType.DIVIDER) return delete(blocks, key)
        if (block.type != BlockType.PARAGRAPH && !block.hasChildren) {
            return EditResult(blocks.replace(index, block.copy(type = BlockType.PARAGRAPH, checked = false)), key, 0)
        }
        if (block.hasChildren) return EditResult(blocks, key, 0)
        val previousIndex = index - 1
        val previous = blocks.getOrNull(previousIndex) ?: return EditResult(blocks, key, 0)
        if (!previous.type.hasText || previous.type == BlockType.CODE) {
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
        if (!canChangeType(block) || type == BlockType.UNSUPPORTED) return EditResult(blocks, key)
        val updated = block.copy(
            type = type,
            checked = if (type == BlockType.TODO) block.checked else false,
            text = if (type == BlockType.DIVIDER) "" else block.text,
            language = if (type == BlockType.CODE) block.language ?: "plain text" else null,
        )
        return EditResult(blocks.replace(index, updated), key)
    }

    /** Notion can't change a block's kind; the app recreates it, which would lose nested content. */
    fun canChangeType(block: Block): Boolean = block.type != BlockType.UNSUPPORTED && !block.hasChildren

    fun toggleChecked(blocks: List<Block>, key: String): List<Block> =
        blocks.map { if (it.key == key && it.type == BlockType.TODO) it.copy(checked = !it.checked) else it }

    fun delete(blocks: List<Block>, key: String): EditResult {
        val index = blocks.indexOfFirst { it.key == key }
        if (index < 0) return EditResult(blocks)
        return EditResult(blocks.removeAtIndex(index), null)
    }

    /** Adds an empty block of [type] after block [afterKey], or at the end when it is null. */
    fun insertAfter(blocks: List<Block>, afterKey: String?, type: BlockType): EditResult {
        val block = newBlock(type)
        val index = afterKey?.let { k -> blocks.indexOfFirst { it.key == k } } ?: -1
        val result = blocks.toMutableList().apply { if (index < 0) add(block) else add(index + 1, block) }
        return EditResult(result, block.key, 0)
    }

    companion object {
        private val NUMBERED_PREFIX = Regex("""^\d+[.)] """)

        /** (type, checked, prefix length) for Markdown typed at the start of a line. */
        fun shortcutFor(text: String): Triple<BlockType, Boolean, Int>? = when {
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
            text.startsWith("---") -> Triple(BlockType.DIVIDER, false, 3)
            NUMBERED_PREFIX.containsMatchIn(text) -> Triple(BlockType.NUMBERED, false, NUMBERED_PREFIX.find(text)!!.value.length)
            else -> null
        }

        /** The number shown in front of each numbered list item. */
        fun numbers(blocks: List<Block>): Map<String, Int> {
            val result = mutableMapOf<String, Int>()
            var n = 0
            blocks.forEach { block ->
                if (block.type == BlockType.NUMBERED) {
                    n++
                    result[block.key] = n
                } else {
                    n = 0
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
                    val text = com.impca201.noter.data.notion.InlineMarkdown.stripped(block.text)
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
