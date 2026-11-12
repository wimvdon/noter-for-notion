package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.CardLayout
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.DatabaseSetup
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PropertySchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatabaseSetupsTest {
    private fun note(id: String) = NoteSummary(id, "db", id)

    @Test
    fun `a Notion view shows only its notes, in its order, pinned ones first`() {
        val notes = listOf(note("1"), note("2"), note("3"), note("4"))
        val content = DatabaseSetups.content(notes, members = listOf("3", "4", "1", "9", "3"), pinned = listOf("4", "2"))
        assertEquals(listOf("4"), content.pinned.map { it.id })
        // A note the view has but this phone doesn't yet (9) waits for the next refresh; one listed twice shows once.
        assertEquals(listOf("3", "1"), content.others.map { it.id })
    }

    @Test
    fun `the cards show the first chip row from before, else a status, else a select property`() {
        val schema = DatabaseSchema(
            "db", "Tasks",
            listOf(PropertySchema("t", "Name", "title"), PropertySchema("k", "Kind", "select"), PropertySchema("s", "Status", "status")),
        )
        assertEquals("Kind", DatabaseSetups.cardProperty(DatabaseSetup(quickFilters = listOf("Kind")), schema))
        assertEquals("Status", DatabaseSetups.cardProperty(DatabaseSetup(quickFilters = listOf("Gone")), schema))
        assertEquals("Kind", DatabaseSetups.cardProperty(DatabaseSetup(), schema.copy(properties = schema.properties.filter { it.type != "status" })))
        assertNull(DatabaseSetups.cardProperty(DatabaseSetup(), schema.copy(properties = schema.properties.take(1))))
    }

    @Test
    fun `a data source's setup is found with or without dashes, else an empty one`() {
        val kept = DatabaseSetup(cards = CardLayout(cover = true))
        assertEquals(kept, DatabaseSetups.of(mapOf("ds-1" to kept), "ds1"))
        assertEquals(DatabaseSetup(), DatabaseSetups.of(mapOf("ds-1" to kept), "ds-2"))
    }
}
