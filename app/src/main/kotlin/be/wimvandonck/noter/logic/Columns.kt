package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block

/**
 * Notion's columns and tabs: a column list block holds column blocks, each with its own blocks;
 * a tab block holds its tabs, each a text line (the tab's name) with the tab's blocks in it. On a
 * phone the columns and tabs are shown one below the other; these blocks only hold their blocks,
 * so they don't indent them. A tab's name is shown as a label and isn't edited here (Notion only
 * allows text lines directly in a tab block).
 */
object Columns {
    const val LIST = "column_list"
    const val COLUMN = "column"
    const val TABS = "tab"

    /** A tab of a tab block (Notion's text line directly in a tab block), see [BlockCodec]. */
    const val TAB = "tab_item"

    fun isContainer(block: Block): Boolean = block.rawType in CONTAINERS

    private val CONTAINERS = setOf(LIST, COLUMN, TABS, TAB)

    /** How far each block is indented on screen: its depth, not counting the columns it is in. */
    fun visualDepths(blocks: List<Block>): List<Int> {
        val containers = ArrayList<Pair<Int, Boolean>>() // (depth, is a column block) of the blocks above
        return blocks.map { block ->
            while (containers.isNotEmpty() && containers.last().first >= block.depth) containers.removeAt(containers.lastIndex)
            val inColumns = containers.count { it.second }
            containers += block.depth to isContainer(block)
            (block.depth - inColumns).coerceAtLeast(0)
        }
    }

    /** The block's parent (the nearest block above it one level less deep), if any. */
    fun parentOf(blocks: List<Block>, index: Int): Block? {
        val depth = blocks[index].depth
        if (depth == 0) return null
        for (i in index - 1 downTo 0) if (blocks[i].depth == depth - 1) return blocks[i]
        return null
    }
}
