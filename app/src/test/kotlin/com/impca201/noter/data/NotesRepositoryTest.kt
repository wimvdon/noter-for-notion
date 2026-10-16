package com.impca201.noter.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.impca201.noter.data.api.NotionClient
import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.data.store.CacheStore
import com.impca201.noter.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections

class NotesRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val notion = FakeNotion()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
        notion.close()
    }

    private fun repository(): NotesRepository {
        val settings = SettingsStore(PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb").also { it.delete() } })
        return NotesRepository(
            client = NotionClient(apiKey = { "secret_test" }, baseUrl = notion.baseUrl),
            hasKey = { true },
            settings = settings,
            cache = CacheStore(folder.newFolder("cache")),
            scope = scope,
        ).also { repo ->
            runBlocking { settings.update { it.copy(databases = listOf(DatabaseConfig("db", "Notes"))) } }
            repo.start()
        }
    }

    @Test
    fun `a new note stays open while Notion creates it`() = runBlocking {
        notion.respond = { sent ->
            when {
                sent.path.startsWith("/databases/db/query") -> 200 to FakeNotion.list()
                sent.path.startsWith("/databases/db") -> 200 to FakeNotion.database("db")
                sent.method == "POST" && sent.path == "/pages" -> 200 to FakeNotion.page("page-1", "db")
                else -> 200 to "{}"
            }
        }
        val repo = repository()
        val localId = checkNotNull(repo.createNote("db"))
        val seen = Collections.synchronizedList(mutableListOf<String?>())
        val watcher = scope.launch { repo.note(localId).collect { seen += it?.id } }

        repo.flush()

        val note = withTimeout(5_000) { repo.note(localId).first { it?.id == "page-1" } }
        assertEquals("page-1", note?.id)
        assertEquals("page-1", repo.resolve(localId))
        watcher.cancel()
        // The editor never saw the note disappear.
        assertFalse(seen.toString(), seen.contains(null))
    }

    @Test
    fun `edits to a note in the trash are not sent`() = runBlocking {
        notion.respond = { sent ->
            when {
                sent.path.startsWith("/databases/db/query") -> 200 to FakeNotion.list(FakeNotion.page("page-1", "db", "Old"))
                sent.path.startsWith("/databases/db") -> 200 to FakeNotion.database("db")
                sent.method == "PATCH" && sent.path == "/pages/page-1" -> 200 to FakeNotion.page("page-1", "db", archived = true)
                else -> 200 to "{}"
            }
        }
        val repo = repository()
        repo.refresh("db", userAsked = true)
        assertEquals(listOf("page-1"), repo.databases.value["db"]?.notes?.map { it.id })

        repo.trashNote("page-1")
        // An editor that is closing hands over what was still waiting.
        repo.updateProperty("page-1", NoteProperty("Name", "title", PropertyValue.Text("New")))
        repo.saveContent("page-1", emptyList())
        repo.flush()

        val patches = notion.requests.filter { it.method == "PATCH" }
        assertEquals(1, patches.size)
        assertTrue(patches.single().body, patches.single().body.contains("\"archived\":true"))
    }

    @Test
    fun `saving a note that was trashed in Notion fails quietly`() = runBlocking {
        notion.respond = { sent ->
            when {
                sent.path.startsWith("/databases/db/query") -> 200 to FakeNotion.list(FakeNotion.page("page-1", "db", "Old"))
                sent.path.startsWith("/databases/db") -> 200 to FakeNotion.database("db")
                sent.method == "PATCH" -> 400 to FakeNotion.ARCHIVED
                else -> 200 to "{}"
            }
        }
        val repo = repository()
        repo.refresh("db", userAsked = true)
        val events = Collections.synchronizedList(mutableListOf<RepoEvent>())
        val collector = scope.launch { repo.events.collect { events += it } }

        repo.updateProperty("page-1", NoteProperty("Name", "title", PropertyValue.Text("New")))
        repo.flush()

        collector.cancel()
        assertTrue(events.toString(), events.isEmpty())
        assertEquals(0, repo.pendingCount.first())
    }
}
