package be.wimvandonck.noter.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A token for another workspace (#58), next to the main one: [name] is who it acts as and
 * [workspace] the workspace's name, both as Notion said when it was added.
 */
@Serializable
data class WorkspaceToken(val token: String, val name: String = "", val workspace: String = "")

/** The list of tokens for other workspaces: adding, removing and storing it. */
object WorkspaceTokens {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(WorkspaceToken.serializer())

    /**
     * [list] with [added] at the end. A blank token or the main token ([primary]) isn't added;
     * a token that is already there is replaced (its names may have changed) and moves to the end.
     */
    fun add(list: List<WorkspaceToken>, primary: String, added: WorkspaceToken): List<WorkspaceToken> {
        if (added.token.isBlank() || added.token == primary) return list
        return list.filter { it.token != added.token } + added
    }

    fun remove(list: List<WorkspaceToken>, token: String): List<WorkspaceToken> = list.filter { it.token != token }

    /** The name to show for [token]: the workspace's, else who it acts as. */
    fun label(token: WorkspaceToken): String = token.workspace.ifBlank { token.name }

    fun encode(list: List<WorkspaceToken>): String = json.encodeToString(serializer, list)

    /** The stored list; nothing (rather than a crash) when it can't be read. */
    fun decode(text: String?): List<WorkspaceToken> =
        if (text.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
}
