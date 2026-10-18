package be.wimvandonck.noter.logic

/** What another app shared to Noter (#63): a title, text, and the first web link in it. */
data class SharedContent(val title: String, val text: String, val url: String?)

/** Reads a share from another app (Android's send action with text). */
object SharedText {
    /** A shared title longer than this is cut off (Notion shows long titles badly). */
    const val MAX_TITLE = 120

    private val LINK = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /**
     * From a share's subject and text; null when both are empty. Apps often share "Title" plus a
     * link, or the title and the link in the text: the text without the link is the title then,
     * when it is one short line.
     */
    fun of(subject: String?, text: String?): SharedContent? {
        val body = text.orEmpty().trim()
        val title = subject.orEmpty().trim()
        if (title.isEmpty() && body.isEmpty()) return null
        val match = LINK.find(body)
        val url = match?.value?.trimEnd('.', ',', ';', ':', '!', '?', '\'', '"')
        val rest = if (match != null) body.removeRange(match.range).trim() else body
        val oneLine = rest.isNotEmpty() && '\n' !in rest && rest.length <= MAX_TITLE
        return when {
            title.isEmpty() && url != null && oneLine -> SharedContent(rest, "", url)
            rest == title -> SharedContent(title, "", url)
            else -> SharedContent(title, rest, url)
        }
    }
}
