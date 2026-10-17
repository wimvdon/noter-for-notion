package be.wimvandonck.noter.logic

/**
 * Adding an option to a select or multi-select property while picking: Notion makes the option
 * when a note is saved with a name it doesn't know yet (a status can't get options that way).
 */
object NewOptions {
    /**
     * The option to offer for what was typed in [query], or null: nothing typed, a comma (Notion
     * doesn't allow one in an option's name), or an option of that name already exists in
     * [existing] (in any case, so "work" doesn't sit next to "Work").
     */
    fun offer(query: String, existing: List<String>): String? {
        val name = query.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty() || ',' in name) return null
        return name.takeIf { existing.none { it.equals(name, ignoreCase = true) } }
    }
}
