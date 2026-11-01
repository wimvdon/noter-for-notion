package be.wimvandonck.noter.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.HiddenItem
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.ThemeMode
import be.wimvandonck.noter.logic.LegacyNotebooks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DataFiles.SETTINGS)

/** Everything the user set up, backed up with Android's backup. Notion is never changed by these. */
@Serializable
data class AppSettings(
    /** How each database's notes are shown (filters, rows of chips, sort, cards), by data source ID. */
    val setups: Map<String, DatabaseSetup> = emptyMap(),
    /** Databases pinned on Home, newest first. */
    val pinnedDatabases: List<PinnedDatabase> = emptyList(),
    /** Databases and pages hidden from Home's list (#108), newest first; Search still finds them. */
    val hiddenOnHome: List<HiddenItem> = emptyList(),
    /** Pinned note and page IDs: shown on Home and on top of their database. */
    val pinned: List<String> = emptyList(),
    /** Custom note order per database ID, and for Home under [HOME]. */
    val order: Map<String, List<String>> = emptyMap(),
    /** How each database's properties are shown, by database ID. */
    val propertyLayouts: Map<String, PropertyLayout> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Notebooks from before the redesign: turned into [setups] and pins when read (LegacyNotebooks). */
    val databases: List<DatabaseConfig> = emptyList(),
) {
    companion object {
        const val HOME = "home"
    }
}

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.settingsDataStore)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val key = stringPreferencesKey("settings_json")

    // Kept separately so the theme can be read without decoding everything.
    private val darkKey = stringPreferencesKey("theme_mode")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")

    val settings: Flow<AppSettings> = dataStore.data.map { decode(it) }

    private fun decode(prefs: Preferences): AppSettings {
        val stored = prefs[key]?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()
        val theme = prefs[darkKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        // Notebooks from before are read as setups and pins (saved that way with the next change).
        return LegacyNotebooks.migrate(stored).copy(themeMode = theme, dynamicColor = prefs[dynamicKey] ?: false)
    }

    suspend fun current(): AppSettings = settings.first()

    /** Changes the settings in one atomic step. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(decode(prefs))
            prefs[key] = json.encodeToString(AppSettings.serializer(), updated)
            prefs[darkKey] = updated.themeMode.name
            prefs[dynamicKey] = updated.dynamicColor
        }
    }
}
