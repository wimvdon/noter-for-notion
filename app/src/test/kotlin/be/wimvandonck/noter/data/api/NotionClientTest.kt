package be.wimvandonck.noter.data.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class NotionClientTest {
    @Test
    fun `an answer cut off halfway is offline, not a crash`() = runBlocking {
        // A server that promises 100 bytes, sends a few and hangs up.
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { server ->
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    socket.use {
                        val input = it.getInputStream().bufferedReader()
                        while (input.readLine()?.isNotEmpty() == true) Unit
                        it.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{\"object\"".toByteArray())
                            flush()
                        }
                    }
                }
            }
            val client = NotionClient(apiKey = { "secret_test" }, baseUrl = "http://127.0.0.1:${server.localPort}/v1")
            val error = runCatching { client.getPage("p1") }.exceptionOrNull()
            assertTrue(error.toString(), error is NotionException && error.isOffline)
        }
    }
}
