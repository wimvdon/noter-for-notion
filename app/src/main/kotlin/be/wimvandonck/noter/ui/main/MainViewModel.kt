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
import be.wimvandonck.noter.data.api.NotionErrors
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.data.notion.TokenKind
import be.wimvandonck.noter.data.store.AppSettings
import be.wimvandonck.noter.logic.ChipFilter
import be.wimvandonck.noter.logic.ChipGroup
import be.wimvandonck.noter.logic.DatabaseSetups
import be.wimvandonck.noter.logic.FilterChip
import be.wimvandonck.noter.logic.NoteLists
import be.wimvandonck.noter.logic.NoteSearch
import be.wimvandonck.noter.logic.NoteTarget
import be.wimvandonck.noter.logic.NoteTargets
import be.wimvandonck.noter.logic.PropertyLayouts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What a list of notes shows: Home's pinned notes, or a database's notes with its filters. */
data class NotesUiState(
    val ready: Boolean = false,
    val hasKey: Boolean = false,
    /** True on Home: its cards show which database a note is in ([sourceIcons]). */
    val isHome: Boolean = false,
    /** The filter chips above the notes, one row per property. */
    val chipGroups: List<ChipGroup> = emptyList(),
    val pinned: List<NoteSummary> = emptyList(),
    val others: List<NoteSummary> = emptyList(),
    val pinnedIds: Set<String> = emptySet(),
    /** Icons of the databases, by data source ID (for Home's cards). */
    val sourceIcons: Map<String, PageIcon> = emptyMap(),
    /** The property cards show when no properties were chosen for them (the first quick filter). */
    val cardProperty: String? = null,
    /** How each database shows its properties, with what its cards show (see PropertyLayouts.cardLayouts). */
    val propertyLayouts: Map<String, PropertyLayout> = emptyMap(),
    /** True while nothing is cached yet and the first download runs. */
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val pendingCount: Int = 0,
    val offline: Boolean = false,
    /** The data source shown (null on Home). */
    val databaseId: String? = null,
    /** Titles of linked pages, by page ID, for relation filters. */
    val chipTitles: Map<String, String> = emptyMap(),
    /** How the database shows its notes (null on Home). */
    val setup: DatabaseSetup? = null,
    /** How many properties filter the notes now. */
    val filterCount: Int = 0,
    /** Every property the database can filter by, with its values, for the Filters panel. */
    val filterOptions: List<ChipGroup> = emptyList(),
    /** Notion can't find the data source (deleted, in the trash, or no access). */
    val sourceMissing: Boolean = false,
) {
    /** Related databases whose pages name relation chips. */
    val chipRelationDatabases: Set<String>
        get() = (chipGroups + filterOptions).mapNotNull { it.property.takeIf { p -> p.type == "relation" }?.relationDatabaseId }.toSet()
}

/** Home: a search bar, then what is pinned (databases, notes and pages), then Notion's databases and pages. */
data class HomeUiState(
    val notes: NotesUiState = NotesUiState(),
    /**
     * Pages can be added at the top of the workspace: only with a personal access token (Notion
     * refuses it to an internal connection).
     */
    val canAddTopLevel: Boolean = false,
    val pinnedDatabases: List<PinnedDatabase> = emptyList(),
    /** The most recently edited databases and pages (null until known). */
    val recent: List<WorkspaceItem>? = null,
    /** Asking Notion for [recent] failed (an error from NotionErrors). */
    val recentError: String? = null,
    /** Where + can put a new note. */
    val targets: List<NoteTarget> = emptyList(),
)

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
 * The notes of data source [sourceId] ([db]) as [setup] shows them: filtered, sorted, its quick
 * filters on top (see DatabaseViewModel).
 */
internal fun setupContent(base: NotesUiState, setup: DatabaseSetup, db: DatabaseState?, settings: AppSettings, sourceId: String): NotesUiState {
    val notes = db?.notes.orEmpty()
    val content = DatabaseSetups.content(notes, db?.schema, setup, settings.pinned, settings.order[sourceId].orEmpty())
    val selected = DatabaseSetups.selection(setup, db?.schema)
    return base.copy(
        chipGroups = content.groups,
        pinned = content.pinned,
        others = content.others,
        cardProperty = setup.quickFilters.firstOrNull(),
        loading = db?.loaded != true,
        refreshing = db?.refreshing == true,
        error = db?.error,
        databaseId = sourceId,
        setup = setup,
        filterCount = content.filterCount,
        filterOptions = db?.schema?.properties.orEmpty().filter { it.isChipCandidate }.map { property ->
            ChipGroup(property, ChipFilter.chips(property, notes), selected[property.name].orEmpty())
        },
    )
}

