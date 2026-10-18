package be.wimvandonck.noter.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Which workspace a Notion object is in, when the app has tokens for several (#58). A token only
 * sees its own workspace, so the router remembers which workspace ([Workspaces.id] of the token)
 * answered for an ID. An ID it doesn't know yet is tried with every token, in the order of
 * [candidates]. What it knows about pages, databases and data sources ([owners]) is kept on the
 * phone, and shows which workspace one is in; blocks are only remembered while the app runs.
 */
class KeyRouter {
    private val _owners = MutableStateFlow<Map<String, String>>(emptyMap())
    private val blocks = ConcurrentHashMap<String, String>()

    /** The workspace of each object, by ID (without dashes, in lower case). */
    val owners: StateFlow<Map<String, String>> get() = _owners

    /** The tokens to try for [id], in order: the one of its workspace, then the others. */
    fun candidates(id: String?, keys: List<String>): List<String> {
        val workspace = id?.let { workspaceOf(it) } ?: return keys
        val known = keys.firstOrNull { Workspaces.id(it) == workspace } ?: return keys
        return listOf(known) + keys.filter { it != known }
    }

    /** [key] worked for [id]; [kept]: a page, database or data source (not a block). */
    fun remember(id: String, key: String, kept: Boolean = true) = remember(listOf(id), key, kept)

    /** [key] worked for every one of [ids] (see [remember]). */
    fun remember(ids: List<String>, key: String, kept: Boolean = true) {
        if (key.isBlank()) return
        val workspace = Workspaces.id(key)
        val known = ids.filter { it.isNotBlank() }.map(::normalize)
        if (!kept) {
            known.forEach { blocks[it] = workspace }
            return
        }
        if (known.isEmpty() || known.all { _owners.value[it] == workspace }) return
        _owners.update { it + known.associateWith { workspace } }
    }

    /** The workspace that answered for [id], if any. */
    fun workspaceOf(id: String): String? = normalize(id).let { _owners.value[it] ?: blocks[it] }

    /** What was kept on the phone (added to what is known now, which wins). */
    fun restore(saved: Map<String, String>) {
        _owners.update { saved + it }
    }

    /** Forgets everything about [workspace] (its token was removed). */
    fun forget(workspace: String) {
        _owners.update { map -> map.filterValues { it != workspace } }
        blocks.values.removeAll { it == workspace }
    }

    /** Forgets everything (the last token was removed). */
    fun clear() {
        _owners.value = emptyMap()
        blocks.clear()
    }

    companion object {
        /** Notion writes the same ID with or without dashes. */
        fun normalize(id: String): String = id.replace("-", "").lowercase()
    }
}
