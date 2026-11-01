package be.wimvandonck.noter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.data.DatabaseState
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.ui.common.DatabaseCreation
import be.wimvandonck.noter.data.RepoEvent
import be.wimvandonck.noter.data.api.NotionErrors
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSort
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.data.store.AppSettings
import be.wimvandonck.noter.logic.ChipFilter
import be.wimvandonck.noter.logic.ChipGroup
import be.wimvandonck.noter.logic.DatabaseSetups
import be.wimvandonck.noter.logic.FilterChip
import be.wimvandonck.noter.logic.NoteLists
import be.wimvandonck.noter.logic.NoteSearch
import be.wimvandonck.noter.logic.NoteTargets
import be.wimvandonck.noter.logic.HiddenOnHome
import be.wimvandonck.noter.logic.PageTree
import be.wimvandonck.noter.logic.PropertyLayouts
import be.wimvandonck.noter.logic.SearchKind
import be.wimvandonck.noter.logic.SearchResults
import be.wimvandonck.noter.logic.SharedContent
import be.wimvandonck.noter.logic.TargetGroup
import be.wimvandonck.noter.logic.TreeNode
import be.wimvandonck.noter.logic.Workspace
import be.wimvandonck.noter.logic.WorkspaceGroup
import be.wimvandonck.noter.logic.Workspaces
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
import kotlinx.coroutines.coroutineScope
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
    /** What the Sort menu offers (`DatabaseSetups.sortChoices`). */
    val sortChoices: List<NoteSort> = emptyList(),
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
    /** The profile picture of the person the token acts as, shown in place of Settings' cogwheel (null: the cogwheel). */
    val avatarUrl: String? = null,
    val pinnedDatabases: List<PinnedDatabase> = emptyList(),
    /** The most recently edited databases and pages (null until known), for + and as [tree]. */
    val recent: List<WorkspaceItem>? = null,
    /** [recent] as Home shows it: a tree of two levels (`logic/PageTree.kt`). */
    val tree: List<TreeNode> = emptyList(),
    /** The entries of [tree] whose databases and pages show below them, by ID. */
    val expanded: Set<String> = emptySet(),
    /** How many entries of the tree's top level show (each workspace's, with several); "Show more" adds more. */
    val treeLimit: Int = PageTree.PAGE_SIZE,
    /** Asking Notion for [recent] failed (an error from NotionErrors). */
    val recentError: String? = null,
    /** Where + can put a new note or page, by workspace. */
    val targetGroups: List<TargetGroup> = emptyList(),
    /** There are tokens for more than one workspace: lists say which workspace things are in. */
    val severalWorkspaces: Boolean = false,
    /** [recent] by workspace (with several), each as a tree. */
    val treeGroups: List<WorkspaceGroup<TreeNode>> = emptyList(),
    /** The workspaces folded away on Home ([Workspace.id]). */
    val collapsed: Set<String> = emptySet(),
    /** The workspace's name of each pinned database, by its ID (with several workspaces). */
    val pinnedWorkspaces: Map<String, String> = emptyMap(),
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
 * Search as shown (#60): the notes here and Notion's results in one list, best matches first
 * ([results]; null while there is nothing yet), filtered by [kind] and [workspace].
 */
data class SearchUiState(
    val query: String = "",
    val results: List<WorkspaceItem>? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val kind: SearchKind = SearchKind.ALL,
    /** Only this workspace's results ([Workspace.id]); null: every workspace. */
    val workspace: String? = null,
    /** The workspaces to filter by (only with several). */
    val workspaces: List<Workspace> = emptyList(),
    /** The workspace's name of each result, by its ID (with several workspaces). */
    val workspaceNames: Map<String, String> = emptyMap(),
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
        sortChoices = DatabaseSetups.sortChoices(db?.schema),
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
    /** Every workspace with a token, the main one first. */
    workspaces: Flow<List<Workspace>>,
    avatarUrl: StateFlow<String?>,
    /** The workspaces folded away on Home, and keeping a change to them. */
    collapsed: StateFlow<Set<String>>,
    private val setCollapsed: (Set<String>) -> Unit,
    /** What another app shared and isn't saved yet (#63, NoterApp.shares). */
    private val shares: MutableStateFlow<SharedContent?> = MutableStateFlow(null),
) : ViewModel() {
    val events: Flow<RepoEvent> = repository.events

    private val recentError = MutableStateFlow<String?>(null)

    /** What Notion said about the pinned databases (their data sources, for +), by pinned ID. */
    private val infos = MutableStateFlow<Map<String, DatabaseInfo>>(emptyMap())

    /** Pull-to-refresh is busy, until everything it asked for is back (#93). */
    private val pulling = MutableStateFlow(false)

    private data class Status(val pending: Int, val offline: Boolean, val hasKey: Boolean, val avatarUrl: String?, val pulling: Boolean)

    private val status = combine(repository.pendingCount, repository.offline, hasKey, avatarUrl, pulling, ::Status)

    /** Home's tree: the entries opened, and how many of its top level show. */
    private val expanded = MutableStateFlow<Set<String>>(emptySet())
    private val treeLimit = MutableStateFlow(PageTree.PAGE_SIZE)

    private data class Spaces(
        val workspaces: List<Workspace>,
        val owners: Map<String, String>,
        val collapsed: Set<String>,
        val expanded: Set<String>,
        val treeLimit: Int,
    )

    private val workspaceInfo = combine(workspaces, repository.owners, collapsed, expanded, treeLimit, ::Spaces)

    val state: StateFlow<HomeUiState> = combine(
        repository.settingsFlow(),
        repository.databases,
        combine(repository.recent, recentError, infos, ::Triple),
        status,
        workspaceInfo,
    ) { settings, databases, (recent, error, known), (pending, offline, key, avatar, pull), (spaces, owners, folded, opened, limit) ->
        val pinned = NoteLists.pinned(databases.values.flatMap { it.notes }, settings.pinned, settings.order[AppSettings.HOME].orEmpty())
        val several = spaces.size > 1
        val hidden = HiddenOnHome.ids(settings.hiddenOnHome)
        HomeUiState(
            notes = NotesUiState(
                ready = true,
                hasKey = key,
                isHome = true,
                pinned = pinned,
                pinnedIds = settings.pinned.toSet(),
                sourceIcons = sourceIcons(settings.pinnedDatabases, recent, known),
                propertyLayouts = PropertyLayouts.cardLayouts(settings.propertyLayouts, settings.setups),
                refreshing = pull || databases.values.any { it.refreshing },
                pendingCount = pending,
                offline = offline,
            ),
            avatarUrl = avatar,
            pinnedDatabases = settings.pinnedDatabases,
            recent = recent,
            tree = if (several) emptyList() else PageTree.build(recent.orEmpty(), hidden),
            expanded = opened,
            treeLimit = limit,
            recentError = error,
            targetGroups = NoteTargets.grouped(NoteTargets.of(settings.pinnedDatabases, recent, known), spaces, owners),
            severalWorkspaces = several,
            treeGroups = if (several) {
                Workspaces.group(recent.orEmpty(), owners, spaces, SearchResults::idsOf).map { WorkspaceGroup(it.workspace, PageTree.build(it.items, hidden)) }
            } else {
                emptyList()
            },
            collapsed = folded,
            pinnedWorkspaces = if (several) {
                settings.pinnedDatabases.mapNotNull { pin ->
                    val ids = listOf(pin.id) + known[pin.id]?.sources.orEmpty().map { it.id }
                    Workspaces.ownerOf(ids, owners, spaces)?.let { pin.id to it.name }
                }.toMap()
            } else {
                emptyMap()
            },
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

    /**
     * Pull-to-refresh: sends what waits, refreshes the pinned notes' databases and the list. The
     * indicator stays until all of it is done (the list can take a few seconds).
     */
    fun refresh() {
        if (pulling.value) return
        pulling.value = true
        viewModelScope.launch {
            try {
                coroutineScope {
                    repository.flush()
                    repository.pinnedSources().forEach { launch { repository.refresh(it, userAsked = true) } }
                    refreshRecent()
                }
            } finally {
                pulling.value = false
            }
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


    /** Hides a database or page from Home's list (#108, after the user confirmed); Search still finds it. */
    fun hideOnHome(item: WorkspaceItem) = viewModelScope.launch { repository.hideOnHome(item) }

    /** Saves the order of Home's pinned databases after the user dragged them. */
    fun savePinnedDatabaseOrder(ids: List<String>) = viewModelScope.launch { repository.savePinnedDatabaseOrder(ids) }

    /** Saves the order of Home's pinned notes after the user dragged them. */
    fun saveOrder(ids: List<String>) = viewModelScope.launch { repository.saveOrder(AppSettings.HOME, ids) }

    /** Creates a note in data source [databaseId] and calls [onCreated] with its ID. */
    fun createNote(databaseId: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val id = repository.createNote(databaseId) ?: return@launch
        onCreated(id)
    }

    /** Makes a page at the top of [workspace] (null: the main one). */
    fun createPage(workspace: String?, onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(repository.createPage(null, workspace = workspace))
    }

    /** Shows the databases and pages inside Home's tree entry [id], or hides them again. */
    fun toggleExpanded(id: String) = expanded.update { if (id in it) it - id else it + id }

    /** Shows more of the top level of Home's tree. */
    fun showMore() = treeLimit.update { it + PageTree.PAGE_SIZE }

    // ---- Shared from another app (#63) ----

    /** What another app shared, waiting for the user to say where it goes; null when nothing is. */
    val sharing: StateFlow<SharedContent?> = shares.asStateFlow()

    private val _savingShare = MutableStateFlow(false)

    /** True while a share is saved (its page downloaded, the note made). */
    val savingShare: StateFlow<Boolean> = _savingShare.asStateFlow()

    /** Leaves the share unsaved. */
    fun dismissShare() {
        shares.value = null
    }

    /**
     * Saves the share as a note in data source [databaseId], or as a page at the top of
     * [workspace] when that is null, then opens it with [onCreated]. When the note can't be made
     * the share stays, so another place can be chosen.
     */
    fun saveShared(databaseId: String?, workspace: String?, onCreated: (String) -> Unit) {
        val shared = shares.value ?: return
        if (_savingShare.value) return
        _savingShare.value = true
        viewModelScope.launch {
            try {
                val id = repository.saveShared(shared, databaseId, workspace) ?: return@launch
                shares.value = null
                onCreated(id)
            } finally {
                _savingShare.value = false
            }
        }
    }

    /** The New database dialog (#71): a database at the top of a workspace. */
    val newDatabase = DatabaseCreation(viewModelScope, repository)

    /** Opens the New database dialog for the top of [workspace] (null: the main one). */
    fun openNewDatabase(workspace: String?) = newDatabase.open(null, workspace)

    /** Folds workspace [id] away on Home, or unfolds it. */
    fun toggleCollapsed(id: String) {
        val now = state.value.collapsed
        setCollapsed(if (id in now) now - id else now + id)
    }

    // ---- Search ----

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()
    private var searchJob: Job? = null
    private val searchFilter = MutableStateFlow(SearchKind.ALL to null as String?)

    val search: StateFlow<SearchUiState> = combine(_browse, searchFilter, workspaces, repository.owners) { browse, (kind, workspace), spaces, owners ->
        val several = spaces.size > 1
        // A workspace that was removed meanwhile doesn't filter anymore.
        val shownWorkspace = workspace?.takeIf { id -> several && spaces.any { it.id == id } }
        val merged = SearchResults.merge(browse.query, browse.notes, browse.items)
        SearchUiState(
            query = browse.query,
            results = if (browse.items == null && merged.isEmpty()) null else SearchResults.filter(merged, kind, shownWorkspace, owners, spaces),
            loading = browse.loading,
            error = browse.error,
            kind = kind,
            workspace = shownWorkspace,
            workspaces = if (several) spaces else emptyList(),
            workspaceNames = if (several) {
                merged.mapNotNull { item -> Workspaces.ownerOf(SearchResults.idsOf(item), owners, spaces)?.let { item.id to it.name } }.toMap()
            } else {
                emptyMap()
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /** Shows only [kind] in Search (again: everything). */
    fun toggleSearchKind(kind: SearchKind) = searchFilter.update { (now, ws) -> (if (now == kind) SearchKind.ALL else kind) to ws }

    /** Shows only workspace [id]'s results in Search (again: every workspace). */
    fun toggleSearchWorkspace(id: String) = searchFilter.update { (kind, now) -> kind to (if (now == id) null else id) }

    /** Back to an empty search (when leaving Search). */
    fun resetSearch() {
        searchJob?.cancel()
        _browse.value = BrowseState()
        searchFilter.value = SearchKind.ALL to null
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
                val keys = app.apiKeyStore
                MainViewModel(
                    app.repository,
                    keys.hasKey,
                    combine(keys.main, keys.extras) { main, extras -> Workspaces.of(listOf(main) + extras) },
                    keys.avatarUrl,
                    keys.collapsed,
                    keys::setCollapsed,
                    app.shares,
                )
            }
        }
    }
}
