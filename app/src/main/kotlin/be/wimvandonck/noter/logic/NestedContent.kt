package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType

/**
 * Whether what is nested in a note's blocks is on screen. A note opens with the copy made for its
 * card first, which only has the top level; the rest arrives when Notion answers. Until then the
 * blocks whose content is missing show a loading placeholder, and the note can't be edited (an
 * edit would keep the incomplete copy on screen).
 */
object NestedContent {
    enum class State {
        /** Everything the app shows is there. */
        LOADED,

        /** The nested blocks are being downloaded. */
        LOADING,

        /** The nested blocks couldn't be downloaded (offline, or unsent edits keep the copy). */
        NOT_LOADED,
    }

    /**
     * True when opening the note downloads what is nested in [block] (text blocks and columns, not
     * deeper than the app goes), but this copy doesn't have it yet.
     */
    fun isMissing(block: Block): Boolean =
        block.hasChildren &&
            (block.type != BlockType.UNSUPPORTED || Columns.isContainer(block)) &&
            block.depth < NotesRepository.MAX_DEPTH

    fun anyMissing(blocks: List<Block>): Boolean = blocks.any(::isMissing)

    /** The state of a copy of [blocks] while its download is under way ([downloading]) or after. */
    fun state(blocks: List<Block>, downloading: Boolean): State = when {
        !anyMissing(blocks) -> State.LOADED
        downloading -> State.LOADING
        else -> State.NOT_LOADED
    }
}
