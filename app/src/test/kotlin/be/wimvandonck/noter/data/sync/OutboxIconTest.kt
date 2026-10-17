package be.wimvandonck.noter.data.sync

import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PageIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxIconTest {
    @Test
    fun `a newer icon replaces the older one, and a refresh keeps it`() {
        var queue = Outbox.add(emptyList(), PendingOp.SetIcon("p", "🥑"))
        queue = Outbox.add(queue, PendingOp.SetIcon("p", null))
        assertEquals(listOf(PendingOp.SetIcon("p", null)), queue)
        val fromNotion = NoteSummary("p", "db", "", icon = PageIcon(emoji = "🥑"))
        assertEquals(null, Outbox.applyPending(fromNotion, queue).icon)
        assertEquals(PageIcon(emoji = "🍕"), Outbox.applyPending(fromNotion, listOf(PendingOp.SetIcon("p", "🍕"))).icon)
    }

    @Test
    fun `a note made on this phone sends its icon with the create`() {
        val local = NoteSummary.LOCAL_PREFIX + "1"
        assertEquals(emptyList<PendingOp>(), Outbox.add(emptyList(), PendingOp.SetIcon(local, "🥑")))
        assertEquals(listOf(PendingOp.SetIcon("new", "🥑")), Outbox.rename(listOf(PendingOp.SetIcon(local, "🥑")), local, "new"))
    }
}
