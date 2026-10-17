package be.wimvandonck.noter.ui.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.logic.Notebooks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The view being added or edited in the sheet. [layout] is what the cards in the overview show,
 * kept per database (so for every view of it).
 */
data class DatabaseDraft(
    val config: DatabaseConfig,
    val isNew: Boolean,
    val schema: DatabaseSchema? = null,
    val schemaError: String? = null,
    val layout: PropertyLayout? = null,
)

data class ViewsUiState(
    val views: List<DatabaseConfig> = emptyList(),
    val hasKey: Boolean = false,
    /** Databases the token can see, for "Add notebook" (null while loading). */
    val available: List<PageRef>? = null,
    /** Pages (not in a database) whose subpages can be a notebook, for "Add notebook". */
    val availablePages: List<PageRef> = emptyList(),
    val availableError: String? = null,
    val draft: DatabaseDraft? = null,
)

/** Manage views: add, edit, order and remove the views. */
class ViewsViewModel(
    private val repository: NotesRepository,
    hasKey: StateFlow<Boolean>,
) : ViewModel() {
    private val available = MutableStateFlow<List<PageRef>?>(null)
    private val availableError = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow<DatabaseDraft?>(null)
    private val availablePages = MutableStateFlow<List<PageRef>>(emptyList())

    val state: StateFlow<ViewsUiState> = combine(
        repository.settingsFlow(),
        hasKey,
        combine(available, availableError, availablePages) { a, e, p -> Triple(a, e, p) },
        draft,
    ) { settings, key, (a, e, pages), d ->
        ViewsUiState(settings.databases, key, a, pages, e, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewsUiState(hasKey = hasKey.value))

    fun loadAvailable() {
        available.value = null
        availableError.value = null
        viewModelScope.launch {
            try {
                // Pages first come from the same search as Browse: those not in a database.
                availablePages.value = repository.browse("").filter { !it.isDatabase && it.note?.databaseId == NotesRepository.PAGES }
                    .map { PageRef(it.id, it.title) }
                available.value = repository.searchDatabases()
            } catch (e: NotionException) {
                availableError.value = e.message
                available.value = emptyList()
            }
        }
    }

    /**
     * A notebook of page [page]: its subpages are the notes, in the page's order. There is nothing
     * to filter by (subpages only have a title).
     */
    fun startAddPage(page: PageRef) {
        val key = NotesRepository.pageNotebookKey(page.id)
        val config = DatabaseConfig(
            id = "view-" + UUID.randomUUID(),
            name = page.title,
            databaseId = key,
            pageId = page.id,
        )
        draft.value = DatabaseDraft(config, isNew = true, schema = PAGE_SCHEMA)
        loadLayout(key)
    }

    /** "Add as notebook" on a page (its key) or a database's page. */
    fun startAddFor(databaseId: String) {
        if (NotesRepository.isPageNotebook(databaseId)) {
            val pageId = NotesRepository.pageOf(databaseId)
            val title = repository.databases.value.values.flatMap { it.notes }.firstOrNull { it.id == pageId }?.title.orEmpty()
            return startAddPage(PageRef(pageId, title))
        }
        val title = repository.databases.value[databaseId]?.schema?.title.orEmpty()
        startAdd(PageRef(databaseId, title))
    }

    /**
     * Starts a new notebook of [database]: all its notes, set up and filtered on this phone (see
     * Notebooks.newNotebook).
     */
    fun startAdd(database: PageRef) {
        val config = DatabaseConfig(
            id = "view-" + UUID.randomUUID(),
            name = database.title,
            databaseId = database.id,
        )
        draft.value = DatabaseDraft(config, isNew = true)
        viewModelScope.launch {
            val existing = repository.currentSettings().propertyLayouts[database.id]
            try {
                val schema = repository.fetchSchema(database.id)
                draft.update { d ->
                    if (d?.config?.id != config.id) return@update d
                    d.copy(
                        schema = schema,
                        config = Notebooks.newNotebook(d.config, schema),
                        layout = existing,
                    )
                }
            } catch (e: NotionException) {
                draft.update { d -> if (d?.config?.id == config.id) d.copy(schemaError = e.message, layout = existing) else d }
            }
        }
    }

    fun startEdit(config: DatabaseConfig) {
        draft.value = DatabaseDraft(config, isNew = false, schema = if (config.pageId != null) PAGE_SCHEMA else null)
        loadLayout(config.sourceId)
        if (config.pageId == null) loadSchema(config.sourceId)
    }

    /** Opens the editor of view [id] (from "Edit view" on a database's page). */
    fun startEdit(id: String) = viewModelScope.launch {
        repository.currentSettings().databases.firstOrNull { it.id == id }?.let(::startEdit)
    }

    private fun loadLayout(databaseId: String) = viewModelScope.launch {
        val layout = repository.currentSettings().propertyLayouts[databaseId]
        draft.update { d -> if (d?.config?.sourceId == databaseId) d.copy(layout = layout) else d }
    }

    /** Changes what the cards show (saved with the view). */
    fun editLayout(transform: (PropertyLayout?) -> PropertyLayout) {
        draft.update { it?.copy(layout = transform(it.layout)) }
    }

    private fun loadSchema(id: String) {
        viewModelScope.launch {
            try {
                val schema = repository.fetchSchema(id)
                draft.update { d -> if (d?.config?.sourceId != id) d else d.copy(schema = schema) }
            } catch (e: NotionException) {
                draft.update { d -> if (d?.config?.sourceId == id) d.copy(schemaError = e.message) else d }
            }
        }
    }

    fun editDraft(transform: (DatabaseConfig) -> DatabaseConfig) {
        draft.update { it?.copy(config = transform(it.config)) }
    }

    fun saveDraft() {
        val d = draft.value ?: return
        if (d.config.name.isBlank()) return
        draft.value = null
        viewModelScope.launch {
            d.layout?.let { layout -> repository.updateSettings { s -> s.copy(propertyLayouts = s.propertyLayouts + (d.config.sourceId to layout)) } }
            repository.saveDatabase(d.config.copy(name = d.config.name.trim()))
        }
    }

    fun cancelDraft() {
        draft.value = null
    }

    fun removeDatabase(id: String) {
        draft.value = null
        viewModelScope.launch { repository.removeDatabase(id) }
    }

    fun moveDatabase(id: String, up: Boolean) = viewModelScope.launch { repository.moveDatabase(id, up) }

    companion object {
        /** A page's notebook: its notes (subpages) have only a title. */
        private val PAGE_SCHEMA = DatabaseSchema("page", "", listOf(PropertySchema(NotesRepository.PAGE_TITLE, NotesRepository.PAGE_TITLE, "title")))

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NoterApp
                ViewsViewModel(app.repository, app.apiKeyStore.hasKey)
            }
        }
    }
}
