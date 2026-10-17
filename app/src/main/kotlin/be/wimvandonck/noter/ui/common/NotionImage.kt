package be.wimvandonck.noter.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import be.wimvandonck.noter.data.model.NotionFiles
import be.wimvandonck.noter.ui.theme.skeleton

/**
 * An image from Notion (a cover or an image block). Files Notion hosts are cached without their
 * expiring signature (see [NotionFiles]), so opening the note again doesn't download them again.
 */
@Composable
fun NotionImage(
    url: String,
    expiry: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalPlatformContext.current
    val request = remember(url, expiry) {
        val key = NotionFiles.cacheKey(url, expiry)
        ImageRequest.Builder(context).data(url).memoryCacheKey(key).diskCacheKey(key).build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().skeleton()) },
        error = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
