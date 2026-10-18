package be.wimvandonck.noter.data.store

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import be.wimvandonck.noter.data.notion.TokenKind
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

    /** Stores [key]; [kind] is what Notion said it is (null: not known yet). */
    fun set(key: String, kind: TokenKind? = null) {
        cached = key
        prefs.edit {
            putString(KEY, key)
            if (kind != null) putString(KIND, kind.name) else remove(KIND)
            // The picture is the old token's owner's; the new token's comes with setAvatarUrl.
            remove(AVATAR)
        }
        _hasKey.value = key.isNotBlank()
        _kind.value = kind
        _avatar.value = null
    }

    private val _kind by lazy { MutableStateFlow(prefs.getString(KIND, null)?.let { runCatching { TokenKind.valueOf(it) }.getOrNull() }) }

    /** The stored key's kind: a personal access token or a connection's (null: not known yet). */
    val kind: StateFlow<TokenKind?> get() = _kind

    fun setKind(kind: TokenKind) {
        prefs.edit { putString(KIND, kind.name) }
        _kind.value = kind
    }

    private val _avatar by lazy { MutableStateFlow(prefs.getString(AVATAR, null)) }

    /** The profile picture of the person the token acts as (null: none, or not known yet). */
    val avatarUrl: StateFlow<String?> get() = _avatar

    fun setAvatarUrl(url: String?) {
        prefs.edit { if (url != null) putString(AVATAR, url) else remove(AVATAR) }
        _avatar.value = url
    }

    private companion object {
        const val TAG = "ApiKeyStore"
        const val KEY = "api_key"
        const val KIND = "token_kind"
        const val AVATAR = "owner_avatar_url"
    }
}
