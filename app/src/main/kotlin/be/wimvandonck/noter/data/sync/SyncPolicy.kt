package be.wimvandonck.noter.data.sync

import be.wimvandonck.noter.data.model.NoteSummary

enum class RefreshKind { NONE, CHANGES, FULL }

/**
 * When to ask Notion for news. The app always shows what it has first and refreshes in the
 * background, so the lists open instantly and work offline.
 *
 * - CHANGES only asks for notes edited since the last sync: one small request.
 * - FULL reloads the whole database, which also drops notes deleted in Notion. It runs on
 *   pull-to-refresh, the first time, and every few hours.
 */
object SyncPolicy {
    const val CHANGES_INTERVAL_MS = 60_000L
    const val FULL_INTERVAL_MS = 6 * 60 * 60_000L

    /** Notion rounds edit times to the minute and phone clocks drift: look back a little further. */
    const val CHANGES_OVERLAP_MS = 5 * 60_000L

    fun decide(nowMs: Long, lastSyncMs: Long, lastFullSyncMs: Long, userAsked: Boolean): RefreshKind = when {
        userAsked || lastFullSyncMs <= 0 || nowMs - lastFullSyncMs >= FULL_INTERVAL_MS -> RefreshKind.FULL
        nowMs - lastSyncMs >= CHANGES_INTERVAL_MS -> RefreshKind.CHANGES
        else -> RefreshKind.NONE
    }

    /**
     * The notes after a refresh. A FULL refresh replaces everything except notes created on this
     * phone that Notion doesn't have yet. CHANGES replaces or adds the changed notes and drops
     * notes that were moved to the trash.
     */
    fun merge(
        existing: List<NoteSummary>,
        fetched: List<NoteSummary>,
        full: Boolean,
        trashedIds: Set<String> = emptySet(),
    ): List<NoteSummary> {
        if (full) return fetched + existing.filter { it.isLocal }
        val byId = fetched.associateBy { it.id }
        val updated = existing.filter { it.id !in trashedIds }.map { byId[it.id] ?: it }
        val existingIds = existing.map { it.id }.toSet()
        return fetched.filter { it.id !in existingIds && it.id !in trashedIds } + updated
    }
}
