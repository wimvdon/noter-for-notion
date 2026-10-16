package com.impca201.noter.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.impca201.noter.NoterApp
import com.impca201.noter.data.NotesRepository
import com.impca201.noter.data.model.Block
import com.impca201.noter.data.model.BlockType
import com.impca201.noter.data.model.DatabaseSchema
import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.model.NotionUser
import com.impca201.noter.data.model.PageRef
import com.impca201.noter.data.model.PropertyLayout
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.data.sync.SaveTiming
import com.impca201.noter.logic.DocumentEditor
import com.impca201.noter.logic.EditResult
import com.impca201.noter.logic.PropertyLayouts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the cursor should go after an edit that changed the structure (new line, joined lines, …). */
data class FocusTarget(val key: String, val cursor: Int, val serial: Long)

data class NoteUiState(
    val note: NoteSummary? = null,
    val schema: DatabaseSchema? = null,
    val pinned: Boolean = false,
    val blocks: List<Block> = emptyList(),
    /** True until content is known (cached or downloaded). */
    val loadingContent: Boolean = true,
    val contentError: Boolean = false,
    val users: List<NotionUser> = emptyList(),
    /** Pages of related databases, by database ID (null while loading or when not shared). */
    val relatedPages: Map<String, List<PageRef>?> = emptyMap(),
    /** How the note's database shows its properties (null: the default). */
    val layout: PropertyLayout? = null,
)

