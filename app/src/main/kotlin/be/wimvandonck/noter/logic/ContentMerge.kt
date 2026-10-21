package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block

/**
 * Edits made on this phone while a newer version of the note was on its way from Notion, put on
 * top of that newer version. Without this the edits (made on the older copy) would be saved as
 * the whole note: blocks added in Notion meanwhile would be deleted and blocks changed there set
 * back.
 *
 * Blocks are matched by their key: a block downloaded from Notion keeps the key it had on this
 * phone (BlockMatch.keysFor), so [start], [local] and [remote] share keys.
 */
object ContentMerge {
    /**
     * [remote] with the changes that turned [start] (the copy the editor opened) into [local] (the
     * editor now): blocks changed or moved here take their version from here, blocks deleted here
     * go, and blocks added here go in after the block before them here. A block changed here but
     * deleted in Notion is kept, as a new block. Everything else is as Notion has it.
     */
    fun merge(start: List<Block>, local: List<Block>, remote: List<Block>): List<Block> {
        val startByKey = start.associateBy { it.key }
        val localByKey = local.associateBy { it.key }
        val remoteKeys = remote.map { it.key }.toSet()
        val deletedHere = startByKey.keys - localByKey.keys

        val merged = remote.filter { it.key !in deletedHere }.map { block ->
            val mine = localByKey[block.key]
            val before = startByKey[block.key]
            if (mine != null && before != null && mine != before) mine.copy(notionId = block.notionId) else block
        }.toMutableList()

        local.forEachIndexed { i, block ->
            if (block.key in remoteKeys && block.key !in deletedHere) return@forEachIndexed
            val before = startByKey[block.key]
            // Kept here but gone from Notion, unchanged here: Notion's delete stands.
            if (before != null && before == block) return@forEachIndexed
            // Added here, or changed here but deleted in Notion: a new block after the one before it here.
            val added = if (before != null) block.copy(notionId = null) else block
            val previous = (i - 1 downTo 0).map { local[it].key }.firstOrNull { key -> merged.any { it.key == key } }
            var at = if (previous == null) 0 else merged.indexOfFirst { it.key == previous } + 1
            // Not between a block and what Notion nested in it meanwhile (it would become theirs).
            while (previous != null && at < merged.size && merged[at].depth > added.depth) at++
            merged.add(at, added)
        }
        return merged
    }
}
