package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.notion.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class SignInsTest {
    @Test
    fun `the state names the app and its public key, as the helper reads it`() {
        val key = ByteArray(65) { if (it == 0) 4 else it.toByte() }
        val state = SignIns.state("be.wimvandonck.noter.debug", key)
        val (scheme, encoded) = state.split("~")
        assertEquals("be.wimvandonck.noter.debug", scheme)
        assertEquals(SealedBox.base64Url(key), encoded)
        assertTrue(encoded.none { it == '=' || it == '+' || it == '/' })
    }

    @Test
    fun `Notion's page gets the client, the helper's address and the state`() {
        val url = URI(SignIns.authorizeUrl("client id", "https://helper.example/", "be.wimvandonck.noter~abc"))
        assertEquals("api.notion.com", url.host)
        assertEquals("/v1/oauth/authorize", url.path)
        val query = url.rawQuery.split("&").associate { part ->
            part.substringBefore("=") to URLDecoder.decode(part.substringAfter("="), "UTF-8")
        }
        assertEquals("client id", query["client_id"])
        assertEquals("code", query["response_type"])
        assertEquals("user", query["owner"])
        assertEquals("https://helper.example/callback", query["redirect_uri"])
        assertEquals("be.wimvandonck.noter~abc", query["state"])
    }

    @Test
    fun `the link back is a result, a refusal or nothing`() {
        assertEquals(SignInReturn.Sealed("x.y.z"), SignIns.returned("x.y.z", null))
        assertEquals(SignInReturn.Refused("access_denied"), SignIns.returned(null, "access_denied"))
        assertNull(SignIns.returned(null, null))
        assertNull(SignIns.returned("", " "))
    }

    @Test
    fun `reads Notion's token answer`() {
        val grant = SignIns.grant(
            """{"access_token":"ntn_a","token_type":"bearer","refresh_token":"ntn_r","bot_id":"b","workspace_id":"ws",
               "workspace_name":"Home","workspace_icon":null,"owner":{"type":"user","user":{"object":"user","id":"u","name":"Wim"}},
               "duplicated_template_id":null}""",
        )
        assertEquals(SignInGrant("ntn_a", "ntn_r", "ws", "Home", "Wim"), grant)
        assertEquals(
            WorkspaceToken("ntn_a", name = "Wim", workspace = "Home", refresh = "ntn_r", workspaceId = "ws"),
            grant!!.toToken(),
        )
        assertTrue(grant.toToken().signedIn)
    }

    @Test
    fun `an answer without an access token is no grant`() {
        assertNull(SignIns.grant("""{"error":"invalid_grant"}"""))
        assertNull(SignIns.grant("not json"))
        assertEquals(SignInGrant("a", "", "", "", ""), SignIns.grant("""{"access_token":"a","refresh_token":null}"""))
    }

    @Test
    fun `a pasted token isn't signed in`() {
        assertEquals(false, WorkspaceToken("ntn_x", kind = TokenKind.PERSONAL).signedIn)
    }
}
