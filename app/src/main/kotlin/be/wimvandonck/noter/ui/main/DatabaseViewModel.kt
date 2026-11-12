package be.wimvandonck.noter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.api.NotionErrors
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.model.CardLayout
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionView
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.QuickAction
import be.wimvandonck.noter.logic.DatabaseEdit
import be.wimvandonck.noter.logic.EditedProperty
import be.wimvandonck.noter.logic.PropertyKind
import be.wimvandonck.noter.logic.SchemaEdits
import be.wimvandonck.noter.logic.PropertyLayouts
import be.wimvandonck.noter.logic.QuickActions
import be.wimvandonck.noter.logic.NotionViews
import be.wimvandonck.noter.logic.DatabaseSetups
import be.wimvandonck.noter.data.ViewStatus
import be.wimvandonck.noter.data.store.ViewsCache
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import be.wimvandonck.noter.logic.ReadOnlyBlocks

data class DatabaseUiState(
    /** The database's name (of the database, for one of several data sources). */
    val title: String = "",
    val icon: PageIcon? = null,
    val content: NotesUiState = NotesUiState(),
    /** The database is pinned on Home. */
    val pinned: Boolean = false,
    /** A database with several data sources: each of them, for tabs (null: just one). */
    val sources: List<PageRef>? = null,
    /** The data source shown. */
    val sourceId: String = "",
    /** What the cards show now (see PropertyLayouts.cards), for the view settings. */
    val cards: CardLayout = CardLayout(),
    /** The data source's properties (the cards can show some). */
    val properties: List<PropertySchema> = emptyList(),
    /** Notion's web link to the database, for Open in Notion, Share and editing its views. */
    val notionUrl: String = "",
    /** The data source's Notion views shown as tabs, in the user's order. */
    val views: List<NotionView> = emptyList(),
    /** Its Notion views hidden from the tabs. */
    val hiddenViews: List<NotionView> = emptyList(),
    /** The view shown (null: no views known yet). */
    val viewId: String? = null,
)

/**
 * Edit database while it is open: the data source's [properties] when it opened (what the edits
 * are compared with, so properties loading later never read as removed), whether Notion is saving
 * it, and what went wrong ([error]: see NotionErrors).
 */
data class DatabaseEditState(val properties: List<PropertySchema>, val saving: Boolean = false, val error: String? = null)

/** What quick actions can choose from (#119): people, and pages of related databases by database ID. */
data class QuickLookups(
    val users: List<NotionUser> = emptyList(),
    val pages: Map<String, List<PageRef>> = emptyMap(),
)

/** Something the database's screen tells the user once. */
sealed interface DatabaseEvent {
    /** Quick action [action] was deleted from place [index] (Undo puts it back). */
    data class QuickActionDeleted(val action: QuickAction, val index: Int) : DatabaseEvent
}

/**
 * A database: its notes as its Notion views show them, one tab each (the order and the hidden
 * ones kept on this phone), and the cards and quick actions kept for it ([DatabaseSetup], per
 * data source). [openedId] is a data source (from Home or Search) or a database (a link, or a
 * database of several data sources); a database with several data sources shows one at a time,
 * with tabs.
 */
