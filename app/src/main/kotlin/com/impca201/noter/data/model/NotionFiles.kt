package com.impca201.noter.data.model

import java.time.Instant

/**
 * Files uploaded to Notion (images, covers, attachments) come with a link that works for about an
 * hour. Content kept on this phone is downloaded again when one of its links has expired, and
 * images are cached under the link without its signature, so a fresh link reuses the cached image.
 */
object NotionFiles {
    /** Download again a little before the link really stops working. */
    const val MARGIN_MS = 60_000L

    fun isExpired(expiry: String?, nowMs: Long): Boolean {
        expiry ?: return false
        val ms = runCatching { Instant.parse(expiry).toEpochMilli() }.getOrNull() ?: return true
        return ms - MARGIN_MS <= nowMs
    }

    /** The key an image is cached under: a Notion file without its expiring signature. */
    fun cacheKey(url: String, expiry: String?): String = if (expiry != null) url.substringBefore('?') else url
}
