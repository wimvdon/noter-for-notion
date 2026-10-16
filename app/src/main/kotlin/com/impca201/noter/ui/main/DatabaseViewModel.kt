package com.impca201.noter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.impca201.noter.NoterApp
import com.impca201.noter.data.NotesRepository
import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.model.NoteSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class DatabaseUiState(
    val title: String = "",
    val content: MainUiState = MainUiState(),
    /** Views of this database in the navigation. */
    val viewCount: Int = 0,
)

/** A database opened from Browse or from a link in a note: its notes, with filters. */
class DatabaseViewModel(
    private val databaseId: String,
    private val repository: NotesRepository,
    hasKey: StateFlow<Boolean>,
) : ViewModel() {
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<DatabaseUiState> = combine(
        repository.settingsFlow(),
        repository.databases,
        selected,
        repository.pendingCount,
        hasKey,
    ) { settings, databases, chips, pending, key ->
        val db = databases[databaseId]
        val base = MainUiState(
            ready = true,
            hasKey = key,
            currentTab = databaseId,
            pinnedIds = settings.pinned.toSet(),
            propertyLayouts = settings.propertyLayouts,
            pendingCount = pending,
        )
        DatabaseUiState(
            title = db?.schema?.title.orEmpty(),
            content = databaseContent(base, databaseId, db, defaultChipProperty(databaseId, settings, db), chips, settings),
            viewCount = settings.databases.count { it.sourceId == databaseId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatabaseUiState())

    val previews: StateFlow<Map<String, String>> = repository.previews

    init {
        viewModelScope.launch { repository.openDatabase(databaseId) }
    }

    fun loadPreviews(notes: List<NoteSummary>) = repository.loadPreviews(notes)

    fun toggleChip(key: String) {
        selected.value = selected.value.let { if (key in it) it - key else it + key }
    }

    fun clearChips() {
        selected.value = emptySet()
    }

    fun refresh() = viewModelScope.launch {
        repository.flush()
        repository.refresh(databaseId, userAsked = true)
    }

    fun togglePin(id: String) = viewModelScope.launch { repository.togglePin(id) }

    fun trash(id: String) = viewModelScope.launch { repository.trashNote(id) }

    fun saveOrder(ids: List<String>) = viewModelScope.launch { repository.saveOrder(databaseId, ids) }

    fun createNote(onCreated: (String) -> Unit) {
        val ui = state.value.content
        viewModelScope.launch {
            val chipKey = ui.selectedChips.firstOrNull()
            val schema = ui.chipProperty?.let { repository.databases.value[databaseId]?.schema?.property(it) }
            val chip = ui.chips.firstOrNull { it.key == chipKey }
            val preset = if (schema != null && chip != null) listOfNotNull(presetFor(schema, chip)) else emptyList()
            val id = repository.createNote(databaseId, preset) ?: return@launch
            onCreated(id)
        }
    }

    /** Adds this database to the navigation as a view, with the filters selected now. */
    fun addView(name: String) {
        val ui = state.value.content
        val view = DatabaseConfig(
            id = "view-" + UUID.randomUUID(),
            name = name,
            chipProperty = ui.chipProperty,
            databaseId = databaseId,
            filters = ui.selectedChips.toList(),
        )
        viewModelScope.launch { repository.saveDatabase(view) }
    }

    companion object {
        fun factory(databaseId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NoterApp
                DatabaseViewModel(databaseId, app.repository, app.apiKeyStore.hasKey)
            }
        }
    }
}
