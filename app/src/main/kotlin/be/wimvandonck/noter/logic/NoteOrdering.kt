package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary

/** A list split into pinned notes (on top) and the rest. */
data class ArrangedNotes(
    val pinned: List<NoteSummary>,
    val others: List<NoteSummary>,
) {
    val all: List<NoteSummary> get() = pinned + others
}

/**
 * The order notes are shown in. Pins and the custom order live only on this phone; Notion is not
 * changed.
 *
 * Notes the user placed by hand keep that place. Notes that were never placed (new ones) come
 * first, the most recently edited on top.
 */
object NoteOrdering {
    fun sort(notes: List<NoteSummary>, order: List<String>): List<NoteSummary> {
        val position = order.withIndex().associate { (index, id) -> id to index }
        val (placed, unplaced) = notes.partition { it.id in position }
        return unplaced.sortedByDescending { it.lastEditedTime } + placed.sortedBy { position.getValue(it.id) }
    }

    fun arrange(notes: List<NoteSummary>, pinned: Set<String>, order: List<String>): ArrangedNotes {
        val sorted = sort(notes, order)
        val (top, rest) = sorted.partition { it.id in pinned }
        return ArrangedNotes(top, rest)
    }

    /** [items] with the item at [from] moved to [to] (notes, notebooks). */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * The full order after the user reordered only some of the notes (for example with a filter on):
     * the reordered notes take the places those notes had in [full], in their new order.
     */
    fun applySubsetOrder(full: List<String>, reorderedSubset: List<String>): List<String> {
        val subset = reorderedSubset.toSet()
        val missing = reorderedSubset.filter { it !in full }
        val base = missing + full
        val next = reorderedSubset.iterator()
        return base.map { id -> if (id in subset && next.hasNext()) next.next() else id }
    }

    /** [order] with [oldId] replaced by [newId] (a note created on this phone got its Notion ID). */
    fun rename(order: List<String>, oldId: String, newId: String): List<String> =
        order.map { if (it == oldId) newId else it }
}
