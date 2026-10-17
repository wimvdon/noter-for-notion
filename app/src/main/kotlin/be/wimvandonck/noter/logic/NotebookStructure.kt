package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.PageRef

/**
 * Keeps the notebooks in step with how Notion groups data sources into databases, which can change
 * after a notebook was made: a data source moved into another database becomes a sub-notebook of
 * that database's notebook (keeping its filters, sort and cards), a database that got a second data
 * source gets sub-notebooks, and one left with a single data source becomes a plain notebook again.
 */
object NotebookStructure {
    /**
     * The notebooks after the check. [parentOf] is the database each data source is in now, by data
     * source ID; [sourcesOf] the data sources of each database, by database ID (IDs with or without
     * dashes). What isn't known (offline, not asked) stays as it is, and so does a page's notebook.
     * A second notebook of a data source that already has one (other filters) stays on its own.
     */
    fun reconcile(
        notebooks: List<DatabaseConfig>,
        parentOf: Map<String, String>,
        sourcesOf: Map<String, List<PageRef>>,
        newId: () -> String,
    ): List<DatabaseConfig> {
        val parents = parentOf.entries.associate { plain(it.key) to plain(it.value) }
        val sources = sourcesOf.entries.associate { plain(it.key) to it.value }
        val databaseIds = sourcesOf.keys.associateBy(::plain)

        // The database a notebook stands for: a notebook with sub-notebooks keeps its own.
        fun databaseOf(notebook: DatabaseConfig): String? = when {
            notebook.pageId != null -> null
            notebook.children.isNotEmpty() -> notebook.databaseId?.let(::plain)
            else -> parents[plain(notebook.sourceId)]
        }

        // Per database of several data sources, the notebook that holds them: one that already has
        // sub-notebooks, else the one of the database's first data source, else the first.
        val owners = mutableMapOf<String, DatabaseConfig>()
        notebooks.groupBy { databaseOf(it) }.forEach { (database, group) ->
            val list = database?.let { sources[it] } ?: return@forEach
            if (list.size < 2) return@forEach
            val first = plain(list.first().id)
            owners[database] = group.firstOrNull { it.children.isNotEmpty() }
                ?: group.firstOrNull { plain(it.sourceId) == first }
                ?: group.first()
        }

        // Plain notebooks taken in as a sub-notebook, by data source: the first of each that the
        // database's notebook doesn't have yet.
        val takenIn = mutableMapOf<String, DatabaseConfig>()
        for (notebook in notebooks) {
            val owner = databaseOf(notebook)?.let { owners[it] } ?: continue
            if (notebook.id == owner.id || notebook.children.isNotEmpty()) continue
            val source = plain(notebook.sourceId)
            if (ownLeaves(owner).none { plain(it.sourceId) == source } && source !in takenIn) takenIn[source] = notebook
        }
        val takenIds = takenIn.values.map { it.id }.toSet()

        return notebooks.flatMap { notebook ->
            val database = databaseOf(notebook)
            val list = database?.let { sources[it] }
            when {
                notebook.id in takenIds -> emptyList()
                database == null || list == null -> listOf(notebook)
                list.size < 2 -> listOf(collapsed(notebook, list))
                owners[database]?.id != notebook.id -> listOf(notebook)
                else -> container(notebook, database, databaseIds.getValue(database), list, takenIn, parents, newId)
            }
        }
    }

    private fun ownLeaves(notebook: DatabaseConfig) = notebook.children.ifEmpty { listOf(notebook) }

    /** A notebook with sub-notebooks whose database has one data source left: a plain notebook of it. */
    private fun collapsed(notebook: DatabaseConfig, list: List<PageRef>): DatabaseConfig {
        if (notebook.children.isEmpty()) return notebook
        val only = list.singleOrNull() ?: return notebook
        val child = notebook.children.firstOrNull { plain(it.sourceId) == plain(only.id) } ?: return notebook
        return child.copy(id = notebook.id, name = notebook.name, icon = notebook.icon, children = emptyList())
    }

    /**
     * [notebook] holding every data source of [database] in Notion's order, then any sub-notebook
     * whose data source moved to another database, as a notebook of its own.
     */
    private fun container(
        notebook: DatabaseConfig,
        database: String,
        databaseId: String,
        list: List<PageRef>,
        takenIn: Map<String, DatabaseConfig>,
        parents: Map<String, String>,
        newId: () -> String,
    ): List<DatabaseConfig> {
        val plainNotebook = notebook.children.isEmpty()
        val children = list.map { source ->
            val id = plain(source.id)
            val own = ownLeaves(notebook).firstOrNull { plain(it.sourceId) == id }
            when {
                // A plain notebook getting sub-notebooks: its settings go to its own data source's.
                own != null && plainNotebook -> own.copy(id = newId(), name = source.title.ifBlank { own.name })
                own != null -> own
                else -> takenIn[id]?.let { it.copy(name = source.title.ifBlank { it.name }) }
                    ?: DatabaseConfig(id = newId(), name = source.title, databaseId = source.id)
            }
        }
        val movedOut = notebook.children.filter { child ->
            val source = plain(child.sourceId)
            list.none { plain(it.id) == source } && parents[source].let { it != null && it != database }
        }
        val top = if (plainNotebook) {
            notebook.copy(databaseId = databaseId, quickFilters = emptyList(), selected = emptyMap(), sort = null, cards = null)
        } else {
            notebook
        }
        return listOf(top.copy(children = children)) + movedOut
    }

    private fun plain(id: String) = id.replace("-", "")
}
