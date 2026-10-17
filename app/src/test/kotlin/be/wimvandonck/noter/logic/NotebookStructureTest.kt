package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.NotebookSort
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.SortKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NotebookStructureTest {
    private var next = 0
    private val newId = { "new${next++}" }

    private val notities = DatabaseConfig("n", "Notities", icon = "note", databaseId = "ds-notities", quickFilters = listOf("Status"))
    private val development = DatabaseConfig(
        "d", "Development", icon = "code", databaseId = "ds-dev",
        quickFilters = listOf("Area"), sort = NotebookSort(SortKind.TITLE),
    )

    @Test
    fun `a data source moved into another database becomes a sub-notebook of that database's notebook`() {
        val result = NotebookStructure.reconcile(
            listOf(development, notities),
            parentOf = mapOf("ds-notities" to "db-notities", "ds-dev" to "db-notities"),
            sourcesOf = mapOf("db-notities" to listOf(PageRef("ds-notities", "Notities"), PageRef("ds-dev", "Development"))),
            newId = newId,
        )
        assertEquals(1, result.size)
        val top = result.single()
        assertEquals("n", top.id)
        assertEquals("Notities", top.name)
        assertEquals("db-notities", top.databaseId)
        assertEquals(emptyList<String>(), top.quickFilters)
        assertEquals(listOf("ds-notities", "ds-dev"), top.children.map { it.sourceId })
        // Both keep their filters and sort.
        assertEquals(listOf("Status"), top.children[0].quickFilters)
        assertEquals("d", top.children[1].id)
        assertEquals(listOf("Area"), top.children[1].quickFilters)
        assertEquals(NotebookSort(SortKind.TITLE), top.children[1].sort)
    }

    @Test
    fun `a database that got a second data source without a notebook gets a new sub-notebook`() {
        val result = NotebookStructure.reconcile(
            listOf(notities),
            parentOf = mapOf("ds-notities" to "db-notities"),
            sourcesOf = mapOf("db-notities" to listOf(PageRef("ds-notities", "Notities"), PageRef("ds-dev", "Development"))),
            newId = newId,
        )
        assertEquals(listOf("Notities", "Development"), result.single().children.map { it.name })
        assertEquals("ds-dev", result.single().children[1].databaseId)
    }

    @Test
    fun `nothing changed leaves the notebooks as they are`() {
        val notebooks = listOf(notities, development)
        val result = NotebookStructure.reconcile(
            notebooks,
            parentOf = mapOf("ds-notities" to "db-notities", "ds-dev" to "db-dev"),
            sourcesOf = mapOf(
                "db-notities" to listOf(PageRef("ds-notities", "Notities")),
                "db-dev" to listOf(PageRef("ds-dev", "Development")),
            ),
            newId = newId,
        )
        assertEquals(notebooks, result)
    }

    @Test
    fun `what isn't known stays as it is`() {
        val notebooks = listOf(notities, development)
        assertEquals(notebooks, NotebookStructure.reconcile(notebooks, emptyMap(), emptyMap(), newId))
    }

    @Test
    fun `a second notebook of a data source the database's notebook has stays on its own`() {
        val other = notities.copy(id = "n2", name = "Notities werk")
        val container = DatabaseConfig(
            "g", "Notities", databaseId = "db-notities",
            children = listOf(DatabaseConfig("c1", "Notities", databaseId = "ds-notities")),
        )
        val result = NotebookStructure.reconcile(
            listOf(container, other),
            parentOf = mapOf("ds-notities" to "db-notities"),
            sourcesOf = mapOf("db-notities" to listOf(PageRef("ds-notities", "Notities"), PageRef("ds-dev", "Development"))),
            newId = newId,
        )
        assertEquals(listOf("g", "n2"), result.map { it.id })
        assertEquals(listOf("c1", "new0"), result[0].children.map { it.id })
    }

    @Test
    fun `a database left with one data source becomes a plain notebook, and one moved away gets its own`() {
        val container = DatabaseConfig(
            "g", "Gezondheid", icon = "heart", databaseId = "db-g",
            children = listOf(
                DatabaseConfig("h", "Huid", databaseId = "ds-huid", quickFilters = listOf("Type")),
                DatabaseConfig("s", "Spijsvertering", databaseId = "ds-spijs"),
            ),
        )
        val result = NotebookStructure.reconcile(
            listOf(container),
            parentOf = mapOf("ds-huid" to "db-g", "ds-spijs" to "db-other"),
            sourcesOf = mapOf("db-g" to listOf(PageRef("ds-huid", "Huid"))),
            newId = newId,
        )
        assertEquals(1, result.size)
        val plain = result.single()
        assertEquals("g", plain.id)
        assertEquals("Gezondheid", plain.name)
        assertEquals("ds-huid", plain.databaseId)
        assertEquals(listOf("Type"), plain.quickFilters)
        assertEquals(emptyList<DatabaseConfig>(), plain.children)

        val moved = NotebookStructure.reconcile(
            listOf(container),
            parentOf = mapOf("ds-huid" to "db-g", "ds-spijs" to "db-other"),
            sourcesOf = mapOf("db-g" to listOf(PageRef("ds-huid", "Huid"), PageRef("ds-new", "Nieuw"))),
            newId = newId,
        )
        assertEquals(listOf("g", "s"), moved.map { it.id })
        assertEquals(listOf("ds-huid", "ds-new"), moved[0].children.map { it.sourceId })
    }

    @Test
    fun `a page's notebook is never touched`() {
        val page = DatabaseConfig("p", "Page", databaseId = "page:abc", pageId = "abc")
        val result = NotebookStructure.reconcile(listOf(page), mapOf("page:abc" to "x"), mapOf("x" to listOf(PageRef("a", ""), PageRef("b", ""))), newId)
        assertSame(page, result.single())
    }
}
