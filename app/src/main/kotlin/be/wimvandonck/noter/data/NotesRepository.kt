package be.wimvandonck.noter.data

import be.wimvandonck.noter.data.api.NotionClient
import be.wimvandonck.noter.data.api.NotionErrors
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.api.objOrNull
import be.wimvandonck.noter.data.api.stringOrNull
import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.PageContent
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.data.notion.BlockCodec
import be.wimvandonck.noter.data.notion.InlineMarkdown
import be.wimvandonck.noter.data.web.WebPageClient
import be.wimvandonck.noter.logic.SharedContent
import be.wimvandonck.noter.logic.SharedNotes
import be.wimvandonck.noter.logic.SubpageTitles
import be.wimvandonck.noter.logic.WebArticles
import kotlinx.serialization.json.JsonObject
import be.wimvandonck.noter.data.notion.PropertyCodec
import be.wimvandonck.noter.data.notion.TokenCodec
import be.wimvandonck.noter.data.notion.TokenInfo
import be.wimvandonck.noter.data.notion.WorkspaceCodec
import be.wimvandonck.noter.data.store.AppSettings
import be.wimvandonck.noter.data.store.CacheStore
import be.wimvandonck.noter.data.store.DatabaseCache
import be.wimvandonck.noter.data.store.PagesCache
import be.wimvandonck.noter.data.store.SettingsStore
import be.wimvandonck.noter.data.store.UsersCache
import be.wimvandonck.noter.data.sync.BlockMatch
import be.wimvandonck.noter.data.sync.BlockRef
import be.wimvandonck.noter.data.sync.ContentDiff
import be.wimvandonck.noter.data.sync.ContentOp
import be.wimvandonck.noter.data.sync.Outbox
import be.wimvandonck.noter.data.sync.PendingOp
import be.wimvandonck.noter.data.sync.SaveTiming
import be.wimvandonck.noter.data.sync.SourceKeys
import be.wimvandonck.noter.data.sync.RefreshKind
import be.wimvandonck.noter.data.sync.SyncPolicy
import be.wimvandonck.noter.data.sync.withProperty
import be.wimvandonck.noter.logic.Columns
import be.wimvandonck.noter.logic.KnownUsers
import be.wimvandonck.noter.logic.NoteOrdering
import be.wimvandonck.noter.logic.DatabaseSetups
import be.wimvandonck.noter.logic.NotePreviews
import be.wimvandonck.noter.logic.SchemaOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import be.wimvandonck.noter.logic.NoteLists

