package be.wimvandonck.noter.data.sync

import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.store.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceKeysTest {
    @Test
    fun `a database's key becomes its data source's in views, order and layouts`() {
        val settings = AppSettings(
            databases = listOf(DatabaseConfig("db", "Notes"), DatabaseConfig("v2", "Todo", databaseId = "db"), DatabaseConfig("v3", "Other", databaseId = "x")),
            order = mapOf("db" to listOf("p1"), AppSettings.HOME to listOf("p2")),
            propertyLayouts = mapOf("db" to PropertyLayout(onCard = listOf("Status"))),
        )

        val renamed = SourceKeys.renameInSettings(settings, "db", "ds")

        assertEquals(listOf("ds", "ds", "x"), renamed.databases.map { it.sourceId })
        // The view keeps its own ID, so the navigation keeps it selected.
        assertEquals(listOf("db", "v2", "v3"), renamed.databases.map { it.id })
        assertEquals(mapOf("ds" to listOf("p1"), AppSettings.HOME to listOf("p2")), renamed.order)
        assertEquals(listOf("Status"), renamed.propertyLayouts["ds"]?.onCard)
    }

    @Test
    fun `notes waiting to be created go to the data source`() {
        val queue = listOf(PendingOp.CreatePage("local-1", "db"), PendingOp.SaveContent("p"))
        assertEquals(listOf(PendingOp.CreatePage("local-1", "ds"), PendingOp.SaveContent("p")), SourceKeys.renameInOutbox(queue, "db", "ds"))
    }

    @Test
    fun `notes kept under both keys are merged`() {
        val old = listOf(NoteSummary("a", "db", "A"), NoteSummary("b", "db", "B old"))
        val new = listOf(NoteSummary("b", "ds", "B new"))
        assertEquals(listOf("B new", "A"), SourceKeys.mergeNotes(old, new, "ds").map { it.title })
        assertEquals(setOf("ds"), SourceKeys.mergeNotes(old, new, "ds").map { it.databaseId }.toSet())
    }
}
