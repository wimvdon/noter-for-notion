package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.notion.TokenKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A token for a workspace (#58): [name] is who it acts as, [workspace] the workspace's name and
 * [kind] the kind of token, as Notion said when it was added or the app last started.
 *
 * [workspaceId] is Notion's ID of the workspace, the same for every token of it (blank: not known
 * yet). A token from "Connect to Notion" (OAuth) also has a [refresh] token and the [id] the phone
 * knows the workspace by ([Workspaces.id] of its first token), which stays when a refresh replaces
 * the token.
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
    val signedIn: Boolean get() = refresh.isNotBlank()
}

/** Where a new token went ([WorkspaceTokens.place]). */
sealed interface Placement {
    /** Kept: the new [main] token and the [others]; [replaced] is the way it took the place of, if any. */
    data class Added(val main: WorkspaceToken, val others: List<WorkspaceToken>, val replaced: WorkspaceToken? = null) : Placement

    /** Not kept: the workspace is already added in a way that sees more ([existing]). */
    data class Refused(val existing: WorkspaceToken) : Placement
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

    /**
     * [main] and [others] with the workspace at [from] moved to [to] (positions in main + others).
     * The one on top becomes the main token. Positions outside the list change nothing.
     */
    fun move(main: WorkspaceToken, others: List<WorkspaceToken>, from: Int, to: Int): Pair<WorkspaceToken, List<WorkspaceToken>> {
        val all = (listOf(main) + others).toMutableList()
        if (main.token.isBlank() || from !in all.indices || to !in all.indices || from == to) return main to others
        all.add(to, all.removeAt(from))
        return all.first() to all.drop(1)
    }

    /** [list] with what Notion now says about [token] (its names, kind and workspace); the order stays. */
    fun update(
        list: List<WorkspaceToken>,
        token: String,
        name: String,
        workspace: String,
        kind: TokenKind,
        workspaceId: String = "",
    ): List<WorkspaceToken> =
        list.map {
            if (it.token == token) it.copy(name = name, workspace = workspace, kind = kind, workspaceId = workspaceId.ifBlank { it.workspaceId }) else it
        }

    /**
     * How much a token sees: a personal access token everything the person sees, Connect to
     * Notion the pages chosen in Notion, a connection the pages a member shared with it.
     */
    fun access(token: WorkspaceToken): Int = when {
        token.signedIn -> 2
        token.kind == TokenKind.PERSONAL -> 3
        token.kind == TokenKind.CONNECTION -> 1
        else -> 0
    }

    /** How [token] was added (null: its kind isn't known yet). */
    fun way(token: WorkspaceToken): WorkspaceWay? = when {
        token.signedIn -> WorkspaceWay.SIGN_IN
        token.kind == TokenKind.PERSONAL -> WorkspaceWay.PERSONAL
        token.kind == TokenKind.CONNECTION -> WorkspaceWay.CONNECTION
        else -> null
    }

    /**
     * Where [added] goes. Each Notion workspace is added once: a token for one that is already
     * there replaces its token in place, unless the one there sees more ([access]), which is never
     * replaced by one that sees less. A new workspace becomes the main token when there is none,
     * else goes after the others.
     */
    fun place(main: WorkspaceToken, others: List<WorkspaceToken>, added: WorkspaceToken): Placement {
        val same = { t: WorkspaceToken ->
            t.token.isNotBlank() && (t.token == added.token || added.workspaceId.isNotBlank() && t.workspaceId == added.workspaceId)
        }
        val existing = (listOf(main) + others).firstOrNull(same)
            ?: return if (main.token.isBlank()) Placement.Added(added, others) else Placement.Added(main, add(others, main.token, added))
        if (access(added) < access(existing)) return Placement.Refused(existing)
        val fresh = if (existing.token == added.token) added.copy(id = existing.id) else replaced(existing, added)
        val replacedWay = existing.takeIf { it.token != added.token && way(it) != way(added) }
        return if (same(main)) {
            Placement.Added(fresh, others, replacedWay)
        } else {
            Placement.Added(main, others.map { if (same(it)) fresh else it }, replacedWay)
        }
    }

    /**
     * [main] and [others] with each Notion workspace once (from before tokens were checked for
     * that): the token that sees most stays, the first of equals. The main token stays the main
     * one unless it goes, then the one that stays for its workspace takes its place.
     */
    fun deduplicate(main: WorkspaceToken, others: List<WorkspaceToken>): Pair<WorkspaceToken, List<WorkspaceToken>> {
        val all = listOf(main) + others
        fun keeper(t: WorkspaceToken): WorkspaceToken =
            if (t.workspaceId.isBlank()) t else all.filter { it.workspaceId == t.workspaceId }.maxBy(::access)
        val newMain = if (main.token.isBlank()) main else keeper(main)
        val rest = others.map(::keeper).distinctBy { it.token }.filter { it.token != newMain.token }
        return newMain to rest
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
