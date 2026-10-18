package be.wimvandonck.noter.data.api

import be.wimvandonck.noter.data.FakeNotion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The client with tokens for two workspaces (#58), against a fake Notion that knows which token sees what. */
class MultiWorkspaceClientTest {
    private val notFound = 404 to """{"object":"error","status":404,"code":"object_not_found","message":"Could not find page"}"""

    @Test
    fun `a page of the second workspace is found with its token, and asked with it next time`() = runBlocking {
        FakeNotion { sent -> if (sent.auth == "Bearer b") 200 to """{"object":"page","id":"p1"}""" else notFound }.use { notion ->
            val client = NotionClient(apiKey = { "a" }, extraKeys = { listOf("b") }, baseUrl = notion.baseUrl)
            assertEquals("p1", client.getPage("p1").stringOrNull("id"))
            assertEquals(listOf("Bearer a", "Bearer b"), notion.requests.map { it.auth })
            notion.requests.clear()
            client.getPage("p1")
            assertEquals(listOf("Bearer b"), notion.requests.map { it.auth })
        }
    }

    @Test
    fun `not found in any workspace is not found`() = runBlocking {
        FakeNotion { notFound }.use { notion ->
            val client = NotionClient(apiKey = { "a" }, extraKeys = { listOf("b") }, baseUrl = notion.baseUrl)
            val error = runCatching { client.getPage("p1") }.exceptionOrNull()
            assertTrue(error is NotionException && error.isNotFound)
            assertEquals(2, notion.requests.size)
        }
    }

    @Test
    fun `search asks every workspace, and its results remember their workspace`() = runBlocking {
        FakeNotion { sent ->
            when {
                sent.path == "/search" && sent.auth == "Bearer a" ->
                    200 to """{"object":"list","results":[{"object":"page","id":"x","last_edited_time":"2026-10-01T10:00:00.000Z"}],"has_more":false}"""
                sent.path == "/search" ->
                    200 to """{"object":"list","results":[{"object":"page","id":"y","last_edited_time":"2026-10-02T10:00:00.000Z"}],"has_more":false}"""
                else -> 200 to """{"object":"page","id":"y"}"""
            }
        }.use { notion ->
            val client = NotionClient(apiKey = { "a" }, extraKeys = { listOf("b") }, baseUrl = notion.baseUrl)
            assertEquals(listOf("y", "x"), client.search("", limit = 10).map { it.stringOrNull("id") })
            notion.requests.clear()
            client.getPage("y")
            assertEquals(listOf("Bearer b"), notion.requests.map { it.auth })
        }
    }

    @Test
    fun `one refused token doesn't hide the other workspace's results`() = runBlocking {
        FakeNotion { sent ->
            if (sent.auth == "Bearer b") {
                401 to """{"object":"error","status":401,"code":"unauthorized","message":"API token is invalid."}"""
            } else {
                200 to """{"object":"list","results":[{"object":"page","id":"x"}],"has_more":false}"""
            }
        }.use { notion ->
            val client = NotionClient(apiKey = { "a" }, extraKeys = { listOf("b") }, baseUrl = notion.baseUrl)
            assertEquals(listOf("x"), client.search("", limit = 10).map { it.stringOrNull("id") })
        }
    }

    @Test
    fun `with one token nothing changes`() = runBlocking {
        FakeNotion { notFound }.use { notion ->
            val client = NotionClient(apiKey = { "a" }, baseUrl = notion.baseUrl)
            runCatching { client.getPage("p1") }
            assertEquals(listOf("Bearer a"), notion.requests.map { it.auth })
        }
    }
}
