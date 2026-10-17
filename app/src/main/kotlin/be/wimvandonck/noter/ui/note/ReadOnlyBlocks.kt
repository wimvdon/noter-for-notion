package be.wimvandonck.noter.ui.note

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.Block
import be.wimvandonck.noter.data.model.BlockType
import be.wimvandonck.noter.data.notion.InlineMarkdown
import be.wimvandonck.noter.logic.ReadOnlyBlocks
import be.wimvandonck.noter.ui.common.NotionImage
import be.wimvandonck.noter.ui.theme.AppShapes

/**
 * Blocks the editor shows but can't change (see [BlockType.UNSUPPORTED]): images, files, links,
 * tables, equations, linked pages and a table of contents. Tapping one opens it (in Notion, or
 * the file or link in the browser; linked pages and databases in the app).
 */
@Composable
fun ReadOnlyBlock(block: Block, headings: List<Block>, onOpenPage: (String) -> Unit, onOpenDatabase: (String) -> Unit) {
    val context = LocalContext.current
    val open: (String) -> Unit = { link -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) } }
    val url = block.url
    when (block.rawType) {
        "image" -> if (url != null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 0.dp)) {
                NotionImage(
                    url = url,
                    expiry = block.urlExpiry,
                    contentDescription = block.text.ifBlank { stringResource(R.string.block_image) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .heightIn(min = 120.dp, max = 560.dp)
                        .clip(AppShapes.Item)
                        .clickable(role = Role.Image, onClickLabel = stringResource(R.string.action_open_link)) { open(url) },
                )
                if (block.text.isNotBlank()) Caption(block.text)
            }
        } else {
            Fallback(block)
        }
        "video", "file", "pdf", "audio", "bookmark", "embed", "link_preview" -> if (url != null) {
            LinkRow(
                icon = iconFor(block.rawType),
                title = block.text.ifBlank { ReadOnlyBlocks.fileName(url) },
                subtitle = ReadOnlyBlocks.host(url),
                onClick = { open(url) },
            )
        } else {
            Fallback(block)
        }
        "child_page", "child_database", "link_to_page" -> if (url != null) {
            LinkRow(
                icon = if (block.linksDatabase) Icons.Rounded.TableChart else Icons.Rounded.Description,
                title = block.text.ifBlank { stringResource(if (block.rawType == "link_to_page") R.string.block_linked_page else R.string.untitled) },
                subtitle = null,
                trailing = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                onClick = { if (block.linksDatabase) onOpenDatabase(url) else onOpenPage(url) },
            )
        } else {
            Fallback(block)
        }
        "equation" -> Surface(
            shape = AppShapes.Item,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Functions, contentDescription = stringResource(R.string.block_equation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(block.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
        }
        "table" -> {
            val rows = block.rows
            if (rows.isNullOrEmpty()) Fallback(block) else Table(rows, block.headerRow)
        }
        "table_of_contents" -> if (headings.isNotEmpty()) TableOfContents(headings) else Unit
        // Notion's path to the page: the app has its own back arrow.
        "breadcrumb" -> Unit
        else -> Fallback(block)
    }
}

private fun iconFor(rawType: String?): ImageVector = when (rawType) {
    "video" -> Icons.Rounded.Movie
    "audio" -> Icons.Rounded.AudioFile
    "pdf" -> Icons.Rounded.PictureAsPdf
    "file" -> Icons.AutoMirrored.Rounded.InsertDriveFile
    else -> Icons.Rounded.Link
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, end = 8.dp),
    )
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: ImageVector = Icons.AutoMirrored.Rounded.OpenInNew,
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.Item,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Icon(
                trailing,
                contentDescription = stringResource(R.string.action_open_link),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A table as a grid that scrolls sideways when it is wider than the screen. */
@Composable
private fun Table(rows: List<List<String>>, headerRow: Boolean) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val columns = rows.maxOf { it.size }
    Column(
        Modifier
            .padding(top = 6.dp, bottom = 6.dp, end = 8.dp)
            .clip(AppShapes.Item)
            .border(1.dp, outline, AppShapes.Item)
            .horizontalScroll(rememberScrollState())
            .width(IntrinsicSize.Max),
    ) {
        rows.forEachIndexed { r, row ->
            if (r > 0) HorizontalDivider(color = outline)
            val header = headerRow && r == 0
            Row(Modifier.fillMaxWidth().then(if (header) Modifier.background(MaterialTheme.colorScheme.surfaceContainer) else Modifier)) {
                for (c in 0 until columns) {
                    Text(
                        InlineMarkdown.stripped(row.getOrElse(c) { "" }),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (header) FontWeight.SemiBold else null,
                        modifier = Modifier.widthIn(min = 72.dp, max = 240.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TableOfContents(headings: List<Block>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            stringResource(R.string.block_table_of_contents),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        headings.forEach { heading ->
            val indent = when (heading.type) {
                BlockType.HEADING_2 -> 16.dp
                BlockType.HEADING_3 -> 32.dp
                else -> 0.dp
            }
            Text(
                InlineMarkdown.stripped(heading.text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = indent, top = 2.dp, bottom = 2.dp),
            )
        }
    }
}

/** Anything else: its kind and text, to be edited in Notion. */
@Composable
private fun Fallback(block: Block) {
    Surface(
        shape = AppShapes.Item,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 0.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.unsupported_block, block.rawType.orEmpty().replace('_', ' ')),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (block.text.isNotBlank()) {
                    Text(block.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                }
            }
        }
    }
}
