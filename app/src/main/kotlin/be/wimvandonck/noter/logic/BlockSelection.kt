package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType

/**
 * Selecting several lines of a note at once (to copy or delete them): each block is its own text
 * field, so lines are selected as whole blocks.
 */
object BlockSelection {
    /** Text blocks and dividers can be selected; blocks the editor only shows can't. */
    fun selectable(block: Block): Boolean = block.type.hasText || block.type == BlockType.DIVIDER

    /** [selected] with [key] switched on or off. */
    fun toggle(selected: Set<String>, key: String): Set<String> = if (key in selected) selected - key else selected + key

    /** Every line that can be selected. */
    fun all(blocks: List<Block>): Set<String> = blocks.filter { selectable(it) }.map { it.key }.toSet()

    /**
     * The selected lines, in the order of the note, as Markdown text to copy: headings with #,
     * lists with their marker, to-dos with a box, quotes with >, nested lines indented.
     */
    fun asText(blocks: List<Block>, selected: Set<String>): String {
        val numbers = DocumentEditor.numbers(blocks)
        return blocks.filter { it.key in selected }.joinToString("\n") { block ->
            val text = block.text
            val line = when (block.type) {
                BlockType.HEADING_1 -> "# $text"
                BlockType.HEADING_2 -> "## $text"
                BlockType.HEADING_3 -> "### $text"
                BlockType.HEADING_4 -> "#### $text"
                BlockType.BULLETED -> "- $text"
                BlockType.NUMBERED -> "${numbers[block.key] ?: 1}. $text"
                BlockType.TODO -> (if (block.checked) "- [x] " else "- [ ] ") + text
                BlockType.QUOTE -> "> $text"
                BlockType.CODE -> "```\n$text\n```"
                BlockType.DIVIDER -> "---"
                else -> text
            }
            if (block.type == BlockType.CODE) line else "  ".repeat(block.depth) + line
        }
    }
}
