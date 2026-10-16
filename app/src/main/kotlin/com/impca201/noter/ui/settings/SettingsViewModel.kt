package com.impca201.noter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.impca201.noter.NoterApp
import com.impca201.noter.data.NotesRepository
import com.impca201.noter.data.api.NotionException
import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.model.DatabaseSchema
import com.impca201.noter.data.model.PageRef
import com.impca201.noter.data.model.ThemeMode
import com.impca201.noter.data.store.ApiKeyStore
import com.impca201.noter.data.store.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** The result of the last key test, for the Connection section. */
sealed interface KeyStatus {
    data object None : KeyStatus

    data object Testing : KeyStatus

    data class Ok(val botName: String) : KeyStatus

    data class Failed(val message: String) : KeyStatus

    data object Removed : KeyStatus
}

/** The view being added or edited in the sheet. */
data class DatabaseDraft(
    val config: DatabaseConfig,
    val isNew: Boolean,
    val schema: DatabaseSchema? = null,
    val schemaError: String? = null,
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val hasKey: Boolean = false,
    val keyStatus: KeyStatus = KeyStatus.None,
    val pendingCount: Int = 0,
    /** Databases shared with the integration, for "Add database" (null while loading). */
    val available: List<PageRef>? = null,
    val availableError: String? = null,
    val draft: DatabaseDraft? = null,
)

class SettingsViewModel(
    private val repository: NotesRepository,
    private val keyStore: ApiKeyStore,
) : ViewModel() {
    private val keyStatus = MutableStateFlow<KeyStatus>(KeyStatus.None)
    private val available = MutableStateFlow<List<PageRef>?>(null)
    private val availableError = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow<DatabaseDraft?>(null)

    private val lookups = combine(available, availableError, draft) { a, e, d -> Triple(a, e, d) }

    val state: StateFlow<SettingsUiState> = combine(
        repository.settingsFlow(),
        keyStore.hasKey,
        keyStatus,
        repository.pendingCount,
        lookups,
    ) { settings, hasKey, status, pending, (a, e, d) ->
        SettingsUiState(settings, hasKey, status, pending, a, e, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(hasKey = keyStore.get().isNotBlank()))

    fun currentKey(): String = keyStore.get()

    fun saveKey(key: String) {
        val trimmed = key.trim()
        keyStatus.value = KeyStatus.Testing
        viewModelScope.launch {
            keyStatus.value = try {
                val name = repository.testKey(trimmed)
                keyStore.set(trimmed)
                repository.onForeground()
                KeyStatus.Ok(name)
            } catch (e: NotionException) {
                KeyStatus.Failed(e.message.orEmpty())
            }
        }
    }

    fun removeKey() {
        viewModelScope.launch {
            keyStore.set("")
            repository.clearData()
            keyStatus.value = KeyStatus.Removed
        }
    }

    fun syncNow() = repository.onForeground()

    fun loadAvailable() {
        available.value = null
        availableError.value = null
        viewModelScope.launch {
            try {
                available.value = repository.searchDatabases()
            } catch (e: NotionException) {
                availableError.value = e.message
                available.value = emptyList()
            }
        }
    }

    fun startAdd(database: PageRef) {
        val config = DatabaseConfig(id = "view-" + UUID.randomUUID(), name = database.title, databaseId = database.id)
        draft.value = DatabaseDraft(config, isNew = true)
        loadSchema(database.id)
    }

    fun startEdit(config: DatabaseConfig) {
        draft.value = DatabaseDraft(config, isNew = false)
        loadSchema(config.sourceId)
    }

    private fun loadSchema(id: String) {
        viewModelScope.launch {
            try {
                val schema = repository.fetchSchema(id)
                draft.update { d ->
                    if (d?.config?.sourceId != id) return@update d
                    // Suggest a status or select property for the chips when there is none yet.
                    val chip = d.config.chipProperty ?: if (d.isNew) {
                        schema.properties.firstOrNull { it.type == "status" }?.name
                            ?: schema.properties.firstOrNull { it.type == "select" }?.name
                    } else {
                        null
                    }
                    d.copy(schema = schema, config = d.config.copy(chipProperty = chip))
                }
            } catch (e: NotionException) {
                draft.update { d -> if (d?.config?.sourceId == id) d.copy(schemaError = e.message) else d }
            }
        }
    }

    fun editDraft(transform: (DatabaseConfig) -> DatabaseConfig) {
        draft.update { it?.copy(config = transform(it.config)) }
    }

    fun saveDraft() {
        val d = draft.value ?: return
        if (d.config.name.isBlank()) return
        draft.value = null
        viewModelScope.launch { repository.saveDatabase(d.config.copy(name = d.config.name.trim())) }
    }

    fun cancelDraft() {
        draft.value = null
    }

    fun removeDatabase(id: String) {
        draft.value = null
        viewModelScope.launch { repository.removeDatabase(id) }
    }

    fun moveDatabase(id: String, up: Boolean) = viewModelScope.launch { repository.moveDatabase(id, up) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.updateSettings { it.copy(themeMode = mode) } }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.updateSettings { it.copy(dynamicColor = enabled) } }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NoterApp
                SettingsViewModel(app.repository, app.apiKeyStore)
            }
        }
    }
}
