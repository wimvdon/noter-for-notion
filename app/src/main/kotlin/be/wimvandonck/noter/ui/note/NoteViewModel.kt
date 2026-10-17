package be.wimvandonck.noter.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.sync.SaveTiming
import be.wimvandonck.noter.logic.DocumentEditor
import be.wimvandonck.noter.logic.EditResult
import be.wimvandonck.noter.logic.NestedContent
import be.wimvandonck.noter.logic.PropertyLayouts
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
    /** Whether what is nested in the blocks is there yet (see [NestedContent]). */
    val nested: NestedContent.State = NestedContent.State.LOADED,
    val users: List<NotionUser> = emptyList(),
    /** Pages of related databases, by database ID (null while loading or when not shared). */
    val relatedPages: Map<String, List<PageRef>?> = emptyMap(),
    /** How the note's database shows its properties (null: the default). */
    val layout: PropertyLayout? = null,
    /** Changes waiting to be sent to Notion (all notes), for the sync button. */
    val pendingCount: Int = 0,
    val offline: Boolean = false,
    /** True while the sync button's sync runs. */
    val syncing: Boolean = false,
) {
    /** The content can be edited: not while the note's full content is still on its way. */
    val editable: Boolean get() = nested != NestedContent.State.LOADING
}

class NoteViewModel(
    private val pageId: String,
    private val repository: NotesRepository,
) : ViewModel() {
    private val editor = DocumentEditor()
    private val blocks = MutableStateFlow<List<Block>>(emptyList())
    private val loadingContent = MutableStateFlow(true)

    /** True while the note's content is being downloaded after opening it. */
    private val downloading = MutableStateFlow(true)
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

    private val syncing = MutableStateFlow(false)
    private val extras = combine(users, relatedPages, contentError) { u, r, e -> Triple(u, r, e) }
    private val syncStatus = combine(repository.pendingCount, repository.offline, syncing) { p, o, s -> Triple(p, o, s) }

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
    }.combine(syncStatus) { state, (pending, offline, busy) ->
        state.copy(pendingCount = pending, offline = offline, syncing = busy)
    }.combine(downloading) { state, busy ->
        state.copy(nested = NestedContent.state(state.blocks, busy))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteUiState())

    init {
        viewModelScope.launch {
            val cached = repository.cachedContent(pageId)
            if (cached != null) {
                setLoaded(cached.blocks)
            }
            val fresh = repository.fetchContent(pageId)
            downloading.value = false
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

    /** Edits wait until the note's full content is here (an edit would keep the incomplete copy). */
    private fun editable(): Boolean = NestedContent.state(blocks.value, downloading.value) != NestedContent.State.LOADING

    private fun apply(result: EditResult, typedKey: String? = null, typedText: String? = null) {
        if (!editable()) return
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
        if (!editable()) return
        blocks.value = editor.toggleChecked(blocks.value, key)
        scheduleSave(fast = true)
    }

    fun delete(key: String) = apply(editor.delete(blocks.value, key))

    /** Tapping below the content: a text line at the end to type in. */
    fun addLineAtEnd() {
        if (!editable()) return
        val result = editor.addLineAtEnd(blocks.value)
        if (result.blocks === blocks.value) {
            result.focusKey?.let { focusBlock(it, 0) }
        } else {
            apply(result)
        }
    }

    fun insertAfter(key: String, type: BlockType) = apply(editor.insertAfter(blocks.value, key, type))

    fun canChangeType(block: Block) = editor.canChangeType(blocks.value, block.key)

    fun canIndent(block: Block) = editor.canIndent(blocks.value, block.key)

    fun canOutdent(block: Block) = editor.canOutdent(blocks.value, block.key)

    fun indent(key: String) = apply(editor.indent(blocks.value, key))

    fun outdent(key: String) = apply(editor.outdent(blocks.value, key))

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

    /** The user arranged the properties: [shown] in this order, then [hidden] (folded away). */
    fun arrangeProperties(shown: List<String>, hidden: List<String>) = updateLayout { PropertyLayouts.arranged(it, shown, hidden) }



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

    /** Sets the page's icon to [emoji], or removes it when that is null. */
    fun setIcon(emoji: String?) = viewModelScope.launch { repository.setIcon(pageId, emoji) }

    /** Makes a new page inside this one and opens it. */
    fun createSubpage(onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(repository.createPage(repository.resolve(pageId)))
    }

    /**
     * The sync button: hands over edits that are still waiting, sends everything and gets the
     * note's latest version from Notion. The editor shows it unless the user typed meanwhile.
     */
    fun sync() {
        if (syncing.value) return
        syncing.value = true
        viewModelScope.launch {
            try {
                if (saveJob?.isActive == true) {
                    saveJob?.cancel()
                    firstUnsavedMs = 0L
                    repository.saveContent(pageId, savable())
                }
                pendingProperties.values.toList().forEach { repository.updateProperty(pageId, it) }
                val before = blocks.value
                val fresh = repository.syncNote(pageId)
                if (fresh != null && blocks.value === before && saveJob?.isActive != true) {
                    setLoaded(fresh.blocks)
                    contentError.value = false
                }
            } finally {
                syncing.value = false
            }
        }
    }

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
