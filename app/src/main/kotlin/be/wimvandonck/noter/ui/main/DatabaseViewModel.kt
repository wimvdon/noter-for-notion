package be.wimvandonck.noter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.logic.Notebooks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DatabaseUiState(
    val title: String = "",
    val content: MainUiState = MainUiState(),
    /** Views of this database in the navigation. */
    val viewCount: Int = 0,
    /** The first view of this database ("Edit view" opens it), null when there is none. */
    val viewId: String? = null,
    /** A database with several data sources: each of them, for tabs (null: just one). */
    val sources: List<PageRef>? = null,
    /** The data source shown. */
    val sourceId: String = "",
)

/**
 * A database opened from Browse or from a link in a note: its notes, with filters. [openedId] is a
 * data source (from Browse) or a database (from a link); a database with several data sources
 * shows one at a time, with tabs.
 */
class DatabaseViewModel(
    private val openedId: String,
    private val repository: NotesRepository,
    hasKey: StateFlow<Boolean>,
) : ViewModel() {
    /** Filters picked here (a database on its own keeps them until it closes). */
    private val selected = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val chipTitles = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Loads the titles of a related database's pages, for the chips of a relation filter. */
    fun loadChipTitles(databaseId: String) = viewModelScope.launch {
        val pages = repository.relatedPages(databaseId) ?: return@launch
        chipTitles.update { it + pages.associate { page -> page.id to page.title } }
    }
    private val source = MutableStateFlow(openedId)
    private val sources = MutableStateFlow<List<PageRef>?>(null)
    private val databaseId: String get() = source.value
    private val status = combine(repository.pendingCount, hasKey, source, sources) { pending, key, id, list -> Status(pending, key, id, list) }

    private data class Status(val pending: Int, val hasKey: Boolean, val sourceId: String, val sources: List<PageRef>?)

    val state: StateFlow<DatabaseUiState> = combine(
        repository.settingsFlow(),
        repository.databases,
        selected,
        status,
    ) { settings, databases, chips, (pending, key, databaseId, list) ->
        val db = databases[databaseId]
        val base = MainUiState(
            ready = true,
            hasKey = key,
            currentTab = databaseId,
            pinnedIds = settings.pinned.toSet(),
            propertyLayouts = settings.propertyLayouts,
            pendingCount = pending,
        )
        // Shown like a notebook that isn't saved: its quick filter, the custom order.
        val unsaved = DatabaseConfig(
            id = databaseId,
            name = db?.schema?.title.orEmpty(),
            databaseId = databaseId,
            quickFilters = listOfNotNull(defaultChipProperty(databaseId, settings, db)?.name),
            selected = chips,
        )
        DatabaseUiState(
            title = db?.schema?.title.orEmpty(),
            content = notebookContent(base, unsaved, db, null, settings),
            viewCount = settings.databases.count { it.sourceId == databaseId },
            viewId = settings.databases.firstOrNull { it.sourceId == databaseId }?.id,
            sources = list,
            sourceId = databaseId,
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
            if (resolved != openedId) sources.value = repository.dataSourcesOf(openedId)
            repository.openDatabase(resolved)
        }
    }

    /** Shows another data source of the database (a tab). */
    fun selectSource(id: String) {
        if (id == source.value) return
        source.value = id
        selected.value = emptyMap()
        viewModelScope.launch { repository.openDatabase(id) }
    }

    fun loadPreviews(notes: List<NoteSummary>) = repository.loadPreviews(notes)

    fun toggleChip(property: String?, key: String) {
        if (property != null) selected.value = Notebooks.toggle(selected.value, property, key)
    }

    fun clearChips() {
        selected.value = emptyMap()
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
            val preset = presets(ui.filterOptions, selected.value.mapValues { it.value.toSet() })
            val id = repository.createNote(databaseId, preset) ?: return@launch
            onCreated(id)
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
