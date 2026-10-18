package be.wimvandonck.noter.logic

import java.util.concurrent.ConcurrentHashMap

/**
 * Which token to use for a Notion object when the app has tokens for several workspaces (#58).
 * A token only sees its own workspace, so the router remembers which token last worked for an
 * ID. An ID it doesn't know yet is tried with every token, in the order of [candidates].
 */
class KeyRouter {
    private val owners = ConcurrentHashMap<String, String>()

    /** The tokens to try for [id], in order: the one that worked for it before, then the others. */
    fun candidates(id: String?, keys: List<String>): List<String> {
        val known = id?.let { owners[normalize(it)] }?.takeIf { it in keys } ?: return keys
        return listOf(known) + keys.filter { it != known }
    }

    /** [key] worked for [id]. */
    fun remember(id: String, key: String) {
        if (id.isNotBlank() && key.isNotBlank()) owners[normalize(id)] = key
    }

    /** The token that worked for [id], if any. */
    fun keyOf(id: String): String? = owners[normalize(id)]

    companion object {
        /** Notion writes the same ID with or without dashes. */
        fun normalize(id: String): String = id.replace("-", "").lowercase()
    }
}
