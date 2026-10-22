package be.wimvandonck.noter

import be.wimvandonck.noter.logic.LauncherShortcuts
import be.wimvandonck.noter.logic.SharedContent
import kotlinx.coroutines.flow.MutableStateFlow
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.SignIn
import be.wimvandonck.noter.data.api.SignInClient
import be.wimvandonck.noter.logic.TokenRefresher
import be.wimvandonck.noter.data.api.NotionClient
import be.wimvandonck.noter.data.icons.IconCatalog
import be.wimvandonck.noter.data.store.ApiKeyStore
import be.wimvandonck.noter.data.store.CacheStore
import be.wimvandonck.noter.data.store.DataFiles
import be.wimvandonck.noter.data.store.SettingsStore
import be.wimvandonck.noter.logic.WorkspaceTokens
import be.wimvandonck.noter.logic.Workspaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

/** Creates the app's single instances (no dependency-injection library needed for this size). */
class NoterApp : Application(), SingletonImageLoader.Factory {
    lateinit var apiKeyStore: ApiKeyStore
        private set
    lateinit var repository: NotesRepository
        private set
    lateinit var signIn: SignIn
        private set

    /** What another app shared and Home hasn't saved yet (#63; MainActivity sets it). */
    val shares = MutableStateFlow<SharedContent?>(null)

    /** The screen a launcher shortcut opens (#76: `note:<id>` or `database:<id>`; MainActivity sets it). */
    val opens = MutableStateFlow<String?>(null)

    /** View icons (Material Symbols), read from the assets the first time they are needed. */
    val iconCatalog: IconCatalog by lazy {
        IconCatalog.parse(assets.open("material_symbols.txt").bufferedReader().use { it.readText() })
    }

    override fun onCreate() {
        super.onCreate()
        apiKeyStore = ApiKeyStore(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val signInClient = SignInClient()
        val refresher = TokenRefresher(find = apiKeyStore::find, refresh = signInClient::refresh, save = apiKeyStore::replace)
        repository = NotesRepository(
            client = NotionClient(
                apiKey = { apiKeyStore.get() },
                extraKeys = { apiKeyStore.extraTokens() },
                renewKey = refresher::renew,
            ),
            hasKey = { apiKeyStore.get().isNotBlank() },
            settings = SettingsStore(this),
            cache = CacheStore(File(noBackupFilesDir, DataFiles.CACHE_DIR)),
            scope = scope,
        )
        repository.start()
        signIn = SignIn(apiKeyStore, repository, scheme = packageName, scope = scope)
        // Ask Notion who each token is at each start: its kind and workspace's name, and the main
        // token's owner's profile picture for Home.
        val key = apiKeyStore.get()
        if (key.isNotBlank()) {
            scope.launch {
                runCatching {
                    val info = repository.tokenInfo(key)
                    // Not when another token was saved while this was on its way.
                    if (apiKeyStore.get() == key) {
                        apiKeyStore.setMainInfo(info.kind, info.name, info.workspace.orEmpty(), info.workspaceId)
                        apiKeyStore.setAvatarUrl(info.avatarUrl)
                    }
                }
                apiKeyStore.extras.value.forEach { extra ->
                    runCatching {
                        val info = repository.tokenInfo(extra.token)
                        apiKeyStore.setExtras(WorkspaceTokens.update(apiKeyStore.extras.value, extra.token, info.name, info.workspace.orEmpty(), info.kind, info.workspaceId))
                    }
                }
                // Now that every token's workspace is known: each workspace once, the token that
                // sees most staying (tokens added before this was checked).
                val gone = apiKeyStore.deduplicate()
                gone.forEach { repository.forgetWorkspace(Workspaces.id(it.token)) }
                if (gone.any { it.token == key }) {
                    runCatching { apiKeyStore.setAvatarUrl(repository.tokenInfo(apiKeyStore.get()).avatarUrl) }
                }
            }
        }
        // The launcher's shortcuts follow what is pinned (#76), a moment after it changes.
        val shortcuts = Shortcuts(this)
        scope.launch(Dispatchers.Default) {
            combine(repository.settingsFlow(), repository.databases) { settings, databases ->
                LauncherShortcuts.plan(settings.pinnedDatabases, settings.pinned, databases.values.flatMap { it.notes }, shortcuts.max)
            }.distinctUntilChanged().collectLatest { targets ->
                delay(SHORTCUTS_DELAY_MS)
                shortcuts.publish(targets)
            }
        }
        // Read the icons in the background now, so the first screen doesn't wait for them.
        scope.launch(Dispatchers.Default) { iconCatalog }
    }

    /**
     * Images (note covers, image blocks) with Coil: kept in memory and in the cache folder (never
     * backed up), downscaled to the size they are shown at.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images").toOkioPath()).maxSizeBytes(IMAGE_CACHE_BYTES).build() }
            .crossfade(true)
            .build()

    private companion object {
        const val IMAGE_CACHE_BYTES = 100L * 1024 * 1024

        /** The launcher's shortcuts change this long after the pins stop changing (a drag changes them often). */
        const val SHORTCUTS_DELAY_MS = 1_000L
    }
}
