package be.wimvandonck.noter.ui.main

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.wimvandonck.noter.R
import be.wimvandonck.noter.ui.common.PageIconView
import be.wimvandonck.noter.ui.common.SMALL_PAGE_ICON
import be.wimvandonck.noter.ui.common.SyncButton
import be.wimvandonck.noter.ui.common.errorText
import be.wimvandonck.noter.ui.note.PageIconSheet
import be.wimvandonck.noter.ui.theme.AppShapes
import be.wimvandonck.noter.ui.theme.EmptyState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.material.icons.rounded.Share
import be.wimvandonck.noter.ui.common.shareLink

/**
 * A database, opened from Home, Search or a link in a note: its notes with rows of filter chips.
 * The top app bar has Filters, Sort, Cards (what the cards show) and a ⋮ menu (pin on Home, open
 * in Notion, share). Everything changed here is kept for the database at once; there is nothing
 * to save. A database of several data sources has a tab per data source, each with its own
 * filters, sort and cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(
    databaseId: String,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val viewModel: DatabaseViewModel = viewModel(key = "database-$databaseId", factory = DatabaseViewModel.factory(databaseId))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val untitled = stringResource(R.string.untitled)
    val content = state.content
    var filtersOpen by remember { mutableStateOf(false) }
    var cardsOpen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var choosingIcon by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    val context = LocalContext.current
    val actions = SetupActions(onArrangeFilters = viewModel::arrangeFilters, onSort = viewModel::setSort)

    val sortedByHand = stringResource(R.string.sorted_by_hand)
    val undo = stringResource(R.string.undo)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DatabaseEvent.SortedByHand -> {
                    // Dragging changed the order; the old one is a tap away.
                    val result = snackbar.showSnackbar(sortedByHand, actionLabel = undo, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoSortedByHand(event)
                }
                is DatabaseEvent.EditFailed ->
                    snackbar.showSnackbar(resources.getString(R.string.database_edit_failed, errorText(resources, event.error)))
            }
        }
    }

    // The header (app bar, tabs and rows of chips) scrolls away with the notes and comes back on
    // the first scroll up. Another data source brings it back (a short list can't scroll it into view).
    val header = rememberQuickReturnHeaderState()
    LaunchedEffect(state.sourceId) { header.show() }

    Scaffold(
        modifier = Modifier.nestedScroll(header.connection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // The status bar keeps its place; everything under it scrolls away.
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets),
            ) {
                QuickReturnHeader(header) {
                    Column {
                        TopAppBar(
                            title = {
                                // As on a page: tap the icon to change it, the name to rename (also in ⋮).
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    state.icon?.let {
                                        PageIconView(
                                            it,
                                            size = SMALL_PAGE_ICON,
                                            modifier = Modifier
                                                .clip(AppShapes.Item)
                                                .clickable(onClickLabel = stringResource(R.string.change_icon), role = Role.Button) { choosingIcon = true },
                                        )
                                    }
                                    Text(
                                        state.title.ifBlank { untitled },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.action_rename)) { renaming = true },
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            },
                            actions = {
                                SyncButton(content.pendingCount, content.offline, syncing = false, onSync = { viewModel.refresh() }, alwaysShown = false)
                                if (content.hasKey) SetupBarActions(content, actions, onOpenFilters = { filtersOpen = true })
                                // What the cards show: the same icon as the properties button of a page.
                                IconButton(onClick = { cardsOpen = true }) {
                                    Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.cards_title))
                                }
                                Box {
                                    IconButton(onClick = { menu = true }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                                    }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(if (state.pinned) R.string.action_unpin else R.string.action_pin)) },
                                            leadingIcon = { Icon(if (state.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, contentDescription = null) },
                                            onClick = {
                                                menu = false
                                                viewModel.togglePinDatabase()
                                            },
                                        )
                                        // Name and icon here, so the screen itself stays the notes.
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_rename)) },
                                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                            onClick = {
                                                menu = false
                                                renaming = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(if (state.icon != null) R.string.change_icon else R.string.add_icon)) },
                                            leadingIcon = { Icon(Icons.Rounded.AddReaction, contentDescription = null) },
                                            onClick = {
                                                menu = false
                                                choosingIcon = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_open_in_notion)) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                                            onClick = {
                                                menu = false
                                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, state.notionUrl.toUri())) }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_share)) },
                                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                            onClick = {
                                                menu = false
                                                shareLink(context, state.title.ifBlank { untitled }, state.notionUrl)
                                            },
                                        )
                                    }
                                }
                            },
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        )
                        // A database with several data sources: one tab each (they are peers of one database).
                        state.sources?.let { sources ->
                            PrimaryScrollableTabRow(selectedTabIndex = sources.indexOfFirst { it.id == state.sourceId }.coerceAtLeast(0), edgePadding = 16.dp) {
                                sources.forEach { source ->
                                    Tab(
                                        selected = source.id == state.sourceId,
                                        onClick = { viewModel.selectSource(source.id) },
                                        text = { Text(source.title.ifBlank { untitled }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                        }
                        if (content.hasKey && content.chipGroups.isNotEmpty() && !content.sourceMissing) {
                            ChipRows(content, onToggle = viewModel::toggleChip, onClearRow = viewModel::clearRow)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (content.hasKey && !content.sourceMissing) {
                FloatingActionButton(onClick = { viewModel.createNote(onOpenNote) }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_new_note))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (content.sourceMissing) {
                // Notion can't find the data source (deleted, in the trash, or no access any more).
                EmptyState(
                    Icons.Rounded.SearchOff,
                    stringResource(R.string.source_missing_title),
                    body = stringResource(R.string.source_missing_body),
                ) {
                    if (state.pinned) Button(onClick = viewModel::togglePinDatabase) { Text(stringResource(R.string.action_unpin)) }
                    OutlinedButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.retry)) }
                }
            } else {
                NotesPane(
                    state = content,
                    previews = previews,
                    onLoadPreviews = viewModel::loadPreviews,
                    onToggleChip = viewModel::toggleChip,
                    onClearChips = viewModel::clearChips,
                    onLoadChipTitles = { viewModel.loadChipTitles(it) },
                    onRefresh = { viewModel.refresh() },
                    onOpen = onOpenNote,
                    onTogglePin = { viewModel.togglePin(it) },
                    onReordered = viewModel::saveOrder,
                )
            }
        }
    }

    if (filtersOpen) {
        FiltersSheet(
            state = content,
            actions = actions,
            onToggle = viewModel::toggleChip,
            onClear = viewModel::clearChips,
            onDismiss = { filtersOpen = false },
        )
    }
    if (renaming) {
        RenameDialog(
            current = state.title,
            onRename = { renaming = false; viewModel.rename(it) },
            onDismiss = { renaming = false },
        )
    }
    if (choosingIcon) {
        PageIconSheet(
            hasIcon = state.icon != null,
            onPick = { emoji -> choosingIcon = false; viewModel.setIcon(emoji) },
            onRemove = { choosingIcon = false; viewModel.setIcon(null) },
            onDismiss = { choosingIcon = false },
        )
    }
    if (cardsOpen) {
        CardsSheet(
            cards = state.cards,
            properties = state.properties,
            onCards = viewModel::editCards,
            onDismiss = { cardsOpen = false },
        )
    }
}

/** The database's name, to change; Rename is off while it is empty or unchanged. */
@Composable
private fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_database)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.new_database_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank() && name.trim() != current) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
