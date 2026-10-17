package be.wimvandonck.noter.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.data.model.NotionFiles
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.logic.PageIcons

/**
 * A page's icon at [size]: its emoji, its image, or Notion's own icon drawn with the closest
 * Material Symbol in the icon's color (Notion's icons themselves are SVG files the app can't draw).
 */
@Composable
fun PageIconView(icon: PageIcon, size: Dp, modifier: Modifier = Modifier) {
    when {
        icon.emoji != null -> {
            // An emoji is text: about 80 % of the line height fills the box.
            val fontSize = with(LocalDensity.current) { (size * 0.8f).toSp() }
            Box(modifier.size(size), contentAlignment = Alignment.Center) {
                Text(icon.emoji, style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize, lineHeight = fontSize))
            }
        }
        icon.url != null -> {
            val context = LocalPlatformContext.current
            val request = remember(icon.url, icon.urlExpiry) {
                val key = NotionFiles.cacheKey(icon.url, icon.urlExpiry)
                ImageRequest.Builder(context).data(icon.url).memoryCacheKey(key).diskCacheKey(key).build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = null,
                modifier = modifier.size(size),
                error = { FallbackIcon(size, MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
        else -> {
            val catalog = (LocalContext.current.applicationContext as NoterApp).iconCatalog
            val match = remember(icon.name, catalog) { icon.name?.let { PageIcons.closestSymbol(it, catalog) } }
            val tint = notionIconColor(icon.color) ?: MaterialTheme.colorScheme.onSurfaceVariant
            if (match != null) {
                Icon(DatabaseIcons.vector(match), contentDescription = null, tint = tint, modifier = modifier.size(size))
            } else {
                FallbackIcon(size, tint, modifier)
            }
        }
    }
}

@Composable
private fun FallbackIcon(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Icon(Icons.Rounded.Description, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Notion's colors for its own icons (the same in light and dark theme, as in Notion). */
private fun notionIconColor(color: String?): Color? = when (color) {
    "gray" -> Color(0xFF91918E)
    "brown" -> Color(0xFFA27763)
    "orange" -> Color(0xFFD9730D)
    "yellow" -> Color(0xFFCB912F)
    "green" -> Color(0xFF448361)
    "blue" -> Color(0xFF337EA9)
    "purple" -> Color(0xFF9065B0)
    "pink" -> Color(0xFFC14C8A)
    "red" -> Color(0xFFD44C47)
    else -> null
}

/** The size of a page icon next to a title in lists and on cards. */
val SMALL_PAGE_ICON: Dp = 20.dp
