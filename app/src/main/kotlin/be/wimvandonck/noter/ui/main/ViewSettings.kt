package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.CardLayout
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.logic.PropertyLayouts
import be.wimvandonck.noter.ui.common.NoterSheet

/**
 * A database's view settings (the Tune button in its app bar): what the cards of every view show,
 * and a link to edit the views in Notion (Noter never changes them; the tabs follow Notion's order).
 */
@Composable
internal fun ViewSettingsSheet(
    cards: CardLayout,
    properties: List<PropertySchema>,
    onCards: ((CardLayout) -> CardLayout) -> Unit,
    onEditInNotion: () -> Unit,
    onDismiss: () -> Unit,
) {
    NoterSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.view_settings), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onEditInNotion) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.edit_views_in_notion))
            }
            HorizontalDivider()
            Text(stringResource(R.string.cards_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            SwitchRow(stringResource(R.string.card_cover), cards.cover) { on -> onCards { it.copy(cover = on) } }
            SwitchRow(stringResource(R.string.card_content), cards.content) { on -> onCards { it.copy(content = on) } }
            val shown = properties.filter { it.type != "title" }
            if (shown.isNotEmpty()) {
                Text(stringResource(R.string.cards_properties, PropertyLayouts.MAX_ON_CARD), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { property ->
                        val selected = property.name in cards.properties
                        FilterChip(
                            selected = selected,
                            enabled = selected || cards.properties.size < PropertyLayouts.MAX_ON_CARD,
                            onClick = { onCards { PropertyLayouts.toggleOnCard(it, property.name) } },
                            label = { Text(property.name) },
                        )
                    }
                }
            }
        }
    }
}

/** A switch row; the whole row toggles. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
