package com.impca201.noter.data.store

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    fun set(key: String) {
        cached = key
        prefs.edit { putString(KEY, key) }
        _hasKey.value = key.isNotBlank()
    }

    private companion object {
        const val TAG = "ApiKeyStore"
        const val KEY = "api_key"
    }
}
