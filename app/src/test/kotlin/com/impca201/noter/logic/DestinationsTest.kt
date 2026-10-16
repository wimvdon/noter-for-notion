package com.impca201.noter.logic

import com.impca201.noter.data.model.DatabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationsTest {
    private fun views(n: Int) = (1..n).map { DatabaseConfig("v$it", "View $it", databaseId = "db") }

    @Test
    fun `up to three views fit in the navigation bar, more go to a drawer`() {
        assertFalse(Destinations.useDrawer(views(0)))
        assertFalse(Destinations.useDrawer(views(3)))
        assertTrue(Destinations.useDrawer(views(4)))
        assertEquals(listOf("home", "browse", "v1"), Destinations.keys(views(1)))
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
