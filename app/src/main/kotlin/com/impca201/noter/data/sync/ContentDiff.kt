package com.impca201.noter.data.sync

import com.impca201.noter.data.model.Block
import com.impca201.noter.data.model.BlockType

/** One change to send to Notion to turn the synced content into the edited content. */
sealed interface ContentOp {
    data class Update(val notionId: String, val block: Block) : ContentOp

    data class Delete(val notionId: String) : ContentOp

    /**
     * Insert [blocks] after the block [afterNotionId], or at the start of the page when it is null
     * and the page already has blocks before which they go ([atStart]), or at the end otherwise.
     */
    data class Insert(val afterNotionId: String?, val atStart: Boolean, val blocks: List<Block>) : ContentOp
}

/**
 * Works out the Notion calls that turn [base] (what Notion had after the last sync) into
 * [current] (the content on this phone). Blocks are matched by their Notion ID.
 *
 * Notion can't change a block's type or move it, so a block whose type changed is deleted and
 * inserted again. The editor never moves blocks, and won't change the type of a block with nested
 * content (that would delete the nested content).
 */
object ContentDiff {
    fun plan(base: List<Block>, current: List<Block>): List<ContentOp> {
        val baseById = base.mapNotNull { b -> b.notionId?.let { it to b } }.toMap()
        val ops = mutableListOf<ContentOp>()
        val deletes = mutableListOf<ContentOp.Delete>()

        // Blocks whose type changed become new blocks.
        val normalized = current.map { block ->
            val old = block.notionId?.let { baseById[it] }
            if (old != null && old.type != block.type && block.type != BlockType.UNSUPPORTED) {
                deletes += ContentOp.Delete(old.notionId!!)
                block.copy(notionId = null)
            } else {
                block
            }
        }
        val kept = normalized.mapNotNull { it.notionId }.toSet()
        base.forEach { b ->
            val id = b.notionId ?: return@forEach
            if (id !in kept) deletes += ContentOp.Delete(id)
        }

        normalized.forEach { block ->
            val id = block.notionId ?: return@forEach
            val old = baseById[id] ?: return@forEach
            if (block.type != BlockType.UNSUPPORTED && !old.sameContentAs(block)) ops += ContentOp.Update(id, block)
        }
        ops += deletes.distinct()

        // Runs of new blocks, each inserted after the kept block before it.
        val anyKept = kept.isNotEmpty()
        var anchor: String? = null
        var run = mutableListOf<Block>()
        fun closeRun() {
            if (run.isNotEmpty()) {
                ops += ContentOp.Insert(anchor, atStart = anchor == null && anyKept, blocks = run)
                run = mutableListOf()
            }
        }
        normalized.forEach { block ->
            if (block.notionId == null) {
                if (block.type != BlockType.UNSUPPORTED) run += block
            } else {
                closeRun()
                anchor = block.notionId
            }
        }
        closeRun()
        return ops
    }
}
