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
import be.wimvandonck.noter.data.model.NotebookSort
import be.wimvandonck.noter.data.model.SortKind
import be.wimvandonck.noter.data.model.ViewResult
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
import be.wimvandonck.noter.logic.ChipGroup
import be.wimvandonck.noter.logic.FilterChip
import be.wimvandonck.noter.logic.Notebooks
import be.wimvandonck.noter.logic.NoteLists
import be.wimvandonck.noter.logic.NoteSearch
import be.wimvandonck.noter.logic.NoteSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A top-level destination: Pinned or a notebook. */
data class Tab(val key: String, val name: String, val icon: String?) {
    val isPinned: Boolean get() = key == Destinations.PINNED
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
    /** The filter chips above the notes, per property (on Pinned: one group, the sources). */
    val chipGroups: List<ChipGroup> = emptyList(),
    val pinned: List<NoteSummary> = emptyList(),
    val others: List<NoteSummary> = emptyList(),
    val pinnedIds: Set<String> = emptySet(),
    /** Icons of the sources on Pinned, by database ID (the first view's icon). */
    val databaseIcons: Map<String, String> = emptyMap(),
    /** The property cards show when no properties were chosen for them (the first quick filter). */
    val cardProperty: String? = null,
    /** How each database shows its properties, for the properties on the cards. */
    val propertyLayouts: Map<String, PropertyLayout> = emptyMap(),
    /** True while nothing is cached yet for this tab and the first download runs. */
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val pendingCount: Int = 0,
    val offline: Boolean = false,
    /** The database shown (null on Pinned). */
    val databaseId: String? = null,
    /** Titles of linked pages, by page ID, for relation filters. */
    val chipTitles: Map<String, String> = emptyMap(),
    /** The notebooks, for the navigation bar and "New note" on Pinned. */
    val views: List<DatabaseConfig> = emptyList(),
    /** The notebook shown, with its filters and sort (null on Pinned and a database on its own). */
    val notebook: DatabaseConfig? = null,
    /** How many properties filter the notebook now. */
    val filterCount: Int = 0,
    /** Every property the notebook can filter by, with its values, for the Filters panel. */
    val filterOptions: List<ChipGroup> = emptyList(),
    /** The Notion view the notebook follows doesn't exist any more (all notes are shown). */
    val viewMissing: Boolean = false,
) {
    val isHome: Boolean get() = currentTab == Destinations.PINNED

    /** Related databases whose pages name relation chips. */
    val chipRelationDatabases: Set<String>
        get() = (chipGroups + filterOptions).mapNotNull { it.property?.takeIf { p -> p.type == "relation" }?.relationDatabaseId }.toSet()
}

/** Something the overview tells the user once. */
sealed interface MainEvent {
    /** Dragging a note switched the notebook from [previous] to the custom order (Undo restores it). */
    data class SortedByHand(val notebookId: String, val previous: NotebookSort?) : MainEvent
}

/**
 * Search: the notes on this phone whose title matches ([notes], at once), then the pages and
 * databases Notion finds ([items]; without a query, the most recently edited ones).
 */
