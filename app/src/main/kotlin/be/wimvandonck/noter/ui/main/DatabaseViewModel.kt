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
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteSort
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.SortKind
import be.wimvandonck.noter.logic.DatabaseEdit
import be.wimvandonck.noter.logic.DatabaseSetups
import be.wimvandonck.noter.logic.PropertyLayouts
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
    /** What the cards show now (see PropertyLayouts.cards), for the Cards panel. */
    val cards: CardLayout = CardLayout(),
    /** The data source's properties (the cards can show some). */
    val properties: List<PropertySchema> = emptyList(),
    /** Notion's web link to the database, for Open in Notion and Share. */
    val notionUrl: String = "",
)

/** Something the database's screen tells the user once. */
sealed interface DatabaseEvent {
    /** Dragging a note switched the data source from [previous] to the custom order (Undo restores it). */
    data class SortedByHand(val sourceId: String, val previous: NoteSort?) : DatabaseEvent

    /** Notion didn't take a new name or icon ([error]: see NotionErrors). */
    data class EditFailed(val error: String) : DatabaseEvent
}

/**
 * A database: its notes with the filters, sort and cards kept for it on this phone
 * ([DatabaseSetup], per data source; every change is kept at once). [openedId] is a data source
 * (from Home or Search) or a database (a link, or a database of several data sources); a database
 * with several data sources shows one at a time, with tabs.
 */
