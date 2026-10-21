package be.wimvandonck.noter.logic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Renews a token from "Connect to Notion" when Notion no longer accepts it. Notion replaces the
 * refresh token at every refresh and may cancel the sign-in when an old one is used again, so
 * refreshes go one at a time, and a request that still had the old token gets the new one
 * without a second refresh.
 *
 * [find] is the stored token for a token string, [refresh] asks the helper (null: refused or
 * unreachable), [save] keeps the new token in place of the old one. [now] is in milliseconds.
 */
class TokenRefresher(
    private val find: (token: String) -> WorkspaceToken?,
    private val refresh: suspend (refreshToken: String) -> SignInGrant?,
    private val save: (old: WorkspaceToken, new: WorkspaceToken) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** Old token → the token that replaced it. */
    private val replaced = HashMap<String, String>()

    /** When each workspace ([WorkspaceToken.id]) was last refreshed or tried. */
    private val lastTry = HashMap<String, Long>()

    /** A token to use instead of [token], which Notion refused; null when there is none. */
    suspend fun renew(token: String): String? = mutex.withLock {
        replaced[token]?.let { return@withLock it }
        val stored = find(token)?.takeIf { it.refresh.isNotBlank() } ?: return@withLock null
        val id = stored.id.ifBlank { Workspaces.id(stored.token) }
        // A token refused right after a refresh isn't helped by another one.
        val last = lastTry[id]
        if (last != null && now() - last < MIN_INTERVAL_MS) return@withLock null
        lastTry[id] = now()
        val grant = refresh(stored.refresh) ?: return@withLock null
        val fresh = WorkspaceTokens.replaced(stored, grant.toToken().copy(refresh = grant.refreshToken.ifBlank { stored.refresh }))
        Workspaces.keep(fresh.token, fresh.id)
        save(stored, fresh)
        replaced[token] = fresh.token
        fresh.token
    }

    private companion object {
        const val MIN_INTERVAL_MS = 60_000L
    }
}
