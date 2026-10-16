package com.impca201.noter.data.icons

/**
 * The icons a view can have: the Material Symbols set, read from the app's assets. Each icon
 * has a name ("sports-soccer"), search words (its name's words and its category) and the path
 * data to draw it (24 × 24). Copied from RSS Reader's folder icons.
 */
class IconCatalog(
    val icons: List<Icon>,
) {
    data class Icon(
        val name: String,
        val keywords: List<String>,
        val paths: List<String>,
    ) {
        /** The name as words, for screen readers: "sports soccer". */
        val label: String get() = name.replace('-', ' ')
    }

    private val byName = icons.associateBy { it.name }

    fun icon(name: String): Icon? = byName[name]

    /**
     * Icons matching what the user typed, best first: the exact name, names starting with it,
     * names whose words start with every typed word, then matches on category or related words.
     * With nothing typed: the suggested icons first, then all others.
     */
    fun search(query: String): List<Icon> {
        val words = query.lowercase().split(nonWord).filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            val suggested = SUGGESTED.mapNotNull(byName::get)
            return suggested + icons.filterNot { it.name in SUGGESTED }
        }
        val joined = words.joinToString("-")
        return icons
            .mapNotNull { icon ->
                val nameWords = icon.name.split('-')
                val score =
                    when {
                        icon.name == joined -> 0
                        icon.name.startsWith(joined) -> 1
                        words.all { w -> nameWords.any { it.startsWith(w) } } -> 2
                        words.all { w -> nameWords.any { it.startsWith(w) } || icon.keywords.any { it.startsWith(w) } } -> 3
                        else -> return@mapNotNull null
                    }
                icon to score
            }.sortedWith(compareBy({ it.second }, { it.first.name.length }, { it.first.name }))
            .map { it.first }
    }

    companion object {
        const val DEFAULT = "sticky-note-2"

        private val nonWord = Regex("[^\\p{L}\\p{N}]+")

        /** Shown first when nothing is searched: things people often keep notes about. */
        val SUGGESTED =
            listOf(
                "sticky-note-2", "checklist", "task-alt", "list-alt", "article", "edit-note", "description",
                "menu-book", "lightbulb", "psychology", "work", "home", "shopping-cart", "grocery", "restaurant",
                "calendar-month", "event", "group", "person", "school", "code", "build", "attach-money",
                "savings", "receipt-long", "flight", "travel-explore", "fitness-center", "favorite", "star",
                "flag", "bookmark", "folder", "inventory-2", "music-note", "movie", "palette", "pets", "auto-awesome",
            )

        /** Extra search words for icons whose names miss common words. */
        private val RELATED =
            mapOf(
                "sticky-note-2" to listOf("note", "notes", "memo"),
                "edit-note" to listOf("note", "notes", "write", "journal"),
                "checklist" to listOf("todo", "tasks"),
                "task-alt" to listOf("todo", "done"),
                "lightbulb" to listOf("idea", "ideas"),
                "psychology" to listOf("mind", "thoughts"),
                "grocery" to listOf("groceries", "food", "shopping"),
                "restaurant" to listOf("food", "cooking", "recipes"),
                "menu-book" to listOf("books", "reading", "recipes"),
                "attach-money" to listOf("money", "finance"),
                "savings" to listOf("money", "finance", "budget"),
                "flight" to listOf("travel", "plane", "holiday"),
                "fitness-center" to listOf("health", "sport", "gym"),
                "favorite" to listOf("heart", "love"),
                "group" to listOf("people", "team", "contacts"),
                "inventory-2" to listOf("box", "archive"),
                "auto-awesome" to listOf("sparkle", "magic"),
                "palette" to listOf("art", "design"),
            )

        /** View icons stored before this catalog existed, by their old keys. */
        private val OLD_NAMES =
            mapOf(
                "note" to "sticky-note-2", "checklist" to "checklist", "list" to "list-alt", "article" to "article",
                "book" to "menu-book", "idea" to "lightbulb", "work" to "work", "home" to "home",
                "groceries" to "grocery", "food" to "restaurant", "calendar" to "calendar-month", "people" to "group",
                "school" to "school", "code" to "code", "build" to "build", "money" to "attach-money",
                "travel" to "flight", "health" to "fitness-center", "heart" to "favorite", "star" to "star",
                "flag" to "flag", "bookmark" to "bookmark", "folder" to "folder", "box" to "inventory-2",
                "music" to "music-note", "movie" to "movie", "art" to "palette", "pets" to "pets", "sparkle" to "auto-awesome",
            )

        /** A stored view icon: today's names as they are, keys from before translated, nothing → the note. */
        fun fromStored(value: String?): String =
            when {
                value.isNullOrBlank() -> DEFAULT
                else -> OLD_NAMES[value] ?: value
            }

        /** Reads the asset: `#` comment lines, then `name<TAB>categories<TAB>path|path` per icon. */
        fun parse(text: String): IconCatalog =
            IconCatalog(
                text
                    .lineSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull { line ->
                        val fields = line.split('\t')
                        if (fields.size != 3 || fields[0].isEmpty() || fields[2].isEmpty()) return@mapNotNull null
                        val categories = fields[1].split(',').flatMap { it.split(nonWord) }.filter { it.isNotEmpty() }
                        Icon(
                            name = fields[0],
                            keywords = categories + RELATED[fields[0]].orEmpty(),
                            paths = fields[2].split('|'),
                        )
                    }.toList(),
            )
    }
}
