package com.impca201.noter

import android.app.Application
import com.impca201.noter.data.NotesRepository
import com.impca201.noter.data.api.NotionClient
import com.impca201.noter.data.store.ApiKeyStore
import com.impca201.noter.data.store.CacheStore
import com.impca201.noter.data.store.DataFiles
import com.impca201.noter.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Creates the app's single instances (no dependency-injection library needed for this size). */
class NoterApp : Application() {
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
}
