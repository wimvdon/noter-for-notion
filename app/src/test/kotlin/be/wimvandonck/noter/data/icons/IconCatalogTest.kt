package be.wimvandonck.noter.data.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IconCatalogTest {
    private val catalog =
        IconCatalog.parse(
            """
            # comment
            folder${'\t'}text${'\t'}M1 1h2v2z
            newspaper${'\t'}text${'\t'}M2 2h2|M3 3h1
            sports-soccer${'\t'}activities${'\t'}M0 0z
            sports-esports${'\t'}activities${'\t'}M0 0z
            soccer-ball-broken${'\t'}${'\t'}M0 0z
            broken line without tabs
            empty-path${'\t'}x${'\t'}
            """.trimIndent(),
        )

    @Test
    fun `reads names, categories and paths, skipping comments and broken lines`() {
        assertEquals(listOf("folder", "newspaper", "sports-soccer", "sports-esports", "soccer-ball-broken"), catalog.icons.map { it.name })
        assertEquals(listOf("M2 2h2", "M3 3h1"), catalog.icon("newspaper")!!.paths)
        assertEquals("sports soccer", catalog.icon("sports-soccer")!!.label)
        assertNull(catalog.icon("nope"))
    }

    @Test
    fun `search puts the best name matches first`() {
        assertEquals(listOf("soccer-ball-broken", "sports-soccer"), catalog.search("soccer").map { it.name })
        assertEquals(listOf("sports-soccer", "sports-esports"), catalog.search("sports").map { it.name }.take(2))
        assertEquals("sports-soccer", catalog.search("Sports soccer").first().name)
        // Category and related words count too, after name matches.
        assertEquals(listOf("newspaper"), catalog.search("news").map { it.name })
        assertTrue(catalog.search("activities").map { it.name }.containsAll(listOf("sports-soccer", "sports-esports")))
        assertEquals(emptyList<IconCatalog.Icon>(), catalog.search("zebra"))
    }

    @Test
    fun `nothing typed shows suggestions first, then everything`() {
        val all = catalog.search("  ")
        assertEquals(catalog.icons.size, all.size)
        assertEquals("folder", all.first().name)
    }

    @Test
    fun `old view icons map to their new names`() {
        assertEquals("favorite", IconCatalog.fromStored("heart"))
        assertEquals("sticky-note-2", IconCatalog.fromStored("note"))
        assertEquals("rocket-launch", IconCatalog.fromStored("rocket-launch"))
        assertEquals(IconCatalog.DEFAULT, IconCatalog.fromStored(""))
        assertEquals(IconCatalog.DEFAULT, IconCatalog.fromStored(null))
    }

    @Test
    fun `the bundled catalog has every suggested and migrated icon`() {
        val file = listOf(File("src/main/assets/material_symbols.txt"), File("app/src/main/assets/material_symbols.txt")).first { it.exists() }
        val real = IconCatalog.parse(file.readText())
        assertTrue(real.icons.size > 3000)
        IconCatalog.SUGGESTED.forEach { assertTrue("missing $it", real.icon(it) != null) }
        listOf(
            "note", "checklist", "list", "article", "book", "idea", "work", "home", "groceries", "food", "calendar",
            "people", "school", "code", "build", "money", "travel", "health", "heart", "star", "flag", "bookmark",
            "folder", "box", "music", "movie", "art", "pets", "sparkle",
        ).forEach { assertTrue("missing $it", real.icon(IconCatalog.fromStored(it)) != null) }
    }
}
