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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Notion API key, encrypted with the Android Keystore (the same approach as Time Tracker for
 * Notion). It is not in Android's backup: a key encrypted on one phone can't be read on another.
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
    fun set(key: String, kind: TokenKind? = null, name: String = "", workspace: String = "") {
        cached = key
        prefs.edit {
            putString(KEY, key)
            if (kind != null) putString(KIND, kind.name) else remove(KIND)
            putString(NAME, name)
            putString(WORKSPACE, workspace)
            // The picture is the old token's owner's; the new token's comes with setAvatarUrl.
            remove(AVATAR)
        }
        _hasKey.value = key.isNotBlank()
        _kind.value = kind
        _avatar.value = null
        _main.value = WorkspaceToken(key, name, workspace, kind)
    }

    private val _main by lazy {
        MutableStateFlow(WorkspaceToken(get(), prefs.getString(NAME, "").orEmpty(), prefs.getString(WORKSPACE, "").orEmpty(), _kind.value))
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
        _main.value = WorkspaceToken(get(), name, workspace, kind)
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

    private val _extras by lazy { MutableStateFlow(WorkspaceTokens.decode(prefs.getString(EXTRAS, null))) }

    /** Tokens for other workspaces (#58), next to the main one. Encrypted like it, never backed up. */
    val extras: StateFlow<List<WorkspaceToken>> get() = _extras

    fun extraTokens(): List<String> = _extras.value.map { it.token }

    fun setExtras(list: List<WorkspaceToken>) {
        prefs.edit { putString(EXTRAS, WorkspaceTokens.encode(list)) }
        _extras.value = list
    }

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
    }
}
