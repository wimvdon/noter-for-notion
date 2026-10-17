package be.wimvandonck.noter.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class NotionErrorsTest {
    @Test
    fun `common errors get a key the screen translates, others keep Notion's message`() {
        assertEquals(NotionErrors.OFFLINE, NotionErrors.of(NotionException("timeout")))
        assertEquals(NotionErrors.KEY_REJECTED, NotionErrors.of(NotionException("API token is invalid.", status = 401)))
        assertEquals(NotionErrors.NOT_FOUND, NotionErrors.of(NotionException("Could not find database", status = 404)))
        assertEquals(NotionErrors.BUSY, NotionErrors.of(NotionException("Rate limited", status = 429)))
        assertEquals(NotionErrors.BUSY, NotionErrors.of(NotionException("Bad gateway", status = 502)))
        assertEquals(NotionErrors.NO_DATA_SOURCE, NotionErrors.of(NotionException("", status = 404, code = NotionErrors.NO_DATA_SOURCE)))
        assertEquals("body.title should be defined", NotionErrors.of(NotionException("body.title should be defined", status = 400)))
    }
}
