package be.wimvandonck.noter.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import be.wimvandonck.noter.R
import be.wimvandonck.noter.logic.ReadingLine
import be.wimvandonck.noter.logic.ReadingStyle
import be.wimvandonck.noter.ui.theme.AppShapes
import be.wimvandonck.noter.ui.theme.readableWidth

/**
 * The note as plain text in one selection area (⋮ › Select text), so a selection can run across
 * several lines: touch and hold, drag the handles, then Copy or Share from the system's menu.
 * Nothing can be changed here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectTextView(title: String, lines: List<ReadingLine>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.action_select_text)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                    SelectionContainer(Modifier.readableWidth()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (title.isNotBlank()) {
                                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                            }
                            lines.forEach { ReadingRow(it) }
                            Box(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingRow(line: ReadingLine) {
    val indent = Modifier.padding(start = 24.dp * line.depth)
    when (line.style) {
        ReadingStyle.DIVIDER -> HorizontalDivider(indent.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ReadingStyle.QUOTE -> Row(indent.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
            Text(line.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
        }
        ReadingStyle.CODE -> Text(
            line.text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShapes.Item)
                .padding(12.dp),
        )
        ReadingStyle.HEADING_1 -> Text(line.text, indent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        ReadingStyle.HEADING_2 -> Text(line.text, indent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        ReadingStyle.HEADING_3 -> Text(line.text, indent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        ReadingStyle.BODY -> Text(line.text, indent, style = MaterialTheme.typography.bodyLarge)
    }
}
