package be.wimvandonck.noter.data.store

import be.wimvandonck.noter.data.model.DatabaseInfo
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PageContent
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.sync.PendingOp
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/** What the app knows about one database, kept between app starts. */
@Serializable
data class DatabaseCache(
    val schema: DatabaseSchema? = null,
    val notes: List<NoteSummary> = emptyList(),
    /** Phone time (ms) the last refresh started; the next one asks for changes since then. */
    val lastSyncMs: Long = 0,
    val lastFullSyncMs: Long = 0,
)

@Serializable
data class UsersCache(val users: List<NotionUser> = emptyList(), val fetchedMs: Long = 0)

@Serializable
data class PagesCache(val pages: List<PageRef> = emptyList(), val fetchedMs: Long = 0)

/**
 * Copies of Notion data and the queue of unsent changes, as JSON files in [dir] (the app's
 * no-backup folder: Notion is the real copy, and the queue only means something on this phone).
 * Each write goes to a temporary file first, so a crash never leaves half a file.
 */
class CacheStore(private val dir: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    init {
        dir.mkdirs()
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun <T> read(name: String, serializer: KSerializer<T>): T? {
        val file = File(dir, name)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()
    }

    private fun <T> write(name: String, serializer: KSerializer<T>, value: T) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        if (!tmp.renameTo(File(dir, name))) {
            File(dir, name).delete()
            tmp.renameTo(File(dir, name))
        }
    }

    @Synchronized
    fun database(id: String): DatabaseCache? = read("db_${safe(id)}.json", DatabaseCache.serializer())

    @Synchronized
    fun saveDatabase(id: String, cache: DatabaseCache) = write("db_${safe(id)}.json", DatabaseCache.serializer(), cache)

    @Synchronized
    fun deleteDatabase(id: String) {
        File(dir, "db_${safe(id)}.json").delete()
    }

    @Synchronized
    fun content(pageId: String): PageContent? = read("page_${safe(pageId)}.json", PageContent.serializer())

    @Synchronized
    fun saveContent(content: PageContent) = write("page_${safe(content.pageId)}.json", PageContent.serializer(), content)

    @Synchronized
    fun deleteContent(pageId: String) {
        File(dir, "page_${safe(pageId)}.json").delete()
    }

    @Synchronized
    fun outbox(): List<PendingOp> = read(OUTBOX, ListSerializer(PendingOp.serializer())).orEmpty()

    @Synchronized
    fun saveOutbox(queue: List<PendingOp>) = write(OUTBOX, ListSerializer(PendingOp.serializer()), queue)

    @Synchronized
    /** Which data source each known key is (a data source's own ID, or a database's from before). */
    fun sources(): Map<String, String> = read(SOURCES, MapSerializer(String.serializer(), String.serializer())).orEmpty()

    fun saveSources(sources: Map<String, String>) = write(SOURCES, MapSerializer(String.serializer(), String.serializer()), sources)

    fun users(): UsersCache? = read(USERS, UsersCache.serializer())

    @Synchronized
    fun saveUsers(cache: UsersCache) = write(USERS, UsersCache.serializer(), cache)

    @Synchronized
    fun pages(databaseId: String): PagesCache? = read("pages_${safe(databaseId)}.json", PagesCache.serializer())

    @Synchronized
    fun savePages(databaseId: String, cache: PagesCache) =
        write("pages_${safe(databaseId)}.json", PagesCache.serializer(), cache)

    /** Databases' names and data sources, by database ID, as Notion said when last asked. */
    @Synchronized
    fun databaseInfos(): Map<String, DatabaseInfo> = read(DATABASES, MapSerializer(String.serializer(), DatabaseInfo.serializer())).orEmpty()

    @Synchronized
    fun saveDatabaseInfos(infos: Map<String, DatabaseInfo>) = write(DATABASES, MapSerializer(String.serializer(), DatabaseInfo.serializer()), infos)

    /** Home's databases and pages, as Notion's search listed them when last asked. */
    @Synchronized
    fun recent(): List<WorkspaceItem>? = read(RECENT, ListSerializer(WorkspaceItem.serializer()))

    @Synchronized
    fun saveRecent(items: List<WorkspaceItem>) = write(RECENT, ListSerializer(WorkspaceItem.serializer()), items)

    /** Whether each data source checked is Notion's People directory (#92), by ID without dashes. */
    @Synchronized
    fun peopleChecks(): Map<String, Boolean> = read(PEOPLE, MapSerializer(String.serializer(), Boolean.serializer())).orEmpty()

    @Synchronized
    fun savePeopleChecks(checks: Map<String, Boolean>) = write(PEOPLE, MapSerializer(String.serializer(), Boolean.serializer()), checks)

    /** Which workspace each page, database and data source is in (see KeyRouter). */
    @Synchronized
    fun owners(): Map<String, String> = read(OWNERS, MapSerializer(String.serializer(), String.serializer())).orEmpty()

    @Synchronized
    fun saveOwners(owners: Map<String, String>) = write(OWNERS, MapSerializer(String.serializer(), String.serializer()), owners)

    /** The keys of the databases with a copy here (see [database]), as written in the file names. */
    @Synchronized
    fun databaseKeys(): List<String> =
        dir.listFiles().orEmpty().map { it.name }
            .filter { it.startsWith("db_") && it.endsWith(".json") }
            .map { it.removePrefix("db_").removeSuffix(".json") }
            .sorted()

    /** Removes everything (another Notion workspace was connected). */
    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val OUTBOX = "outbox.json"
        const val USERS = "users.json"
        const val OWNERS = "workspaces.json"
        // v2: a database is asked about before a data source (see NotesRepository.resolveSource), so
        // keys resolved the other way round before are asked about again.
        const val SOURCES = "sources_v2.json"
        const val RECENT = "recent.json"
        const val DATABASES = "databases.json"
        const val PEOPLE = "people_directory.json"
    }
}
