package be.wimvandonck.noter.data.api

import be.wimvandonck.noter.data.FakeNotion
import be.wimvandonck.noter.logic.Workspaces
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

    @Test
    fun `a page at the top goes with its workspace's token`() = runBlocking {
        FakeNotion { 200 to """{"object":"page","id":"new"}""" }.use { notion ->
            val client = NotionClient(apiKey = { "a" }, extraKeys = { listOf("b") }, baseUrl = notion.baseUrl)
            client.createPageIn(null, emptyMap(), workspace = Workspaces.id("b"))
            client.createPageIn(null, emptyMap())
            client.createPageIn(null, emptyMap(), workspace = "removed")
            assertEquals(listOf("Bearer b", "Bearer a", "Bearer a"), notion.requests.map { it.auth })
            assertEquals(Workspaces.id("a"), client.owners.value["new"])
        }
    }

    @Test
    fun `with one token the workspace of what it answered is remembered too`() = runBlocking {
        FakeNotion { 200 to """{"object":"page","id":"p1"}""" }.use { notion ->
            val client = NotionClient(apiKey = { "a" }, baseUrl = notion.baseUrl)
            client.getPage("p1")
            assertEquals(mapOf("p1" to Workspaces.id("a")), client.owners.value)
        }
    }

    @Test
    fun `a database goes with its page's workspace, or at the top with the workspace asked for`() = runBlocking {
        FakeNotion { sent ->
            when {
                sent.path == "/pages/p1" && sent.auth == "Bearer b" -> 200 to """{"object":"page","id":"p1"}"""
                sent.path == "/databases" -> 200 to """{"object":"database","id":"db1","data_sources":[{"id":"ds1","name":"Books"}]}"""
                else -> notFound
            }
        }.use { notion ->
            val client = NotionClient(apiKey = { "a" }, extraKeys = { listOf("b") }, baseUrl = notion.baseUrl)
            client.getPage("p1")
            notion.requests.clear()
            client.createDatabase(mapOf("parent" to mapOf("type" to "page_id", "page_id" to "p1")))
            client.createDatabase(mapOf("parent" to mapOf("type" to "workspace", "workspace" to true)), workspace = Workspaces.id("b"))
            assertEquals(listOf("Bearer b", "Bearer b"), notion.requests.map { it.auth })
            assertEquals(Workspaces.id("b"), client.owners.value["ds1"])
        }
    }
}

class RenewKeyClientTest {
    private val refused = 401 to """{"object":"error","status":401,"code":"unauthorized","message":"API token is invalid."}"""

    @Test
    fun `a refused token is renewed and the request sent again with the new one`() = runBlocking {
        FakeNotion { sent -> if (sent.auth == "Bearer new") 200 to """{"object":"page","id":"p1"}""" else refused }.use { notion ->
            var key = "old"
            val client = NotionClient(apiKey = { key }, baseUrl = notion.baseUrl, renewKey = { if (it == "old") "new".also { key = it } else null })
            assertEquals("p1", client.getPage("p1").stringOrNull("id"))
            assertEquals(listOf("Bearer old", "Bearer new"), notion.requests.map { it.auth })
        }
    }

    @Test
    fun `a token that can't be renewed is refused as before, after one try`() = runBlocking {
        FakeNotion { refused }.use { notion ->
            var asked = 0
            val client = NotionClient(apiKey = { "old" }, baseUrl = notion.baseUrl, renewKey = { asked++; null })
            val error = runCatching { client.getPage("p1") }.exceptionOrNull()
            assertTrue(error is NotionException && error.status == 401)
            assertEquals(1, asked)
            assertEquals(1, notion.requests.size)
        }
    }

    @Test
    fun `a renewed token that is refused too isn't renewed again`() = runBlocking {
        FakeNotion { refused }.use { notion ->
            var asked = 0
            val client = NotionClient(apiKey = { "old" }, baseUrl = notion.baseUrl, renewKey = { asked++; "new" })
            val error = runCatching { client.getPage("p1") }.exceptionOrNull()
            assertTrue(error is NotionException && error.status == 401)
            assertEquals(1, asked)
            assertEquals(listOf("Bearer old", "Bearer new"), notion.requests.map { it.auth })
        }
    }
}
