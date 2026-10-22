package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block

/**
 * The titles of a page's subpages and databases (Notion's `child_page` and `child_database`
 * blocks). Renaming a subpage doesn't change its parent's edit time, so a copy of the parent that
 * is otherwise up to date can still have old titles: they are asked again each time it opens.
 */
object SubpageTitles {
    private val types = setOf("child_page", "child_database")

    /** The page itself (not something nested) has a subpage or database. */
    fun any(blocks: List<Block>): Boolean = blocks.any { it.depth == 0 && it.rawType in types }

    /** [blocks] with the titles Notion has now ([titles], by block ID); null when none changed. */
    fun renamed(blocks: List<Block>, titles: Map<String, String>): List<Block>? {
        var changed = false
        val result = blocks.map { block ->
            val title = block.notionId?.let { titles[it] }
            if (block.rawType in types && title != null && title != block.text) {
                changed = true
                block.copy(text = title)
            } else {
                block
            }
        }
        return result.takeIf { changed }
    }
}
