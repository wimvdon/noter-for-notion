package com.impca201.noter.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DataFiles.SETTINGS)

/** Everything the user set up, backed up with Android's backup. Notion is never changed by these. */
@Serializable
data class AppSettings(
    val databases: List<DatabaseConfig> = emptyList(),
    /** Pinned note IDs: shown on Home and on top of their database. */
    val pinned: List<String> = emptyList(),
    /** Custom note order per database ID, and for Home under [HOME]. */
    val order: Map<String, List<String>> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
) {
    companion object {
        const val HOME = "home"
    }
}

class SettingsStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val key = stringPreferencesKey("settings_json")

    // Kept separately so the theme can be read without decoding everything.
    private val darkKey = stringPreferencesKey("theme_mode")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { decode(it) }

    private fun decode(prefs: Preferences): AppSettings {
        val stored = prefs[key]?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()
        val theme = prefs[darkKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        return stored.copy(themeMode = theme, dynamicColor = prefs[dynamicKey] ?: false)
    }

    suspend fun current(): AppSettings = settings.first()

    /** Changes the settings in one atomic step. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val updated = transform(decode(prefs))
            prefs[key] = json.encodeToString(AppSettings.serializer(), updated)
            prefs[darkKey] = updated.themeMode.name
            prefs[dynamicKey] = updated.dynamicColor
        }
    }
}
