package com.impca201.noter.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections

/** A request the app sent to [FakeNotion]. */
data class Sent(val method: String, val path: String, val body: String)

/**
 * A tiny stand-in for Notion's API on the test machine (the JDK's own HTTP server), so the
 * repository can be tested with the real client. [respond] answers each request with a status
 * and a JSON body.
 */
class FakeNotion(var respond: (Sent) -> Pair<Int, String> = { 200 to "{}" }) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val requests: MutableList<Sent> = Collections.synchronizedList(mutableListOf())

    init {
        server.createContext("/") { exchange ->
            val sent = Sent(
                exchange.requestMethod,
                exchange.requestURI.toString().removePrefix("/v1"),
                exchange.requestBody.readBytes().decodeToString(),
            )
            requests += sent
            val (status, body) = respond(sent)
            val bytes = body.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/v1"

    override fun close() = server.stop(0)

    companion object {
        fun database(id: String) =
            """{"object":"database","id":"$id","title":[{"plain_text":"Notes","text":{"content":"Notes"}}],
               "properties":{"Name":{"id":"title","name":"Name","type":"title","title":{}}}}"""

        fun page(id: String, databaseId: String, title: String = "", archived: Boolean = false) =
            """{"object":"page","id":"$id","archived":$archived,"url":"https://www.notion.so/$id",
               "created_time":"2026-10-16T10:00:00.000Z","last_edited_time":"2026-10-16T10:00:00.000Z",
               "parent":{"type":"database_id","database_id":"$databaseId"},
               "properties":{"Name":{"id":"title","type":"title","title":[${if (title.isEmpty()) "" else """{"type":"text","plain_text":"$title","text":{"content":"$title"},"annotations":{}}"""}]}}}"""

        fun todo(id: String, text: String, checked: Boolean = false) =
            """{"object":"block","id":"$id","type":"to_do","has_children":false,
               "to_do":{"checked":$checked,"rich_text":[{"type":"text","plain_text":"$text","text":{"content":"$text"},"annotations":{}}]}}"""

        fun list(vararg items: String) = """{"object":"list","results":[${items.joinToString(",")}],"has_more":false}"""

        const val ARCHIVED =
            """{"object":"error","status":400,"code":"validation_error","message":"Can't edit block that is archived. You must unarchive the block before editing."}"""
    }
}
