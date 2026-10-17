package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.model.PageIcon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconCodecTest {
    private fun parse(json: String) = IconCodec.parse(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `reads every kind of icon`() {
        assertEquals(PageIcon(emoji = "🥑"), parse("""{"type":"emoji","emoji":"🥑"}"""))
        assertEquals(PageIcon(name = "pizza", color = "blue"), parse("""{"type":"icon","icon":{"name":"pizza","color":"blue"}}"""))
        assertEquals(PageIcon(name = "pizza", color = "gray"), parse("""{"type":"icon","icon":{"name":"pizza"}}"""))
        assertEquals(PageIcon(url = "https://x/a.png"), parse("""{"type":"external","external":{"url":"https://x/a.png"}}"""))
        assertEquals(PageIcon(url = "https://x/bufo.png"), parse("""{"type":"custom_emoji","custom_emoji":{"id":"1","name":"bufo","url":"https://x/bufo.png"}}"""))
        assertEquals(
            PageIcon(url = "https://s3/f.png", urlExpiry = "2026-10-17T11:00:00.000Z"),
            parse("""{"type":"file","file":{"url":"https://s3/f.png","expiry_time":"2026-10-17T11:00:00.000Z"}}"""),
        )
    }

    @Test
    fun `no icon or an unknown kind is no icon`() {
        assertNull(IconCodec.parse(null))
        assertNull(parse("""{"type":"something_new","something_new":{}}"""))
    }
}
