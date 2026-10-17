package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.notion.InlineMarkdown
import java.text.Normalizer

/**
 * Search's first section: the notes already on this phone whose title has every word typed,
 * ignoring case and accents, most recently edited first. They show at once, before Notion
 * answers, and also offline.
 */
object NoteSearch {
    const val MAX_RESULTS = 30

    fun matches(notes: Collection<NoteSummary>, query: String): List<NoteSummary> {
        val words = plain(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return notes
            .filter { note -> plain(InlineMarkdown.stripped(note.title)).let { title -> words.all(title::contains) } }
            .distinctBy { it.id }
            .sortedByDescending { it.lastEditedTime }
            .take(MAX_RESULTS)
    }

    /** Lower case, without accents and with single spaces, so "Café" matches "cafe". */
    private fun plain(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()
}