class DatabaseViewModel(
    private val openedId: String,
    private val repository: NotesRepository,
    hasKey: StateFlow<Boolean>,
) : ViewModel() {
    private val chipTitles = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Loads the titles of a related database's pages, for the chips of a relation filter. */
    fun loadChipTitles(databaseId: String) = viewModelScope.launch {
        val pages = repository.relatedPages(databaseId) ?: return@launch
        chipTitles.update { it + pages.associate { page -> page.id to page.title } }
    }

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

    val state: StateFlow<DatabaseUiState> = combine(
        repository.settingsFlow(),
        repository.databases,
        combine(source, sources, label, databaseLink, ::Shown),
        status,
    ) { settings, databases, (id, list, named, link), (pending, offline, key, missing) ->
        val db = databases[id]
        val setup = DatabaseSetups.of(settings.setups, id, db?.schema)
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
            content = setupContent(base, setup, db, settings, id),
            pinned = settings.pinnedDatabases.any { plain(it.id) == plain(pinKey(id, list)) },
            sources = list,
            sourceId = id,
            cards = PropertyLayouts.cards(setup, settings.propertyLayouts[id]),
            properties = db?.schema?.properties.orEmpty(),
            notionUrl = ReadOnlyBlocks.notionUrl(link ?: db?.schema?.databaseId ?: pinKey(id, list)),
        )
    }.combine(chipTitles) { state, titles -> state.copy(content = state.content.copy(chipTitles = titles)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatabaseUiState())

    val previews: StateFlow<Map<String, String>> = repository.previews

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
            repository.openDatabase(resolved)
            // A pinned database keeps the name and icon Notion has now, for Home.
            val known = repository.knownItem(openedId)
            val title = label.value?.first ?: known?.title ?: repository.databases.value[resolved]?.schema?.title
            repository.renamePinnedDatabase(pinKey(resolved, sources.value), title.orEmpty(), label.value?.second ?: known?.icon)
            // Opened as a database: that is the link. Opened as a data source: the database it is in.
            databaseLink.value = if (plain(resolved) != plain(openedId)) openedId else repository.databaseOf(resolved)
        }
    }

    /** What pins this database: the database when it has several data sources, else its data source. */
    private fun pinKey(sourceId: String, sources: List<PageRef>?): String = if (sources != null) openedId else sourceId

    private fun plain(id: String) = id.replace("-", "")

    /** Shows another data source of the database (a tab). */
    fun selectSource(id: String) {
        if (id == source.value) return
        source.value = id
        viewModelScope.launch { repository.openDatabase(id) }
    }

    /** Changes how the data source shown shows its notes (kept on this phone, Notion isn't changed). */
    private fun updateSetup(transform: (DatabaseSetup) -> DatabaseSetup) {
        val id = sourceId
        viewModelScope.launch { repository.updateSetup(id, repository.databases.value[id]?.schema, transform) }
    }

    fun toggleChip(property: String, key: String) = updateSetup { it.copy(selected = DatabaseSetups.toggle(it.selected, property, key)) }

    fun clearChips() = updateSetup { it.copy(selected = emptyMap()) }

    /**
     * The Filters panel was arranged: [shown] have a row of chips above the notes, [hidden] don't,
     * each in this order (filters stay as they are).
     */
    fun arrangeFilters(shown: List<String>, hidden: List<String>) = updateSetup { DatabaseSetups.arranged(it, shown, hidden) }

    /** The × at the end of a row of chips: [property] doesn't filter any more. */
    fun clearRow(property: String) = updateSetup { it.copy(selected = it.selected - property) }

    fun setSort(sort: NoteSort) = updateSetup { it.copy(sort = sort) }

    /** Changes what the cards show, starting from what they show now. */
    fun editCards(transform: (CardLayout) -> CardLayout) {
        val current = state.value.cards
        updateSetup { it.copy(cards = transform(it.cards ?: current)) }
    }

    /** Undo after dragging switched the data source to the custom order. */
    fun undoSortedByHand(event: DatabaseEvent.SortedByHand) = viewModelScope.launch {
        repository.updateSetup(event.sourceId, repository.databases.value[event.sourceId]?.schema) { it.copy(sort = event.previous) }
    }

    /** Pins the database on Home, or unpins it. */
    fun togglePinDatabase() {
        val ui = state.value
        viewModelScope.launch { repository.togglePinDatabase(PinnedDatabase(pinKey(ui.sourceId, ui.sources), ui.title, ui.icon)) }
    }

    /** Renames the database in Notion (Rename in the ⋮ menu). */
    fun rename(title: String) = edit(DatabaseEdit.rename(title))

    /** Sets the database's icon to [emoji] in Notion, or removes it when null. */
    fun setIcon(emoji: String?) = edit(DatabaseEdit.icon(emoji))

    private fun edit(edit: DatabaseEdit) {
        if (edit.title?.isBlank() == true) return
        viewModelScope.launch {
            try {
                repository.editDatabase(databaseLink.value, sourceId, single = sources.value == null, edit)
                // Shown at once, whichever name the screen had (the database's or its data source's).
                val ui = state.value
                label.value = edit.title(ui.title) to edit.icon(ui.icon)
            } catch (e: NotionException) {
                _events.tryEmit(DatabaseEvent.EditFailed(NotionErrors.of(e)))
            }
        }
    }

    fun loadPreviews(notes: List<NoteSummary>) = repository.loadPreviews(notes)

    fun refresh() = viewModelScope.launch {
        repository.flush()
        repository.refresh(sourceId, userAsked = true)
    }

    fun togglePin(id: String) = viewModelScope.launch { repository.togglePin(id) }

    fun trash(id: String) = viewModelScope.launch { repository.trashNote(id) }

    /**
     * Saves the new order after the user dragged notes in [group] (the pinned ones or the others).
     * Sorted another way, dragging switches to the custom order, starting from the order on screen.
     */
    fun saveOrder(group: List<String>) {
        val ui = state.value.content
        val id = ui.databaseId ?: return
        val setup = ui.setup ?: return
        val switching = setup.currentSort.kind != SortKind.CUSTOM
        val ids = when {
            !switching -> group
            ui.pinned.any { it.id in group } -> group + ui.others.map { it.id }
            else -> ui.pinned.map { it.id } + group
        }
        viewModelScope.launch {
            repository.saveOrder(id, ids)
            if (switching) {
                repository.updateSetup(id, repository.databases.value[id]?.schema) { it.copy(sort = NoteSort(SortKind.CUSTOM)) }
                _events.tryEmit(DatabaseEvent.SortedByHand(id, setup.sort))
            }
        }
    }

    /** A new note, with the values of the filters selected where it can. */
    fun createNote(onCreated: (String) -> Unit) {
        val ui = state.value.content
        val id = ui.databaseId ?: return
        viewModelScope.launch {
            val preset = presets(ui.filterOptions, ui.setup?.selected.orEmpty().mapValues { it.value.toSet() })
            val note = repository.createNote(id, preset) ?: return@launch
            onCreated(note)
        }
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
