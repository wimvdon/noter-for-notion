package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubpageTitlesTest {
    private fun subpage(id: String, title: String, rawType: String = "child_page", depth: Int = 0) =
        Block(key = id, notionId = id, type = BlockType.UNSUPPORTED, text = title, rawType = rawType, url = id, depth = depth)

    private val paragraph = Block(key = "p", notionId = "p", text = "Hello")

    @Test
    fun `only a page with subpages or databases of its own asks for their titles`() {
        assertTrue(SubpageTitles.any(listOf(paragraph, subpage("a", "A"))))
        assertTrue(SubpageTitles.any(listOf(subpage("d", "D", rawType = "child_database"))))
        assertFalse(SubpageTitles.any(listOf(paragraph)))
        assertFalse(SubpageTitles.any(listOf(subpage("a", "A", depth = 1))))
    }

    @Test
    fun `a subpage renamed in Notion gets its new title`() {
        val blocks = listOf(paragraph, subpage("a", "Old"), subpage("b", "B"), subpage("d", "Old db", rawType = "child_database"))
        val renamed = SubpageTitles.renamed(blocks, mapOf("a" to "New", "b" to "B", "d" to "New db"))
        assertEquals(listOf(paragraph, subpage("a", "New"), subpage("b", "B"), subpage("d", "New db", rawType = "child_database")), renamed)
    }

    @Test
    fun `nothing renamed, nothing to save`() {
        val blocks = listOf(paragraph, subpage("a", "A"))
        assertNull(SubpageTitles.renamed(blocks, mapOf("a" to "A")))
        assertNull(SubpageTitles.renamed(blocks, emptyMap()))
    }

    @Test
    fun `other blocks keep their text`() {
        assertNull(SubpageTitles.renamed(listOf(paragraph), mapOf("p" to "Changed")))
    }
}
