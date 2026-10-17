package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.logic.Destinations.Section
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationsTest {
    private fun views(n: Int) = (1..n).map { DatabaseConfig("v$it", "View $it", databaseId = "db") }

    @Test
    fun `Pinned, Notebooks and Search are the destinations, notebooks are inside Notebooks`() {
        assertEquals(Section.PINNED, Destinations.section(Destinations.PINNED))
        assertEquals(Section.SEARCH, Destinations.section(Destinations.SEARCH))
        assertEquals(Section.NOTEBOOKS, Destinations.section("v1"))
        assertEquals(Section.NOTEBOOKS, Destinations.section(Destinations.NOTEBOOKS))
    }

    @Test
    fun `a removed notebook gives way to the first one, or to Notebooks without any`() {
        assertEquals("v1", Destinations.valid("v1", views(1)))
        assertEquals("v1", Destinations.valid("v2", views(1)))
        assertEquals(Destinations.NOTEBOOKS, Destinations.valid("v2", views(0)))
        assertEquals("v1", Destinations.valid(Destinations.NOTEBOOKS, views(2)))
        assertEquals(Destinations.SEARCH, Destinations.valid(Destinations.SEARCH, views(0)))
        assertEquals(Destinations.PINNED, Destinations.valid(Destinations.PINNED, views(2)))
    }

    @Test
    fun `Notebooks opens the notebook seen last`() {
        assertEquals("v2", Destinations.notebook("v2", views(3)))
        assertEquals("v1", Destinations.notebook(null, views(3)))
        assertEquals("v1", Destinations.notebook("gone", views(3)))
        assertEquals(Destinations.NOTEBOOKS, Destinations.notebook(null, views(0)))
    }

    @Test
    fun `views saved before views existed show their own database`() {
        assertEquals("db", DatabaseConfig("db", "Notes").sourceId)
        assertEquals("db", DatabaseConfig("v", "Notes", databaseId = "db").sourceId)
    }
}
