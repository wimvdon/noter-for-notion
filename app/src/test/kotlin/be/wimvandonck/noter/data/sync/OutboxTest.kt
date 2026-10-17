package be.wimvandonck.noter.data.sync

import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxTest {
    private fun prop(name: String, text: String) = NoteProperty(name, "rich_text", PropertyValue.Text(text))

    @Test
    fun `a newer value of a property replaces the older one in place`() {
        var queue = Outbox.add(emptyList(), PendingOp.SetProperty("p", prop("A", "1")))
        queue = Outbox.add(queue, PendingOp.SaveContent("p"))
        queue = Outbox.add(queue, PendingOp.SetProperty("p", prop("A", "2")))
        assertEquals(listOf(PendingOp.SetProperty("p", prop("A", "2")), PendingOp.SaveContent("p")), queue)
    }

    @Test
    fun `content is queued once per note`() {
        var queue = Outbox.add(emptyList(), PendingOp.SaveContent("p"))
        queue = Outbox.add(queue, PendingOp.SaveContent("p"))
        assertEquals(1, queue.size)
    }

    @Test
    fun `edits of a note that is not in Notion yet go with its creation`() {
        val local = NoteSummary.LOCAL_PREFIX + "1"
        var queue = Outbox.add(emptyList(), PendingOp.CreatePage(local, "db"))
        queue = Outbox.add(queue, PendingOp.SetProperty(local, prop("A", "x")))
        queue = Outbox.add(queue, PendingOp.SaveContent(local))
        assertEquals(listOf(PendingOp.CreatePage(local, "db")), queue)
    }

    @Test
    fun `trashing a note that is not in Notion yet just forgets it`() {
        val local = NoteSummary.LOCAL_PREFIX + "1"
        val queue = Outbox.add(listOf(PendingOp.CreatePage(local, "db")), PendingOp.TrashPage(local))
        assertEquals(emptyList<PendingOp>(), queue)
    }

    @Test
    fun `trashing drops the note's other changes`() {
        var queue = Outbox.add(emptyList(), PendingOp.SetProperty("p", prop("A", "1")))
        queue = Outbox.add(queue, PendingOp.SaveContent("q"))
        queue = Outbox.add(queue, PendingOp.TrashPage("p"))
        assertEquals(listOf(PendingOp.SaveContent("q"), PendingOp.TrashPage("p")), queue)
    }

    @Test
    fun `renaming moves queued changes to the Notion ID`() {
        val queue = Outbox.rename(listOf(PendingOp.SaveContent("local-1"), PendingOp.SaveContent("x")), "local-1", "n1")
        assertEquals(listOf(PendingOp.SaveContent("n1"), PendingOp.SaveContent("x")), queue)
    }

    @Test
    fun `pending values are applied over a refreshed note`() {
        val note = NoteSummary("p", "db", "Old", properties = listOf(NoteProperty("Name", "title", PropertyValue.Text("Old"))))
        val queue = listOf(PendingOp.SetProperty("p", NoteProperty("Name", "title", PropertyValue.Text("New"))))
        val result = Outbox.applyPending(note, queue)
        assertEquals("New", result.title)
        assertEquals(PropertyValue.Text("New"), result.property("Name")!!.value)
    }
}
