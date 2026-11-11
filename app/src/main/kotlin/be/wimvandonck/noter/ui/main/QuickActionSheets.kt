package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.QuickAction
import be.wimvandonck.noter.logic.QuickActions
import be.wimvandonck.noter.ui.common.NoterSheet
import java.util.UUID

/**
 * What + offers in a database with quick actions (#119): an empty note, or one with a quick
 * action's values. Without quick actions + makes an empty note at once.
 */
@Composable
internal fun NewNoteSheet(
    actions: List<QuickAction>,
    state: NotesUiState,
    onEmptyNote: () -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onDismiss: () -> Unit,
) {
    NoterSheet(onDismiss = onDismiss) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SheetHeader(stringResource(R.string.action_new_note))
            ListItem(
                headlineContent = { Text(stringResource(R.string.quick_action_empty_note)) },
                leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onEmptyNote),
            )
            actions.forEach { action ->
                ListItem(
                    headlineContent = { Text(action.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = quickActionSummary(action, state)?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                    leadingContent = { Icon(Icons.Rounded.Bolt, contentDescription = null) },
                    modifier = Modifier.clickable { onQuickAction(action) },
                )
            }
        }
    }
}

/** ⋮ › Quick actions: the database's quick actions, to change or delete, and New quick action. */
@Composable
internal fun QuickActionsSheet(
    state: NotesUiState,
    onEdit: (QuickAction?) -> Unit,
    onDelete: (QuickAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val actions = state.setup?.quickActions.orEmpty()
    NoterSheet(onDismiss = onDismiss) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.quick_actions),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.quick_actions_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )
            actions.forEach { action ->
                ListItem(
                    headlineContent = { Text(action.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = quickActionSummary(action, state)?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                    leadingContent = { Icon(Icons.Rounded.Bolt, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { onDelete(action) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.quick_action_delete, action.name))
                        }
                    },
                    modifier = Modifier.clickable(onClickLabel = stringResource(R.string.quick_action_edit)) { onEdit(action) },
                )
            }
            Button(onClick = { onEdit(null) }, modifier = Modifier.padding(start = 24.dp, top = 8.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.quick_action_new))
            }
        }
    }
}

/**
 * Making or changing a quick action: its name, and per property the values a new note gets (one,
 * or several for multi-select, people and relations). Cancel or Save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickActionEditor(
    action: QuickAction?,
    state: NotesUiState,
    onSave: (QuickAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(action?.id) { mutableStateOf(action?.name.orEmpty()) }
    var values by rememberSaveable(action?.id) { mutableStateOf(action?.values.orEmpty()) }
    val choices = QuickActions.choices(state.filterOptions)
    NoterSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(if (action == null) R.string.quick_action_new else R.string.quick_action_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.quick_action_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (choices.isEmpty()) {
                Text(
                    stringResource(R.string.quick_action_no_values),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            choices.forEach { group ->
                val property = group.property.name
                Text(property, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.chips.forEach { chip ->
                        FilterChip(
                            selected = chip.key in values[property].orEmpty(),
                            onClick = { values = QuickActions.toggle(values, property, group.property.type, chip.key) },
                            label = { Text(chipLabel(chip, property, state)) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = name.isNotBlank(),
                    onClick = { onSave(QuickAction(action?.id ?: UUID.randomUUID().toString(), name, values)) },
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

/** The values a quick action gives, in the database's property order, or null for none. */
@Composable
private fun quickActionSummary(action: QuickAction, state: NotesUiState): String? {
    val labels = QuickActions.choices(state.filterOptions).flatMap { group ->
        val keys = action.values[group.property.name].orEmpty()
        group.chips.filter { it.key in keys }.map { chipLabel(it, group.property.name, state) }
    }
    return labels.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
