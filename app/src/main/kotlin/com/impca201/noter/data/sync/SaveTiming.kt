package com.impca201.noter.data.sync

/**
 * When to save after an edit. Saving waits for a short pause in typing ([quietMs]) so one save
 * carries several letters, but never longer than [maxWaitMs] after the first unsaved edit, so
 * text is also saved while the user keeps typing.
 *
 * Notion allows about three requests per second per integration. The note screen hands edits to
 * the phone's copy with [LOCAL], and the queue sends them to Notion with [NOTION]: at most about
 * one round of requests per second while typing (one request per changed block), well within
 * the limit; the client also waits when Notion answers "too many requests".
 */
data class SaveTiming(val quietMs: Long, val maxWaitMs: Long) {
    /**
     * How long to wait before saving, at [nowMs], when the oldest edit not saved yet was made at
     * [firstPendingMs].
     */
    fun delayMs(nowMs: Long, firstPendingMs: Long): Long =
        minOf(quietMs, firstPendingMs + maxWaitMs - nowMs).coerceAtLeast(0L)

    companion object {
        /** From the editor to the copy on the phone. */
        val LOCAL = SaveTiming(quietMs = 250, maxWaitMs = 1_000)

        /** From the copy on the phone to Notion. */
        val NOTION = SaveTiming(quietMs = 400, maxWaitMs = 1_000)
    }
}
