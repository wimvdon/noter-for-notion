package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationsTest {
    private fun views(n: Int) = (1..n).map { DatabaseConfig("v$it", "View $it", databaseId = "db") }

    @Test
    fun `the bar holds as many destinations as fit on the screen`() {
        assertEquals(5, Destinations.barCapacity(411))
        assertEquals(10, Destinations.barCapacity(800))
        // Pinned and Browse always stay.
        assertEquals(2, Destinations.barCapacity(120))
        assertEquals(listOf("home", "browse", "v1"), Destinations.keys(views(1)))
    }

    @Test
    fun `only views pinned to the bar are in it, as many as fit`() {
        val all = views(6).mapIndexed { i, v -> v.copy(inBar = i != 1) }
        assertEquals(listOf("v1", "v3", "v4"), Destinations.barViews(all, 411).map { it.id })
        assertEquals(listOf("v1", "v3", "v4", "v5", "v6"), Destinations.barViews(all, 800).map { it.id })
        assertEquals(0, Destinations.barRoom(all, 411))
        assertEquals(3, Destinations.barRoom(all, 800))
    }

    @Test
    fun `a removed view falls back to Pinned`() {
        assertEquals("v1", Destinations.valid("v1", views(1)))
        assertEquals("browse", Destinations.valid("browse", views(0)))
        assertEquals("home", Destinations.valid("v2", views(1)))
    }

    @Test
    fun `a view opens with its own filters until the user picks others`() {
        val view = DatabaseConfig("v", "Todo", databaseId = "db", chipProperty = "Status", filters = listOf("Todo"))
        assertEquals(setOf("Todo"), Destinations.selectedFilters(view, null))
        assertEquals(emptySet<String>(), Destinations.selectedFilters(view, emptySet()))
        assertEquals(setOf("Done"), Destinations.selectedFilters(view, setOf("Done")))
    }

    @Test
    fun `views saved before views existed show their own database`() {
        assertEquals("db", DatabaseConfig("db", "Notes").sourceId)
        assertEquals("db", DatabaseConfig("v", "Notes", databaseId = "db").sourceId)
    }
}
