package be.wimvandonck.noter.data.sync

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType

/** A block an insert refers to: by its Notion ID, or by its key when it is inserted earlier in the same plan. */
data class BlockRef(val key: String, val notionId: String?)

/** One change to send to Notion to turn the synced content into the edited content. */
sealed interface ContentOp {
    data class Update(val notionId: String, val block: Block) : ContentOp

    data class Delete(val notionId: String) : ContentOp

    /**
     * Insert [blocks] into [parent] (null: the page), after the block [after]; or, when it is null,
     * at the start when the parent already has blocks before which they go ([atStart]), or at the
     * end otherwise.
     */
    data class Insert(val parent: BlockRef?, val after: BlockRef?, val atStart: Boolean, val blocks: List<Block>) : ContentOp
}

/**
 * Works out the Notion calls that turn [base] (what Notion had after the last sync) into
 * [current] (the content on this phone). Blocks are matched by their Notion ID; nesting comes
 * from [Block.depth] (a block's parent is the nearest block above it one level less deep).
 *
 * Notion can't change a block's type or move it to another parent, so such a block (and what is
 * nested in it) is deleted and inserted again. The editor never moves blocks within a parent, and
 * only lets a block be recreated when everything in it can be recreated (no images, tables or
 * content it doesn't show).
 */
object ContentDiff {
    fun plan(base: List<Block>, current: List<Block>): List<ContentOp> {
        val baseById = base.mapNotNull { b -> b.notionId?.let { it to b } }.toMap()
        val parents = parentIndexes(current)
        val deleted = mutableListOf<String>()

        // Blocks whose type or parent changed, and blocks inside a new block, become new blocks.
        val normalized = mutableListOf<Block>()
        current.forEachIndexed { i, block ->
            val old = block.notionId?.let { baseById[it] }
            val parent = parents[i]?.let { normalized[it] }
            val recreate = old != null && block.type != BlockType.UNSUPPORTED && (
                old.type != block.type ||
                    (parent != null && parent.notionId == null) ||
                    old.parentId != parent?.notionId
                )
            if (recreate) {
                deleted += old.notionId!!
                normalized += block.copy(notionId = null)
            } else {
                normalized += block
            }
        }
        val kept = normalized.mapNotNull { it.notionId }.toSet()
        base.forEach { b -> b.notionId?.let { if (it !in kept && it !in deleted) deleted += it } }

        val ops = mutableListOf<ContentOp>()
        normalized.forEach { block ->
            val id = block.notionId ?: return@forEach
            val old = baseById[id] ?: return@forEach
            if (block.type != BlockType.UNSUPPORTED && !old.sameContentAs(block)) ops += ContentOp.Update(id, block)
        }
        // Deleting a block deletes what is nested in it: only delete the outermost ones.
        val deletedSet = deleted.toSet()
        deleted.distinct().forEach { id ->
            if (ancestors(id, baseById).none { it in deletedSet }) ops += ContentOp.Delete(id)
        }
        ops += inserts(normalized, parents)
        return ops
    }

    /** For each block, the index of its parent in [blocks] (null: the page). */
    fun parentIndexes(blocks: List<Block>): List<Int?> {
        val stack = ArrayList<Int>()
        return blocks.mapIndexed { i, block ->
            while (stack.isNotEmpty() && blocks[stack.last()].depth >= block.depth) stack.removeAt(stack.lastIndex)
            val parent = stack.lastOrNull()?.takeIf { blocks[it].depth == block.depth - 1 }
            stack += i
            parent
        }
    }

    private fun ancestors(id: String, baseById: Map<String, Block>): Sequence<String> =
        generateSequence(baseById[id]?.parentId) { baseById[it]?.parentId }

    /**
     * Runs of new blocks per parent, each inserted after the sibling before it. Runs come in the
     * order of their first block, so a new parent is always inserted before what goes in it.
     */
    private fun inserts(blocks: List<Block>, parents: List<Int?>): List<ContentOp.Insert> {
        class Run(val first: Int, val parent: Int?, val after: Block?, val atStart: Boolean, val blocks: MutableList<Block>)

        fun ref(b: Block) = BlockRef(b.key, b.notionId)
        val runs = mutableListOf<Run>()
        val open = mutableMapOf<Int?, Run>()
        val lastSibling = mutableMapOf<Int?, Block>()
        val keptChildren = mutableSetOf<Int?>()
        blocks.forEachIndexed { i, b -> if (b.notionId != null) keptChildren += parents[i] }

        blocks.forEachIndexed { i, block ->
            val parent = parents[i]
            if (block.notionId != null || block.type == BlockType.UNSUPPORTED) {
                open.remove(parent)
            } else {
                val run = open[parent]
                if (run != null) {
                    run.blocks += block
                } else {
                    val after = lastSibling[parent]
                    val atStart = after == null && parent in keptChildren
                    Run(i, parent, after, atStart, mutableListOf(block)).also {
                        runs += it
                        open[parent] = it
                    }
                }
            }
            lastSibling[parent] = block
        }
        return runs.sortedBy { it.first }.map { run ->
            ContentOp.Insert(run.parent?.let { ref(blocks[it]) }, run.after?.let(::ref), run.atStart, run.blocks)
        }
    }
}
