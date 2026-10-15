package com.impca201.noter.data.sync

import com.impca201.noter.data.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPolicyTest {
    private val hour = 60 * 60_000L

    @Test
    fun `first time and pull-to-refresh load everything`() {
        assertEquals(RefreshKind.FULL, SyncPolicy.decide(nowMs = 10 * hour, lastSyncMs = 0, lastFullSyncMs = 0, userAsked = false))
        assertEquals(RefreshKind.FULL, SyncPolicy.decide(10 * hour, 10 * hour, 10 * hour, userAsked = true))
    }

    @Test
    fun `recently synced asks only for changes, very recently nothing`() {
        assertEquals(RefreshKind.CHANGES, SyncPolicy.decide(10 * hour, 10 * hour - 2 * 60_000, 9 * hour, false))
        assertEquals(RefreshKind.NONE, SyncPolicy.decide(10 * hour, 10 * hour - 10_000, 9 * hour, false))
    }

    @Test
    fun `everything is reloaded every few hours`() {
        assertEquals(RefreshKind.FULL, SyncPolicy.decide(20 * hour, 19 * hour, 13 * hour, false))
    }

    private fun note(id: String, title: String = id) = NoteSummary(id, "db", title)

    @Test
    fun `a full refresh replaces notes but keeps ones not in Notion yet`() {
        val local = note(NoteSummary.LOCAL_PREFIX + "1")
        val merged = SyncPolicy.merge(listOf(note("a"), note("b"), local), listOf(note("a", "A2")), full = true)
        assertEquals(listOf(note("a", "A2"), local), merged)
    }

    @Test
    fun `a changes refresh updates, adds and drops trashed notes`() {
        val merged = SyncPolicy.merge(
            existing = listOf(note("a"), note("b"), note("c")),
            fetched = listOf(note("new"), note("b", "B2")),
            full = false,
            trashedIds = setOf("c"),
        )
        assertEquals(listOf(note("new"), note("a"), note("b", "B2")), merged)
    }
}
