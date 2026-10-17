package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.NotebookSort
import be.wimvandonck.noter.data.model.SortKind

/** What the Filters panel and the Sort menu change in the notebook shown. */
class NotebookActions(
    val onQuickFilter: (property: String, shown: Boolean) -> Unit,
    val onFollowView: (Boolean) -> Unit,
    val onSort: (NotebookSort) -> Unit,
    val onResetToView: () -> Unit,
)

/** The first two chips of a notebook: Filters (opens the panel) and the sort (opens its menu). */
@Composable
internal fun NotebookChips(state: MainUiState, actions: NotebookActions, onOpenFilters: () -> Unit) {
    val notebook = state.notebook ?: return
    FilterChip(
        selected = state.filterCount > 0,
        onClick = onOpenFilters,
        label = {
            Text(if (state.filterCount > 0) stringResource(R.string.filters_count, state.filterCount) else stringResource(R.string.filters))
        },
        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
    )
    Spacer(Modifier.width(8.dp))
    var menu by remember { mutableStateOf(false) }
    val sort = notebook.currentSort
    androidx.compose.foundation.layout.Box {
        FilterChip(
            selected = false,
            onClick = { menu = true },
            label = { Text(sortLabel(sort)) },
            leadingIcon = { Icon(Icons.Rounded.SwapVert, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = stringResource(R.string.sort), Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val choices = buildList {
                if (notebook.viewId != null && notebook.followView) add(NotebookSort(SortKind.NOTION))
                add(NotebookSort(SortKind.CUSTOM))
                add(NotebookSort(SortKind.LAST_EDITED))
                add(NotebookSort(SortKind.CREATED))
                add(NotebookSort(SortKind.TITLE))
                state.filterOptions.mapNotNull { it.property }.filter { it.type == "date" }.forEach {
                    add(NotebookSort(SortKind.DATE, it.name, descending = false))
                }
            }
            choices.forEach { choice ->
                val current = choice.kind == sort.kind && choice.property == sort.property
                DropdownMenuItem(
                    text = { Text(sortLabel(choice)) },
                    leadingIcon = { if (current) Icon(Icons.Rounded.Check, contentDescription = null) else Spacer(Modifier.size(24.dp)) },
                    onClick = {
                        menu = false
                        if (!current) actions.onSort(choice)
                    },
                )
            }
            // Newest first or oldest first, A–Z or Z–A: not for Notion's or the custom order.
            if (sort.kind != SortKind.NOTION && sort.kind != SortKind.CUSTOM) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_reverse)) },
                    leadingIcon = { Icon(Icons.Rounded.SwapVert, contentDescription = null) },
                    onClick = {
                        menu = false
                        actions.onSort(sort.copy(descending = !sort.descending))
                    },
                )
            }
        }
    }
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun sortLabel(sort: NotebookSort): String = when (sort.kind) {
    SortKind.NOTION -> stringResource(R.string.sort_notion)
    SortKind.CUSTOM -> stringResource(R.string.sort_custom)
    SortKind.LAST_EDITED -> stringResource(R.string.sort_last_edited)
    SortKind.CREATED -> stringResource(R.string.sort_created)
    SortKind.TITLE -> stringResource(R.string.sort_title)
    SortKind.DATE -> sort.property.orEmpty()
}

/**
 * The notebook's Filters panel: follow the Notion view or not, every property it can filter by
 * with its values (and whether they show as chips above the notes), clear, and back to Notion's
 * view. Everything is kept on this phone; Notion's view isn't changed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NotebookFiltersSheet(
    state: MainUiState,
    actions: NotebookActions,
    onToggle: (property: String, key: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val notebook = state.notebook ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (state.filterCount > 0) TextButton(onClick = onClear) { Text(stringResource(R.string.empty_clear_filters)) }
            }
            if (notebook.viewId != null) {
                SwitchRow(
                    title = stringResource(R.string.follow_view, notebook.viewName.orEmpty()),
                    summary = stringResource(R.string.follow_view_help),
                    checked = notebook.followView,
                    onChange = actions.onFollowView,
                )
                if (state.viewMissing) {
                    Text(
                        stringResource(R.string.view_missing, notebook.viewName.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            state.filterOptions.forEach { group ->
                val property = group.property ?: return@forEach
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        property.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    val shown = property.name in notebook.quickFilters
                    Row(
                        Modifier.toggleable(value = shown, role = Role.Switch) { actions.onQuickFilter(property.name, it) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.show_as_chips),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = shown, onCheckedChange = null)
                    }
                }
                if (group.chips.isEmpty()) {
                    Text(
                        stringResource(R.string.filter_no_values),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.chips.forEach { chip ->
                            FilterChip(
                                selected = chip.key in group.selected,
                                onClick = { onToggle(property.name, chip.key) },
                                label = { Text(chipLabel(chip, property.name, state)) },
                            )
                        }
                    }
                }
            }
            if (notebook.viewId != null) {
                Spacer(Modifier.width(0.dp))
                OutlinedButton(onClick = actions.onResetToView, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reset_to_view))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
