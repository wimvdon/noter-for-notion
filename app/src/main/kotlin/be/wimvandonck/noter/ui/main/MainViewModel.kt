package be.wimvandonck.noter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.data.DatabaseState
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.RepoEvent
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.data.notion.TokenKind
import be.wimvandonck.noter.data.store.AppSettings
import be.wimvandonck.noter.logic.ChipFilter
import be.wimvandonck.noter.logic.Destinations
import be.wimvandonck.noter.logic.FilterChip
import be.wimvandonck.noter.logic.NoteLists
import be.wimvandonck.noter.logic.NoteSource
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
import java.time.LocalDate

/** A top-level destination: Pinned, Browse or a view. */
data class Tab(val key: String, val name: String, val icon: String?) {
    val isPinned: Boolean get() = key == Destinations.PINNED
    val isBrowse: Boolean get() = key == Destinations.BROWSE
}

data class MainUiState(
    val ready: Boolean = false,
    val hasKey: Boolean = false,
    /**
     * Pages can be added at the top of the workspace: only with a personal access token (Notion
     * refuses it to an internal connection).
     */
    val canAddTopLevel: Boolean = false,
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
    /** The database a relation filter links to (its pages' titles name the chips), else null. */
    val chipRelationDatabase: String? = null,
    /** Titles of linked pages, by page ID, for relation filters. */
    val chipTitles: Map<String, String> = emptyMap(),
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
        chipRelationDatabase = chipSchema?.takeIf { it.type == "relation" }?.relationDatabaseId,
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
internal fun presetFor(schema: PropertySchema, chip: FilterChip, today: LocalDate = LocalDate.now()): NoteProperty? {
    val value = when (schema.type) {
        "select", "status" -> PropertyValue.Select(schema.options.firstOrNull { it.name == chip.key })
        "multi_select" -> PropertyValue.MultiSelect(listOfNotNull(schema.options.firstOrNull { it.name == chip.key }))
        "people" -> PropertyValue.People(listOf(NotionUser(chip.key, chip.label)))
        "checkbox" -> PropertyValue.Checkbox(chip.key == ChipFilter.CHECKED)
        "relation" -> PropertyValue.Relation(listOf(chip.key))
        // A note made while "Today" is selected is for today; other date ranges have no one date.
        "date" -> if (chip.key == ChipFilter.TODAY) PropertyValue.Date(today.toString()) else return null
        // Computed by Notion (created by, formulas): nothing to fill in.
        else -> return null
    }
    return NoteProperty(schema.name, schema.type, value)
}

class MainViewModel(
    private val repository: NotesRepository,
    hasKey: StateFlow<Boolean>,
    tokenKind: StateFlow<TokenKind?>,
) : ViewModel() {
    private val chipTitles = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Loads the titles of a related database's pages, for the chips of a relation filter. */
    fun loadChipTitles(databaseId: String) = viewModelScope.launch {
        val pages = repository.relatedPages(databaseId) ?: return@launch
        chipTitles.update { it + pages.associate { page -> page.id to page.title } }
    }

    private val currentTab = MutableStateFlow(Destinations.PINNED)

    /** Filters the user picked per destination since the app started (else a view's own). */
    private val selectedChips = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    val events: Flow<RepoEvent> = repository.events

    private data class Status(val pending: Int, val offline: Boolean, val hasKey: Boolean, val kind: TokenKind?)

    private val status = combine(repository.pendingCount, repository.offline, hasKey, tokenKind, ::Status)

    val state: StateFlow<MainUiState> = combine(
        repository.settingsFlow(),
        repository.databases,
        currentTab,
        selectedChips,
        status,
    ) { settings, databases, tab, chipsByTab, (pending, offline, key, kind) ->
        val views = settings.databases
        val tabKey = Destinations.valid(tab, views)
        val tabs = listOf(Tab(Destinations.PINNED, "", null), Tab(Destinations.BROWSE, "", null)) +
            views.map { Tab(it.id, it.name, it.icon) }
        val base = MainUiState(
            ready = true,
            hasKey = key,
            canAddTopLevel = kind == TokenKind.PERSONAL,
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
    }.combine(chipTitles) { state, titles -> state.copy(chipTitles = titles) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

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
                MainViewModel(app.repository, app.apiKeyStore.hasKey, app.apiKeyStore.kind)
            }
        }
    }
}
