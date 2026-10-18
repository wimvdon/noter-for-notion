package be.wimvandonck.noter.data.api

import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.notion.InlineMarkdown
import be.wimvandonck.noter.logic.KeyRouter
import be.wimvandonck.noter.logic.Workspaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
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
    val isArchived: Boolean
        get() = status == 400 && message.orEmpty().let { it.contains("archived", ignoreCase = true) || it.contains("trash", ignoreCase = true) }
    val isTemporary: Boolean get() = status == null || status == 429 || status >= 500
}

/**
 * Notion's REST API (api.notion.com), with the main token ([apiKey]) and the tokens for other
 * workspaces ([extraKeys], #58). A request about a page, block, database or data source goes with
 * the token that worked for it before ([KeyRouter]), else with each token in turn until one isn't
 * answered with "not found". Search and the list of people ask every workspace and merge. Which
 * workspace answered for an object is remembered ([owners]), also with one token, so the screens
 * can tell where a page or database is.
 */
class NotionClient(
    private val apiKey: () -> String,
    private val extraKeys: () -> List<String> = { emptyList() },
    /** Only tests change this (to a server on the test machine). */
    private val baseUrl: String = BASE_URL,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val router = KeyRouter()

    /** The workspace ([Workspaces.id]) of each object a token answered for, by ID (see [KeyRouter]). */
    val owners: StateFlow<Map<String, String>> get() = router.owners

    /** What the phone kept about which workspace objects are in. */
    fun restoreOwners(saved: Map<String, String>) = router.restore(saved)

    /** Forgets which objects were in [workspace] (its token was removed). */
    fun forgetWorkspace(workspace: String) = router.forget(workspace)

    /** Forgets which workspace every object is in (the last token was removed). */
    fun forgetWorkspaces() = router.clear()

    /** Every token, the main one first; none without a main token. */
    private fun keys(): List<String> {
        val primary = apiKey()
        if (primary.isBlank()) return emptyList()
        return (listOf(primary) + extraKeys()).filter { it.isNotBlank() }.distinct()
    }

    /**
     * Sends a request with [key] when given, else with the right token (see the class comment).
     * With one token this is the same as [send] with it.
     */
    private suspend fun call(
        method: String,
        endpoint: String,
        body: Any? = null,
        key: String? = null,
        adds: Boolean = false,
    ): JsonObject {
        if (key != null) return send(method, endpoint, body, key, adds).also { remember(it, key) }
        val all = keys()
        val subject = NotionRoutes.subjectId(endpoint, body)
        if (all.size <= 1 || subject == null) {
            val only = all.firstOrNull().orEmpty()
            return send(method, endpoint, body, only, adds).also { data ->
                subject?.let { router.remember(it, only, kept = !NotionRoutes.isBlock(endpoint)) }
                remember(data, only)
            }
        }
        var firstError: NotionException? = null
        for (candidate in router.candidates(subject, all)) {
            try {
                val data = send(method, endpoint, body, candidate, adds)
                router.remember(subject, candidate, kept = !NotionRoutes.isBlock(endpoint))
                remember(data, candidate)
                return data
            } catch (e: NotionException) {
                // Another workspace's token doesn't know the object (404) or isn't allowed (401, 403):
                // try the next one. Anything else (offline, Notion busy, a bad request) stops here.
                if (e.status != 404 && e.status != 401 && e.status != 403) throw e
                val previous = firstError
                if (previous == null || previous.isNotFound && !e.isNotFound) firstError = e
            }
        }
        throw firstError ?: NotionException("No Notion API key", status = 401, code = "no_key")
    }

    /** The objects in [data] belong to [key]'s workspace. */
    private fun remember(data: JsonObject, key: String) {
        router.remember(NotionRoutes.objectIds(data), key)
        router.remember(NotionRoutes.objectIds(data, blocks = true), key, kept = false)
    }

    /** The token of [workspace] ([Workspaces.id]), if the app still has it. */
    private fun keyOf(workspace: String): String? = keys().firstOrNull { Workspaces.id(it) == workspace }

    /**
     * [block] once per token: the results of the tokens that worked. Fails only when every token
     * failed (with the first error), so one refused token doesn't hide the other workspaces.
     */
    private suspend fun <T> eachKey(block: suspend (key: String) -> T): List<T> {
        val all = keys()
        if (all.size <= 1) return listOf(block(all.firstOrNull().orEmpty()))
        val results = mutableListOf<T>()
        var firstError: NotionException? = null
        for (key in all) {
            try {
                results.add(block(key))
            } catch (e: NotionException) {
                if (firstError == null) firstError = e
            }
        }
        if (results.isEmpty()) throw firstError ?: NotionException("No Notion API key", status = 401, code = "no_key")
        return results
    }

    /**
     * Notion allows about three requests per second per integration and answers 429 above that.
     * Throttled and server-side failures are retried here, honouring Retry-After. Requests that
     * add something ([adds]) are only retried when throttled: after a server error Notion may have
     * added it anyway, and a second try would add it twice.
     */
    private suspend fun send(
        method: String,
        endpoint: String,
        body: Any?,
        key: String,
        adds: Boolean,
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

    /** The token's own (bot) user, with [key] or the stored key: who it acts as (see TokenCodec). */
    suspend fun me(key: String = apiKey()): JsonObject = send("GET", "/users/me", null, key, adds = false)

    /**
     * Data sources shared with the integration, most recently edited first. A database usually has
     * one; a database with several has one result per data source.
     */
    suspend fun searchDatabases(): List<PageRef> =
        NotionRoutes.newestFirst(
            eachKey { key ->
                paged { cursor ->
                    call(
                        "POST", "/search",
                        buildMap {
                            put("filter", mapOf("value" to "data_source", "property" to "object"))
                            put("sort", mapOf("direction" to "descending", "timestamp" to "last_edited_time"))
                            put("page_size", 100)
                            if (cursor != null) put("start_cursor", cursor)
                        },
                        key = key,
                    )
                }
            },
        ).mapNotNull { db ->
            val id = db.stringOrNull("id") ?: return@mapNotNull null
            PageRef(id, InlineMarkdown.plainText(db.arrayOrNull("title")).trim())
        }

    /**
     * Pages and databases shared with the integration whose title matches [query] (everything when
     * blank), most recently edited first; at most [limit] results.
     */
    suspend fun search(query: String, limit: Int): List<JsonObject> =
        NotionRoutes.newestFirst(eachKey { key -> search(query, limit, key) }, limit)

    private suspend fun search(query: String, limit: Int, key: String): List<JsonObject> {
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
                key = key,
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

    suspend fun createPage(
        dataSourceId: String,
        properties: Map<String, Any?>,
        children: List<Map<String, Any?>>,
        emoji: String? = null,
    ): JsonObject =
        createPage(mapOf("type" to "data_source_id", "data_source_id" to dataSourceId), properties, children, emoji)

    /**
     * Creates a page on its own: inside page [parentPageId], or at the top of [workspace] (the main
     * one when null or no longer there) when it is null. Notion allows the top only to a personal
     * access token; a connection gets an error.
     */
    suspend fun createPageIn(
        parentPageId: String?,
        properties: Map<String, Any?>,
        emoji: String? = null,
        workspace: String? = null,
        children: List<Map<String, Any?>> = emptyList(),
    ): JsonObject =
        createPage(
            if (parentPageId != null) mapOf("type" to "page_id", "page_id" to parentPageId) else mapOf("type" to "workspace", "workspace" to true),
            properties,
            children,
            emoji,
            key = if (parentPageId == null) workspace?.let(::keyOf) else null,
        )

    private suspend fun createPage(
        parent: Map<String, Any?>,
        properties: Map<String, Any?>,
        children: List<Map<String, Any?>>,
        emoji: String?,
        key: String? = null,
    ): JsonObject =
        call(
            "POST", "/pages",
            buildMap {
                put("parent", parent)
                put("properties", properties)
                if (emoji != null) put("icon", emojiIcon(emoji))
                if (children.isNotEmpty()) put("children", children.take(MAX_CHILDREN))
            },
            key = key,
            adds = true,
        )

    /** Sets the page's icon to [emoji], or removes it when that is null. */
    suspend fun updatePageIcon(pageId: String, emoji: String?): JsonObject =
        call("PATCH", "/pages/$pageId", mapOf("icon" to emoji?.let(::emojiIcon)))

    private fun emojiIcon(emoji: String) = mapOf("type" to "emoji", "emoji" to emoji)

    suspend fun updatePageProperties(pageId: String, properties: Map<String, Any?>): JsonObject =
        call("PATCH", "/pages/$pageId", mapOf("properties" to properties))

    /** Moves a page to Notion's trash (it can be restored there for 30 days). */
    suspend fun trashPage(pageId: String) {
        call("PATCH", "/pages/$pageId", mapOf("in_trash" to true))
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
                    anchor != null -> put("position", mapOf("type" to "after_block", "after_block" to mapOf("id" to anchor)))
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

    /** The people of every workspace (each person once). */
    suspend fun listUsers(): List<JsonObject> =
        eachKey { key ->
            paged { cursor -> call("GET", "/users?page_size=100" + (cursor?.let { "&start_cursor=$it" } ?: ""), key = key) }
        }.flatten().distinctBy { it.stringOrNull("id") ?: it.toString() }

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
        /** Data sources (see decisions.md, 2026-10-16), "in_trash" and "position" (2026-10-17). */
        const val NOTION_VERSION = "2026-03-11"
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
