package com.impca201.noter.data.sync

import com.impca201.noter.data.model.Block

/**
 * Ties blocks on this phone to the blocks Notion has, so a block is never sent twice.
 *
 * A block typed on this phone has no Notion ID until Notion answers that it was added. When that
 * answer is lost (a timeout, a server error, an answer the app can't read) Notion may still have
 * added the block. Sending it again would add it twice, so the content is downloaded first and
 * each block without an ID takes the ID of a block Notion has that the phone didn't know yet,
 * with the same kind and text, in order.
 */
object BlockMatch {
    /**
     * [local] with Notion IDs filled in from [remote] for blocks that have none. Only remote blocks
     * not known on this phone ([knownIds] and the IDs [local] already has) are taken, each at most
     * once, and only in the same order as on this phone.
     */
    fun adopt(local: List<Block>, remote: List<Block>, knownIds: Set<String>): List<Block> {
        val taken = local.mapNotNull { it.notionId }.toMutableSet().apply { addAll(knownIds) }
        val candidates = remote.filter { it.notionId != null && it.notionId !in taken }
        var next = 0
        return local.map { block ->
            if (block.notionId != null) return@map block
            val index = (next until candidates.size).firstOrNull { candidates[it].sameContentAs(block) }
                ?: return@map block
            next = index + 1
            block.copy(notionId = candidates[index].notionId)
        }
    }

    /**
     * Keys for blocks downloaded from Notion: a block keeps the key it has on this phone (matched by
     * its Notion ID, else by [adopt]), so the editor keeps its place and later edits find it.
     */
    fun keysFor(previous: List<Block>, remote: List<Block>): Map<String, String> {
        val byId = previous.mapNotNull { b -> b.notionId?.let { it to b.key } }.toMap().toMutableMap()
        adopt(previous, remote, emptySet()).zip(previous).forEach { (adopted, old) ->
            if (old.notionId == null && adopted.notionId != null) byId[adopted.notionId] = old.key
        }
        return byId
    }

    /**
     * [blocks] with the Notion IDs that [stored] has for the same keys. The editor keeps its own
     * copy of the blocks and doesn't learn the IDs Notion gives new blocks (or a block that was
     * added again with a new kind); without this the block would lose its ID at the next edit, and
     * be deleted and added again, or its edits would go to a block Notion no longer has.
     */
    fun keepIds(blocks: List<Block>, stored: List<Block>): List<Block> {
        val storedByKey = stored.associateBy { it.key }
        val seen = mutableSetOf<String>()
        return blocks.map { block ->
            val id = if (block.key in storedByKey) storedByKey.getValue(block.key).notionId else block.notionId
            // Never two blocks with one ID (a copy of a block keeps its key only once).
            val unique = id?.takeIf { seen.add(it) }
            if (unique == block.notionId) block else block.copy(notionId = unique)
        }
    }
}
