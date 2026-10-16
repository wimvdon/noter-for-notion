package com.impca201.noter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.impca201.noter.NoterApp
import com.impca201.noter.data.DatabaseState
import com.impca201.noter.data.NotesRepository
import com.impca201.noter.data.RepoEvent
import com.impca201.noter.data.api.NotionException
import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.model.NotionUser
import com.impca201.noter.data.model.PropertyLayout
import com.impca201.noter.data.model.PropertySchema
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.data.model.WorkspaceItem
import com.impca201.noter.data.store.AppSettings
import com.impca201.noter.logic.ChipFilter
import com.impca201.noter.logic.Destinations
import com.impca201.noter.logic.FilterChip
import com.impca201.noter.logic.NoteLists
import com.impca201.noter.logic.NoteSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A top-level destination: Pinned, Browse or a view. */
data class Tab(val key: String, val name: String, val icon: String?) {
    val isPinned: Boolean get() = key == Destinations.PINNED
    val isBrowse: Boolean get() = key == Destinations.BROWSE
}

data class MainUiState(
    val ready: Boolean = false,
    val hasKey: Boolean = false,
    val tabs: List<Tab> = emptyList(),
    val currentTab: String = Destinations.PINNED,
    val chips: List<FilterChip> = emptyList(),
    val selectedChips: Set<String> = emptySet(),
    val pinned: List<NoteSummary> = emptyList(),
    val others: List<NoteSummary> = emptyList(),
    val pinnedIds: Set<String> = emptySet(),
    /** Icons of the sources on Pinned, by database ID (the first view's icon). */
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
    /** The database of the view shown (null on Pinned and Browse). */
    val databaseId: String? = null,
    /** The views, for the navigation bar and "New note" on Pinned and Browse. */
    val views: List<DatabaseConfig> = emptyList(),
) {
    val isHome: Boolean get() = currentTab == Destinations.PINNED
    val isBrowse: Boolean get() = currentTab == Destinations.BROWSE
}

