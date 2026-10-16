package com.impca201.noter.logic

import java.net.URI
import java.net.URLDecoder

/** Labels and links for the blocks the editor shows read-only (files, links, linked pages). */
object ReadOnlyBlocks {
    /** A file's name from its link: the last part of the path, without the query. */
    fun fileName(url: String): String {
        val path = runCatching { URI(url).rawPath }.getOrNull() ?: url.substringBefore('?')
        val name = path.trimEnd('/').substringAfterLast('/')
        return runCatching { URLDecoder.decode(name, "UTF-8") }.getOrDefault(name).ifBlank { url }
    }

    /** The website of a link, without "www.". */
    fun host(url: String): String? = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")

    /** Notion's web link for a page or database ID. */
    fun notionUrl(id: String): String = "https://www.notion.so/" + id.replace("-", "")
}