data class BrowseState(
    val query: String = "",
    val notes: List<NoteSummary> = emptyList(),
    val items: List<WorkspaceItem>? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * The notes of one database ([db]) as notebook [config] shows them: in its Notion view, filtered,
 * sorted, its quick filters on top. Also for a database opened on its own (a notebook that isn't
 * saved, see DatabaseViewModel).
 */
internal fun notebookContent(
    base: MainUiState,
    config: DatabaseConfig,
    db: DatabaseState?,
    viewResult: ViewResult?,
    settings: AppSettings,
    viewMissing: Boolean = false,
): MainUiState {
    val notes = db?.notes.orEmpty()
    val content = Notebooks.content(notes, db?.schema, config, viewResult, settings.pinned, settings.order[config.sourceId].orEmpty())
    val inView = if (config.viewId != null && config.followView) Notebooks.inView(notes, viewResult) else notes
    val selected = Notebooks.selection(config, db?.schema)
    return base.copy(
        chipGroups = content.groups,
        pinned = content.pinned,
        others = content.others,
        cardProperty = config.quickFilters.firstOrNull(),
        loading = db?.loaded != true,
        refreshing = db?.refreshing == true,
        error = db?.error,
        databaseId = config.sourceId,
        filterCount = content.filterCount,
        filterOptions = db?.schema?.properties.orEmpty().filter { it.isChipCandidate }.map { property ->
            ChipGroup(property, ChipFilter.chips(property, inView), selected[property.name].orEmpty())
        },
        viewMissing = viewMissing,
    )
}

/** The filter property a database opened on its own uses: a notebook's first quick filter, else status or select. */
internal fun defaultChipProperty(databaseId: String, settings: AppSettings, db: DatabaseState?): PropertySchema? {
    val schema = db?.schema ?: return null
    val chosen = settings.databases.firstOrNull { it.sourceId == databaseId && it.quickFilters.isNotEmpty() }?.quickFilters?.first()
    return chosen?.let { schema.property(it) }
        ?: schema.properties.firstOrNull { it.type == "status" }
        ?: schema.properties.firstOrNull { it.type == "select" }
}

/** The values selected in [groups] that a new note gets (the first of each property, where it can). */
internal fun presets(groups: List<ChipGroup>, selected: Map<String, Set<String>>): List<NoteProperty> =
    groups.mapNotNull { group ->
        val property = group.property ?: return@mapNotNull null
        val key = selected[property.name]?.firstOrNull() ?: return@mapNotNull null
        val chip = group.chips.firstOrNull { it.key == key } ?: return@mapNotNull null
        presetFor(property, chip)
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

    /** Sources picked on Pinned since the app started (a notebook keeps its own filters). */
    private val pinnedChips = MutableStateFlow<Set<String>>(emptySet())

    val events: Flow<RepoEvent> = repository.events

    private val _mainEvents = MutableSharedFlow<MainEvent>(extraBufferCapacity = 4)
    val mainEvents: SharedFlow<MainEvent> = _mainEvents

    private data class Status(val pending: Int, val offline: Boolean, val hasKey: Boolean, val kind: TokenKind?)

    private val status = combine(repository.pendingCount, repository.offline, hasKey, tokenKind, ::Status)

    private val views = combine(repository.viewResults, repository.missingViews) { results, missing -> results to missing }

    val state: StateFlow<MainUiState> = combine(
        repository.settingsFlow(),
        repository.databases,
        currentTab,
        combine(pinnedChips, views) { chips, v -> chips to v },
        status,
    ) { settings, databases, tab, (chips, viewState), (pending, offline, key, kind) ->
        val (viewResults, missingViews) = viewState
        val notebooks = settings.databases
        val tabKey = Destinations.valid(tab, notebooks)
        val tabs = listOf(Tab(Destinations.PINNED, "", null)) + notebooks.map { Tab(it.id, it.name, it.icon) }
        val base = MainUiState(
            ready = true,
            hasKey = key,
            canAddTopLevel = kind == TokenKind.PERSONAL,
            tabs = tabs,
            currentTab = tabKey,
            pinnedIds = settings.pinned.toSet(),
            databaseIcons = notebooks.reversed().associate { it.sourceId to it.icon },
            propertyLayouts = settings.propertyLayouts,
            pendingCount = pending,
            offline = offline,
            views = notebooks,
        )
        when (tabKey) {
            Destinations.PINNED -> {
                val content = NoteLists.home(
                    sources(settings, databases),
                    databases.mapValues { it.value.notes },
                    settings.pinned,
                    settings.order[AppSettings.HOME].orEmpty(),
                    chips,
                )
                base.copy(
                    chipGroups = if (content.chips.isEmpty()) emptyList() else listOf(ChipGroup(null, content.chips, chips)),
                    pinned = content.pinned,
                    loading = databases.values.any { !it.loaded } && content.pinned.isEmpty(),
                    refreshing = databases.values.any { it.refreshing },
                )
            }
            else -> {
                val notebook = notebooks.first { it.id == tabKey }
                val missing = notebook.viewId != null && notebook.viewId in missingViews
                // A view Notion doesn't have any more: all notes, until the notebook is set up again.
                val shown = if (missing) notebook.copy(followView = false) else notebook
                notebookContent(base, shown, databases[notebook.sourceId], viewResults[notebook.viewId], settings, missing)
                    .copy(notebook = notebook)
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
        viewModelScope.launch {
            val view = repository.currentSettings().databases.firstOrNull { it.id == key } ?: return@launch
            repository.refresh(view.sourceId, userAsked = false)
        }
    }

    /** Changes the notebook shown, kept on this phone (Notion's view isn't changed). */
    private fun updateNotebook(transform: (DatabaseConfig) -> DatabaseConfig) {
        val id = state.value.notebook?.id ?: return
        viewModelScope.launch {
            repository.updateSettings { s -> s.copy(databases = s.databases.map { if (it.id == id) transform(it) else it }) }
        }
    }

    /** A chip was tapped: a source on Pinned ([property] null), or a value of a notebook's property. */
    fun toggleChip(property: String?, key: String) {
        if (property == null) {
            pinnedChips.update { if (key in it) it - key else it + key }
        } else {
            updateNotebook { it.copy(selected = Notebooks.toggle(it.selected, property, key)) }
        }
    }

    fun clearChips() {
        if (state.value.isHome) pinnedChips.value = emptySet() else updateNotebook { it.copy(selected = emptyMap()) }
    }

    /** Shows the property's values as chips above the notes, or not. */
    fun setQuickFilter(property: String, shown: Boolean) =
        updateNotebook { it.copy(quickFilters = if (shown) (it.quickFilters - property) + property else it.quickFilters - property) }

    /** Follows the Notion view's filter (shows only its notes), or shows all notes of the database. */
    fun setFollowView(follow: Boolean) {
        updateNotebook { it.copy(followView = follow) }
        val view = state.value.notebook?.viewId ?: return
        if (follow) viewModelScope.launch { repository.refreshView(view) }
    }

    fun setSort(sort: NotebookSort?) = updateNotebook { it.copy(sort = sort) }

    /** Back to what the Notion view shows: its filter and order, no filters of the notebook's own. */
    fun resetToView() = updateNotebook { it.copy(followView = true, selected = emptyMap(), sort = null) }

    /** Undo after dragging switched a notebook to the custom order. */
    fun undoSortedByHand(event: MainEvent.SortedByHand) {
        viewModelScope.launch {
            repository.updateSettings { s -> s.copy(databases = s.databases.map { if (it.id == event.notebookId) it.copy(sort = event.previous) else it }) }
        }
    }

    fun refresh() {
        val ui = state.value
        viewModelScope.launch {
            repository.flush()
            when {
                ui.isHome -> repository.databases.value.keys.forEach { launch { repository.refresh(it, userAsked = true) } }
                else -> {
                    ui.databaseId?.let { repository.refresh(it, userAsked = true) }
                    ui.notebook?.takeIf { it.followView }?.viewId?.let { repository.refreshView(it) }
                }
            }
        }
    }

    /** Preview text of each note's content, by page ID. */
    val previews: StateFlow<Map<String, String>> = repository.previews

    /** Fills in the previews of the notes on screen (from the phone's copies, else from Notion). */
    fun loadPreviews(notes: List<NoteSummary>) = repository.loadPreviews(notes)

    fun togglePin(id: String) = viewModelScope.launch { repository.togglePin(id) }

    fun trash(id: String) = viewModelScope.launch { repository.trashNote(id) }

    /**
     * Saves the new order after the user dragged notes in [group] (the pinned ones or the others),
     * per database; Pinned has its own. In a notebook sorted another way, dragging switches it to
     * the custom order, starting from the order on screen.
     */
    fun saveOrder(group: List<String>) {
        val ui = state.value
        val key = if (ui.isHome) AppSettings.HOME else ui.databaseId ?: return
        val notebook = ui.notebook
        val switching = notebook != null && notebook.currentSort.kind != SortKind.CUSTOM
        val ids = if (!switching) {
            group
        } else if (ui.pinned.any { it.id in group }) {
            group + ui.others.map { it.id }
        } else {
            ui.pinned.map { it.id } + group
        }
        viewModelScope.launch {
            repository.saveOrder(key, ids)
            if (switching && notebook != null) {
                repository.updateSettings { s ->
                    s.copy(databases = s.databases.map { if (it.id == notebook.id) it.copy(sort = NotebookSort(SortKind.CUSTOM)) else it })
                }
                _mainEvents.tryEmit(MainEvent.SortedByHand(notebook.id, notebook.sort))
            }
        }
    }

    /**
     * Creates a note in [databaseId] (the current notebook's when null) and calls [onCreated] with
     * its ID. In a notebook with filters selected, the new note gets their values where it can; on
     * Pinned the new note is pinned so it shows up there.
     */
    fun createNote(databaseId: String? = null, onCreated: (String) -> Unit) {
        val ui = state.value
        val target = databaseId ?: ui.databaseId ?: return
        viewModelScope.launch {
            val notebook = ui.notebook
            val preset = if (target == ui.databaseId && notebook != null) {
                presets(ui.filterOptions, notebook.selected.mapValues { it.value.toSet() })
            } else {
                emptyList()
            }
            val id = repository.createNote(target, preset) ?: return@launch
            if (ui.isHome) repository.togglePin(id)
            onCreated(id)
        }
    }

    /** Makes a page on its own: inside page [parentPageId], or at the top of the workspace. */
    fun createPage(parentPageId: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repository.createPage(parentPageId)) }
    }

    // ---- Browse ----

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()
    private var searchJob: Job? = null

    /**
     * Searches the notes here at once, and Notion's pages and databases after a short pause in
     * typing (each letter isn't a request).
     */
    fun search(query: String) {
        val notes = NoteSearch.matches(repository.databases.value.values.flatMap { it.notes }, query)
        _browse.update { it.copy(query = query, notes = notes, loading = true, error = null) }
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
