package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import be.wimvandonck.noter.data.notion.InlineMarkdown

/** How a line looks in [ReadingText]. */
enum class ReadingStyle { HEADING_1, HEADING_2, HEADING_3, BODY, QUOTE, CODE, DIVIDER }

/** One line of a note as text to read and select, with its list marker in front of it. */
data class ReadingLine(val key: String, val text: String, val style: ReadingStyle, val depth: Int = 0)

/**
 * A note as plain text, one line per block, for selecting text across several lines at once
 * (in the editor each block is its own text field, and a selection can't go past one).
 */
object ReadingText {
    fun lines(blocks: List<Block>): List<ReadingLine> {
        val numbers = DocumentEditor.numbers(blocks)
        return blocks.filter { it.type != BlockType.UNSUPPORTED }.map { block ->
            // Code keeps its characters as typed; elsewhere the Markdown marks go.
            val text = if (block.type == BlockType.CODE) block.text else InlineMarkdown.stripped(block.text)
            val style = when (block.type) {
                BlockType.HEADING_1 -> ReadingStyle.HEADING_1
                BlockType.HEADING_2 -> ReadingStyle.HEADING_2
                BlockType.HEADING_3, BlockType.HEADING_4 -> ReadingStyle.HEADING_3
                BlockType.QUOTE -> ReadingStyle.QUOTE
                BlockType.CODE -> ReadingStyle.CODE
                BlockType.DIVIDER -> ReadingStyle.DIVIDER
                else -> ReadingStyle.BODY
            }
            val marker = when (block.type) {
                BlockType.BULLETED -> "• "
                BlockType.NUMBERED -> "${numbers[block.key] ?: 1}. "
                BlockType.TODO -> if (block.checked) "☑ " else "☐ "
                else -> ""
            }
            ReadingLine(block.key, if (block.type == BlockType.DIVIDER) "" else marker + text, style, block.depth)
        }
    }
}
