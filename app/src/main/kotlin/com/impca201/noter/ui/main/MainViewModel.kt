package com.impca201.noter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.impca201.noter.NoterApp
import com.impca201.noter.data.NotesRepository
import com.impca201.noter.data.RepoEvent
import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.model.NotionUser
import com.impca201.noter.data.model.PropertyLayout
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.data.store.AppSettings
import com.impca201.noter.logic.FilterChip
import com.impca201.noter.logic.NoteLists
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A destination of the navigation bar: Home or a database. */
data class Tab(val key: String, val name: String, val icon: String?)

data class MainUiState(
    val ready: Boolean = false,
    val hasKey: Boolean = false,
    val tabs: List<Tab> = emptyList(),
    val currentTab: String = AppSettings.HOME,
    val chips: List<FilterChip> = emptyList(),
    val selectedChips: Set<String> = emptySet(),
    val pinned: List<NoteSummary> = emptyList(),
    val others: List<NoteSummary> = emptyList(),
    val pinnedIds: Set<String> = emptySet(),
    val databaseIcons: Map<String, String> = emptyMap(),
    val chipProperty: String? = null,
    /** How each database shows its properties, for the properties on the cards. */
    val propertyLayouts: Map<String, PropertyLayout> = emptyMap(),
    /** True while nothing is cached yet for this tab and the first download runs. */
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val pendingCount: Int = 0,
    val offline: Boolean = false,
    val hasDatabases: Boolean = false,
) {
    val isHome: Boolean get() = currentTab == AppSettings.HOME
}

class MainViewModel(private val repository: NotesRepository, hasKey: StateFlow<Boolean>) : ViewModel() {
    private val currentTab = MutableStateFlow(AppSettings.HOME)
    private val selectedChips = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    val events: Flow<RepoEvent> = repository.events

    private val status = combine(repository.pendingCount, repository.offline, hasKey) { pending, offline, key ->
        Triple(pending, offline, key)
    }

    val state: StateFlow<MainUiState> = combine(
        repository.settingsFlow(),
        repository.databases,
        currentTab,
        selectedChips,
        status,
    ) { settings, databases, tab, chipsByTab, (pending, offline, key) ->
        val dbConfigs = settings.databases
        val tabKey = if (tab == AppSettings.HOME || dbConfigs.any { it.id == tab }) tab else AppSettings.HOME
        val tabs = listOf(Tab(AppSettings.HOME, "", null)) + dbConfigs.map { Tab(it.id, it.name, it.icon) }
        val selected = chipsByTab[tabKey].orEmpty()
        val base = MainUiState(
            ready = true,
            hasKey = key,
            tabs = tabs,
            currentTab = tabKey,
            pinnedIds = settings.pinned.toSet(),
            databaseIcons = dbConfigs.associate { it.id to it.icon },
            propertyLayouts = settings.propertyLayouts,
            pendingCount = pending,
            offline = offline,
            hasDatabases = dbConfigs.isNotEmpty(),
        )
        if (tabKey == AppSettings.HOME) {
            val content = NoteLists.home(
                dbConfigs,
                databases.mapValues { it.value.notes },
                settings.pinned,
                settings.order[AppSettings.HOME].orEmpty(),
                selected,
            )
            base.copy(
                chips = content.chips,
                selectedChips = selected,
                pinned = content.pinned,
                loading = dbConfigs.any { databases[it.id]?.loaded != true },
                refreshing = databases.values.any { it.refreshing },
            )
        } else {
            val config = dbConfigs.first { it.id == tabKey }
            val db = databases[tabKey]
            val chipProperty = config.chipProperty?.let { db?.schema?.property(it) }
            val content = NoteLists.database(
                db?.notes.orEmpty(),
                chipProperty,
                selected,
                settings.pinned,
                settings.order[tabKey].orEmpty(),
            )
            base.copy(
                chips = content.chips,
                selectedChips = selected,
                pinned = content.pinned,
                others = content.others,
                chipProperty = chipProperty?.name,
                loading = db?.loaded != true,
                refreshing = db?.refreshing == true,
                error = db?.error,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun selectTab(key: String) {
        currentTab.value = key
        if (key != AppSettings.HOME) viewModelScope.launch { repository.refresh(key, userAsked = false) }
    }

    fun toggleChip(key: String) {
        val tab = state.value.currentTab
        selectedChips.update { map ->
            val current = map[tab].orEmpty()
            map + (tab to if (key in current) current - key else current + key)
        }
    }

    fun clearChips() {
        val tab = state.value.currentTab
        selectedChips.update { it - tab }
    }

    fun refresh() {
        val tab = state.value.currentTab
        viewModelScope.launch {
            repository.flush()
            if (tab == AppSettings.HOME) {
                repository.currentSettings().databases.forEach { launch { repository.refresh(it.id, userAsked = true) } }
            } else {
                repository.refresh(tab, userAsked = true)
            }
        }
    }

    /** Preview text of each note's content, by page ID. */
    val previews: StateFlow<Map<String, String>> = repository.previews

    /** Fills in the previews of the notes on screen (from the phone's copies, else from Notion). */
    fun loadPreviews(notes: List<NoteSummary>) = repository.loadPreviews(notes)

    fun togglePin(id: String) = viewModelScope.launch { repository.togglePin(id) }

    fun trash(id: String) = viewModelScope.launch { repository.trashNote(id) }

    /** Saves the new order after the user moved notes in the current tab. */
    fun saveOrder(ids: List<String>) {
        val tab = state.value.currentTab
        viewModelScope.launch { repository.saveOrder(tab, ids) }
    }

    /**
     * Creates a note in [databaseId] (the current tab when null) and calls [onCreated] with its ID.
     * On a database tab with chips selected, the new note gets the first selected chip's value; on
     * Home the new note is pinned so it shows up there.
     */
    fun createNote(databaseId: String? = null, onCreated: (String) -> Unit) {
        val ui = state.value
        val target = databaseId ?: ui.currentTab.takeIf { it != AppSettings.HOME } ?: return
        viewModelScope.launch {
            val preset = if (target == ui.currentTab) presetFromChips(target, ui) else emptyList()
            val id = repository.createNote(target, preset) ?: return@launch
            if (ui.isHome) repository.togglePin(id)
            onCreated(id)
        }
    }

    private fun presetFromChips(databaseId: String, ui: MainUiState): List<NoteProperty> {
        val chipKey = ui.selectedChips.firstOrNull() ?: return emptyList()
        val propName = ui.chipProperty ?: return emptyList()
        val schema = repository.databases.value[databaseId]?.schema?.property(propName) ?: return emptyList()
        val chip = ui.chips.firstOrNull { it.key == chipKey } ?: return emptyList()
        val value = when (schema.type) {
            "select", "status" -> PropertyValue.Select(schema.options.firstOrNull { it.name == chip.key })
            "multi_select" -> PropertyValue.MultiSelect(listOfNotNull(schema.options.firstOrNull { it.name == chip.key }))
            "people" -> PropertyValue.People(listOf(NotionUser(chip.key, chip.label)))
            else -> return emptyList()
        }
        return listOf(NoteProperty(schema.name, schema.type, value))
    }

    fun databases(): List<DatabaseConfig> = state.value.tabs.drop(1).map { DatabaseConfig(it.key, it.name, it.icon ?: DatabaseConfig.DEFAULT_ICON) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NoterApp
                MainViewModel(app.repository, app.apiKeyStore.hasKey)
            }
        }
    }
}
