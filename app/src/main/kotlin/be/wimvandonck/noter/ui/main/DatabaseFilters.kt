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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.CardLayout
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.logic.PropertyLayouts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import be.wimvandonck.noter.data.model.NoteSort
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.SortKind
import be.wimvandonck.noter.logic.DatabaseSetups
import be.wimvandonck.noter.ui.note.PropertyArranger

/** What the Filters panel and the Sort menu change in the database shown. */
class SetupActions(
    /** The properties with a row of chips above the notes, in this order. */
    val onQuickFilters: (List<String>) -> Unit,
    val onSort: (NoteSort) -> Unit,
)

/**
 * The database's buttons in the top app bar: Filters (opens the panel; a dot while filters are
 * on) and Sort (a menu).
 */
@Composable
internal fun SetupBarActions(state: NotesUiState, actions: SetupActions, onOpenFilters: () -> Unit) {
    val setup = state.setup ?: return
    if (state.filterOptions.isNotEmpty()) {
        val description = if (state.filterCount > 0) stringResource(R.string.filters_count, state.filterCount) else stringResource(R.string.filters)
        IconButton(onClick = onOpenFilters) {
            BadgedBox(badge = { if (state.filterCount > 0) Badge() }) {
                Icon(Icons.Rounded.FilterList, contentDescription = description)
            }
        }
    }
    var menu by remember { mutableStateOf(false) }
    val sort = setup.currentSort
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.sort_by, sortLabel(sort)))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val choices = buildList {
                add(NoteSort(SortKind.CUSTOM))
                add(NoteSort(SortKind.LAST_EDITED))
                add(NoteSort(SortKind.CREATED))
                add(NoteSort(SortKind.TITLE))
                state.filterOptions.map { it.property }.filter { it.type == "date" }.forEach {
                    add(NoteSort(SortKind.DATE, it.name, descending = false))
                }
            }
            choices.forEach { choice ->
                val current = choice.kind == sort.kind && choice.property == sort.property
                val directional = choice.kind != SortKind.CUSTOM
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
private fun sortLabel(sort: NoteSort): String = when (sort.kind) {
    SortKind.CUSTOM, SortKind.NOTION -> stringResource(R.string.sort_custom)
    SortKind.LAST_EDITED -> stringResource(R.string.sort_last_edited)
    SortKind.CREATED -> stringResource(R.string.sort_created)
    SortKind.TITLE -> stringResource(R.string.sort_title)
    SortKind.DATE -> sort.property.orEmpty()
}

/**
 * The database's Filters panel: which properties have a row of chips above the notes, arranged
 * like a note's properties (drag by the handle, past the "Hidden" line, or the eye), each with its
 * values to filter by below it, and Clear filters. Everything is kept on this phone.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FiltersSheet(
    state: NotesUiState,
    actions: SetupActions,
    onToggle: (property: String, key: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val setup = state.setup ?: return
    val candidates = state.filterOptions.map { it.property.name }
    val (rows, others) = DatabaseSetups.chipRows(candidates, setup.quickFilters)
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
            // One list: each property with a handle, its name and an eye (its row of chips above the
            // notes), and below it the values to filter by (also while its row is hidden).
            val groups = state.filterOptions.associateBy { it.property.name }
            PropertyArranger(
                names = candidates,
                layout = PropertyLayout(order = rows + others, hidden = others),
                onArrange = { shown, _ -> actions.onQuickFilters(shown) },
                onDone = null,
                shownTitle = stringResource(R.string.chip_rows_shown),
                below = { name ->
                    val group = groups[name] ?: return@PropertyArranger
                    if (group.chips.isEmpty()) {
                        Text(
                            stringResource(R.string.filter_no_values),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp, bottom = 12.dp),
                        )
                    } else {
                        FlowRow(
                            Modifier.padding(start = 32.dp, end = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            group.chips.forEach { chip ->
                                FilterChip(
                                    selected = chip.key in group.selected,
                                    onClick = { onToggle(name, chip.key) },
                                    label = { ChipText(chipLabel(chip, name, state), chip.count) },
                                )
                            }
                        }
                    }
                },
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

/**
 * What the database's cards show: the header image, the start of the content, and up to three
 * properties. Kept on this phone at once.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CardsSheet(
    cards: CardLayout,
    properties: List<PropertySchema>,
    onCards: ((CardLayout) -> CardLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.cards_title), style = MaterialTheme.typography.titleLarge)
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
