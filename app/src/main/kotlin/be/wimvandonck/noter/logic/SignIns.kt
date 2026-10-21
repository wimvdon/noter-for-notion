package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.api.objOrNull
import be.wimvandonck.noter.data.api.stringOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/** What Notion gave for "Connect to Notion" (OAuth): the tokens and the workspace they are for. */
data class SignInGrant(
    val accessToken: String,
    val refreshToken: String,
    val workspaceId: String,
    val workspaceName: String,
    /** The person who connected, when Notion says. */
    val person: String,
) {
    fun toToken(): WorkspaceToken =
        WorkspaceToken(accessToken, name = person, workspace = workspaceName, refresh = refreshToken, workspaceId = workspaceId)
}

/** How a sign-in came back to the app (the helper's link, see [SignIns.returned]). */
sealed interface SignInReturn {
    /** The tokens, sealed for this phone ([SealedBox]). */
    data class Sealed(val sealed: String) : SignInReturn

    /** The person refused access, or Notion refused the sign-in ([error] is Notion's code). */
    data class Refused(val error: String) : SignInReturn
}

/**
 * "Connect to Notion" with OAuth: the app opens Notion's page with a fresh public key in `state`,
 * Notion sends the person to the sign-in helper (`worker/`), which trades the code for tokens with
 * the connection's secret and sends them back sealed for this phone, to `<app id>://oauth`.
 */
object SignIns {
    private val json = Json { ignoreUnknownKeys = true }

    /** What the app sends as `state`: where the helper sends the person back, and the key to seal for. */
    fun state(scheme: String, publicKey: ByteArray): String = "$scheme~${SealedBox.base64Url(publicKey)}"

    /** Notion's page where the person chooses the pages Noter may use. */
    fun authorizeUrl(clientId: String, helperUrl: String, state: String): String =
        "https://api.notion.com/v1/oauth/authorize?client_id=${encode(clientId)}&response_type=code&owner=user" +
            "&redirect_uri=${encode(redirectUri(helperUrl))}&state=${encode(state)}"

    /** The helper's address Notion sends the person to; the same in the connection's settings in Notion. */
    fun redirectUri(helperUrl: String): String = "${helperUrl.trimEnd('/')}/callback"

    /** The helper's link back ([result] or [error], its query parameters), or null when it is neither. */
    fun returned(result: String?, error: String?): SignInReturn? = when {
        !result.isNullOrBlank() -> SignInReturn.Sealed(result)
        !error.isNullOrBlank() -> SignInReturn.Refused(error)
        else -> null
    }

    /** Notion's token answer (`POST /v1/oauth/token`), or null without an access token. */
    fun grant(text: String): SignInGrant? {
        val data = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        return grant(data)
    }

    fun grant(data: JsonObject): SignInGrant? {
        val access = data.stringOrNull("access_token")?.ifBlank { null } ?: return null
        val person = data.objOrNull("owner")?.objOrNull("user")?.stringOrNull("name").orEmpty()
        return SignInGrant(
            accessToken = access,
            refreshToken = data.stringOrNull("refresh_token").orEmpty(),
            workspaceId = data.stringOrNull("workspace_id").orEmpty(),
            workspaceName = data.stringOrNull("workspace_name").orEmpty(),
            person = person,
        )
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}
