package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.notion.TokenKind
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * A workspace the app has a token for, as the screens show it: [id] tells it apart without the
 * token itself ([Workspaces.id]), [name] is the workspace's name (or who the token acts as),
 * [kind] the token's kind (null: not known yet) and [person] who the token acts as.
 */
data class Workspace(
    val id: String,
    val name: String,
    val kind: TokenKind? = null,
    val person: String = "",
    /** Connected with "Connect to Notion" (OAuth) rather than a pasted token. */
    val signedIn: Boolean = false,
) {
    /** Pages can be added at the top of this workspace: not with a connection's token (Notion refuses it). */
    val canAddTopLevel: Boolean get() = kind != TokenKind.CONNECTION
}

/** A list split by workspace; [workspace] is null for what isn't known to be in one yet. */
data class WorkspaceGroup<T>(val workspace: Workspace?, val items: List<T>)

/** Which workspace a page or database is in, for showing it (see [KeyRouter]). */
object Workspaces {
    private val ids = ConcurrentHashMap<String, String>()

    /**
     * A short, stable name for [token]'s workspace that doesn't reveal the token: the start of its
     * SHA-256 hash. It is what the phone keeps about which workspace an object is in.
     */
    fun id(token: String): String = ids.getOrPut(token) {
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Uses [id] for [token] from now on: a refreshed token ([WorkspaceToken.id]) keeps its
     * workspace's ID, so what the phone knows about where pages are stays right.
     */
    fun keep(token: String, id: String) {
        if (token.isNotBlank() && id.isNotBlank()) ids[token] = id
    }

    /** [keep]s the ID of each of [tokens] that has one. */
    fun keepAll(tokens: List<WorkspaceToken>) = tokens.forEach { keep(it.token, it.id) }

    /** The workspaces of [tokens] (the main one first), each once. */
    fun of(tokens: List<WorkspaceToken>): List<Workspace> =
        tokens.filter { it.token.isNotBlank() }.map {
            keep(it.token, it.id)
            Workspace(id(it.token), WorkspaceTokens.label(it), it.kind, it.name, it.signedIn)
        }.distinctBy { it.id }

    /** The workspace of the first of [ids] that is known to be in one of [workspaces]. */
    fun ownerOf(ids: List<String?>, owners: Map<String, String>, workspaces: List<Workspace>): Workspace? {
        for (id in ids) {
            val owner = id?.let { owners[KeyRouter.normalize(it)] } ?: continue
            workspaces.firstOrNull { it.id == owner }?.let { return it }
        }
        return null
    }

    /**
     * [items] by workspace, in the order of [workspaces], each keeping its own order; what isn't
     * known to be in one comes last. Empty groups are left out.
     */
    fun <T> group(items: List<T>, owners: Map<String, String>, workspaces: List<Workspace>, idsOf: (T) -> List<String?>): List<WorkspaceGroup<T>> {
        val byWorkspace = items.groupBy { ownerOf(idsOf(it), owners, workspaces)?.id }
        return workspaces.mapNotNull { ws -> byWorkspace[ws.id]?.let { WorkspaceGroup(ws, it) } } +
            listOfNotNull(byWorkspace[null]?.let { WorkspaceGroup(null, it) })
    }
}
