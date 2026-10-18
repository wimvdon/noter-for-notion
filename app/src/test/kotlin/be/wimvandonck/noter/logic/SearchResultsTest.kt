package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultsTest {
    private fun item(id: String, title: String, edited: String = "", database: Boolean = false) =
        WorkspaceItem(id, isDatabase = database, title = title, lastEditedTime = edited)

    @Test
    fun `the best matching titles come first, then the newest`() {
        val items = listOf(
            item("a", "Notes about cooking", "2026-10-05"),
            item("b", "Cook", "2026-10-01"),
            item("c", "Cookbook", "2026-10-02"),
            item("d", "Something else", "2026-10-09"),
            item("e", "Cookies", "2026-10-03"),
        )
        assertEquals(listOf("b", "e", "c", "a", "d"), SearchResults.merge("cook", emptyList(), items).map { it.id })
    }

    @Test
    fun `scores ignore case and accents`() {
        assertEquals(0, SearchResults.score("Café", "cafe"))
        assertEquals(1, SearchResults.score("Café menu", "CAFE"))
        assertEquals(2, SearchResults.score("Weekly menu planning", "plan menu"))
        assertEquals(3, SearchResults.score("Mealplan", "plan"))
        assertEquals(4, SearchResults.score("Groceries", "plan"))
    }

    @Test
    fun `notes on the phone and Notion's results are one list, each page once`() {
        val note = NoteSummary("aaaa-1111", "db", "Cook **book**", lastEditedTime = "2026-10-01")
        val merged = SearchResults.merge("cook", listOf(note), listOf(item("aaaa1111", "Cookbook"), item("db1", "Cooking", database = true)))
        assertEquals(listOf("aaaa-1111", "db1"), merged.map { it.id })
        assertEquals(note, merged.first().note)
        assertEquals("Cook book", merged.first().title)
    }

    @Test
    fun `without a query Notion's order stays`() {
        val items = listOf(item("b", "B"), item("a", "A"))
        assertEquals(listOf("b", "a"), SearchResults.merge("", emptyList(), items).map { it.id })
    }

    @Test
    fun `filters by kind and by workspace`() {
        val spaces = listOf(Workspace("w1", "Work"), Workspace("w2", "Club"))
        val owners = mapOf("p1" to "w1", "d1" to "w2", "ds2" to "w1")
        val results = listOf(
            item("p1", "Page"),
            item("d1", "Database", database = true),
            WorkspaceItem("d2", isDatabase = true, title = "Several", sources = listOf(be.wimvandonck.noter.data.model.PageRef("ds2", "A"))),
        )
        assertEquals(listOf("p1"), SearchResults.filter(results, SearchKind.PAGES, null, owners, spaces).map { it.id })
        assertEquals(listOf("d1", "d2"), SearchResults.filter(results, SearchKind.DATABASES, null, owners, spaces).map { it.id })
        assertEquals(listOf("p1", "d2"), SearchResults.filter(results, SearchKind.ALL, "w1", owners, spaces).map { it.id })
        assertEquals(listOf("d1"), SearchResults.filter(results, SearchKind.DATABASES, "w2", owners, spaces).map { it.id })
    }
}
