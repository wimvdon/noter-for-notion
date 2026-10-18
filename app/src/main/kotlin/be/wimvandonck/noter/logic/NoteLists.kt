package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary

/** Builds Home's pinned notes from the notes on this phone (a database's own: see DatabaseSetups). */
object NoteLists {
    /**
     * The pinned notes and pages among [notes] (of every database here), in the order they were
     * pinned (newest first) unless placed by hand ([order]).
     */
    fun pinned(notes: List<NoteSummary>, pinned: List<String>, order: List<String>): List<NoteSummary> {
        val pinOrder = pinned.withIndex().associate { (i, id) -> id to i }
        val byPinOrder = notes.filter { it.id in pinOrder }.distinctBy { it.id }.sortedBy { pinOrder.getValue(it.id) }
        val placed = order.withIndex().associate { (i, id) -> id to i }
        val (inOrder, rest) = byPinOrder.partition { it.id in placed }
        return rest + inOrder.sortedBy { placed.getValue(it.id) }
    }
}
