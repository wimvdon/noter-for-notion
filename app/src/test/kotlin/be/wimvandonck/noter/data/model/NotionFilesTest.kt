package be.wimvandonck.noter.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NotionFilesTest {
    private val now = Instant.parse("2026-10-16T10:00:00Z").toEpochMilli()

    @Test
    fun `a link expires a minute before Notion says`() {
        assertFalse(NotionFiles.isExpired(null, now))
        assertFalse(NotionFiles.isExpired("2026-10-16T10:30:00.000Z", now))
        assertTrue(NotionFiles.isExpired("2026-10-16T10:00:30.000Z", now))
        assertTrue(NotionFiles.isExpired("2026-10-16T09:00:00.000Z", now))
        assertTrue(NotionFiles.isExpired("not a date", now))
    }

    @Test
    fun `files Notion hosts are cached without their signature`() {
        assertEquals("https://s3/x/cat.png", NotionFiles.cacheKey("https://s3/x/cat.png?X-Amz-Signature=1", "2026-10-16T10:30:00.000Z"))
        assertEquals("https://x/y.png?size=2", NotionFiles.cacheKey("https://x/y.png?size=2", null))
    }

    @Test
    fun `content with an expired file is downloaded again`() {
        val block = Block("k", type = BlockType.UNSUPPORTED, rawType = "image", url = "u", urlExpiry = "2026-10-16T09:00:00.000Z")
        assertTrue(PageContent("p", listOf(block)).hasExpiredFiles(now))
        assertFalse(PageContent("p", listOf(block.copy(urlExpiry = null))).hasExpiredFiles(now))
    }
}
