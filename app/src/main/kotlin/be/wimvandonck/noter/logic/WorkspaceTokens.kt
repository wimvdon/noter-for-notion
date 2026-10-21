package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.notion.TokenKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A token for a workspace (#58): [name] is who it acts as, [workspace] the workspace's name and
 * [kind] the kind of token, as Notion said when it was added or the app last started.
 *
 * A token from "Connect to Notion" (OAuth) also has a [refresh] token, Notion's [workspaceId], and
 * the [id] the phone knows the workspace by ([Workspaces.id] of its first token), which stays when
 * a refresh replaces the token.
 */
@Serializable
data class WorkspaceToken(
    val token: String,
    val name: String = "",
    val workspace: String = "",
    val kind: TokenKind? = null,
    val refresh: String = "",
    val workspaceId: String = "",
    val id: String = "",
) {
    /** Made with "Connect to Notion" (OAuth) rather than pasted. */
    val signedIn: Boolean get() = refresh.isNotBlank() || workspaceId.isNotBlank()
}

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

    /** [list] with what Notion now says about [token] (its names and kind); the order stays. */
    fun update(list: List<WorkspaceToken>, token: String, name: String, workspace: String, kind: TokenKind): List<WorkspaceToken> =
        list.map { if (it.token == token) it.copy(name = name, workspace = workspace, kind = kind) else it }

    /**
     * Where a token from "Connect to Notion" goes: in place of the token it had for the same
     * Notion workspace (connecting again), else as the main token when there is none, else at the
     * end of the others. Returns the new main token and the others.
     */
    fun signIn(main: WorkspaceToken, others: List<WorkspaceToken>, added: WorkspaceToken): Pair<WorkspaceToken, List<WorkspaceToken>> {
        val same = { t: WorkspaceToken -> added.workspaceId.isNotBlank() && t.workspaceId == added.workspaceId }
        return when {
            main.token.isNotBlank() && same(main) -> replaced(main, added) to others
            others.any(same) -> main to others.map { if (same(it)) replaced(it, added) else it }
            main.token.isBlank() -> added to others
            else -> main to add(others, main.token, added)
        }
    }

    /**
     * [new] in place of [old]: the workspace keeps the ID the phone knows it by (so what is known
     * about where pages are stays), and the names until Notion is asked again.
     */
    fun replaced(old: WorkspaceToken, new: WorkspaceToken): WorkspaceToken =
        new.copy(
            id = old.id.ifBlank { Workspaces.id(old.token) },
            name = new.name.ifBlank { old.name },
            workspace = new.workspace.ifBlank { old.workspace },
            kind = new.kind ?: old.kind,
            workspaceId = new.workspaceId.ifBlank { old.workspaceId },
        )

    /** The name to show for [token]: the workspace's, else who it acts as. */
    fun label(token: WorkspaceToken): String = token.workspace.ifBlank { token.name }

    fun encode(list: List<WorkspaceToken>): String = json.encodeToString(serializer, list)

    /** The stored list; nothing (rather than a crash) when it can't be read. */
    fun decode(text: String?): List<WorkspaceToken> =
        if (text.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
}
