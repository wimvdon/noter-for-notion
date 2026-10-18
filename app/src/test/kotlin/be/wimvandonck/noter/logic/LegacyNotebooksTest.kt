package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.CardLayout
import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteSort
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.SortKind
import be.wimvandonck.noter.data.store.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LegacyNotebooksTest {
    private val tasks = DatabaseConfig(
        "v1", "Tasks", databaseId = "ds-tasks",
        quickFilters = listOf("Status"), selected = mapOf("Status" to listOf("Todo")),
        sort = NoteSort(SortKind.TITLE), cards = CardLayout(content = false),
    )
    private val health = DatabaseConfig(
        "v2", "Health", databaseId = "db-health",
        children = listOf(DatabaseConfig("s", "Skin", databaseId = "ds-skin", quickFilters = listOf("Kind")), DatabaseConfig("f", "Food", databaseId = "ds-food")),
    )
    private val journal = DatabaseConfig("v3", "Journal", databaseId = "page:p-1", pageId = "p-1", sort = NoteSort(SortKind.NOTION))

    @Test
    fun `each notebook's database keeps its setup and is pinned, in the order of the tabs`() {
        val migrated = LegacyNotebooks.migrate(AppSettings(databases = listOf(tasks, health)))

        assertEquals(emptyList<DatabaseConfig>(), migrated.databases)
        assertEquals(
            DatabaseSetup(quickFilters = listOf("Status"), selected = mapOf("Status" to listOf("Todo")), sort = NoteSort(SortKind.TITLE), cards = CardLayout(content = false)),
            migrated.setups["ds-tasks"],
        )
        // A notebook of several data sources: each data source keeps its sub-notebook's setup, the database is pinned.
        assertEquals(listOf("Kind"), migrated.setups["ds-skin"]?.quickFilters)
        assertEquals(setOf("ds-tasks", "ds-skin", "ds-food"), migrated.setups.keys)
        assertEquals(listOf(PinnedDatabase("ds-tasks", "Tasks"), PinnedDatabase("db-health", "Health")), migrated.pinnedDatabases)
    }

    @Test
    fun `a page's notebook becomes a pinned page`() {
        val migrated = LegacyNotebooks.migrate(AppSettings(databases = listOf(journal), pinned = listOf("n-1")))
        assertEquals(listOf("n-1", "p-1"), migrated.pinned)
        assertEquals(emptyMap<String, DatabaseSetup>(), migrated.setups)
        assertEquals(emptyList<PinnedDatabase>(), migrated.pinnedDatabases)
        // Pinned already (written without dashes): not twice.
        assertEquals(listOf("p1"), LegacyNotebooks.migrate(AppSettings(databases = listOf(journal.copy(pageId = "p-1")), pinned = listOf("p1"))).pinned.filter { it.startsWith("p") })
    }

    @Test
    fun `the first notebook of a database wins, a setup made since then stays, and a page's order isn't kept`() {
        val second = tasks.copy(id = "v9", name = "Done", quickFilters = listOf("Tag"))
        val newer = DatabaseSetup(quickFilters = listOf("Owner"))
        val migrated = LegacyNotebooks.migrate(
            AppSettings(databases = listOf(tasks, second, health), setups = mapOf("ds-skin" to newer), pinnedDatabases = listOf(PinnedDatabase("ds-tasks", "Tasks"))),
        )
        assertEquals(listOf("Status"), migrated.setups["ds-tasks"]?.quickFilters)
        assertEquals(newer, migrated.setups["ds-skin"])
        // Both notebooks of the database: pinned once.
        assertEquals(listOf("ds-tasks", "db-health"), migrated.pinnedDatabases.map { it.id })
        assertNull(LegacyNotebooks.migrate(AppSettings(databases = listOf(DatabaseConfig("x", "X", databaseId = "ds-x", sort = NoteSort(SortKind.NOTION))))).setups["ds-x"]?.sort)
    }

    @Test
    fun `a notebook from before quick filters keeps its filter property and filters`() {
        val old = DatabaseConfig("n", "N", databaseId = "db", chipProperty = "Status", filters = listOf("Todo"))
        val setup = LegacyNotebooks.migrate(AppSettings(databases = listOf(old))).setups.getValue("db")
        assertEquals(listOf("Status"), setup.quickFilters)
        assertEquals(mapOf("Status" to listOf("Todo")), setup.selected)
    }

    @Test
    fun `settings without notebooks stay as they are`() {
        val settings = AppSettings(pinned = listOf("a"))
        assertSame(settings, LegacyNotebooks.migrate(settings))
    }
}
