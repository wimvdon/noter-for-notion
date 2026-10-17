package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.NotebookSort
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.SortKind
import be.wimvandonck.noter.logic.Notebooks
import be.wimvandonck.noter.ui.note.PropertyArranger

/** What the Filters panel and the Sort menu change in the notebook shown. */
class NotebookActions(
    /** The properties with a row of chips above the notes, in this order. */
    val onQuickFilters: (List<String>) -> Unit,
    val onSort: (NotebookSort) -> Unit,
)

/**
 * The notebook's buttons in the top app bar: Filters (opens the panel; a dot while filters are
 * on) and Sort (a menu). A page's notebook has nothing to filter by (subpages only have a title).
 */
@Composable
internal fun NotebookBarActions(state: MainUiState, actions: NotebookActions, onOpenFilters: () -> Unit) {
    val notebook = state.notebook ?: return
    if (state.filterOptions.isNotEmpty()) {
        val description = if (state.filterCount > 0) stringResource(R.string.filters_count, state.filterCount) else stringResource(R.string.filters)
        IconButton(onClick = onOpenFilters) {
            BadgedBox(badge = { if (state.filterCount > 0) Badge() }) {
                Icon(Icons.Rounded.FilterList, contentDescription = description)
            }
        }
    }
    var menu by remember { mutableStateOf(false) }
    val sort = notebook.currentSort
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.sort_by, sortLabel(sort)))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val choices = buildList {
                if (notebook.hasNotionOrder) add(NotebookSort(SortKind.NOTION))
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
                val directional = choice.kind != SortKind.NOTION && choice.kind != SortKind.CUSTOM
                DropdownMenuItem(
                    text = { Text(sortLabel(choice)) },
                    leadingIcon = { if (current) Icon(Icons.Rounded.Check, contentDescription = null) else Spacer(Modifier.size(24.dp)) },
                    // The current sort's direction; tapping it again turns it around.
                    trailingIcon = if (current && directional) {
                        {
                            Icon(
                                if (sort.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(if (sort.descending) R.string.sort_descending else R.string.sort_ascending),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        when {
                            current && directional -> actions.onSort(sort.copy(descending = !sort.descending))
                            current -> menu = false
                            else -> {
                                menu = false
                                actions.onSort(choice)
                            }
                        }
                    },
                )
            }
        }
    }
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
 * The notebook's Filters panel: which properties have a row of chips above the notes, arranged
 * like a note's properties (drag by the handle, past the "Hidden" line, or the eye), and Clear
 * filters. Everything is kept on this phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotebookFiltersSheet(
    state: MainUiState,
    actions: NotebookActions,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val notebook = state.notebook ?: return
    val candidates = state.filterOptions.mapNotNull { it.property?.name }
    val (rows, others) = Notebooks.chipRows(candidates, notebook.quickFilters)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 12.dp, bottom = 24.dp),
        ) {
            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (state.filterCount > 0) TextButton(onClick = onClear) { Text(stringResource(R.string.empty_clear_filters)) }
            }
            PropertyArranger(
                names = candidates,
                layout = PropertyLayout(order = rows + others, hidden = others),
                onArrange = { shown, _ -> actions.onQuickFilters(shown) },
                onDone = null,
                shownTitle = stringResource(R.string.chip_rows_shown),
            )
        }
    }
}

/**
 * A chip's words with how many notes have it, the count set apart in a quieter color (so the
 * option "5" with one note doesn't read as "5 1").
 */
@Composable
internal fun ChipText(label: String, count: Int) {
    if (count <= 0) {
        Text(label)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                .padding(horizontal = 6.dp),
        )
    }
}
