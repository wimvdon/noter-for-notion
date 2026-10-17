package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePreviewsTest {
    private val note = NoteSummary(id = "p1", databaseId = "d", title = "T", lastEditedTime = "2026-10-16T08:00:00.000Z")

    @Test
    fun `downloads content that isn't on the phone`() {
        assertTrue(NotePreviews.needsDownload(note, null))
    }

    @Test
    fun `downloads again when Notion has a newer edit`() {
        val cached = PageContent("p1", emptyList(), remoteEditedTime = "2026-10-15T08:00:00.000Z")
        assertTrue(NotePreviews.needsDownload(note, cached))
    }

    @Test
    fun `keeps an up-to-date copy`() {
        val cached = PageContent("p1", emptyList(), remoteEditedTime = note.lastEditedTime)
        assertFalse(NotePreviews.needsDownload(note, cached))
    }

    @Test
    fun `never overwrites unsent edits or downloads notes Notion doesn't have yet`() {
        assertFalse(NotePreviews.needsDownload(note, PageContent("p1", emptyList(), dirty = true, remoteEditedTime = "old")))
        assertFalse(NotePreviews.needsDownload(note.copy(id = NoteSummary.LOCAL_PREFIX + "1"), null))
    }

    @Test
    fun `preview shows the first lines with to-dos and bullets`() {
        val blocks = listOf(
            Block("a", type = BlockType.TODO, text = "Milk", checked = true),
            Block("b", type = BlockType.BULLETED, text = "**Eggs**"),
            Block("c", type = BlockType.DIVIDER),
            Block("d", text = "Bread"),
        )
        assertEquals("☑ Milk\n• Eggs\nBread", NotePreviews.text(PageContent("p1", blocks)))
        assertNull(NotePreviews.text(PageContent("p1", emptyList())))
    }
}