class NoteViewModel(
    private val pageId: String,
    private val repository: NotesRepository,
) : ViewModel() {
    private val editor = DocumentEditor()
    private val blocks = MutableStateFlow<List<Block>>(emptyList())
    private val loadingContent = MutableStateFlow(true)
    private val contentError = MutableStateFlow(false)
    private val users = MutableStateFlow<List<NotionUser>>(emptyList())
    private val relatedPages = MutableStateFlow<Map<String, List<PageRef>?>>(emptyMap())

    private val _focus = MutableStateFlow<FocusTarget?>(null)
    val focus: StateFlow<FocusTarget?> = _focus.asStateFlow()

    /** Edits are handed to the repository soon after they are made (see [SaveTiming.LOCAL]). */
    private var editedSinceOpen = false
    private var saveJob: Job? = null

    /** When the oldest edit not saved yet was made (0: everything is saved). */
    private var firstUnsavedMs = 0L
    private val propertyJobs = mutableMapOf<String, Job>()
    private val pendingProperties = mutableMapOf<String, NoteProperty>()
    private var serial = 0L

    private val extras = combine(users, relatedPages, contentError) { u, r, e -> Triple(u, r, e) }

    val state: StateFlow<NoteUiState> = combine(
        repository.note(pageId),
        repository.databases,
        repository.settingsFlow(),
        blocks,
        loadingContent,
    ) { note, databases, settings, blocks, loading ->
        NoteUiState(
            note = note,
            schema = note?.let { databases[it.databaseId]?.schema },
            pinned = note != null && repository.resolve(pageId) in settings.pinned,
            layout = note?.let { settings.propertyLayouts[it.databaseId] },
            blocks = blocks,
            loadingContent = loading,
        )
    }.combine(extras) { state, (u, r, e) ->
        state.copy(users = u, relatedPages = r, contentError = e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteUiState())

    init {
        viewModelScope.launch {
            val cached = repository.cachedContent(pageId)
            if (cached != null) {
                setLoaded(cached.blocks)
            }
            val fresh = repository.fetchContent(pageId)
            when {
                fresh != null && !editedSinceOpen -> setLoaded(fresh.blocks)
                cached == null && fresh == null -> {
                    // New note or offline without a copy: start empty (new notes) or show the problem.
                    val isNew = pageId.startsWith(NoteSummary.LOCAL_PREFIX)
                    contentError.value = !isNew
                    setLoaded(emptyList())
                }
            }
        }
    }

    private fun setLoaded(content: List<Block>) {
        // An empty note gets one empty line to type in; it is only saved once something is typed.
        blocks.value = content.ifEmpty { listOf(editor.newBlock()) }
        loadingContent.value = false
    }

    // ---- Content ----

    private fun apply(result: EditResult, typedKey: String? = null, typedText: String? = null) {
        blocks.value = result.blocks
        val focusKey = result.focusKey
        val structural = typedKey == null || focusKey != typedKey ||
            result.blocks.firstOrNull { it.key == typedKey }?.text != typedText
        if (focusKey != null && result.cursor != null && structural) {
            _focus.value = FocusTarget(focusKey, result.cursor, ++serial)
        }
        scheduleSave()
    }

    fun changeText(key: String, text: String, cursor: Int) =
        apply(editor.changeText(blocks.value, key, text, cursor), typedKey = key, typedText = text)

    fun backspaceAtStart(key: String) = apply(editor.backspaceAtStart(blocks.value, key))

    fun setType(key: String, type: BlockType) = apply(editor.setType(blocks.value, key, type))

    fun toggleChecked(key: String) {
        blocks.value = editor.toggleChecked(blocks.value, key)
        scheduleSave(fast = true)
    }

    fun delete(key: String) = apply(editor.delete(blocks.value, key))

    /** Tapping below the content: a text line at the end to type in. */
    fun addLineAtEnd() {
        val result = editor.addLineAtEnd(blocks.value)
        if (result.blocks === blocks.value) {
            result.focusKey?.let { focusBlock(it, 0) }
        } else {
            apply(result)
        }
    }

    fun insertAfter(key: String, type: BlockType) = apply(editor.insertAfter(blocks.value, key, type))

    fun canChangeType(block: Block) = editor.canChangeType(block)

    /** Moves the cursor to block [key] (Enter in the title goes to the first line). */
    fun focusBlock(key: String, cursor: Int) {
        _focus.value = FocusTarget(key, cursor, ++serial)
    }

    private fun scheduleSave(fast: Boolean = false) {
        editedSinceOpen = true
        val now = System.currentTimeMillis()
        if (firstUnsavedMs == 0L) firstUnsavedMs = now
        val wait = if (fast) 0L else SaveTiming.LOCAL.delayMs(now, firstUnsavedMs)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(wait)
            firstUnsavedMs = 0L
            repository.saveContent(pageId, savable())
        }
    }

    /** The content to store: a single empty line (an untouched new note) is not content. */
    private fun savable(): List<Block> =
        blocks.value.let { list ->
            if (list.size == 1 && list[0].notionId == null && list[0].text.isEmpty() && list[0].type == BlockType.PARAGRAPH) emptyList() else list
        }

    // ---- Properties ----

    /**
     * Changes a property. Text is saved shortly after typing stops; other values at once.
     */
    fun setProperty(property: NoteProperty, debounce: Boolean = false) {
        pendingProperties[property.name] = property
        propertyJobs[property.name]?.cancel()
        propertyJobs[property.name] = viewModelScope.launch {
            if (debounce) delay(SaveTiming.LOCAL.quietMs)
            pendingProperties.remove(property.name)
            repository.updateProperty(pageId, property)
        }
    }

    // ---- How properties are shown (per database) ----

    private val propertyNames: List<String>
        get() = state.value.note?.properties.orEmpty().filter { it.type != "title" }.map { it.name }

    fun reorderProperties(names: List<String>) = updateLayout { PropertyLayouts.reorder(it, names) }

    fun toggleShown(name: String) = updateLayout { PropertyLayouts.toggleShown(it, propertyNames, name) }

    fun toggleOnCard(name: String) = updateLayout { PropertyLayouts.toggleOnCard(it, name) }

    private fun updateLayout(transform: (PropertyLayout?) -> PropertyLayout) {
        val databaseId = state.value.note?.databaseId ?: return
        viewModelScope.launch {
            repository.updateSettings { s -> s.copy(propertyLayouts = s.propertyLayouts + (databaseId to transform(s.propertyLayouts[databaseId]))) }
        }
    }

    private val _openFailed = MutableStateFlow(false)

    /** True after a linked page couldn't be opened (offline); the screen says so once. */
    val openFailed: StateFlow<Boolean> = _openFailed.asStateFlow()

    fun openFailedShown() {
        _openFailed.value = false
    }

    /** Opens a page linked in the note (downloading it when it isn't on this phone yet). */
    fun openLinkedPage(id: String, onOpened: (String) -> Unit) = viewModelScope.launch {
        val opened = repository.openPage(id)
        if (opened != null) onOpened(opened) else _openFailed.value = true
    }

    fun togglePin() = viewModelScope.launch { repository.togglePin(pageId) }

    fun trash(onDone: () -> Unit) = viewModelScope.launch {
        // Nothing of a note in the trash is saved any more.
        saveJob?.cancel()
        propertyJobs.values.forEach { it.cancel() }
        pendingProperties.clear()
        repository.trashNote(pageId)
        onDone()
    }

    fun loadUsers() {
        if (users.value.isNotEmpty()) return
        viewModelScope.launch { users.value = repository.users() }
    }

    fun loadRelated(databaseId: String) {
        if (relatedPages.value[databaseId] != null) return
        viewModelScope.launch {
            val pages = repository.relatedPages(databaseId)
            relatedPages.update { it + (databaseId to pages) }
        }
    }

    /** Loads titles for relation values shown in the note. */
    fun loadRelatedFor(note: NoteSummary, schema: DatabaseSchema?) {
        note.properties.filter { (it.value as? PropertyValue.Relation)?.ids?.isNotEmpty() == true }
            .forEach { prop -> schema?.property(prop.name)?.relationDatabaseId?.let { loadRelated(it) } }
    }

    override fun onCleared() {
        // Leaving the note: hand everything that is still waiting to the repository right away.
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            repository.saveContentInBackground(pageId, savable())
        }
        pendingProperties.values.forEach { repository.updatePropertyInBackground(pageId, it) }
        repository.scheduleFlush(200)
        super.onCleared()
    }

    companion object {
        fun factory(pageId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NoterApp
                NoteViewModel(pageId, app.repository)
            }
        }
    }
}
