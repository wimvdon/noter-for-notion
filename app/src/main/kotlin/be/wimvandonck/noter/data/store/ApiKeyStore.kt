package be.wimvandonck.noter.data.store

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import be.wimvandonck.noter.data.notion.TokenKind
import be.wimvandonck.noter.logic.WorkspaceToken
import be.wimvandonck.noter.logic.WorkspaceTokens
import be.wimvandonck.noter.logic.Workspaces
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Notion tokens (and the refresh tokens of "Connect to Notion"), encrypted with the Android
 * Keystore (the same approach as Time Tracker for Notion). They are not in Android's backup: a key
 * encrypted on one phone can't be read on another.
 */
class ApiKeyStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        runCatching { create() }.getOrElse { e ->
            Log.e(TAG, "Encrypted storage failed, recreating it", e)
            runCatching {
                context.deleteSharedPreferences(DataFiles.API_KEY)
                create()
            }.getOrElse { e2 ->
                Log.e(TAG, "Encrypted storage unavailable, using private storage", e2)
                context.getSharedPreferences(DataFiles.API_KEY_FALLBACK, Context.MODE_PRIVATE)
            }
        }
    }

    @Suppress("DEPRECATION") // security-crypto is deprecated but still the simplest Keystore-backed store.
    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            DataFiles.API_KEY,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Volatile
    private var cached: String? = null

    private val _hasKey by lazy { MutableStateFlow(get().isNotBlank()) }

    /** True when a key is stored. */
    val hasKey: StateFlow<Boolean> get() = _hasKey

    fun get(): String = cached ?: (prefs.getString(KEY, "") ?: "").also { cached = it }

    /**
     * Stores [key] as the main token; [kind], [name] and [workspace] are what Notion said about it
     * (null and blank: not known yet).
     */
    fun set(key: String, kind: TokenKind? = null, name: String = "", workspace: String = "") =
        setMain(WorkspaceToken(key, name, workspace, kind))

    /**
     * Stores [token] as the main token. [keepAvatar]: it is the same person's (a refreshed token);
     * else the old picture goes, and the new token's comes with setAvatarUrl.
     */
    fun setMain(token: WorkspaceToken, keepAvatar: Boolean = false) {
        cached = token.token
        Workspaces.keep(token.token, token.id)
        prefs.edit {
            putString(KEY, token.token)
            if (token.kind != null) putString(KIND, token.kind.name) else remove(KIND)
            putString(NAME, token.name)
            putString(WORKSPACE, token.workspace)
            putString(REFRESH, token.refresh)
            putString(WORKSPACE_ID, token.workspaceId)
            putString(ID, token.id)
            if (!keepAvatar) remove(AVATAR)
        }
        _hasKey.value = token.token.isNotBlank()
        _kind.value = token.kind
        if (!keepAvatar) _avatar.value = null
        _main.value = token
    }

    private val _main by lazy {
        MutableStateFlow(
            WorkspaceToken(
                get(),
                prefs.getString(NAME, "").orEmpty(),
                prefs.getString(WORKSPACE, "").orEmpty(),
                _kind.value,
                refresh = prefs.getString(REFRESH, "").orEmpty(),
                workspaceId = prefs.getString(WORKSPACE_ID, "").orEmpty(),
                id = prefs.getString(ID, "").orEmpty(),
            ).also { Workspaces.keep(it.token, it.id) },
        )
    }

    /** The main token with what Notion said about it. */
    val main: StateFlow<WorkspaceToken> get() = _main

    /** Keeps what Notion now says about the main token (at each start). */
    fun setMainInfo(kind: TokenKind, name: String, workspace: String) {
        prefs.edit {
            putString(KIND, kind.name)
            putString(NAME, name)
            putString(WORKSPACE, workspace)
        }
        _kind.value = kind
        _main.value = _main.value.copy(name = name, workspace = workspace, kind = kind)
    }

    private val _kind by lazy { MutableStateFlow(prefs.getString(KIND, null)?.let { runCatching { TokenKind.valueOf(it) }.getOrNull() }) }

    /** The stored key's kind: a personal access token or a connection's (null: not known yet). */
    val kind: StateFlow<TokenKind?> get() = _kind

    private val _avatar by lazy { MutableStateFlow(prefs.getString(AVATAR, null)) }

    /** The profile picture of the person the token acts as (null: none, or not known yet). */
    val avatarUrl: StateFlow<String?> get() = _avatar

    fun setAvatarUrl(url: String?) {
        prefs.edit { if (url != null) putString(AVATAR, url) else remove(AVATAR) }
        _avatar.value = url
    }

    private val _extras by lazy { MutableStateFlow(WorkspaceTokens.decode(prefs.getString(EXTRAS, null)).also(Workspaces::keepAll)) }

    /** Tokens for other workspaces (#58), next to the main one. Encrypted like it, never backed up. */
    val extras: StateFlow<List<WorkspaceToken>> get() = _extras

    fun extraTokens(): List<String> = _extras.value.map { it.token }

    fun setExtras(list: List<WorkspaceToken>) {
        Workspaces.keepAll(list)
        prefs.edit { putString(EXTRAS, WorkspaceTokens.encode(list)) }
        _extras.value = list
    }

    /** The stored token (main or other) whose token is [token], if any. */
    fun find(token: String): WorkspaceToken? =
        _main.value.takeIf { it.token == token && token.isNotBlank() } ?: _extras.value.firstOrNull { it.token == token }

    /** Keeps [new] in place of [old] (a refreshed token), where [old] was. */
    @Synchronized
    fun replace(old: WorkspaceToken, new: WorkspaceToken) {
        if (_main.value.token == old.token) {
            setMain(new, keepAvatar = true)
        } else {
            setExtras(_extras.value.map { if (it.token == old.token) new else it })
        }
    }

    /**
     * The private key of a "Connect to Notion" in progress (see SealedBox), kept while the person
     * is in the browser, in case Android closes the app meanwhile. Null: none.
     */
    var pendingSignIn: String?
        get() = prefs.getString(PENDING_SIGN_IN, null)
        set(value) = prefs.edit { if (value != null) putString(PENDING_SIGN_IN, value) else remove(PENDING_SIGN_IN) }

    private val _collapsed by lazy { MutableStateFlow(prefs.getStringSet(COLLAPSED, emptySet()).orEmpty().toSet()) }

    /** The workspaces ([be.wimvandonck.noter.logic.Workspaces.id]) folded away on Home. */
    val collapsed: StateFlow<Set<String>> get() = _collapsed

    fun setCollapsed(ids: Set<String>) {
        prefs.edit { putStringSet(COLLAPSED, ids) }
        _collapsed.value = ids
    }

    private companion object {
        const val TAG = "ApiKeyStore"
        const val EXTRAS = "extra_workspace_tokens"
        const val KEY = "api_key"
        const val KIND = "token_kind"
        const val AVATAR = "owner_avatar_url"
        const val NAME = "token_name"
        const val WORKSPACE = "token_workspace"
        const val COLLAPSED = "collapsed_workspaces"
        const val REFRESH = "token_refresh"
        const val WORKSPACE_ID = "token_workspace_id"
        const val ID = "token_id"
        const val PENDING_SIGN_IN = "pending_sign_in"
    }
}