class DatabaseViewModel(
    private val openedId: String,
    private val repository: NotesRepository,
    hasKey: StateFlow<Boolean>,
) : ViewModel() {
    private val source = MutableStateFlow(openedId)
    private val sources = MutableStateFlow<List<PageRef>?>(null)

    /** The database's own name and icon, when Notion says (a database rather than a data source). */
    private val label = MutableStateFlow<Pair<String, PageIcon?>?>(null)

    /** The database itself, for links to Notion (a data source's ID doesn't open in Notion). */
    private val databaseLink = MutableStateFlow<String?>(null)

    private data class Shown(val id: String, val sources: List<PageRef>?, val label: Pair<String, PageIcon?>?, val link: String?)
    private val sourceId: String get() = source.value

    private val _events = MutableSharedFlow<DatabaseEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DatabaseEvent> = _events

    private data class Status(val pending: Int, val offline: Boolean, val hasKey: Boolean, val missing: Set<String>)

    private val status = combine(repository.pendingCount, repository.offline, hasKey, repository.missingSources, ::Status)

    private data class Views(val all: Map<String, ViewsCache>, val status: Map<String, ViewStatus>)

    val state: StateFlow<DatabaseUiState> = combine(
        repository.settingsFlow(),
        combine(repository.databases, combine(repository.views, repository.viewStatus, ::Views), ::Pair),
        combine(source, sources, label, databaseLink, ::Shown),
        status,
    ) { settings, (databases, views), (id, list, named, link), (pending, offline, key, missing) ->
        val db = databases[id]
        val setup = DatabaseSetups.of(settings.setups, id)
        val sourceViews = views.all[id]
        val (shown, hidden) = arrangedViews(sourceViews, settings, id)
        val viewId = NotionViews.chosen(choiceOf(settings, id), shown)
        val listStatus = views.status[id]
        val resultStatus = viewId?.let { views.status["$id/$it"] }
        val members = when {
            viewId != null -> members(sourceViews, viewId, db?.notes.orEmpty())
            // Notion has no views of these notes to show (all of them forms or dashboards): every note, last edited first.
            sourceViews != null && sourceViews.fetchedMs > 0 -> db?.notes.orEmpty().sortedByDescending { it.lastEditedTime }.map { it.id }
            else -> null
        }
        val base = NotesUiState(
            ready = true,
            hasKey = key,
            pinnedIds = settings.pinned.toSet(),
            propertyLayouts = PropertyLayouts.cardLayouts(settings.propertyLayouts, settings.setups + (id to setup)),
            pendingCount = pending,
            offline = offline,
            sourceMissing = id.replace("-", "") in missing,
        )
        val known = repository.knownItem(openedId)
        DatabaseUiState(
            title = named?.first ?: known?.title ?: db?.schema?.title.orEmpty(),
            icon = named?.second ?: known?.icon,
            content = setupContent(
                base, setup, db, settings, id, members,
                viewLoading = listStatus?.asking == true || resultStatus?.asking == true,
                viewError = (if (viewId == null) listStatus?.error else resultStatus?.error),
            ),
            pinned = settings.pinnedDatabases.any { plain(it.id) == plain(pinKey(id, list)) },
            sources = list,
            sourceId = id,
            cards = PropertyLayouts.cards(setup, settings.propertyLayouts[id]),
            properties = db?.schema?.properties.orEmpty(),
            notionUrl = ReadOnlyBlocks.notionUrl(link ?: db?.schema?.databaseId ?: pinKey(id, list)),
            views = shown,
            hiddenViews = hidden,
            viewId = viewId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatabaseUiState())

    val previews: StateFlow<Map<String, String>> = repository.previews

    /** True while the screen is on top: then the view shown is asked again once changes reached Notion. */
    private val visible = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            // A database (from a link) shows its data source; offline it shows what is on the phone.
            val resolved = try {
                repository.resolveSource(openedId)
            } catch (e: NotionException) {
                openedId
            }
            source.value = resolved
            if (resolved != openedId) {
                val info = repository.databaseInfo(openedId)
                if (info != null) label.value = info.title to info.icon
                sources.value = info?.sources?.takeIf { it.size > 1 }
            }
            launch { openViews(resolved) }
            repository.openDatabase(resolved)
            // A pinned database keeps the name and icon Notion has now, for Home.
            val known = repository.knownItem(openedId)
            val title = label.value?.first ?: known?.title ?: repository.databases.value[resolved]?.schema?.title
            repository.renamePinnedDatabase(pinKey(resolved, sources.value), title.orEmpty(), label.value?.second ?: known?.icon)
            // Opened as a database: that is the link. Opened as a data source: the database it is in.
            databaseLink.value = if (plain(resolved) != plain(openedId)) openedId else repository.databaseOf(resolved)
        }
        // Back on screen, or a change reached Notion while it shows: the views and the view's notes again
        // (a note edited here moves in or out of the view, as in Notion).
        viewModelScope.launch {
            combine(visible, repository.lastSentMs, ::Pair).collect { (shown, sent) ->
                if (!shown) return@collect
                val id = sourceId
                val result = state.value.viewId?.let { repository.views.value[id]?.results?.get(it) }
                launch { openViews(id, forceResults = result != null && sent > result.fetchedMs) }
            }
        }
    }

    /** The screen is on top ([shown]) or not (another screen covers it, or the app is in the background). */
    fun setVisible(shown: Boolean) {
        visible.value = shown
    }

    private fun choiceOf(settings: be.wimvandonck.noter.data.store.AppSettings, id: String): String? =
        settings.views[id] ?: settings.views.entries.firstOrNull { plain(it.key) == plain(id) }?.value

    private fun <T> byPlain(map: Map<String, T>, id: String): T? = map[id] ?: map.entries.firstOrNull { plain(it.key) == plain(id) }?.value

    /** Data source [id]'s Notion views: the shown ones in the user's order, then the hidden ones. */
    private fun arrangedViews(views: ViewsCache?, settings: be.wimvandonck.noter.data.store.AppSettings, id: String): Pair<List<NotionView>, List<NotionView>> =
        NotionViews.arranged(
            NotionViews.listed(views?.views.orEmpty(), id),
            byPlain(settings.viewOrder, id).orEmpty(),
            byPlain(settings.hiddenViews, id).orEmpty(),
        )

    /** The notes of Notion view [viewId] as last asked (null: not asked yet), with the ones made since. */
    private fun members(views: ViewsCache?, viewId: String, notes: List<NoteSummary>): List<String>? {
        val result = views?.results?.get(viewId) ?: return null
        return NotionViews.members(result.ids, result.fetchedAt, notes.associate { it.id to it.createdTime })
    }

    /**
     * Data source [id]'s Notion views ([force]: asked again whatever their age), then the notes of
     * the one shown ([forceResults] too). The first time, Notion's first view is kept as chosen.
     */
    private suspend fun openViews(id: String, force: Boolean = false, forceResults: Boolean = force) {
        repository.openViews(id, force)
        val settings = repository.currentSettings()
        val (shown, _) = arrangedViews(repository.views.value[id], settings, id)
        val chosen = NotionViews.chosen(choiceOf(settings, id), shown) ?: return
        if (choiceOf(settings, id) != chosen) repository.chooseView(id, chosen)
        repository.refreshViewResults(id, chosen, forceResults)
    }

    /** Shows Notion view [viewId], kept for next time. */
    fun selectView(viewId: String) {
        val id = sourceId
        if (viewId == state.value.viewId) return
        viewModelScope.launch {
            repository.chooseView(id, viewId)
            openViews(id)
        }
    }

    /**
     * The view settings were arranged: [shown] views (by ID) are tabs in this order, [hidden] aren't.
     * At least one view stays shown.
     */
    fun arrangeViews(shown: List<String>, hidden: List<String>) {
        if (shown.isEmpty()) return
        val id = sourceId
        viewModelScope.launch {
            repository.arrangeViews(id, shown + hidden, hidden)
            openViews(id)
        }
    }

    /** What pins this database: the database when it has several data sources, else its data source. */
    private fun pinKey(sourceId: String, sources: List<PageRef>?): String = if (sources != null) openedId else sourceId

    private fun plain(id: String) = id.replace("-", "")

    /** Shows another data source of the database (a tab). */
    fun selectSource(id: String) {
        if (id == source.value) return
        source.value = id
        viewModelScope.launch {
            launch { openViews(id) }
            repository.openDatabase(id)
        }
    }

    /** Changes what this phone keeps for the data source shown (Notion isn't changed). */
    private fun updateSetup(transform: (DatabaseSetup) -> DatabaseSetup) {
        val id = sourceId
        viewModelScope.launch { repository.updateSetup(id, transform) }
    }

    /** Changes what the cards show, starting from what they show now (for every view). */
    fun editCards(transform: (CardLayout) -> CardLayout) {
        val current = state.value.cards
        updateSetup { it.copy(cards = transform(it.cards ?: current)) }
    }

    /** Pins the database on Home, or unpins it. */
    fun togglePinDatabase() {
        val ui = state.value
        viewModelScope.launch { repository.togglePinDatabase(PinnedDatabase(pinKey(ui.sourceId, ui.sources), ui.title, ui.icon)) }
    }

    private val _editing = MutableStateFlow<DatabaseEditState?>(null)

    /** Edit database while it is open (null: closed). */
    val editing: StateFlow<DatabaseEditState?> get() = _editing

    /** Opens Edit database (⋮, or a tap on the name or icon). */
    fun openEdit() {
        _editing.value = DatabaseEditState(state.value.properties)
    }

    fun dismissEdit() {
        if (_editing.value?.saving != true) _editing.value = null
    }

    /**
     * Saves Edit database in Notion at once: the name and icon ([emoji] when [iconChanged]) of the
     * database, and [properties] of the data source shown ([names]: Notion's name for a new
     * property of each kind). It closes when Notion took everything, else stays open with the error.
     */
    fun saveEdit(title: String, emoji: String?, iconChanged: Boolean, properties: List<EditedProperty>, names: Map<PropertyKind, String>) {
        val open = _editing.value ?: return
        if (open.saving || title.isBlank()) return
        val ui = state.value
        val edit = DatabaseEdit(title = title.trim().takeIf { it != ui.title }, emoji = emoji, setsIcon = iconChanged)
        val change = SchemaEdits.change(open.properties, properties, names)
        _editing.value = open.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                if (edit.title != null || edit.setsIcon) {
                    repository.editDatabase(databaseLink.value, sourceId, single = sources.value == null, edit)
                    // Shown at once, whichever name the screen had (the database's or its data source's).
                    label.value = edit.title(ui.title) to edit.icon(ui.icon)
                }
                repository.editSchema(sourceId, change)
                _editing.value = null
            } catch (e: NotionException) {
                _editing.value = open.copy(saving = false, error = NotionErrors.of(e))
            }
        }
    }

    fun loadPreviews(notes: List<NoteSummary>) = repository.loadPreviews(notes)

    fun refresh() = viewModelScope.launch {
        repository.flush()
        launch { repository.refreshLookups() }
        launch { openViews(sourceId, force = true) }
        repository.refresh(sourceId, userAsked = true)
        // The quick actions' choices too, when their sheets were open before.
        _quickLookups.value = QuickLookups()
    }

    fun togglePin(id: String) = viewModelScope.launch { repository.togglePin(id) }


    /** A new note in the data source shown (Notion gives it the view's defaults when it shows it). */
    fun createNote(onCreated: (String) -> Unit) {
        val id = state.value.content.databaseId ?: return
        viewModelScope.launch {
            val note = repository.createNote(id) ?: return@launch
            onCreated(note)
        }
    }

    /** The data source's properties as Notion describes them (null until known). */
    fun schema(): DatabaseSchema? = repository.databases.value[sourceId]?.schema

    private val _quickLookups = MutableStateFlow(QuickLookups())

    /** People and the pages of related databases, for making quick actions (#119). */
    val quickLookups: StateFlow<QuickLookups> = _quickLookups

    fun loadQuickLookups() = viewModelScope.launch {
        if (_quickLookups.value.users.isEmpty()) {
            _quickLookups.update { it.copy(users = runCatching { repository.users() }.getOrDefault(emptyList())) }
        }
        schema()?.properties.orEmpty().mapNotNull { it.relationDatabaseId.takeIf { _ -> it.type == "relation" } }.distinct().forEach { related ->
            if (related in _quickLookups.value.pages) return@forEach
            val pages = runCatching { repository.relatedPages(related) }.getOrNull() ?: return@forEach
            _quickLookups.update { it.copy(pages = it.pages + (related to pages)) }
        }
    }

    fun saveQuickAction(action: QuickAction) = updateSetup { it.copy(quickActions = QuickActions.save(it.quickActions, action)) }

    fun deleteQuickAction(action: QuickAction) {
        val index = state.value.content.setup?.quickActions.orEmpty().indexOfFirst { it.id == action.id }
        if (index < 0) return
        updateSetup { it.copy(quickActions = QuickActions.remove(it.quickActions, action.id)) }
        _events.tryEmit(DatabaseEvent.QuickActionDeleted(action, index))
    }

    fun undoDeleteQuickAction(event: DatabaseEvent.QuickActionDeleted) =
        updateSetup { it.copy(quickActions = QuickActions.restore(it.quickActions, event.action, event.index)) }

    companion object {
        fun factory(databaseId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NoterApp
                DatabaseViewModel(databaseId, app.repository, app.apiKeyStore.hasKey)
            }
        }
    }
}