/** The values selected in [groups] that a new note gets (the first of each property, where it can). */
internal fun presets(groups: List<ChipGroup>, selected: Map<String, Set<String>>): List<NoteProperty> =
    groups.mapNotNull { group ->
        val key = selected[group.property.name]?.firstOrNull() ?: return@mapNotNull null
        val chip = group.chips.firstOrNull { it.key == key } ?: return@mapNotNull null
        presetFor(group.property, chip)
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

/**
 * The icons of the databases Home knows, by the ID their notes are kept under (a data source; also
 * the database's), for the cards of pinned notes.
 */
internal fun sourceIcons(pinned: List<PinnedDatabase>, recent: List<WorkspaceItem>?, infos: Map<String, DatabaseInfo> = emptyMap()): Map<String, PageIcon> = buildMap {
    recent.orEmpty().filter { it.isDatabase }.forEach { item ->
        val icon = item.icon ?: return@forEach
        put(item.id, icon)
        item.sources.forEach { put(it.id, icon) }
    }
    pinned.forEach { pin ->
        val icon = pin.icon ?: return@forEach
        put(pin.id, icon)
        infos[pin.id]?.sources.orEmpty().forEach { put(it.id, icon) }
    }
}

class MainViewModel(
    private val repository: NotesRepository,
    hasKey: StateFlow<Boolean>,
    tokenKind: StateFlow<TokenKind?>,
) : ViewModel() {
    val events: Flow<RepoEvent> = repository.events

    private val recentError = MutableStateFlow<String?>(null)

    /** What Notion said about the pinned databases (their data sources, for +), by pinned ID. */
    private val infos = MutableStateFlow<Map<String, DatabaseInfo>>(emptyMap())

    private data class Status(val pending: Int, val offline: Boolean, val hasKey: Boolean, val kind: TokenKind?)

    private val status = combine(repository.pendingCount, repository.offline, hasKey, tokenKind, ::Status)

    val state: StateFlow<HomeUiState> = combine(
        repository.settingsFlow(),
        repository.databases,
        combine(repository.recent, recentError, infos, ::Triple),
        status,
    ) { settings, databases, (recent, error, known), (pending, offline, key, kind) ->
        val pinned = NoteLists.pinned(databases.values.flatMap { it.notes }, settings.pinned, settings.order[AppSettings.HOME].orEmpty())
        HomeUiState(
            notes = NotesUiState(
                ready = true,
                hasKey = key,
                isHome = true,
                pinned = pinned,
                pinnedIds = settings.pinned.toSet(),
                sourceIcons = sourceIcons(settings.pinnedDatabases, recent, known),
                propertyLayouts = PropertyLayouts.cardLayouts(settings.propertyLayouts, settings.setups),
                refreshing = databases.values.any { it.refreshing },
                pendingCount = pending,
                offline = offline,
            ),
            canAddTopLevel = kind == TokenKind.PERSONAL,
            pinnedDatabases = settings.pinnedDatabases,
            recent = recent,
            recentError = error,
            targets = NoteTargets.of(settings.pinnedDatabases, recent, known),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Home's list, at start and once a token is saved.
        viewModelScope.launch {
            hasKey.filter { it }.collect { refreshRecent() }
        }
        // What is kept about the pinned databases (a database of several data sources offers each to +).
        viewModelScope.launch {
            repository.settingsFlow().collect { settings ->
                settings.pinnedDatabases.filter { it.id !in infos.value }.forEach { pin ->
                    repository.databaseInfo(pin.id, fresh = false)?.let { info -> infos.update { it + (pin.id to info) } }
                }
            }
        }
    }

    private suspend fun refreshRecent() {
        try {
            repository.refreshRecent()
            recentError.value = null
        } catch (e: NotionException) {
            recentError.value = NotionErrors.of(e)
        }
    }

    /** Pull-to-refresh: sends what waits, refreshes the pinned notes' databases and the list. */
    fun refresh() {
        viewModelScope.launch {
            repository.flush()
            repository.pinnedSources().forEach { launch { repository.refresh(it, userAsked = true) } }
            refreshRecent()
        }
    }

    /** Preview text of each note's content, by page ID. */
    val previews: StateFlow<Map<String, String>> = repository.previews

    /** Fills in the previews of the notes on screen (from the phone's copies, else from Notion). */
    fun loadPreviews(notes: List<NoteSummary>) = repository.loadPreviews(notes)

    fun togglePin(id: String) = viewModelScope.launch { repository.togglePin(id) }

    fun togglePinDatabase(pin: PinnedDatabase) = viewModelScope.launch { repository.togglePinDatabase(pin) }

    /** Pins a database or page from a list (Home's or Search's), or unpins it. */
    fun togglePinItem(item: WorkspaceItem) = viewModelScope.launch {
        if (item.isDatabase) {
            repository.togglePinDatabase(PinnedDatabase(item.id, item.title, item.icon))
        } else {
            val note = item.note ?: return@launch
            repository.togglePin(repository.openNote(note))
        }
    }

    fun trash(id: String) = viewModelScope.launch { repository.trashNote(id) }

    /** Saves the order of Home's pinned notes after the user dragged them. */
    fun saveOrder(ids: List<String>) = viewModelScope.launch { repository.saveOrder(AppSettings.HOME, ids) }

    /** Creates a note in data source [databaseId] and calls [onCreated] with its ID. */
    fun createNote(databaseId: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val id = repository.createNote(databaseId) ?: return@launch
        onCreated(id)
    }

    /** Makes a page at the top of the workspace. */
    fun createPage(onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(repository.createPage(null))
    }

    // ---- Search ----

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()
    private var searchJob: Job? = null

    /** Back to an empty search (when leaving Search). */
    fun resetSearch() {
        searchJob?.cancel()
        _browse.value = BrowseState()
    }

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
                val found = repository.browse(query)
                // At once with what is known about the databases, then after asking Notion.
                _browse.update { it.copy(items = repository.groupByDatabase(found, ask = false), loading = false) }
                val grouped = repository.groupByDatabase(found, ask = true)
                _browse.update { if (it.query == query) it.copy(items = grouped) else it }
            } catch (e: NotionException) {
                _browse.update { it.copy(loading = false, error = NotionErrors.of(e)) }
            }
        }
    }

    /** Opens a page from a list (it becomes known here first). */
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
