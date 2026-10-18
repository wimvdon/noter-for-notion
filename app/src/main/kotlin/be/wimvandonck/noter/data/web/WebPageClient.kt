package be.wimvandonck.noter.data.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A web page as downloaded: its address (after redirects) and its HTML. */
data class WebPage(val url: String, val html: String)

/**
 * Downloads a web page someone shared to the app (#63), so its text and images can go into the
 * new note. Besides images, the only request outside Notion's API (see PRIVACY.md): one GET of
 * the shared link, as a browser would, without cookies or anything about the user or their notes.
 */
class WebPageClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build(),
) {
    /** The page at [url], or null when it can't be had (offline, an error, not a web page). */
    suspend fun fetch(url: String): WebPage? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return@withContext null
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body
                val type = body.contentType()
                if (!response.isSuccessful || (type != null && type.subtype != "html" && type.subtype != "xhtml+xml")) {
                    null
                } else {
                    val source = body.source()
                    source.request(MAX_BYTES)
                    val bytes = source.buffer.readByteArray(minOf(source.buffer.size, MAX_BYTES))
                    WebPage(response.request.url.toString(), String(bytes, type?.charset() ?: Charsets.UTF_8))
                }
            }
        }.getOrNull()
    }

    private companion object {
        /** Enough for any article; a bigger page is read up to here. */
        const val MAX_BYTES = 2L * 1024 * 1024

        /** Some sites only answer browsers. */
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    }
}
