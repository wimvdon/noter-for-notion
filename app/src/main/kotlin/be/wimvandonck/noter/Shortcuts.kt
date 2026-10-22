package be.wimvandonck.noter

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import be.wimvandonck.noter.data.notion.InlineMarkdown
import be.wimvandonck.noter.logic.ShortcutTarget

/**
 * The launcher's shortcuts (#76, long press on the app icon): the pinned databases and notes,
 * each opening its screen (MainActivity reads [EXTRA_KEY]). A Notion emoji is the shortcut's
 * icon; without one, a page or database symbol.
 */
class Shortcuts(private val context: Context) {
    /** How many the launcher shows (it keeps the first). */
    val max: Int get() = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtMost(MAX_SHOWN)

    fun publish(targets: List<ShortcutTarget>) {
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, targets.map(::info)) }
    }

    private fun info(target: ShortcutTarget): ShortcutInfoCompat {
        val title = InlineMarkdown.stripped(target.title).ifBlank { context.getString(R.string.untitled) }
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .putExtra(EXTRA_KEY, target.key)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return ShortcutInfoCompat.Builder(context, target.key)
            .setShortLabel(title.take(SHORT_LABEL))
            .setLongLabel(title)
            .setIcon(icon(target))
            .setIntent(intent)
            .build()
    }

    private fun icon(target: ShortcutTarget): IconCompat {
        val emoji = target.emoji
        if (emoji.isNullOrBlank()) {
            return IconCompat.createWithResource(context, if (target.isDatabase) R.drawable.ic_shortcut_database else R.drawable.ic_shortcut_page)
        }
        // An adaptive icon is 108dp, of which the middle 72dp always shows: the emoji fills that.
        val size = (ADAPTIVE_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(context.getColor(R.color.ic_launcher_background))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * EMOJI_SHARE
            textAlign = Paint.Align.CENTER
        }
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(emoji, size / 2f, baseline, paint)
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    companion object {
        const val ACTION_OPEN = "be.wimvandonck.noter.action.OPEN"
        const val EXTRA_KEY = "be.wimvandonck.noter.extra.KEY"
        private const val MAX_SHOWN = 4
        private const val SHORT_LABEL = 25
        private const val ADAPTIVE_DP = 108
        private const val EMOJI_SHARE = 0.4f
    }
}
