package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.api.objOrNull
import be.wimvandonck.noter.data.api.stringOrNull
import be.wimvandonck.noter.data.model.NotionUser
import kotlinx.serialization.json.JsonObject

/** What kind of Notion token the app has (see [TokenCodec]). */
enum class TokenKind {
    /**
     * A personal access token (or a sign-in): it acts as a person, sees what they see and can add
     * pages at the top of their workspace (among their private pages).
     */
    PERSONAL,

    /** An internal connection's token: a bot that only sees the pages shared with it. */
    CONNECTION,
}

/** The token as Notion describes it: its kind, a name to show, and the person it acts as. */
data class TokenInfo(
    val kind: TokenKind,
    val name: String,
    val owner: NotionUser? = null,
    val workspace: String? = null,
    /** The profile picture of the person the token acts as, when Notion tells it (not for a connection). */
    val avatarUrl: String? = null,
    /** Notion's ID of the workspace ("" when Notion doesn't say): the same for every token of it. */
    val workspaceId: String = "",
)

/** Notion's answer to "who am I" (`GET /users/me`) ⇄ [TokenInfo]. */
object TokenCodec {
    /**
     * Every token is a bot user. A personal access token's bot is owned by the person who made
     * it; an internal connection's bot is owned by the workspace.
     */
    fun parse(me: JsonObject): TokenInfo {
        val bot = me.objOrNull("bot")
        val owner = bot?.objOrNull("owner")
        val workspace = bot?.stringOrNull("workspace_name")
        val workspaceId = bot?.stringOrNull("workspace_id").orEmpty()
        val botName = me.stringOrNull("name").orEmpty()
        return if (owner?.stringOrNull("type") == "user") {
            val ownerUser = owner.objOrNull("user")
            val user = PropertyCodec.parseUser(ownerUser)
            val avatar = ownerUser?.stringOrNull("avatar_url")?.ifBlank { null }
            TokenInfo(TokenKind.PERSONAL, user?.name?.ifBlank { null } ?: botName, user, workspace, avatar, workspaceId)
        } else {
            TokenInfo(TokenKind.CONNECTION, botName, workspace = workspace, workspaceId = workspaceId)
        }
    }
}
