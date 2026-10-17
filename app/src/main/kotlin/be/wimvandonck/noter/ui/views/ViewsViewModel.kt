package be.wimvandonck.noter.ui.views

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
import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.logic.Notebooks
import be.wimvandonck.noter.logic.PropertyLayouts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** The notebook being added or edited in the sheet. What its cards show is in [config] ([cards]). */
data class DatabaseDraft(
    val config: DatabaseConfig,
    val isNew: Boolean,
    val schema: DatabaseSchema? = null,
    /** An error from NotionErrors, while the database's properties couldn't be loaded. */
    val schemaError: String? = null,
) {
    val cards: CardLayout get() = config.cards ?: CardLayout()
}

data class ViewsUiState(
    val views: List<DatabaseConfig> = emptyList(),
    val hasKey: Boolean = false,
    val draft: DatabaseDraft? = null,
)

/** Manage notebooks: add (from Search), edit, order and remove them. */
class ViewsViewModel(
    private val repository: NotesRepository,
    hasKey: StateFlow<Boolean>,
) : ViewModel() {
    private val draft = MutableStateFlow<DatabaseDraft?>(null)

    val state: StateFlow<ViewsUiState> = combine(repository.settingsFlow(), hasKey, draft) { settings, key, d ->
        ViewsUiState(settings.databases, key, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewsUiState(hasKey = hasKey.value))

    /**
     * A notebook of page [page]: its subpages are the notes, in the page's order. There is nothing
     * to filter by (subpages only have a title).
     */
    private fun startAddPage(page: PageRef) {
        val config = DatabaseConfig(
            id = "view-" + UUID.randomUUID(),
            name = page.title,
            databaseId = NotesRepository.pageNotebookKey(page.id),
            pageId = page.id,
        )
        draft.value = DatabaseDraft(config, isNew = true, schema = PAGE_SCHEMA)
        loadCards(config)
    }

    /**
     * Starts a new notebook of [databaseId] (a page's key: of its subpages), named [title] (when
     * not known, after the database or page).
     */
    fun startAddFor(databaseId: String, title: String? = null) {
        if (NotesRepository.isPageNotebook(databaseId)) {
            val pageId = NotesRepository.pageOf(databaseId)
            val known = repository.databases.value.values.flatMap { it.notes }.firstOrNull { it.id == pageId }?.title
            return startAddPage(PageRef(pageId, title ?: known.orEmpty()))
        }
        startAdd(PageRef(databaseId, title ?: repository.databases.value[databaseId]?.schema?.title.orEmpty()))
    }

    /**
     * Starts a new notebook of [database]: all its notes, set up and filtered on this phone (see
     * Notebooks.newNotebook).
     */
    private fun startAdd(database: PageRef) {
        val config = DatabaseConfig(
            id = "view-" + UUID.randomUUID(),
            name = database.title,
            databaseId = database.id,
        )
        draft.value = DatabaseDraft(config, isNew = true)
        loadCards(config)
        viewModelScope.launch {
            try {
                val schema = repository.fetchSchema(database.id)
                draft.update { d ->
                    if (d?.config?.id != config.id) return@update d
                    val named = d.config.copy(name = d.config.name.ifBlank { schema.title })
                    d.copy(schema = schema, config = Notebooks.newNotebook(named, schema))
                }
            } catch (e: NotionException) {
                draft.update { d -> if (d?.config?.id == config.id) d.copy(schemaError = NotionErrors.of(e)) else d }
            }
        }
    }

    fun startEdit(config: DatabaseConfig) {
        draft.value = DatabaseDraft(config, isNew = false, schema = if (config.pageId != null) PAGE_SCHEMA else null)
        loadCards(config)
        if (config.pageId == null) loadSchema(config.sourceId)
    }

    /** Opens the editor of view [id] (from "Edit notebook"). */
    fun startEdit(id: String) = viewModelScope.launch {
        repository.currentSettings().databases.firstOrNull { it.id == id }?.let(::startEdit)
    }

    /** A notebook without cards of its own starts from what its database's cards showed before. */
    private fun loadCards(config: DatabaseConfig) = viewModelScope.launch {
        if (config.cards != null) return@launch
        val cards = PropertyLayouts.cards(null, repository.currentSettings().propertyLayouts[config.sourceId])
        draft.update { d -> if (d?.config?.id == config.id && d.config.cards == null) d.copy(config = d.config.copy(cards = cards)) else d }
    }

    /** Changes what this notebook's cards show. */
    fun editCards(transform: (CardLayout) -> CardLayout) {
        draft.update { it?.copy(config = it.config.copy(cards = transform(it.cards))) }
    }

    private fun loadSchema(id: String) {
        viewModelScope.launch {
            try {
                val schema = repository.fetchSchema(id)
                draft.update { d -> if (d?.config?.sourceId != id) d else d.copy(schema = schema) }
            } catch (e: NotionException) {
                draft.update { d -> if (d?.config?.sourceId == id) d.copy(schemaError = NotionErrors.of(e)) else d }
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
        viewModelScope.launch { repository.saveDatabase(d.config.copy(name = d.config.name.trim())) }
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
