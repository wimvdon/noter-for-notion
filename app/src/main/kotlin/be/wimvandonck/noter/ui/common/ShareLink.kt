package be.wimvandonck.noter.ui.common

import android.content.Context
import android.content.Intent

/** Opens Android's share sheet with [url] (a page's or database's Notion link); [title] names it for apps that show one. */
fun shareLink(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
