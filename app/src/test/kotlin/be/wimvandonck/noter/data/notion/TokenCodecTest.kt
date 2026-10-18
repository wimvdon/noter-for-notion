package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.model.NotionUser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenCodecTest {
    private fun parse(json: String) = TokenCodec.parse(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `a personal access token acts as its owner`() {
        val info = parse(
            """{"object":"user","id":"bot-1","type":"bot","name":"Noter",
               "bot":{"owner":{"type":"user","user":{"object":"user","id":"u1","name":"Wim","type":"person"}},"workspace_name":"Home"}}""",
        )
        assertEquals(TokenInfo(TokenKind.PERSONAL, "Wim", NotionUser("u1", "Wim"), "Home"), info)
    }

    @Test
    fun `a personal access token has its owner's profile picture when Notion tells it`() {
        val info = parse(
            """{"object":"user","id":"bot-1","type":"bot","name":"Noter",
               "bot":{"owner":{"type":"user","user":{"object":"user","id":"u1","name":"Wim","type":"person","avatar_url":"https://example.com/wim.png"}},"workspace_name":"Home"}}""",
        )
        assertEquals("https://example.com/wim.png", info.avatarUrl)
    }

    @Test
    fun `no picture when Notion has none, or for a connection`() {
        val noPicture = parse(
            """{"object":"user","id":"bot-1","type":"bot","name":"Noter",
               "bot":{"owner":{"type":"user","user":{"object":"user","id":"u1","name":"Wim","type":"person","avatar_url":null}},"workspace_name":"Home"}}""",
        )
        assertEquals(null, noPicture.avatarUrl)
        val connection = parse(
            """{"object":"user","id":"bot-2","type":"bot","name":"Noter integration","avatar_url":"https://example.com/bot.png",
               "bot":{"owner":{"type":"workspace","workspace":true},"workspace_name":"Home"}}""",
        )
        assertEquals(null, connection.avatarUrl)
    }

    @Test
    fun `an internal connection is a bot of the workspace`() {
        val info = parse(
            """{"object":"user","id":"bot-2","type":"bot","name":"Noter integration",
               "bot":{"owner":{"type":"workspace","workspace":true},"workspace_name":"Home"}}""",
        )
        assertEquals(TokenInfo(TokenKind.CONNECTION, "Noter integration", workspace = "Home"), info)
    }
}
