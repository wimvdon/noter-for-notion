package com.impca201.noter.ui.views

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** The view being added or edited in the sheet. */
data class DatabaseDraft(
    val config: DatabaseConfig,
    val isNew: Boolean,
    val schema: DatabaseSchema? = null,
    val schemaError: String? = null,
)

data class ViewsUiState(
    val views: List<DatabaseConfig> = emptyList(),
    val hasKey: Boolean = false,
    /** Databases shared with the integration, for "Add view" (null while loading). */
    val available: List<PageRef>? = null,
    val availableError: String? = null,
    val draft: DatabaseDraft? = null,
)

/** Manage views: add, edit, order, pin to the navigation bar and remove the views. */
class ViewsViewModel(
    private val repository: NotesRepository,
    hasKey: StateFlow<Boolean>,
) : ViewModel() {
    private val available = MutableStateFlow<List<PageRef>?>(null)
    private val availableError = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow<DatabaseDraft?>(null)

    val state: StateFlow<ViewsUiState> = combine(
        repository.settingsFlow(),
        hasKey,
        available,
        availableError,
        draft,
    ) { settings, key, a, e, d ->
        ViewsUiState(settings.databases, key, a, e, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewsUiState(hasKey = hasKey.value))

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

    /** Starts a new view of [database]; it goes in the navigation bar when there is [roomInBar]. */
    fun startAdd(database: PageRef, roomInBar: Boolean) {
        val config = DatabaseConfig(
            id = "view-" + UUID.randomUUID(),
            name = database.title,
            databaseId = database.id,
            inBar = roomInBar,
        )
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

    /** Pins the view to the navigation bar, or takes it off (it stays in the drawer). */
    fun setInBar(id: String, inBar: Boolean) = viewModelScope.launch {
        repository.updateSettings { s -> s.copy(databases = s.databases.map { if (it.id == id) it.copy(inBar = inBar) else it }) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NoterApp
                ViewsViewModel(app.repository, app.apiKeyStore.hasKey)
            }
        }
    }
}
