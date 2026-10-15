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

/** Progress of a checklist section: a text block followed by checkboxes. */
data class SectionProgress(val done: Int, val total: Int)

/**
 * The note editor's rules, on a plain list of [Block]s:
 * - Enter splits a block; in a list (bullets, numbers, checkboxes) the new line is the same kind.
 *   Enter on an empty list item ends the list. Under a section title Enter adds a checkbox.
 * - Markdown at the start of a text block changes its kind: `# `, `## `, `### `, `- `, `* `,
 *   `1. `, `[] `, `[ ] `, `[x] `.
 * - Backspace at the start turns a list item or heading into text, then joins it with the line above.
 * - A section is a text block (no checkbox) followed by checkboxes. "Add item" adds a checkbox at
 *   the end of the section.
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

        if (newText.contains('\n')) return splitLines(blocks, index, newText)

        val shortcut = if (block.type == BlockType.PARAGRAPH && !block.hasChildren) shortcutFor(newText) else null
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
        return when {
            block.type.isListItem -> block.type
            // A section title: the next line is a checkbox when the section has checkboxes.
            block.type == BlockType.PARAGRAPH || block.type.isHeading ->
                if (blocks.getOrNull(index + 1)?.type == BlockType.TODO) BlockType.TODO else BlockType.PARAGRAPH
            else -> BlockType.PARAGRAPH
        }
    }

    /** Backspace at the very start of block [key]. */
    fun backspaceAtStart(blocks: List<Block>, key: String): EditResult {
        val index = blocks.indexOfFirst { it.key == key }
        if (index < 0) return EditResult(blocks)
        val block = blocks[index]
        if (block.type == BlockType.UNSUPPORTED) return EditResult(blocks)
        if (block.type != BlockType.PARAGRAPH && !block.hasChildren) {
            return EditResult(blocks.replace(index, block.copy(type = BlockType.PARAGRAPH, checked = false)), key, 0)
        }
        if (block.hasChildren) return EditResult(blocks, key, 0)
        val previousIndex = index - 1
        val previous = blocks.getOrNull(previousIndex) ?: return EditResult(blocks, key, 0)
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
        if (!canChangeType(block) || type == BlockType.UNSUPPORTED) return EditResult(blocks, key)
        val updated = block.copy(type = type, checked = if (type == BlockType.TODO) block.checked else false)
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

    /**
     * Adds a checkbox at the end of the note. An empty text line at the end is reused, so tapping
     * "Add checkbox" twice doesn't leave empty lines behind.
     */
    fun addCheckboxAtEnd(blocks: List<Block>): EditResult {
        val last = blocks.lastOrNull()
        if (last != null && last.type == BlockType.PARAGRAPH && last.text.isEmpty() && !last.hasChildren) {
            return EditResult(blocks.replace(blocks.lastIndex, last.copy(type = BlockType.TODO)), last.key, 0)
        }
        return insertAfter(blocks, null, BlockType.TODO)
    }

    /** Adds a checkbox after the last checkbox of the run of checkboxes that contains [key]. */
    fun addCheckboxToSection(blocks: List<Block>, key: String): EditResult {
        var index = blocks.indexOfFirst { it.key == key }
        if (index < 0) return EditResult(blocks)
        while (blocks.getOrNull(index + 1)?.type == BlockType.TODO) index++
        return insertAfter(blocks, blocks[index].key, BlockType.TODO)
    }

    /**
     * Moves checked checkboxes below the unchecked ones within each section. Notion can't move a
     * block, so a checkbox that moves becomes a new block (and the old one is deleted on sync).
     */
    fun sortCheckedToBottom(blocks: List<Block>): List<Block> {
        val result = mutableListOf<Block>()
        var run = mutableListOf<Block>()
        fun flush() {
            val lastUnchecked = run.indexOfLast { !it.checked }
            val moved = run.withIndex().filter { it.value.checked }.map { (i, b) ->
                if (i < lastUnchecked) b.copy(key = newKey(), notionId = null) else b
            }
            result += run.filter { !it.checked } + moved
            run = mutableListOf()
        }
        blocks.forEach { if (it.type == BlockType.TODO && !it.hasChildren) run += it else { flush(); result += it } }
        flush()
        return result
    }

    /** Removes every checked checkbox. */
    fun deleteChecked(blocks: List<Block>): List<Block> = blocks.filterNot { it.type == BlockType.TODO && it.checked }

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

        /** For each section title (text or heading directly followed by checkboxes): its progress. */
        fun sections(blocks: List<Block>): Map<String, SectionProgress> {
            val result = mutableMapOf<String, SectionProgress>()
            blocks.forEachIndexed { index, block ->
                val isTitle = block.type == BlockType.PARAGRAPH || block.type.isHeading
                if (!isTitle || blocks.getOrNull(index + 1)?.type != BlockType.TODO) return@forEachIndexed
                val todos = blocks.drop(index + 1).takeWhile { it.type == BlockType.TODO }
                result[block.key] = SectionProgress(todos.count { it.checked }, todos.size)
            }
            return result
        }

        /** Keys of the last checkbox of each run of checkboxes (where "Add item" goes). */
        fun lastCheckboxes(blocks: List<Block>): Set<String> =
            blocks.filterIndexed { index, block ->
                block.type == BlockType.TODO && blocks.getOrNull(index + 1)?.type != BlockType.TODO
            }.map { it.key }.toSet()

        /** A short preview of a note's content for lists. */
        fun preview(blocks: List<Block>, maxLines: Int = 3): String =
            blocks.asSequence()
                .filter { it.type != BlockType.UNSUPPORTED && it.text.isNotBlank() }
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