/** The Browse list: pages and databases shared with the integration. */
data class BrowseState(
    val query: String = "",
    val items: List<WorkspaceItem>? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * The note list of one database ([db]), filtered by [chipSchema] with [selected] filters, for a
 * view or a database opened from Browse.
 */
internal fun databaseContent(
    base: MainUiState,
    databaseId: String,
    db: DatabaseState?,
    chipSchema: PropertySchema?,
    selected: Set<String>,
    settings: AppSettings,
): MainUiState {
    val content = NoteLists.database(
        db?.notes.orEmpty(),
        chipSchema,
        selected,
        settings.pinned,
        settings.order[databaseId].orEmpty(),
    )
    return base.copy(
        chips = content.chips,
        selectedChips = selected,
        pinned = content.pinned,
        others = content.others,
        chipProperty = chipSchema?.name,
        loading = db?.loaded != true,
        refreshing = db?.refreshing == true,
        error = db?.error,
        databaseId = databaseId,
    )
}

/** The filter property a database uses when it isn't a view: a view's choice, else status or select. */
internal fun defaultChipProperty(databaseId: String, settings: AppSettings, db: DatabaseState?): PropertySchema? {
    val schema = db?.schema ?: return null
    val chosen = settings.databases.firstOrNull { it.sourceId == databaseId && it.chipProperty != null }?.chipProperty
    return chosen?.let { schema.property(it) }
        ?: schema.properties.firstOrNull { it.type == "status" }
        ?: schema.properties.firstOrNull { it.type == "select" }
}

/** A filter property's value for a new note made while filter [key] is selected. */
internal fun presetFor(schema: PropertySchema, chip: FilterChip): NoteProperty? {
    val value = when (schema.type) {
        "select", "status" -> PropertyValue.Select(schema.options.firstOrNull { it.name == chip.key })
        "multi_select" -> PropertyValue.MultiSelect(listOfNotNull(schema.options.firstOrNull { it.name == chip.key }))
        "people" -> PropertyValue.People(listOf(NotionUser(chip.key, chip.label)))
        "checkbox" -> PropertyValue.Checkbox(chip.key == ChipFilter.CHECKED)
        else -> return null
    }
    return NoteProperty(schema.name, schema.type, value)
}

class MainViewModel(private val repository: NotesRepository, hasKey: StateFlow<Boolean>) : ViewModel() {
    private val currentTab = MutableStateFlow(Destinations.PINNED)

    /** Filters the user picked per destination since the app started (else a view's own). */
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
        val views = settings.databases
        val tabKey = Destinations.valid(tab, views)
        val tabs = listOf(Tab(Destinations.PINNED, "", null), Tab(Destinations.BROWSE, "", null)) +
            views.map { Tab(it.id, it.name, it.icon) }
        val base = MainUiState(
            ready = true,
            hasKey = key,
            tabs = tabs,
            currentTab = tabKey,
            pinnedIds = settings.pinned.toSet(),
            databaseIcons = views.reversed().associate { it.sourceId to it.icon },
            propertyLayouts = settings.propertyLayouts,
            pendingCount = pending,
            offline = offline,
            views = views,
        )
        when (tabKey) {
            Destinations.BROWSE -> base
            Destinations.PINNED -> {
                val selected = chipsByTab[tabKey].orEmpty()
                val content = NoteLists.home(
                    sources(settings, databases),
                    databases.mapValues { it.value.notes },
                    settings.pinned,
                    settings.order[AppSettings.HOME].orEmpty(),
                    selected,
                )
                base.copy(
                    chips = content.chips,
                    selectedChips = selected,
                    pinned = content.pinned,
                    loading = databases.values.any { !it.loaded } && content.pinned.isEmpty(),
                    refreshing = databases.values.any { it.refreshing },
                )
            }
            else -> {
                val view = views.first { it.id == tabKey }
                val db = databases[view.sourceId]
                val chipSchema = view.chipProperty?.let { db?.schema?.property(it) }
                databaseContent(base, view.sourceId, db, chipSchema, Destinations.selectedFilters(view, chipsByTab[tabKey]), settings)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    /** Every source of notes, named after its first view, else its database title ("" for the pages). */
    private fun sources(settings: AppSettings, databases: Map<String, DatabaseState>): List<NoteSource> {
        val fromViews = settings.databases.distinctBy { it.sourceId }.map { NoteSource(it.sourceId, it.name) }
        val others = databases.filterKeys { id -> fromViews.none { it.id == id } }.map { (id, db) ->
            NoteSource(id, if (id == NotesRepository.PAGES) "" else db.schema?.title.orEmpty())
        }
        return fromViews + others
    }

    fun selectTab(key: String) {
        currentTab.value = key
        if (key == Destinations.BROWSE && _browse.value.items == null) search("")
        viewModelScope.launch {
            val view = repository.currentSettings().databases.firstOrNull { it.id == key } ?: return@launch
            repository.refresh(view.sourceId, userAsked = false)
        }
    }

    fun toggleChip(key: String) {
        val ui = state.value
        val tab = ui.currentTab
        selectedChips.update { map ->
            val current = ui.selectedChips
            map + (tab to if (key in current) current - key else current + key)
        }
    }

    fun clearChips() {
        val tab = state.value.currentTab
        selectedChips.update { it + (tab to emptySet()) }
    }

    fun refresh() {
        val ui = state.value
        viewModelScope.launch {
            repository.flush()
            when {
                ui.isBrowse -> search(_browse.value.query)
                ui.isHome -> repository.databases.value.keys.forEach { launch { repository.refresh(it, userAsked = true) } }
                else -> ui.databaseId?.let { repository.refresh(it, userAsked = true) }
            }
        }
    }

    /** Preview text of each note's content, by page ID. */
    val previews: StateFlow<Map<String, String>> = repository.previews

    /** Fills in the previews of the notes on screen (from the phone's copies, else from Notion). */
    fun loadPreviews(notes: List<NoteSummary>) = repository.loadPreviews(notes)

    fun togglePin(id: String) = viewModelScope.launch { repository.togglePin(id) }

    fun trash(id: String) = viewModelScope.launch { repository.trashNote(id) }

    /** Saves the new order after the user moved notes (per database, and Pinned has its own). */
    fun saveOrder(ids: List<String>) {
        val ui = state.value
        val key = if (ui.isHome) AppSettings.HOME else ui.databaseId ?: return
        viewModelScope.launch { repository.saveOrder(key, ids) }
    }

    /**
     * Creates a note in [databaseId] (the current view's when null) and calls [onCreated] with its
     * ID. In a view with filters selected, the new note gets the first selected filter's value; on
     * Pinned the new note is pinned so it shows up there.
     */
    fun createNote(databaseId: String? = null, onCreated: (String) -> Unit) {
        val ui = state.value
        val target = databaseId ?: ui.databaseId ?: return
        viewModelScope.launch {
            val preset = if (target == ui.databaseId) presetFromChips(target, ui) else emptyList()
            val id = repository.createNote(target, preset) ?: return@launch
            if (ui.isHome) repository.togglePin(id)
            onCreated(id)
        }
    }

    /** Makes a page on its own: inside page [parentPageId], or at the top of the workspace. */
    fun createPage(parentPageId: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repository.createPage(parentPageId)) }
    }

    private fun presetFromChips(databaseId: String, ui: MainUiState): List<NoteProperty> {
        val chipKey = ui.selectedChips.firstOrNull() ?: return emptyList()
        val schema = ui.chipProperty?.let { repository.databases.value[databaseId]?.schema?.property(it) } ?: return emptyList()
        val chip = ui.chips.firstOrNull { it.key == chipKey } ?: return emptyList()
        return listOfNotNull(presetFor(schema, chip))
    }

    // ---- Browse ----

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()
    private var searchJob: Job? = null

    /** Searches pages and databases; typing waits for a short pause before asking Notion. */
    fun search(query: String) {
        _browse.update { it.copy(query = query, loading = true, error = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(SEARCH_PAUSE_MS)
            try {
                val items = repository.browse(query)
                _browse.update { it.copy(items = items, loading = false) }
            } catch (e: NotionException) {
                _browse.update { it.copy(loading = false, error = e.message.orEmpty()) }
            }
        }
    }

    /** Opens a page from Browse (it becomes known here first). */
    fun openItem(item: WorkspaceItem, onOpenNote: (String) -> Unit) {
        val note = item.note ?: return
        viewModelScope.launch { onOpenNote(repository.openNote(note)) }
    }

    companion object {
        private const val SEARCH_PAUSE_MS = 400L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NoterApp
                MainViewModel(app.repository, app.apiKeyStore.hasKey)
            }
        }
    }
}
