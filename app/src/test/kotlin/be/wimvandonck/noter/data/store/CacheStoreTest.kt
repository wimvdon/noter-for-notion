package be.wimvandonck.noter.data.store

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PageContent
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.SelectOption
import be.wimvandonck.noter.data.sync.PendingOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `stores and reads databases, content and the queue`() {
        val store = CacheStore(folder.newFolder("cache"))
        val note = NoteSummary(
            "p", "db", "Title",
            properties = listOf(NoteProperty("Status", "status", PropertyValue.Select(SelectOption("Done", "green")))),
        )
        store.saveDatabase("db", DatabaseCache(notes = listOf(note), lastSyncMs = 5))
        assertEquals(listOf(note), store.database("db")!!.notes)

        val content = PageContent("p", listOf(Block("k", "b", BlockType.TODO, "x", checked = true)))
        store.saveContent(content)
        assertEquals(content, store.content("p"))

        val queue = listOf(PendingOp.CreatePage("local-1", "db"), PendingOp.SetProperty("p", note.properties[0]))
        store.saveOutbox(queue)
        assertEquals(queue, store.outbox())
    }

    @Test
    fun `missing or broken files read as nothing`() {
        val dir = folder.newFolder("cache")
        val store = CacheStore(dir)
        assertNull(store.database("x"))
        java.io.File(dir, "outbox.json").writeText("not json")
        assertEquals(emptyList<PendingOp>(), store.outbox())
    }

    @Test
    fun `clear removes everything`() {
        val store = CacheStore(folder.newFolder("cache"))
        store.saveContent(PageContent("p", emptyList()))
        store.clear()
        assertNull(store.content("p"))
    }
}
