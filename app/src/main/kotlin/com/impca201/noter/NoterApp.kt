package com.impca201.noter

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.impca201.noter.data.NotesRepository
import com.impca201.noter.data.api.NotionClient
import com.impca201.noter.data.store.ApiKeyStore
import com.impca201.noter.data.store.CacheStore
import com.impca201.noter.data.store.DataFiles
import com.impca201.noter.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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

    override fun onCreate() {
        super.onCreate()
        apiKeyStore = ApiKeyStore(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        repository = NotesRepository(
            client = NotionClient(apiKey = { apiKeyStore.get() }),
            hasKey = { apiKeyStore.get().isNotBlank() },
            settings = SettingsStore(this),
            cache = CacheStore(File(noBackupFilesDir, DataFiles.CACHE_DIR)),
            scope = scope,
        )
        repository.start()
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
    }
}
