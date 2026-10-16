package com.impca201.noter.data.api

import com.impca201.noter.data.model.PageRef
import com.impca201.noter.data.notion.InlineMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A failed Notion call. [status] is the HTTP status, or null when Notion couldn't be reached
 * (offline): those calls are retried later, the others are not.
 */
class NotionException(
    message: String,
    val status: Int? = null,
    val code: String? = null,
) : Exception(message) {
    val isOffline: Boolean get() = status == null
    val isNotFound: Boolean get() = status == 404

    /** The page or block is in Notion's trash, so it can't be edited. */
    val isArchived: Boolean get() = status == 400 && message.orEmpty().contains("archived", ignoreCase = true)
    val isTemporary: Boolean get() = status == null || status == 429 || status >= 500
}

/** Notion's REST API (api.notion.com), with the integration's API key. */
class NotionClient(
    private val apiKey: () -> String,
    /** Only tests change this (to a server on the test machine). */
    private val baseUrl: String = BASE_URL,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Notion allows about three requests per second per integration and answers 429 above that.
     * Throttled and server-side failures are retried here, honouring Retry-After. Requests that
     * add something ([adds]) are only retried when throttled: after a server error Notion may have
     * added it anyway, and a second try would add it twice.
     */
    private suspend fun call(
        method: String,
        endpoint: String,
        body: Any? = null,
        key: String = apiKey(),
        adds: Boolean = false,
    ): JsonObject =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) throw NotionException("No Notion API key", status = 401, code = "no_key")
            val requestBody = when {
                body != null -> body.toJsonElement().toString().toRequestBody(JSON)
                method != "GET" && method != "DELETE" -> "{}".toRequestBody(JSON)
                else -> null
            }
            val request = Request.Builder()
                .url("$baseUrl$endpoint")
                .header("Authorization", "Bearer $key")
                .header("Notion-Version", NOTION_VERSION)
                .method(method, requestBody)
                .build()

            var lastError = NotionException("Notion API error")
            repeat(MAX_ATTEMPTS) { attempt ->
                val response = try {
                    httpClient.newCall(request).await()
                } catch (e: IOException) {
                    throw NotionException(e.message ?: "Network error")
                }
                val text = response.use { it.body.string() }
                val data = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
                if (response.isSuccessful) return@withContext data

                lastError = NotionException(
                    data.stringOrNull("message") ?: "Notion API error (HTTP ${response.code})",
                    status = response.code,
                    code = data.stringOrNull("code"),
                )
                val retry = if (adds) response.code == 429 else lastError.isTemporary
                if (!retry || attempt == MAX_ATTEMPTS - 1) throw lastError
                val retryAfterMs = response.header("Retry-After")?.toDoubleOrNull()?.times(1000)?.toLong()
                delay((retryAfterMs ?: (500L shl attempt)).coerceIn(300L, 10_000L))
            }
            throw lastError
        }

    /** Checks [key] works; returns the integration's (bot's) name. */
    suspend fun testKey(key: String): String =
        call("GET", "/users/me", key = key).stringOrNull("name").orEmpty()

    /**
     * Data sources shared with the integration, most recently edited first. A database usually has
     * one; a database with several has one result per data source.
     */
    suspend fun searchDatabases(): List<PageRef> =
        paged { cursor ->
            call(
                "POST", "/search",
                buildMap {
                    put("filter", mapOf("value" to "data_source", "property" to "object"))
                    put("sort", mapOf("direction" to "descending", "timestamp" to "last_edited_time"))
                    put("page_size", 100)
                    if (cursor != null) put("start_cursor", cursor)
                },
            )
        }.mapNotNull { db ->
            val id = db.stringOrNull("id") ?: return@mapNotNull null
            PageRef(id, InlineMarkdown.plainText(db.arrayOrNull("title")).trim())
        }

    /**
     * Pages and databases shared with the integration whose title matches [query] (everything when
     * blank), most recently edited first; at most [limit] results.
     */
    suspend fun search(query: String, limit: Int): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val data = call(
                "POST", "/search",
                buildMap {
                    if (query.isNotBlank()) put("query", query)
                    put("sort", mapOf("direction" to "descending", "timestamp" to "last_edited_time"))
                    put("page_size", 100)
                    if (cursor != null) put("start_cursor", cursor)
                },
            )
            data.arrayOrNull("results")?.forEach { el -> el.asObjectOrNull()?.let(results::add) }
            cursor = if (data.boolOrNull("has_more") == true) data.stringOrNull("next_cursor") else null
        } while (cursor != null && results.size < limit)
        return results.take(limit)
    }

    /** A database: its title and its data sources (`data_sources`: id and name of each). */
    suspend fun getDatabase(databaseId: String): JsonObject = call("GET", "/databases/$databaseId")

    /** A data source: its title and its properties (the schema). */
    suspend fun getDataSource(dataSourceId: String): JsonObject = call("GET", "/data_sources/$dataSourceId")

    /** All pages of a data source matching [filter] (Notion's filter object), most recently edited first. */
    suspend fun queryDataSource(dataSourceId: String, filter: Map<String, Any?>? = null): List<JsonObject> =
        paged { cursor ->
            call(
                "POST", "/data_sources/$dataSourceId/query",
                buildMap {
                    if (filter != null) put("filter", filter)
                    put("sorts", listOf(mapOf("timestamp" to "last_edited_time", "direction" to "descending")))
                    put("page_size", 100)
                    if (cursor != null) put("start_cursor", cursor)
                },
            )
        }

    suspend fun getPage(pageId: String): JsonObject = call("GET", "/pages/$pageId")

    suspend fun createPage(dataSourceId: String, properties: Map<String, Any?>, children: List<Map<String, Any?>>): JsonObject =
        createPage(mapOf("type" to "data_source_id", "data_source_id" to dataSourceId), properties, children)

    /**
     * Creates a page on its own: inside page [parentPageId], or at the top of the workspace when it
     * is null (Notion allows that only for some kinds of connections; others get an error).
     */
    suspend fun createPageIn(parentPageId: String?, properties: Map<String, Any?>): JsonObject =
        createPage(
            if (parentPageId != null) mapOf("type" to "page_id", "page_id" to parentPageId) else mapOf("type" to "workspace", "workspace" to true),
            properties,
            emptyList(),
        )

    private suspend fun createPage(parent: Map<String, Any?>, properties: Map<String, Any?>, children: List<Map<String, Any?>>): JsonObject =
        call(
            "POST", "/pages",
            buildMap {
                put("parent", parent)
                put("properties", properties)
                if (children.isNotEmpty()) put("children", children.take(MAX_CHILDREN))
            },
            adds = true,
        )

    suspend fun updatePageProperties(pageId: String, properties: Map<String, Any?>): JsonObject =
        call("PATCH", "/pages/$pageId", mapOf("properties" to properties))

    /** Moves a page to Notion's trash (it can be restored there for 30 days). */
    suspend fun trashPage(pageId: String) {
        call("PATCH", "/pages/$pageId", mapOf("archived" to true))
    }

    suspend fun blockChildren(blockId: String): List<JsonObject> =
        paged { cursor ->
            call("GET", "/blocks/$blockId/children?page_size=100" + (cursor?.let { "&start_cursor=$it" } ?: ""))
        }

    /**
     * Adds [children] after block [after], at the start ([atStart]) or at the end of [parentId].
     * Returns the new blocks' IDs in order, or an empty list when the answer doesn't tell which
     * blocks are new (the caller then downloads the content again).
     *
     * Notion's answer may also list blocks that were already there (the ones after the new
     * blocks, or all of the parent's), so the new blocks are the ones whose IDs aren't in
     * [knownIds], the blocks the caller knows the parent has.
     */
    suspend fun appendChildren(
        parentId: String,
        children: List<Map<String, Any?>>,
        after: String?,
        atStart: Boolean,
        knownIds: Set<String> = emptySet(),
    ): List<String> {
        val ids = mutableListOf<String>()
        var anchor = after
        var start = atStart
        for (chunk in children.chunked(MAX_CHILDREN)) {
            val body = buildMap<String, Any?> {
                put("children", chunk)
                when {
                    anchor != null -> put("after", anchor)
                    start -> put("position", mapOf("type" to "start"))
                }
            }
            val result = call("PATCH", "/blocks/$parentId/children", body, adds = true)
            val returned = result.arrayOrNull("results").orEmpty().mapNotNull { it.asObjectOrNull()?.stringOrNull("id") }
            val newIds = returned.filter { it !in knownIds && it !in ids }
            if (newIds.size != chunk.size) return emptyList()
            ids += newIds
            anchor = newIds.last()
            start = false
        }
        return ids
    }

    suspend fun updateBlock(blockId: String, body: Map<String, Any?>) {
        call("PATCH", "/blocks/$blockId", body)
    }

    suspend fun deleteBlock(blockId: String) {
        call("DELETE", "/blocks/$blockId")
    }

    suspend fun listUsers(): List<JsonObject> =
        paged { cursor -> call("GET", "/users?page_size=100" + (cursor?.let { "&start_cursor=$it" } ?: "")) }

    private suspend fun paged(fetchPage: suspend (cursor: String?) -> JsonObject): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val data = fetchPage(cursor)
            data.arrayOrNull("results")?.forEach { el -> el.asObjectOrNull()?.let(results::add) }
            cursor = if (data.boolOrNull("has_more") == true) data.stringOrNull("next_cursor") else null
        } while (cursor != null)
        return results
    }

    private companion object {
        const val BASE_URL = "https://api.notion.com/v1"
        /** Data sources: a database can have several (see decisions.md, 2026-10-16). */
        const val NOTION_VERSION = "2025-09-03"
        const val MAX_ATTEMPTS = 4

        /** Notion accepts at most 100 blocks per request. */
        const val MAX_CHILDREN = 100
        val JSON = "application/json".toMediaType()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)

        override fun onResponse(call: Call, response: Response) = cont.resume(response)
    })
}
