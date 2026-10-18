package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.data.notion.InlineMarkdown

/** What Search shows: everything, only pages or only databases. */
enum class SearchKind { ALL, PAGES, DATABASES }

/**
 * Search's one list (#60): the notes on this phone and what Notion finds, mixed, the best matches
 * first. Where a page comes from makes no difference to the person searching.
 */
object SearchResults {
    /**
     * The notes on this phone ([notes], already matched) and Notion's results ([items], null while
     * Notion hasn't answered) in one list, each page once (the phone's copy wins). With a query the
     * best matching titles come first (see [score]), newest first among equals; without one,
     * Notion's order (most recently edited first).
     */
    fun merge(query: String, notes: List<NoteSummary>, items: List<WorkspaceItem>?): List<WorkspaceItem> {
        val phone = notes.map { WorkspaceItem(it.id, isDatabase = false, title = InlineMarkdown.stripped(it.title), icon = it.icon, lastEditedTime = it.lastEditedTime, note = it) }
        val all = (phone + items.orEmpty()).distinctBy { plainId(it.id) }
        if (query.isBlank()) return all
        return all.sortedWith(compareBy<WorkspaceItem> { score(it.title, query) }.thenByDescending { it.lastEditedTime })
    }

    /**
     * How well [title] matches [query], ignoring case and accents (lower is better): 0 the same,
     * 1 it starts with it, 2 every word typed starts a word of the title, 3 every word is in the
     * title, 4 none of these (Notion found it by something else).
     */
    fun score(title: String, query: String): Int {
        val t = NoteSearch.plain(title)
        val q = NoteSearch.plain(query)
        val words = q.split(' ').filter { it.isNotEmpty() }
        val titleWords = t.split(' ', '-', '/', '.', ',', ':', '(', ')').filter { it.isNotEmpty() }
        return when {
            q.isEmpty() -> 4
            t == q -> 0
            t.startsWith(q) -> 1
            words.all { w -> titleWords.any { it.startsWith(w) } } -> 2
            words.all { t.contains(it) } -> 3
            else -> 4
        }
    }

    /** [results] of [kind] and, when given, in [workspace] (see [Workspaces.ownerOf]). */
    fun filter(results: List<WorkspaceItem>, kind: SearchKind, workspace: String?, owners: Map<String, String>, workspaces: List<Workspace>): List<WorkspaceItem> =
        results.filter { item ->
            val kindOk = when (kind) {
                SearchKind.ALL -> true
                SearchKind.PAGES -> !item.isDatabase
                SearchKind.DATABASES -> item.isDatabase
            }
            kindOk && (workspace == null || Workspaces.ownerOf(idsOf(item), owners, workspaces)?.id == workspace)
        }

    /** The IDs that tell which workspace [item] is in: its own, its data sources' and its database's. */
    fun idsOf(item: WorkspaceItem): List<String?> =
        listOf(item.id) + item.sources.map { it.id } + listOf(item.databaseId, item.note?.databaseId)

    private fun plainId(id: String) = id.replace("-", "")
}