/** One database's notes as the screens see them. */
data class DatabaseState(
    val schema: DatabaseSchema? = null,
    val notes: List<NoteSummary> = emptyList(),
    val lastSyncMs: Long = 0,
    val lastFullSyncMs: Long = 0,
    /** False until the cache was read (or there was none and the first download finished). */
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

/** Something the user should hear about. */
sealed interface RepoEvent {
    data class SaveFailed(val noteTitle: String, val detail: String) : RepoEvent

    /** Notion refused a new page; it was removed here. [topLevel]: it was to go at the top of the workspace. */
    data class CreateFailed(val topLevel: Boolean, val detail: String) : RepoEvent

    data object KeyRejected : RepoEvent
}

/**
 * The single source of notes for the screens.
 *
 * - **Reading**: everything is shown from the on-phone copy first, then refreshed in the
 *   background (see [SyncPolicy]), so lists and notes open instantly and work offline.
 * - **Writing**: edits change the on-phone copy at once and go into a queue ([Outbox]) that is
 *   sent to Notion soon after each edit (see [SaveTiming]), in order, retried when offline. Content is sent
 *   as a diff of changed blocks ([ContentDiff]), never by rewriting the whole note.
 */
class NotesRepository(
    private val client: NotionClient,
    private val hasKey: () -> Boolean,
    private val settings: SettingsStore,
    private val cache: CacheStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Downloads links shared to the app (#63, [saveShared]). */
    private val web: WebPageClient = WebPageClient(),
) {
    private val lock = Mutex()
    private val flushLock = Mutex()

    private val _databases = MutableStateFlow<Map<String, DatabaseState>>(emptyMap())
    val databases: StateFlow<Map<String, DatabaseState>> = _databases.asStateFlow()

    private val _outbox = MutableStateFlow<List<PendingOp>>(emptyList())

    /** Number of changes waiting to be sent to Notion. */
    val pendingCount: Flow<Int> = _outbox.map { it.size }.distinctUntilChanged()

    private val _offline = MutableStateFlow(false)

    /** True when the last attempt to reach Notion failed for lack of a connection. */
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    private val _missingSources = MutableStateFlow<Set<String>>(emptySet())

    /** Data sources (IDs without dashes) Notion can't find or has in its trash: their databases say so. */
    val missingSources: StateFlow<Set<String>> = _missingSources.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Preview text of notes' content for the overview cards, by page ID (see [NotePreviews]). */
    val previews: StateFlow<Map<String, String>> = _previews.asStateFlow()
    private val previewLock = Mutex()

    private val _recent = MutableStateFlow<List<WorkspaceItem>?>(null)

    /**
     * Home's databases and pages: the most recently edited ones Notion's search lists, kept on this
     * phone (null until the copy was read and nothing is kept; see [refreshRecent]).
     */
    val recent: StateFlow<List<WorkspaceItem>?> = _recent.asStateFlow()

    private val _events = MutableSharedFlow<RepoEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RepoEvent> = _events

    /** Notes created on this phone that got their Notion ID: local ID → Notion ID. */
    private val renamed = MutableStateFlow<Map<String, String>>(emptyMap())

    private var flushJob: Job? = null
    private var retryJob: Job? = null
    private var retryDelayMs = RETRY_MIN_MS

    /** Notes moved to the trash on this phone: later edits to them are dropped, not sent. */
    private val trashed = mutableSetOf<String>()

    fun resolve(id: String): String = renamed.value[id] ?: id

    // ---- Start-up -------------------------------------------------------------------------

    private val outboxLoaded = CompletableDeferred<Unit>()

    fun start() {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                _outbox.value = cache.outbox()
                cache.recent()?.let { _recent.value = it }
                client.restoreOwners(cache.owners())
            }
            outboxLoaded.complete(Unit)
            // Which workspace things are in, kept a moment after it changes (it changes in bursts).
            scope.launch(Dispatchers.IO) {
                client.owners.drop(1).collectLatest { owners ->
                    delay(OWNERS_SAVE_DELAY_MS)
                    cache.saveOwners(owners)
                }
            }
            loadDatabase(PAGES)
            // Every database with a copy here, for the pinned notes on Home and the notes in Search.
            cache.databaseKeys().forEach { name ->
                loadDatabase(if (name.startsWith(LEGACY_PAGE_FILE)) PAGE_PREFIX + name.removePrefix(LEGACY_PAGE_FILE) else name)
            }
            openPinnedPages()
        }
    }

    /**
     * Downloads the pinned pages that have no copy here yet (a page's notebook from before is a
     * pinned page now), once per start of the app.
     */
    private suspend fun openPinnedPages() {
        if (!hasKey()) return
        val missing = settings.current().pinned.filter { id -> !id.startsWith(NoteSummary.LOCAL_PREFIX) && findNote(_databases.value, id) == null }
        missing.forEach { openPage(it) }
    }

    /** The app came to the front: send waiting changes and refresh what is stale. */
    fun onForeground() {
        scope.launch {
            outboxLoaded.await()
            flush()
            // The databases of the pinned notes, for Home; a database refreshes when it is opened.
            pinnedSources().forEach { refresh(it, userAsked = false) }
        }
    }

    /** The databases the pinned notes are in (not the pages on their own: nothing to list). */
    suspend fun pinnedSources(): List<String> {
        val pinned = settings.current().pinned
        return pinned.mapNotNull { findNote(_databases.value, it)?.databaseId }.distinct().filter { it != PAGES && !isPageNotebook(it) }
    }

    /** Databases whose on-phone copy was read (once per app start). */
    private val cacheRead = mutableSetOf<String>()

    private suspend fun loadDatabase(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (id in cacheRead) return@withLock
            cacheRead += id
            val cached = cache.database(id)
            val queue = _outbox.value
            val cachedNotes = cached?.notes.orEmpty().map { Outbox.applyPending(it, queue) }
            _databases.update { map ->
                // Something may already be here (a note created right at start-up): keep it.
                val existing = map[id]
                val notes = existing?.notes.orEmpty().let { now -> now + cachedNotes.filter { c -> now.none { it.id == c.id } } }
                map + (
                    id to (existing ?: DatabaseState()).copy(
                        schema = existing?.schema ?: cached?.schema,
                        notes = notes,
                        lastSyncMs = cached?.lastSyncMs ?: 0,
                        lastFullSyncMs = cached?.lastFullSyncMs ?: 0,
                        // The pages on their own are only what was opened here: nothing to download.
                        loaded = existing?.loaded == true || cached != null || id == PAGES,
                    )
                    )
            }
        }
    }

    // ---- Reading --------------------------------------------------------------------------

    /**
     * The note [pageId] as it changes. Also follows a note created on this phone when it gets its
     * Notion ID (under either ID while the change happens), so an open editor keeps showing it.
     */
    fun note(pageId: String): Flow<NoteSummary?> =
        // Both are read as they are now: combine may hand over the new notes before the new ID
        // (which is set first), and the note would seem gone for a moment.
        combine(_databases, renamed) { _, _ ->
            val map = _databases.value
            renamed.value[pageId]?.let { findNote(map, it) } ?: findNote(map, pageId)
        }.distinctUntilChanged()

    private fun findNote(map: Map<String, DatabaseState>, id: String): NoteSummary? =
        map.values.firstNotNullOfOrNull { db -> db.notes.firstOrNull { it.id == id } }

    /** Refreshes one database from Notion when [SyncPolicy] says it is due (always when [userAsked]). */
    suspend fun refresh(databaseId: String, userAsked: Boolean) {
        if (!hasKey() || databaseId == PAGES) return
        loadDatabase(databaseId)
        val current = _databases.value[databaseId] ?: return
        // A page's notebook from before: its copy stays as it is (its pages are edited as usual).
        if (current.refreshing || isPageNotebook(databaseId)) return
        val kind = SyncPolicy.decide(clock(), current.lastSyncMs, current.lastFullSyncMs, userAsked)
        if (kind == RefreshKind.NONE) return
        setState(databaseId) { it.copy(refreshing = true, error = null) }
        val started = clock()
        try {
            // A key from before data sources: from now on the notes are kept under the data source.
            val sourceId = resolveSource(databaseId)
            if (sourceId != databaseId) {
                renameSource(databaseId, sourceId)
                return refresh(sourceId, userAsked)
            }
            withContext(Dispatchers.IO) {
                val schema = if (kind == RefreshKind.FULL || current.schema == null) {
                    PropertyCodec.parseSchema(client.getDataSource(databaseId))
                } else {
                    current.schema
                }
                val filter = if (kind == RefreshKind.CHANGES) {
                    val since = Instant.ofEpochMilli(current.lastSyncMs - SyncPolicy.CHANGES_OVERLAP_MS).toString()
                    mapOf("timestamp" to "last_edited_time", "last_edited_time" to mapOf("on_or_after" to since))
                } else {
                    null
                }
                val pages = client.queryDataSource(databaseId, filter)
                val fetched = pages.filterNot { PropertyCodec.isTrashed(it) }.mapNotNull { PropertyCodec.parsePage(it, databaseId) }
                val trashed = pages.filter { PropertyCodec.isTrashed(it) }.mapNotNull { it.stringOrNull("id") }.toSet()
                lock.withLock {
                    val queue = _outbox.value
                    val pendingTrash = queue.filterIsInstance<PendingOp.TrashPage>().map { it.pageId }.toSet()
                    val existing = _databases.value[databaseId]?.notes.orEmpty()
                    val merged = SyncPolicy.merge(existing, fetched, kind == RefreshKind.FULL, trashed + pendingTrash)
                        .filter { it.id !in pendingTrash }
                        .map { Outbox.applyPending(it, queue) }
                    val full = kind == RefreshKind.FULL
                    val updated = (_databases.value[databaseId] ?: DatabaseState()).copy(
                        schema = schema?.let { SchemaOptions.withValues(it, merged) },
                        notes = merged,
                        lastSyncMs = started,
                        lastFullSyncMs = if (full) started else current.lastFullSyncMs,
                        loaded = true,
                        refreshing = false,
                        error = null,
                    )
                    _databases.update { it + (databaseId to updated) }
                    saveDatabaseCache(databaseId, updated)
                }
            }
            _offline.value = false
            _missingSources.update { it - plain(databaseId) }
        } catch (e: NotionException) {
            if (e.isOffline) _offline.value = true
            if (e.status == 401) _events.tryEmit(RepoEvent.KeyRejected)
            if (e.status == 404) _missingSources.update { it + plain(databaseId) }
            setState(databaseId) { it.copy(refreshing = false, loaded = true, error = NotionErrors.of(e)) }
        }
    }

    private fun setState(id: String, transform: (DatabaseState) -> DatabaseState) {
        _databases.update { map -> map + (id to transform(map[id] ?: DatabaseState())) }
    }

    private fun saveDatabaseCache(id: String, state: DatabaseState) {
        cache.saveDatabase(id, DatabaseCache(state.schema, state.notes, state.lastSyncMs, state.lastFullSyncMs))
    }

    // ---- Data sources ---------------------------------------------------------------------

    /** Which data source each key is, as far as known (kept on this phone, see [resolveSource]). */
    private val sources = mutableMapOf<String, String>()
    private var sourcesRead = false

    /**
     * The data source that [key] stands for: [key] itself when it is a data source; for a database's
     * ID (from links, and views saved before data sources) its first data source.
     */
    suspend fun resolveSource(key: String): String = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!sourcesRead) {
                sources.putAll(cache.sources())
                sourcesRead = true
            }
            sources[key]
        }?.let { return@withContext it }
        // A database first: Notion may also answer for a database's ID as if it were a data source.
        val fromDatabase = try {
            val list = WorkspaceCodec.dataSources(client.getDatabase(key))
            // A data source's own ID among them (in case Notion answers with its database): itself.
            if (list.any { it.id.replace("-", "") == key.replace("-", "") }) key else list.firstOrNull()?.id
        } catch (e: NotionException) {
            if (e.isTemporary || e.status == 401) throw e
            null
        }
        val source = fromDatabase ?: try {
            client.getDataSource(key)
            key
        } catch (e: NotionException) {
            if (e.isTemporary || e.status == 401) throw e
            throw NotionException("This database has no data source", status = 404, code = NotionErrors.NO_DATA_SOURCE)
        }
        lock.withLock {
            sources[key] = source
            sources[source] = source
            cache.saveSources(sources.toMap())
        }
        source
    }

    /**
     * The data sources of [key] when it is a database with more than one (to choose from), else
     * null. Null too when Notion can't be reached.
     */
    suspend fun dataSourcesOf(key: String): List<PageRef>? = databaseInfo(key)?.sources?.takeIf { it.size > 1 }

    /** What Notion said about databases, by database ID without dashes (see [databaseInfo]). */
    private val databaseInfos = mutableMapOf<String, DatabaseInfo>()
    private var databaseInfosRead = false

    /** Databases asked about since the app started (a list kept from before is asked again once). */
    private val databasesAsked = mutableSetOf<String>()

    /**
     * Database [id]'s name and data sources: kept on this phone, asked again once per start of the
     * app when [fresh]. Null when it isn't a database the connection can read, or Notion can't be
     * reached and nothing is kept.
     */
    suspend fun databaseInfo(id: String, fresh: Boolean = true): DatabaseInfo? = withContext(Dispatchers.IO) {
        val key = id.replace("-", "")
        val known = lock.withLock {
            if (!databaseInfosRead) {
                databaseInfos.putAll(cache.databaseInfos())
                databaseInfosRead = true
            }
            databaseInfos[key]
        }
        if (known != null && (!fresh || key in databasesAsked)) return@withContext known
        if (!fresh) return@withContext null
        val info = try {
            WorkspaceCodec.databaseInfo(client.getDatabase(id))
        } catch (e: NotionException) {
            if (e.isOffline || e.isTemporary) return@withContext known
            null
        }
        lock.withLock {
            databasesAsked += key
            if (info == null) databaseInfos -= key else databaseInfos[key] = info
            cache.saveDatabaseInfos(databaseInfos.toMap())
        }
        info
    }

    /**
     * Search results grouped by their databases (see WorkspaceCodec.group), with what is kept on
     * this phone, or, when [ask], after asking Notion about databases not asked about yet.
     */
    suspend fun groupByDatabase(items: List<WorkspaceItem>, ask: Boolean): List<WorkspaceItem> {
        val ids = items.mapNotNull { it.databaseId }.distinctBy { it.replace("-", "") }
        val infos = ids.mapNotNull { id -> databaseInfo(id, fresh = ask)?.let { id.replace("-", "") to it } }.toMap()
        return WorkspaceCodec.group(items, infos).also { lastFound = it }
    }

    /** Moves everything kept under database [old] to its data source [new] (see [SourceKeys]). */
    private suspend fun renameSource(old: String, new: String) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                cacheRead += new
                val oldState = _databases.value[old]
                if (oldState != null) {
                    val target = _databases.value[new]
                    val merged = (target ?: oldState).copy(
                        notes = SourceKeys.mergeNotes(oldState.notes, target?.notes.orEmpty(), new),
                        // A different key's sync times don't count: refresh in full.
                        lastSyncMs = 0,
                        lastFullSyncMs = 0,
                        refreshing = false,
                    )
                    _databases.update { (it - old) + (new to merged) }
                    saveDatabaseCache(new, merged)
                }
                cache.deleteDatabase(old)
                _outbox.value = SourceKeys.renameInOutbox(_outbox.value, old, new)
                cache.saveOutbox(_outbox.value)
            }
        }
        settings.update { SourceKeys.renameInSettings(it, old, new) }
    }

    // ---- Notes ----------------------------------------------------------------------------

    /**
     * Creates a page on its own on this phone (it is sent to Notion right after): inside page
     * [parentPageId], or at the top of the workspace when that is null. Returns its ID.
     */
    suspend fun createPage(
        parentPageId: String?,
        key: String = PAGES,
        workspace: String? = null,
        /** Its title (plain text), for a page made from a share. */
        title: String = "",
        /** Content made with the page (a share, #63). */
        children: List<Block> = emptyList(),
    ): String = withContext(Dispatchers.IO) {
        loadDatabase(key)
        val id = NoteSummary.LOCAL_PREFIX + UUID.randomUUID()
        val now = Instant.ofEpochMilli(clock()).toString()
        val markdown = InlineMarkdown.escape(title)
        val titleProperty = NoteProperty(PAGE_TITLE, "title", PropertyValue.Text(markdown))
        val page = NoteSummary(id, key, title = markdown, createdTime = now, lastEditedTime = now, properties = listOf(titleProperty))
        lock.withLock {
            setState(key) { it.copy(notes = listOf(page) + it.notes, loaded = true) }
            _databases.value[key]?.let { saveDatabaseCache(key, it) }
            cache.saveContent(PageContent(id, blocks = emptyList(), base = emptyList(), nestedLoaded = true))
            enqueue(PendingOp.CreatePage(id, key, parentPageId?.let(::resolve), workspace.takeIf { parentPageId == null }, children))
        }
        scheduleFlush()
        id
    }

    /**
     * A new note from what another app shared (#63): in data source [databaseId], or a page at
     * the top of [workspace] when that is null. A shared link is downloaded first ([web]) so the
     * page's text and images go into the note; when that fails (offline, not a web page) the note
     * still has the link as a bookmark. Offline, the note is made (with its content) once the
     * phone is back online. Returns the new note's ID (null: the database's properties couldn't
     * be read).
     */
    suspend fun saveShared(shared: SharedContent, databaseId: String?, workspace: String?): String? {
        val article = shared.url?.let { url ->
            web.fetch(url)?.let { page ->
                withContext(Dispatchers.Default) { runCatching { WebArticles.parse(page.html, page.url) }.getOrNull() }
            }
        }
        val title = SharedNotes.title(shared, article)
        val blocks = SharedNotes.blocks(shared, article)
        val id = if (databaseId != null) {
            createNote(databaseId, title = title, children = blocks, url = shared.url)
        } else {
            createPage(null, workspace = workspace, title = title, children = blocks)
        } ?: return null
        // Made in Notion before the note opens (when online), so it opens with the shared content.
        flush()
        return resolve(id)
    }

    /**
     * Makes database [request] in Notion (#71, see NewDatabases) and returns the ID to open it by:
     * its first data source. Not queued like notes: it needs Notion now, and throws when Notion
     * can't be reached or refuses. Home's list is asked again afterwards.
     */
    suspend fun createDatabase(request: Map<String, Any?>, workspace: String? = null): String = withContext(Dispatchers.IO) {
        val created = client.createDatabase(request, workspace)
        val info = WorkspaceCodec.databaseInfo(created) ?: throw NotionException("Notion did not return a database", status = 500)
        lock.withLock {
            if (!databaseInfosRead) {
                databaseInfos.putAll(cache.databaseInfos())
                databaseInfosRead = true
            }
            val key = info.id.replace("-", "")
            databaseInfos[key] = info
            databasesAsked += key
            cache.saveDatabaseInfos(databaseInfos.toMap())
        }
        scope.launch { runCatching { refreshRecent() } }
        info.sources.firstOrNull()?.id ?: info.id
    }

    /** Removes a note that only exists on this phone (Notion refused to make it). */
    private suspend fun dropLocal(pageId: String) {
        lock.withLock {
            val databaseId = findNote(_databases.value, pageId)?.databaseId ?: return@withLock
            setState(databaseId) { db -> db.copy(notes = db.notes.filterNot { it.id == pageId }) }
            _databases.value[databaseId]?.let { saveDatabaseCache(databaseId, it) }
            cache.deleteContent(pageId)
            _outbox.value = _outbox.value.filterNot { it.pageId == pageId }
            cache.saveOutbox(_outbox.value)
        }
        settings.update { s -> s.copy(pinned = s.pinned - pageId) }
    }

    /**
     * Creates a note on this phone (it is sent to Notion right after) and returns its ID.
     * [preset] values are filled in, e.g. the chip that is selected.
     */
    suspend fun createNote(
        databaseId: String,
        preset: List<NoteProperty> = emptyList(),
        /** Its title (plain text), for a note made from a share. */
        title: String = "",
        /** Content made with the note (a share, #63). */
        children: List<Block> = emptyList(),
        /** A shared link: goes in the database's URL property, when it has one (#68). */
        url: String? = null,
    ): String? =
        withContext(Dispatchers.IO) {
            loadDatabase(databaseId)
            var schema = _databases.value[databaseId]?.schema
            if (schema == null) {
                schema = runCatching { PropertyCodec.parseSchema(client.getDataSource(resolveSource(databaseId))) }.getOrNull()
                    ?: return@withContext null
                setState(databaseId) { it.copy(schema = schema) }
            }
            val id = NoteSummary.LOCAL_PREFIX + UUID.randomUUID()
            val now = Instant.ofEpochMilli(clock()).toString()
            // The preset wins over the shared link when both fill the same property.
            val presetByName = (listOfNotNull(SharedNotes.urlProperty(schema, url)) + preset).associateBy { it.name }
            val markdown = InlineMarkdown.escape(title)
            val properties = schema.properties
                .filter { it.type in NoteProperty.EDITABLE_TYPES }
                .map { presetByName[it.name] ?: PropertyCodec.emptyValue(it) }
                .map { if (markdown.isNotEmpty() && it.type == "title") it.copy(value = PropertyValue.Text(markdown)) else it }
            val note = NoteSummary(id, databaseId, title = markdown, createdTime = now, lastEditedTime = now, properties = properties)
            lock.withLock {
                setState(databaseId) { it.copy(notes = listOf(note) + it.notes) }
                _databases.value[databaseId]?.let { saveDatabaseCache(databaseId, it) }
                cache.saveContent(PageContent(id, blocks = emptyList(), base = emptyList()))
                enqueue(PendingOp.CreatePage(id, databaseId, children = children))
            }
            scheduleFlush()
            id
        }

    suspend fun updateProperty(pageId: String, property: NoteProperty) = withContext(Dispatchers.IO) {
        val id = resolve(pageId)
        lock.withLock {
            if (id in trashed) return@withLock
            val databaseId = findNote(_databases.value, id)?.databaseId ?: return@withLock
            val now = Instant.ofEpochMilli(clock()).toString()
            setState(databaseId) { db ->
                val notes = db.notes.map { if (it.id == id) it.withProperty(property).copy(lastEditedTime = now) else it }
                // An option added here shows in every note's picker at once (Notion makes it on sending).
                db.copy(notes = notes, schema = db.schema?.let { SchemaOptions.withValues(it, notes) })
            }
            _databases.value[databaseId]?.let { saveDatabaseCache(databaseId, it) }
            enqueue(PendingOp.SetProperty(id, property))
        }
        scheduleFlush()
    }

    /** Sets the page's icon to [emoji] (removes it when null) here, and queues it for Notion. */
    suspend fun setIcon(pageId: String, emoji: String?) = withContext(Dispatchers.IO) {
        val id = resolve(pageId)
        lock.withLock {
            if (id in trashed) return@withLock
            val databaseId = findNote(_databases.value, id)?.databaseId ?: return@withLock
            val now = Instant.ofEpochMilli(clock()).toString()
            val icon = emoji?.let { PageIcon(emoji = it) }
            setState(databaseId) { db -> db.copy(notes = db.notes.map { if (it.id == id) it.copy(icon = icon, lastEditedTime = now) else it }) }
            _databases.value[databaseId]?.let { saveDatabaseCache(databaseId, it) }
            enqueue(PendingOp.SetIcon(id, emoji))
        }
        scheduleFlush()
    }

    /** Moves the note to Notion's trash and removes it here. */
    suspend fun trashNote(pageId: String) = withContext(Dispatchers.IO) {
        val id = resolve(pageId)
        lock.withLock {
            trashed += id
            val databaseId = findNote(_databases.value, id)?.databaseId
            if (databaseId != null) {
                setState(databaseId) { db -> db.copy(notes = db.notes.filterNot { it.id == id }) }
                _databases.value[databaseId]?.let { saveDatabaseCache(databaseId, it) }
            }
            cache.deleteContent(id)
            enqueue(PendingOp.TrashPage(id))
        }
        settings.update { s -> s.copy(pinned = s.pinned - id) }
        scheduleFlush()
    }

    private fun enqueue(op: PendingOp) {
        if (op !is PendingOp.TrashPage && op.pageId in trashed) return
        _outbox.value = Outbox.add(_outbox.value, op)
        cache.saveOutbox(_outbox.value)
    }

    // ---- Content --------------------------------------------------------------------------

    suspend fun cachedContent(pageId: String): PageContent? = withContext(Dispatchers.IO) { cache.content(resolve(pageId)) }

    /** Stores the edited content here and queues it for Notion. */
    suspend fun saveContent(pageId: String, blocks: List<Block>) = withContext(Dispatchers.IO) {
        val id = resolve(pageId)
        lock.withLock {
            if (id in trashed) return@withLock
            val existing = cache.content(id) ?: PageContent(id, blocks = emptyList(), base = emptyList())
            // The editor doesn't know the IDs Notion gave its new blocks: keep them.
            val withIds = BlockMatch.keepIds(blocks, existing.blocks)
            val dirty = ContentDiff.plan(existing.base, withIds).isNotEmpty()
            val saved = existing.copy(blocks = withIds, dirty = dirty)
            cache.saveContent(saved)
            updatePreview(id, saved)
            if (dirty) enqueue(PendingOp.SaveContent(id))
        }
        scheduleFlush()
    }

    /**
     * The sync button of a note: sends what is waiting, then gets the note's latest version from
     * Notion. Returns the content when nothing on this phone is waiting to be sent.
     */
    suspend fun syncNote(pageId: String): PageContent? {
        flush()
        return fetchContent(pageId)
    }

    /**
     * Gets the latest content from Notion when it changed there. Returns null when the on-phone
     * copy should stay (it has unsent edits, the note is new, or Notion can't be reached).
     */
    suspend fun fetchContent(pageId: String): PageContent? = withContext(Dispatchers.IO) {
        val id = resolve(pageId)
        if (id.startsWith(NoteSummary.LOCAL_PREFIX) || !hasKey()) return@withContext null
        try {
            val page = client.getPage(id)
            val databaseId = findNote(_databases.value, id)?.databaseId
            if (databaseId != null) {
                PropertyCodec.parsePage(page, databaseId)?.let { fresh -> replaceNote(databaseId, fresh) }
            }
            val edited = page.stringOrNull("last_edited_time")
            val cached = cache.content(id)
            val current = cached != null && cached.remoteEditedTime == edited && cached.nestedLoaded && !cached.hasExpiredFiles(clock())
            if (cached != null && current && !cached.dirty) return@withContext withSubpageTitles(id, cached)
            if (cached?.dirty == true) return@withContext null
            val fresh = downloadContent(id, cached?.blocks.orEmpty(), edited)
            lock.withLock {
                val now = cache.content(id)
                if (now?.dirty == true) return@withLock null
                cache.saveContent(fresh)
                updatePreview(id, fresh)
                fresh
            }
        } catch (e: NotionException) {
            if (e.isOffline) _offline.value = true
            null
        }
    }

    /**
     * [cached] with the titles its subpages have in Notion now ([SubpageTitles]): one request
     * when the page has subpages. Null when an edit on this phone is waiting meanwhile.
     */
    private suspend fun withSubpageTitles(id: String, cached: PageContent): PageContent? {
        if (!SubpageTitles.any(cached.blocks)) return cached
        val titles = client.blockChildren(id).mapNotNull { json ->
            val blockId = json.stringOrNull("id") ?: return@mapNotNull null
            BlockCodec.parse(json, blockId)?.let { blockId to it.text }
        }.toMap()
        if (SubpageTitles.renamed(cached.blocks, titles) == null) return cached
        return lock.withLock {
            val now = cache.content(id) ?: cached
            if (now.dirty) return@withLock null
            // Subpages can't be edited here: what Notion has (base) changes the same way.
            val updated = now.copy(
                blocks = SubpageTitles.renamed(now.blocks, titles) ?: now.blocks,
                base = SubpageTitles.renamed(now.base, titles) ?: now.base,
            )
            cache.saveContent(updated)
            updated
        }
    }

    /**
     * Downloads a note's content. With [nested] the blocks inside blocks come too, as deep as
     * [MAX_DEPTH] (one more request per block with nested content); the overview's previews only
     * need the top level.
     */
    private suspend fun downloadContent(id: String, previous: List<Block>, edited: String?, nested: Boolean = true): PageContent {
        val parsed = downloadBlocks(id, parentId = null, parentType = null, depth = 0, nested = nested)
        // Blocks keep their keys on this phone, also blocks sent from here whose ID wasn't known yet.
        val keys = BlockMatch.keysFor(previous, parsed)
        val blocks = parsed.map { it.copy(key = keys[it.notionId] ?: UUID.randomUUID().toString()) }
        return PageContent(id, blocks = blocks, base = blocks, dirty = false, remoteEditedTime = edited, nestedLoaded = nested)
    }

    /**
     * The blocks in [blockId] (a page or a block of Notion's kind [parentType]), each followed by
     * what is nested in it.
     */
    private suspend fun downloadBlocks(blockId: String, parentId: String?, parentType: String?, depth: Int, nested: Boolean): List<Block> {
        val result = mutableListOf<Block>()
        for (json in client.blockChildren(blockId)) {
            val notionId = json.stringOrNull("id") ?: continue
            val parsed = BlockCodec.parse(json, notionId)?.copy(depth = depth, parentId = parentId) ?: continue
            val block = if (parentType == Columns.TABS) BlockCodec.asTab(parsed) else parsed
            when {
                // A table's cells are its rows (nested blocks): one more request per table.
                block.rawType == "table" && block.hasChildren -> result += block.copy(rows = BlockCodec.tableRows(client.blockChildren(notionId)))
                // Text blocks and columns show what is in them; images, synced blocks, … keep theirs in Notion.
                nested && block.hasChildren && (block.type != BlockType.UNSUPPORTED || Columns.isContainer(block)) && depth < MAX_DEPTH -> {
                    result += block.copy(hasChildren = false)
                    result += downloadBlocks(notionId, notionId, block.rawType, depth + 1, nested = true)
                }
                else -> result += block
            }
        }
        return result
    }

    private suspend fun replaceNote(databaseId: String, fresh: NoteSummary) = lock.withLock {
        val note = Outbox.applyPending(fresh, _outbox.value)
        setState(databaseId) { db ->
            val notes = db.notes.map { if (it.id == note.id) note else it }
            db.copy(notes = notes, schema = db.schema?.let { SchemaOptions.withValues(it, notes) })
        }
        _databases.value[databaseId]?.let { saveDatabaseCache(databaseId, it) }
    }

    // ---- Sending changes ------------------------------------------------------------------

    /** When the oldest change not handed to [flush] yet was queued (0: none waiting). */
    private var firstUnsentMs = 0L

    /**
     * Sends the queue soon after an edit: after a short pause in typing, and at least about once a
     * second while typing goes on ([SaveTiming.NOTION]), so a letter isn't a request of its own.
     * [delayMs] overrides the timing (leaving a note sends at once).
     */
    fun scheduleFlush(delayMs: Long? = null) {
        val now = clock()
        if (firstUnsentMs == 0L) firstUnsentMs = now
        val wait = delayMs ?: SaveTiming.NOTION.delayMs(now, firstUnsentMs)
        // Only the wait is cancelled: a send that started always finishes (see flush).
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(wait)
            firstUnsentMs = 0L
            scope.launch { flush() }
        }
    }

    /**
     * Sends every queued change now, in order. Stops at the first one that can't be sent yet.
     *
     * Never cancelled halfway (also when a screen that asked for it closes): Notion may already
     * have carried out a request whose answer would then be lost, and the change would be sent
     * again (a new block added twice).
     */
    suspend fun flush() = withContext(NonCancellable) {
        if (!hasKey()) return@withContext
        flushLock.withLock {
            while (true) {
                val op = lock.withLock { _outbox.value.firstOrNull() } ?: break
                val done = try {
                    withContext(Dispatchers.IO) { execute(op) }
                } catch (e: NotionException) {
                    when {
                        e.isTemporary -> {
                            if (e.isOffline) _offline.value = true
                            scheduleRetry(e.isOffline)
                            return@withLock
                        }
                        e.status == 401 -> {
                            _events.tryEmit(RepoEvent.KeyRejected)
                            return@withLock
                        }
                        // Gone from Notion or moved to its trash (here or elsewhere): nothing left to save.
                        e.isNotFound || e.isArchived -> true
                        // Notion won't make the page: don't keep a note here that can never be saved.
                        op is PendingOp.CreatePage -> {
                            dropLocal(op.pageId)
                            _events.tryEmit(RepoEvent.CreateFailed(op.databaseId == PAGES && op.parentPageId == null, NotionErrors.of(e)))
                            true
                        }
                        else -> {
                            val title = findNote(_databases.value, op.pageId)?.title.orEmpty()
                            _events.tryEmit(RepoEvent.SaveFailed(InlineMarkdown.stripped(title), NotionErrors.of(e)))
                            true
                        }
                    }
                }
                _offline.value = false
                retryDelayMs = RETRY_MIN_MS
                lock.withLock {
                    val queue = _outbox.value.toMutableList()
                    // A create is found by its note (create marks it as sent, and renames it).
                    val index = queue.indexOfFirst {
                        it == op || (op is PendingOp.CreatePage && it is PendingOp.CreatePage && resolve(it.pageId) == resolve(op.pageId))
                    }
                    if (index >= 0) {
                        queue.removeAt(index)
                        // Still more to send for this note (edited while sending): send it again later.
                        if (!done) queue.add(op)
                    }
                    _outbox.value = queue
                    cache.saveOutbox(queue)
                }
            }
        }
    }

    /**
     * Tries again later, waiting longer each time. Without a connection the wait stays short
     * (at most a minute): a try costs nothing then, and changes go out soon after the phone is
     * back online. Opening the app also sends at once ([onForeground]).
     */
    private fun scheduleRetry(offline: Boolean) {
        if (retryJob?.isActive == true) return
        val wait = if (offline) retryDelayMs.coerceAtMost(RETRY_OFFLINE_MAX_MS) else retryDelayMs
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
        retryJob = scope.launch {
            delay(wait)
            flush()
        }
    }

    /** Sends one change. Returns false when the note has newer edits that still need sending. */
    private suspend fun execute(op: PendingOp): Boolean = when (op) {
        is PendingOp.CreatePage -> create(op)
        is PendingOp.SetProperty -> {
            val request = PropertyCodec.toRequest(op.property)
            if (request != null) {
                val page = client.updatePageProperties(op.pageId, mapOf(op.property.name to request))
                val databaseId = findNote(_databases.value, op.pageId)?.databaseId
                if (databaseId != null) {
                    PropertyCodec.parsePage(page, databaseId)?.let { replaceNote(databaseId, it) }
                }
            }
            true
        }
        is PendingOp.SaveContent -> sendContent(op.pageId)
        is PendingOp.SetIcon -> {
            val page = client.updatePageIcon(op.pageId, op.emoji)
            val databaseId = findNote(_databases.value, op.pageId)?.databaseId
            if (databaseId != null) PropertyCodec.parsePage(page, databaseId)?.let { replaceNote(databaseId, it) }
            true
        }
        is PendingOp.TrashPage -> {
            client.trashPage(op.pageId)
            true
        }
    }

    private suspend fun create(op: PendingOp.CreatePage): Boolean {
        val note = findNote(_databases.value, op.pageId) ?: return true
        val sent = note.properties
        val sentEmoji = note.icon?.emoji
        val source = if (op.databaseId == PAGES || isPageNotebook(op.databaseId)) null else resolveSource(op.databaseId)
        // Sent before without an answer (offline, a timeout, a server error): Notion may have made
        // it anyway. Look for it first, so the note isn't made twice.
        var made: JsonObject? = op.sentAt?.let { findCreated(op, note, source, it) }
        if (made == null) markSent(op)
        // Shared content goes with the page; when Notion refuses it (an image link it can't use), less of it.
        val attempts = if (made != null) emptyList() else BlockCodec.createAttempts(op.children)
        for ((i, children) in attempts.withIndex()) {
            try {
                made = if (source == null) {
                    client.createPageIn(op.parentPageId, PropertyCodec.toCreateRequest(sent), sentEmoji, op.workspace, children)
                } else {
                    client.createPage(source, PropertyCodec.toCreateRequest(sent), children = children, emoji = sentEmoji)
                }
                break
            } catch (e: NotionException) {
                if (e.status != 400 || i == attempts.lastIndex) throw e
            }
        }
        val page = made ?: throw NotionException("Notion did not create the page", status = 500)
        val newId = page.stringOrNull("id") ?: throw NotionException("Notion did not return a page ID", status = 500)
        val created = PropertyCodec.parsePage(page, op.databaseId) ?: note.copy(id = newId)
        val oldId = op.pageId
        lock.withLock {
            // The note went to the trash while Notion was creating it: trash the new page too.
            if (oldId in trashed) {
                trashed += newId
                renamed.update { it + (oldId to newId) }
                _outbox.value = Outbox.add(Outbox.rename(_outbox.value, oldId, newId), PendingOp.TrashPage(newId))
                cache.saveOutbox(_outbox.value)
                return@withLock
            }
            renamed.update { it + (oldId to newId) }
            val current = findNote(_databases.value, oldId) ?: note
            // Values changed while the note was being created are sent as normal edits.
            var merged = created
            current.properties.forEach { prop ->
                if (prop.isEditable && sent.firstOrNull { it.name == prop.name } != prop) {
                    merged = merged.withProperty(prop)
                    _outbox.value = Outbox.add(_outbox.value, PendingOp.SetProperty(newId, prop))
                }
            }
            if (current.icon?.emoji != sentEmoji) {
                merged = merged.copy(icon = current.icon)
                _outbox.value = Outbox.add(_outbox.value, PendingOp.SetIcon(newId, current.icon?.emoji))
            }
            setState(op.databaseId) { db -> db.copy(notes = db.notes.map { if (it.id == oldId) merged else it }) }
            _databases.value[op.databaseId]?.let { saveDatabaseCache(op.databaseId, it) }

            val content = cache.content(oldId)
            if (content != null && op.children.isNotEmpty() && content.blocks.isEmpty()) {
                // The shared content went with the page: it is downloaded when the note opens.
                cache.deleteContent(oldId)
            } else if (content != null) {
                val moved = content.copy(pageId = newId, base = emptyList(), dirty = content.blocks.isNotEmpty())
                cache.saveContent(moved)
                cache.deleteContent(oldId)
                if (moved.dirty) _outbox.value = Outbox.add(_outbox.value, PendingOp.SaveContent(newId))
            }
            _outbox.value = Outbox.rename(_outbox.value, oldId, newId)
            cache.saveOutbox(_outbox.value)
        }
        if (newId in trashed) return true
        settings.update { s ->
            s.copy(
                pinned = NoteOrdering.rename(s.pinned, oldId, newId),
                order = s.order.mapValues { (_, ids) -> NoteOrdering.rename(ids, oldId, newId) },
            )
        }
        return true
    }

    /** Keeps when [op] was first sent (see [PendingOp.CreatePage.sentAt]). */
    private suspend fun markSent(op: PendingOp.CreatePage) = lock.withLock {
        if (op.sentAt != null) return@withLock
        val now = clock()
        _outbox.value = _outbox.value.map { if (it is PendingOp.CreatePage && it.pageId == op.pageId) it.copy(sentAt = now) else it }
        cache.saveOutbox(_outbox.value)
    }

    /**
     * The page Notion made for [op] when an earlier try's answer was lost: a page with the note's
     * title, made in the same place since a little before it was first sent ([since]). Null when
     * there is none (it is then made now).
     */
    private suspend fun findCreated(op: PendingOp.CreatePage, note: NoteSummary, source: String?, since: Long): JsonObject? {
        val after = Instant.ofEpochMilli(since - CREATE_CLOCK_SLACK_MS)
        fun madeSince(json: JsonObject) = json.stringOrNull("created_time")?.let { runCatching { Instant.parse(it) }.getOrNull() }?.isAfter(after) == true
        fun sameTitle(page: JsonObject) = PropertyCodec.parsePage(page, op.databaseId)?.title == note.title
        return when {
            source != null -> client.queryDataSource(
                source,
                mapOf("timestamp" to "created_time", "created_time" to mapOf("on_or_after" to after.toString())),
            ).firstOrNull(::sameTitle)
            op.parentPageId != null -> client.blockChildren(op.parentPageId)
                .filter { it.stringOrNull("type") == "child_page" && madeSince(it) }
                .mapNotNull { child -> child.stringOrNull("id")?.let { client.getPage(it) } }
                .lastOrNull(::sameTitle)
            else -> client.search(InlineMarkdown.stripped(note.title), limit = 20)
                .filter { it.stringOrNull("object") == "page" && it.objOrNull("parent")?.stringOrNull("type") == "workspace" && madeSince(it) }
                .firstOrNull(::sameTitle)
        }
    }

    private suspend fun sendContent(pageId: String): Boolean {
        var content = cache.content(pageId) ?: return true
        if (!content.dirty) return true
        if (content.uncertain) content = reconcile(pageId) ?: return true
        val snapshot = content.blocks
        val base = content.base.toMutableList()
        val idsByKey = mutableMapOf<String, String>()
        var reload = false

        suspend fun persist() = lock.withLock {
            val now = cache.content(pageId) ?: return@withLock
            val blocks = now.blocks.map { b -> idsByKey[b.key]?.let { b.copy(notionId = it) } ?: b }
            cache.saveContent(now.copy(blocks = blocks, base = base.toList()))
        }

        for (op in ContentDiff.plan(content.base, snapshot)) {
            when (op) {
                is ContentOp.Update -> {
                    try {
                        client.updateBlock(op.notionId, BlockCodec.toUpdateRequest(op.block))
                    } catch (e: NotionException) {
                        if (!e.isNotFound && e.status != 400) throw e
                        reload = true
                    }
                    // Notion's copy now has the new text; where the block is (its parent) stays as Notion has it.
                    base.replaceAll { if (it.notionId == op.notionId) op.block.copy(parentId = it.parentId) else it }
                }
                is ContentOp.Delete -> {
                    try {
                        client.deleteBlock(op.notionId)
                    } catch (e: NotionException) {
                        if (!e.isNotFound && e.status != 400) throw e
                    }
                    base.removeAll { it.notionId == op.notionId }
                }
                is ContentOp.Insert -> {
                    fun resolve(ref: BlockRef?): String? = ref?.let { it.notionId ?: idsByKey[it.key] }
                    val parentId = resolve(op.parent)
                    val afterId = resolve(op.after)
                    if ((op.parent != null && parentId == null) || (op.after != null && afterId == null)) {
                        // Its parent or the block before it couldn't be added: reload, then try again.
                        reload = true
                        continue
                    }
                    val target = parentId ?: pageId
                    val bodies = op.blocks.map(BlockCodec::toCreateRequest)
                    val known = base.mapNotNull { it.notionId }.toSet()
                    val ids = try {
                        try {
                            client.appendChildren(target, bodies, afterId, op.atStart, known)
                        } catch (e: NotionException) {
                            if (e.status != 400 || !op.atStart) throw e
                            // This API version can't insert at the start: add at the end and reload.
                            client.appendChildren(target, bodies, after = null, atStart = false, knownIds = known)
                            reload = true
                            emptyList()
                        }
                    } catch (e: NotionException) {
                        // No answer, or a server error: Notion may have added the blocks anyway. More
                        // than one request's worth: the first ones were added before this one failed.
                        if (e.status == null || e.status >= 500 || op.blocks.size > NotionClient.MAX_CHILDREN) markUncertain(pageId)
                        throw e
                    }
                    if (ids.size == op.blocks.size) {
                        op.blocks.zip(ids).forEach { (block, id) ->
                            idsByKey[block.key] = id
                            base += block.copy(notionId = id, parentId = parentId)
                        }
                    } else {
                        reload = true
                    }
                }
            }
            persist()
        }

        if (reload) {
            val fresh = downloadContent(pageId, cache.content(pageId)?.blocks.orEmpty(), null)
            return lock.withLock {
                val now = cache.content(pageId) ?: return@withLock true
                // Compare what is on the phone (edited meanwhile or not) with what Notion has now:
                // blocks that couldn't be sent yet (inside a block whose ID is unknown) still go.
                val freshIds = fresh.blocks.mapNotNull { it.notionId }.toSet()
                val blocks = BlockMatch.keepIds(now.blocks, fresh.blocks).map { b ->
                    if (b.notionId != null && b.notionId !in freshIds) b.copy(notionId = null) else b
                }
                val dirty = ContentDiff.plan(fresh.blocks, blocks).isNotEmpty()
                cache.saveContent(now.copy(blocks = blocks, base = fresh.blocks, dirty = dirty, nestedLoaded = true, remoteEditedTime = null))
                !dirty
            }
        }
        return lock.withLock {
            val now = cache.content(pageId) ?: return@withLock true
            val dirty = ContentDiff.plan(now.base, now.blocks).isNotEmpty()
            cache.saveContent(now.copy(dirty = dirty, remoteEditedTime = null))
            !dirty
        }
    }

    private suspend fun markUncertain(pageId: String) = lock.withLock {
        cache.content(pageId)?.let { cache.saveContent(it.copy(uncertain = true)) }
    }

    /**
     * After a send whose outcome is unknown: downloads what Notion has and gives the blocks sent
     * from here the IDs of the blocks Notion added for them (see [BlockMatch]), so they aren't
     * added again. Blocks Notion doesn't have any more are added again.
     */
    private suspend fun reconcile(pageId: String): PageContent? {
        val before = cache.content(pageId) ?: return null
        val remote = downloadContent(pageId, before.blocks, null)
        return lock.withLock {
            val now = cache.content(pageId) ?: return@withLock null
            val known = now.base.mapNotNull { it.notionId }.toSet()
            val remoteIds = remote.blocks.mapNotNull { it.notionId }.toSet()
            val blocks = BlockMatch.adopt(BlockMatch.keepIds(now.blocks, remote.blocks), remote.blocks, known).map { b ->
                if (b.notionId != null && b.notionId !in remoteIds) b.copy(notionId = null) else b
            }
            val ids = blocks.mapNotNull { it.notionId }.toSet()
            // Blocks added in Notion by someone else stay out of the comparison, so they are kept.
            val base = remote.blocks.filter { it.notionId in known || it.notionId in ids }
            now.copy(blocks = blocks, base = base, uncertain = false).also { cache.saveContent(it) }
        }
    }

    // ---- Previews -------------------------------------------------------------------------

    private fun updatePreview(pageId: String, content: PageContent?) {
        val text = NotePreviews.text(content)
        _previews.update { if (text == null) it - pageId else it + (pageId to text) }
    }

    /**
     * Fills in the previews of [notes]: first from the copies on this phone, then by downloading
     * content that is missing or out of date, one note after the other (see [NotePreviews]).
     */
    fun loadPreviews(notes: List<NoteSummary>) {
        scope.launch(Dispatchers.IO) {
            if (previewLock.isLocked) return@launch
            previewLock.withLock {
                val stale = notes.filter { note ->
                    val cached = cache.content(note.id)
                    if (note.id !in _previews.value) updatePreview(note.id, cached)
                    NotePreviews.needsDownload(note, cached)
                }
                if (!hasKey()) return@withLock
                for (note in stale.take(NotePreviews.MAX_DOWNLOADS)) {
                    try {
                        val previous = cache.content(note.id)
                        // Only the top level, unless the copy already had the nested blocks (an opened note).
                        val nested = previous?.nestedLoaded == true
                        val fresh = downloadContent(note.id, previous?.blocks.orEmpty(), note.lastEditedTime, nested)
                        lock.withLock {
                            // Never replace edits made meanwhile.
                            if (cache.content(note.id)?.dirty != true) {
                                cache.saveContent(fresh)
                                updatePreview(note.id, fresh)
                            }
                        }
                    } catch (e: NotionException) {
                        if (e.isOffline) {
                            _offline.value = true
                            return@withLock
                        }
                    }
                }
            }
        }
    }

    // ---- Lookups for property editors ------------------------------------------------------

    /**
     * People in the workspace, for person properties (cached for a day). A personal access token
     * can't list them: then the people in the notes here and the token's owner ([KnownUsers]).
     */
    suspend fun users(forceRefresh: Boolean = false): List<NotionUser> = withContext(Dispatchers.IO) {
        val cached = cache.users()
        if (!forceRefresh && cached != null && clock() - cached.fetchedMs < LOOKUP_TTL_MS) return@withContext cached.users
        try {
            val users = client.listUsers()
                .filter { it.stringOrNull("type") == "person" }
                .mapNotNull { PropertyCodec.parseUser(it) }
                .sortedBy { it.name.lowercase() }
            cache.saveUsers(UsersCache(users, clock()))
            users
        } catch (e: NotionException) {
            val owner = if (e.isTemporary) null else runCatching { TokenCodec.parse(client.me()).owner }.getOrNull()
            KnownUsers.of(_databases.value.values.flatMap { it.notes }, owner, cached?.users.orEmpty())
        }
    }

    /** Pages of a related database, for relation properties (cached for 10 minutes). */
    suspend fun relatedPages(databaseId: String, forceRefresh: Boolean = false): List<PageRef>? =
        withContext(Dispatchers.IO) {
            val cached = cache.pages(databaseId)
            if (!forceRefresh && cached != null && clock() - cached.fetchedMs < RELATED_TTL_MS) return@withContext cached.pages
            try {
                val pages = client.queryDataSource(resolveSource(databaseId)).mapNotNull { page ->
                    PropertyCodec.parsePage(page, databaseId)?.let { PageRef(it.id, InlineMarkdown.stripped(it.title)) }
                }
                cache.savePages(databaseId, PagesCache(pages, clock()))
                pages
            } catch (e: NotionException) {
                cached?.pages
            }
        }

    // ---- Browse -------------------------------------------------------------------------

    /**
     * Pages and databases shared with the integration, most recently edited first. Without a
     * [query] a database's own pages (the notes) are left out; with one they are found too.
     */
    suspend fun browse(query: String): List<WorkspaceItem> = withContext(Dispatchers.IO) {
        WorkspaceCodec.parseAll(client.search(query.trim(), BROWSE_LIMIT), includeNotes = query.isNotBlank(), pagesKey = PAGES)
    }

    /**
     * Asks Notion for Home's databases and pages again ([recent]): the most recently edited ones
     * (not the notes in databases, at most one search's worth), grouped by database. Home shows them
     * as a tree (`logic/PageTree.kt`); + and the pinned cards' icons use them in this order. Throws when Notion can't be reached; what
     * is kept stays.
     */
    suspend fun refreshRecent() {
        val items = groupByDatabase(browse(""), ask = true)
        withContext(Dispatchers.IO) {
            lock.withLock {
                _recent.value = items
                cache.saveRecent(items)
            }
        }
    }

    /** What Home's list or the last search said about database or page [id] (its name and icon), if anything. */
    /**
     * The database data source [sourceId] is in, for links to Notion: from its properties or the
     * last search when the phone knows, else asked from Notion. Null when nobody can tell (offline).
     */
    suspend fun databaseOf(sourceId: String): String? {
        _databases.value[sourceId]?.schema?.databaseId?.let { return it }
        knownItem(sourceId)?.databaseId?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                PropertyCodec.parseSchema(client.getDataSource(sourceId)).databaseId
            } catch (e: NotionException) {
                null
            }
        }
    }

    fun knownItem(id: String): WorkspaceItem? =
        (lastFound + _recent.value.orEmpty()).firstOrNull { plain(it.id) == plain(id) }

    /** The results of the last search, for [knownItem]. */
    @Volatile
    private var lastFound: List<WorkspaceItem> = emptyList()

    /** Makes [note] (from Browse) known here so the editor can open it. Returns its ID. */
    suspend fun openNote(note: NoteSummary): String {
        loadDatabase(note.databaseId)
        withContext(Dispatchers.IO) {
            lock.withLock {
                // Already here: keep the copy here, it may have changes that aren't sent yet.
                if (findNote(_databases.value, note.id) != null) return@withLock
                setState(note.databaseId) { db -> db.copy(notes = listOf(Outbox.applyPending(note, _outbox.value)) + db.notes, loaded = true) }
                _databases.value[note.databaseId]?.let { saveDatabaseCache(note.databaseId, it) }
            }
        }
        if (note.databaseId != PAGES) scope.launch { refresh(note.databaseId, userAsked = false) }
        return note.id
    }

    /** Opens a linked page by its ID (downloading it when it isn't here). Null when Notion can't be reached. */
    suspend fun openPage(pageId: String): String? {
        val id = resolve(pageId)
        if (findNote(_databases.value, id) != null) return id
        return try {
            val page = withContext(Dispatchers.IO) { client.getPage(id) }
            val note = PropertyCodec.parsePage(page, WorkspaceCodec.parentDatabase(page) ?: PAGES) ?: return null
            openNote(note)
        } catch (e: NotionException) {
            if (e.isOffline) _offline.value = true
            null
        }
    }

    /** Starts showing a database (opened from Browse or a link): its copy, then a refresh. */
    suspend fun openDatabase(databaseId: String) {
        loadDatabase(databaseId)
        refresh(databaseId, userAsked = false)
    }

    // ---- Set-up ---------------------------------------------------------------------------

    suspend fun searchDatabases(): List<PageRef> = withContext(Dispatchers.IO) { client.searchDatabases() }

    suspend fun fetchSchema(databaseId: String): DatabaseSchema = withContext(Dispatchers.IO) {
        PropertyCodec.parseSchema(client.getDataSource(resolveSource(databaseId))).also { schema ->
            if (_databases.value.containsKey(databaseId)) setState(databaseId) { it.copy(schema = SchemaOptions.withValues(schema, it.notes)) }
        }
    }

    /**
     * Downloads the properties (and their options) of the database note [pageId] is in again, so
     * options added in Notion since show in its pickers. Nothing for a page's subpages.
     */
    suspend fun refreshSchemaOf(pageId: String) {
        val databaseId = findNote(_databases.value, resolve(pageId))?.databaseId ?: return
        if (databaseId == PAGES || isPageNotebook(databaseId) || !hasKey()) return
        try {
            fetchSchema(databaseId)
        } catch (e: NotionException) {
            if (e.isOffline) _offline.value = true
        }
    }

    /** What kind of token [key] (the stored one when null) is, and who it acts as. */
    suspend fun tokenInfo(key: String? = null): TokenInfo = withContext(Dispatchers.IO) {
        TokenCodec.parse(if (key != null) client.me(key) else client.me())
    }

    /**
     * Changes how data source [sourceId] shows its notes (kept on this phone, see [DatabaseSetup]);
     * one never changed starts from [DatabaseSetups.initial] for [schema].
     */
    suspend fun updateSetup(sourceId: String, schema: DatabaseSchema?, transform: (DatabaseSetup) -> DatabaseSetup) {
        settings.update { s ->
            val key = s.setups.keys.firstOrNull { plain(it) == plain(sourceId) } ?: sourceId
            s.copy(setups = s.setups + (key to transform(DatabaseSetups.of(s.setups, sourceId, schema))))
        }
    }

    /** Pins database [pin] on Home (at the top), or unpins it when it is pinned. */
    suspend fun togglePinDatabase(pin: PinnedDatabase) {
        settings.update { s ->
            val pinned = s.pinnedDatabases.any { plain(it.id) == plain(pin.id) }
            s.copy(pinnedDatabases = if (pinned) s.pinnedDatabases.filterNot { plain(it.id) == plain(pin.id) } else listOf(pin) + s.pinnedDatabases)
        }
    }

    /** Keeps the order the user dragged Home's pinned databases to ([ids], first on top). */
    suspend fun savePinnedDatabaseOrder(ids: List<String>) {
        settings.update { s ->
            val ordered = NoteLists.reorderDatabases(s.pinnedDatabases, ids)
            if (ordered == s.pinnedDatabases) s else s.copy(pinnedDatabases = ordered)
        }
    }

    /** Keeps a pinned database's name and icon as Notion has them now (Home shows them offline). */
    suspend fun renamePinnedDatabase(id: String, title: String, icon: PageIcon?) {
        if (title.isBlank()) return
        settings.update { s ->
            val changed = s.pinnedDatabases.map { pin ->
                if (plain(pin.id) == plain(id) && (pin.title != title || (icon != null && pin.icon != icon))) pin.copy(title = title, icon = icon ?: pin.icon) else pin
            }
            if (changed == s.pinnedDatabases) s else s.copy(pinnedDatabases = changed)
        }
    }

    private fun plain(id: String) = id.replace("-", "")

    /** Forgets all Notion data on this phone (the API key was removed). Settings stay. */
    /** Which workspace each page, database and data source is in (Workspaces.id, by ID without dashes). */
    val owners: StateFlow<Map<String, String>> get() = client.owners

    /** Forgets which objects were in [workspace] (its token was removed); its notes stay on the phone. */
    fun forgetWorkspace(workspace: String) = client.forgetWorkspace(workspace)

    suspend fun clearData() = withContext(Dispatchers.IO) {
        lock.withLock {
            client.forgetWorkspaces()
            cache.clear()
            databaseInfos.clear()
            databasesAsked.clear()
            _recent.value = null
            _outbox.value = emptyList()
            _databases.update { map -> map.mapValues { DatabaseState(loaded = true) } }
        }
    }

    suspend fun togglePin(pageId: String) {
        val id = resolve(pageId)
        settings.update { s -> s.copy(pinned = if (id in s.pinned) s.pinned - id else listOf(id) + s.pinned) }
    }

    /** Saves the order after the user moved notes in a list ([listKey] is a database ID or Home). */
    suspend fun saveOrder(listKey: String, visibleIds: List<String>) {
        settings.update { s ->
            val full = s.order[listKey].orEmpty()
            s.copy(order = s.order + (listKey to NoteOrdering.applySubsetOrder(full, visibleIds)))
        }
    }

    fun settingsFlow(): Flow<AppSettings> = settings.settings

    suspend fun currentSettings(): AppSettings = settings.current()

    /** Saves edited content without waiting (a screen is closing). */
    fun saveContentInBackground(pageId: String, blocks: List<Block>) {
        scope.launch { saveContent(pageId, blocks) }
    }

    /** Saves a property without waiting (a screen is closing). */
    fun updatePropertyInBackground(pageId: String, property: NoteProperty) {
        scope.launch { updateProperty(pageId, property) }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = settings.update(transform)

    companion object {
        /** The key under which pages that are not in a database are kept, like a database's notes. */
        const val PAGES = "pages"

        /** The title property of a page on its own (Notion's name for it). */
        const val PAGE_TITLE = "title"

        /**
         * Keys of pages' notebooks from before the redesign (their notes were the page's subpages).
         * Their copies are still read, so a change or new subpage waiting to be sent isn't lost.
         */
        private const val PAGE_PREFIX = "page:"

        /** How a page's notebook's key is written in its copy's file name (see CacheStore). */
        private const val LEGACY_PAGE_FILE = "page_"

        private fun isPageNotebook(key: String): Boolean = key.startsWith(PAGE_PREFIX)

        /** Results of one Browse search (one or two requests). */
        private const val BROWSE_LIMIT = 200
        private const val RETRY_MIN_MS = 15_000L

        /** How much earlier than this phone's clock a page sent without an answer may have been made (clocks differ). */
        private const val CREATE_CLOCK_SLACK_MS = 10 * 60_000L
        private const val OWNERS_SAVE_DELAY_MS = 2_000L
        private const val RETRY_MAX_MS = 5 * 60_000L
        private const val RETRY_OFFLINE_MAX_MS = 60_000L
        private const val LOOKUP_TTL_MS = 24 * 60 * 60_000L
        private const val RELATED_TTL_MS = 10 * 60_000L

        /** How deep nested blocks are downloaded (deeper ones stay in Notion, as nested content). */
        const val MAX_DEPTH = 5
    }
}

/** True when [blocks] has a block of the editor's kinds (used for empty-state hints). */
fun List<Block>.hasEditableContent(): Boolean = any { it.type != BlockType.UNSUPPORTED && it.text.isNotBlank() }
