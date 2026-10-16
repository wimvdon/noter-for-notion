package com.impca201.noter.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.impca201.noter.NoterApp
import com.impca201.noter.data.icons.IconCatalog
import java.util.concurrent.ConcurrentHashMap

/** The icons of views: Material Symbols by name (see [IconCatalog]), drawn as vectors. */
object DatabaseIcons {
    private val cache = ConcurrentHashMap<String, ImageVector>()

    /** The icon stored for a view (also the keys from before the catalog), else the note icon. */
    @Composable
    fun icon(stored: String?): ImageVector {
        val catalog = (LocalContext.current.applicationContext as NoterApp).iconCatalog
        return remember(stored, catalog) { catalog.icon(IconCatalog.fromStored(stored))?.let(::vector) } ?: Icons.AutoMirrored.Rounded.StickyNote2
    }

    /** Drawn icons are kept, so scrolling the icon picker doesn't parse the same paths again. */
    fun vector(icon: IconCatalog.Icon): ImageVector =
        cache.getOrPut(icon.name) {
            ImageVector
                .Builder(name = icon.name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
                .apply { icon.paths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) } }
                .build()
        }
}
