package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedNotesTest {
    private val article = WebArticle("Page title", listOf(Block("x", text = "From the page")))

    @Test
    fun `the title is the shared one, else the page's, else the site, else the first line`() {
        assertEquals("Shared", SharedNotes.title(SharedContent("Shared", "", "https://www.example.com/a"), article))
        assertEquals("Page title", SharedNotes.title(SharedContent("", "", "https://www.example.com/a"), article))
        assertEquals("example.com", SharedNotes.title(SharedContent("", "", "https://www.example.com/a"), null))
        assertEquals("Line one", SharedNotes.title(SharedContent("", "Line one\nLine two", null), null))
        assertEquals(SharedText.MAX_TITLE, SharedNotes.title(SharedContent("x".repeat(500), "", null), null).length)
    }

    @Test
    fun `the note has the link as a bookmark, the shared text, then the page`() {
        val blocks = SharedNotes.blocks(SharedContent("T", "One *star*\n\nTwo", "https://example.com"), article)
        assertEquals(listOf(BlockType.UNSUPPORTED, BlockType.PARAGRAPH, BlockType.PARAGRAPH, BlockType.PARAGRAPH), blocks.map { it.type })
        assertEquals(SharedNotes.BOOKMARK, blocks[0].rawType)
        assertEquals("https://example.com", blocks[0].url)
        // Shared text is plain: Markdown markers in it are kept as they are.
        assertEquals("One \\*star\\*", blocks[1].text)
        assertEquals("Two", blocks[2].text)
        assertEquals("From the page", blocks[3].text)
        assertEquals(blocks.size, blocks.map { it.key }.toSet().size)
    }

    @Test
    fun `at most what Notion takes with a new page`() {
        val long = WebArticle("", List(150) { Block("b$it", text = "p$it") })
        assertEquals(SharedNotes.MAX_BLOCKS, SharedNotes.blocks(SharedContent("", "", "https://example.com"), long).size)
    }

    private fun schema(vararg props: Pair<String, String>) =
        DatabaseSchema(id = "db", title = "Notes", properties = props.map { (name, type) -> PropertySchema(name = name, type = type) })

    @Test
    fun `a shared link fills the database's URL property`() {
        val prop = SharedNotes.urlProperty(schema("Name" to "title", "Address" to "url"), "https://example.com/a")
        assertEquals("Address", prop?.name)
        assertEquals("url", prop?.type)
        assertEquals(PropertyValue.Plain("https://example.com/a"), prop?.value)
    }

    @Test
    fun `with several URL properties the one named like a link is used`() {
        val db = schema("Name" to "title", "Homepage" to "url", "Link" to "url")
        assertEquals("Link", SharedNotes.urlProperty(db, "https://example.com")?.name)
        assertEquals("Homepage", SharedNotes.urlProperty(schema("Homepage" to "url", "Other" to "url"), "https://example.com")?.name)
    }

    @Test
    fun `no URL property, no link or no schema means nothing to fill`() {
        assertNull(SharedNotes.urlProperty(schema("Name" to "title", "Notes" to "rich_text"), "https://example.com"))
        assertNull(SharedNotes.urlProperty(schema("Link" to "url"), null))
        assertNull(SharedNotes.urlProperty(schema("Link" to "url"), " "))
        assertNull(SharedNotes.urlProperty(null, "https://example.com"))
    }
}
