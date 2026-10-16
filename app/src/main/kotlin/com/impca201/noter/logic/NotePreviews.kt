package com.impca201.noter.logic

import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.model.PageContent

/**
 * The previews on the overview's note cards come from the note's content kept on this phone.
 * Content that is missing, or older than the note's last edit in Notion, is downloaded once (one
 * request per note), a few notes at a time, so the overview stays within Notion's rate limit.
 */
object NotePreviews {
    /** Lines of content shown on a card. */
    const val PREVIEW_LINES = 8

    /** Notes downloaded per round; the next round starts when the list changes again. */
    const val MAX_DOWNLOADS = 24

    /** True when [note]'s content should be downloaded for its preview. */
    fun needsDownload(note: NoteSummary, cached: PageContent?): Boolean = when {
        note.isLocal -> false
        cached == null -> true
        // Edits on this phone that Notion doesn't have yet: the phone's copy is the newest.
        cached.dirty -> false
        else -> note.lastEditedTime.isNotBlank() && cached.remoteEditedTime != note.lastEditedTime
    }

    /** The preview text of [content], or null when there is nothing to show. */
    fun text(content: PageContent?): String? =
        content?.let { DocumentEditor.preview(it.blocks, PREVIEW_LINES) }?.takeIf { it.isNotBlank() }
}
