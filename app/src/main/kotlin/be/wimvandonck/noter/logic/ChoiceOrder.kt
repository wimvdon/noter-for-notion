package be.wimvandonck.noter.logic

/**
 * The order of a picker's choices (options, people, linked pages). When the picker opens, what is
 * selected comes first; after that the order stays put while the user ticks and unticks, so
 * nothing jumps away from under their finger. Choices that turn up later (loaded, or just added)
 * go at the end.
 */
object ChoiceOrder {
    /**
     * [keys] in the order to show them. [previous] is the order shown so far (empty when the
     * picker just opened or had nothing to show yet), [selected] the keys selected now.
     */
    fun arrange(keys: List<String>, selected: Set<String>, previous: List<String>): List<String> {
        val unique = keys.distinct()
        if (previous.isEmpty()) {
            val (on, off) = unique.partition { it in selected }
            return on + off
        }
        val present = unique.toSet()
        val kept = previous.filter { it in present }
        val known = kept.toSet()
        return kept + unique.filter { it !in known }
    }
}
