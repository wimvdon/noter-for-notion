package be.wimvandonck.noter.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.data.model.QuickAction

/**
 * The database's quick actions (#119) under a note's properties: one small button each, as a
 * Notion button sits on its page. A tap changes this note's properties.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickActionButtons(actions: List<QuickAction>, onRun: (QuickAction) -> Unit) {
    if (actions.isEmpty()) return
    FlowRow(
        Modifier.padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        actions.forEach { action ->
            OutlinedButton(
                onClick = { onRun(action) },
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(action.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
