package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.RepoEvent
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.notion.InlineMarkdown
import be.wimvandonck.noter.data.notion.PropertyCodec
import be.wimvandonck.noter.logic.NotionDates
import be.wimvandonck.noter.logic.PropertyLayouts
import be.wimvandonck.noter.ui.common.DatabaseIcons
import be.wimvandonck.noter.ui.common.NotionImage
import be.wimvandonck.noter.ui.common.PageIconView
import be.wimvandonck.noter.ui.common.SMALL_PAGE_ICON
import be.wimvandonck.noter.ui.common.OptionChip
import be.wimvandonck.noter.ui.common.SyncButton
import be.wimvandonck.noter.ui.common.errorText
import be.wimvandonck.noter.logic.ChipFilter
import be.wimvandonck.noter.logic.FilterChip as Chip
import be.wimvandonck.noter.logic.Destinations
import be.wimvandonck.noter.logic.NoteTargets
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import be.wimvandonck.noter.ui.theme.EmptyState
import be.wimvandonck.noter.ui.theme.SkeletonBlock

/**
 * The overview, in the style of Google Keep. The navigation bar holds the top-level destinations:
 * Pinned (the pinned notes of everything), Notebooks (the user's notebooks as tabs: peers within
 * one destination) and Search (the notes here, then Notion's pages and databases). A notebook's
 * Filters and Sort are buttons in the top app bar; the ⋮ menu has Edit notebook, Manage
 * notebooks and Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenNote: (String) -> Unit,
    onOpenDatabase: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenViews: () -> Unit,
    onEditNotebook: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = androidx.compose.ui.platform.LocalResources.current
    var choosingDatabase by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    val section = state.section
    // Back from Notebooks or Search goes to Pinned, the start destination, before leaving the app.
    BackHandler(enabled = state.ready && !state.isHome) { viewModel.selectTab(Destinations.PINNED) }

    val sortedByHand = stringResource(R.string.sorted_by_hand)
    val undo = stringResource(R.string.undo)
    LaunchedEffect(viewModel) {
        viewModel.mainEvents.collect { event ->
            when (event) {
                is MainEvent.SortedByHand -> {
                    // Dragging changed the order; the old one is a tap away.
                    val result = snackbar.showSnackbar(sortedByHand, actionLabel = undo, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoSortedByHand(event)
                }
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is RepoEvent.SaveFailed -> resources.getString(
                    R.string.sync_failed,
                    event.noteTitle.ifBlank { resources.getString(R.string.untitled) },
                    errorText(resources, event.detail),
                )
                RepoEvent.KeyRejected -> resources.getString(R.string.sync_key_rejected)
                is RepoEvent.CreateFailed ->
                    if (event.topLevel) resources.getString(R.string.create_top_level_failed) else resources.getString(R.string.create_failed, errorText(resources, event.detail))
            }
            snackbar.showSnackbar(text)
        }
    }

    val notebookActions = NotebookActions(onQuickFilters = viewModel::setQuickFilters, onSort = viewModel::setSort)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val targets = NoteTargets.of(state.views, null)
    val navigationBar: @Composable () -> Unit = {
        NavigationBar {
            NavigationBarItem(
                selected = section == Destinations.Section.PINNED,
                onClick = { viewModel.selectTab(Destinations.PINNED) },
                icon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_home), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
            NavigationBarItem(
                selected = section == Destinations.Section.NOTEBOOKS,
                onClick = { viewModel.selectNotebooks() },
                icon = { Icon(Icons.AutoMirrored.Rounded.LibraryBooks, contentDescription = null) },
                label = { Text(stringResource(R.string.section_views), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
            NavigationBarItem(
                selected = section == Destinations.Section.SEARCH,
                onClick = { viewModel.selectTab(Destinations.SEARCH) },
                enabled = state.hasKey,
                icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.action_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }

    // A notebook adds to itself; Pinned and Search ask where (when there is a choice): a notebook,
    // or a page at the top of the workspace (with a personal access token). On Pinned it is pinned.
    val newButton: @Composable () -> Unit = {
        val inNotebook = state.notebook != null
        val noNotebook = section == Destinations.Section.NOTEBOOKS && state.notebook == null
        if (state.hasKey && !noNotebook && (inNotebook || targets.isNotEmpty() || state.canAddTopLevel)) {
            FloatingActionButton(
                onClick = {
                    when {
                        inNotebook -> viewModel.createNote(onCreated = onOpenNote)
                        targets.size == 1 && !state.canAddTopLevel -> viewModel.createNote(targets.first().databaseId, onOpenNote)
                        else -> choosingDatabase = true
                    }
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_new_note))
            }
        }
    }

    if (section == Destinations.Section.SEARCH) {
        SearchScreen(viewModel = viewModel, onOpenNote = onOpenNote, onOpenDatabase = onOpenDatabase, bottomBar = navigationBar, newButton = newButton)
    } else Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(if (state.isHome) R.string.tab_home else R.string.section_views),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        SyncButton(state.pendingCount, state.offline, syncing = false, onSync = viewModel::refresh, alwaysShown = false)
                        if (state.hasKey) NotebookBarActions(state, notebookActions, onOpenFilters = { filtersOpen = true })
                        OverflowMenu(
                            notebookId = state.notebook?.id,
                            onEditNotebook = onEditNotebook,
                            onManage = onOpenViews,
                            onSettings = onOpenSettings,
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
                // The notebooks are peers within Notebooks: tabs, in the order of Manage notebooks.
                if (section == Destinations.Section.NOTEBOOKS && state.tabs.isNotEmpty() && state.hasKey) {
                    val selected = state.tabs.indexOfFirst { it.key == state.currentTab }.coerceAtLeast(0)
                    PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 16.dp) {
                        state.tabs.forEach { tab ->
                            LeadingIconTab(
                                selected = tab.key == state.currentTab,
                                onClick = { viewModel.selectTab(tab.key) },
                                icon = { Icon(DatabaseIcons.icon(tab.icon), contentDescription = null, Modifier.size(18.dp)) },
                                text = { Text(tab.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }
        },
        bottomBar = navigationBar,
        floatingActionButton = newButton,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.ready -> Unit
                !state.hasKey -> EmptyState(
                    Icons.Rounded.Key,
                    stringResource(R.string.empty_no_key_title),
                    body = stringResource(R.string.empty_no_key),
                ) {
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.empty_no_key_action)) }
                }
                section == Destinations.Section.NOTEBOOKS && state.notebook == null -> EmptyState(
                    Icons.AutoMirrored.Rounded.LibraryBooks,
                    stringResource(R.string.no_notebooks),
                    body = stringResource(R.string.no_notebooks_body),
                ) {
                    Button(onClick = onOpenViews) { Text(stringResource(R.string.add_database)) }
                }
                else -> NotesPane(
                    state = state,
                    previews = previews,
                    onLoadPreviews = viewModel::loadPreviews,
                    onToggleChip = viewModel::toggleChip,
                    onClearChips = viewModel::clearChips,
                    onLoadChipTitles = { viewModel.loadChipTitles(it) },
                    onRefresh = viewModel::refresh,
                    onOpen = onOpenNote,
                    onTogglePin = viewModel::togglePin,
                    onTrash = viewModel::trash,
                    onReordered = viewModel::saveOrder,
                    onClearRow = viewModel::clearRow,
                )
            }
        }
    }

    if (filtersOpen && state.notebook != null) {
        NotebookFiltersSheet(
            state = state,
            actions = notebookActions,
            onClear = viewModel::clearChips,
            onDismiss = { filtersOpen = false },
        )
    }

    if (choosingDatabase) {
        ModalBottomSheet(onDismissRequest = { choosingDatabase = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Notion only lets a personal access token add pages at the top of the workspace.
                if (state.canAddTopLevel) {
                    SheetHeader(stringResource(R.string.new_page))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.new_page_top_level)) },
                        supportingContent = { Text(stringResource(R.string.new_page_top_level_help)) },
                        leadingContent = { Icon(Icons.Rounded.Workspaces, contentDescription = null) },
                        modifier = Modifier.clickable {
                            choosingDatabase = false
                            viewModel.createPage(null, onOpenNote)
                        },
                    )
                }
                if (targets.isNotEmpty()) SheetHeader(stringResource(R.string.new_note_in))
                targets.forEach { target ->
                    ListItem(
                        headlineContent = { Text(target.name.ifBlank { stringResource(R.string.untitled) }) },
                        leadingContent = {
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                if (target.pageIcon != null) {
                                    PageIconView(target.pageIcon, size = 24.dp)
                                } else {
                                    Icon(DatabaseIcons.icon(target.iconKey), contentDescription = null)
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            choosingDatabase = false
                            viewModel.createNote(target.databaseId, onOpenNote)
                        },
                    )
                }
                Spacer(Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

/** The top app bar's ⋮ menu: Edit notebook (in a notebook), Manage notebooks, Settings. */
@Composable
private fun OverflowMenu(notebookId: String?, onEditNotebook: (String) -> Unit, onManage: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (notebookId != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_view)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        open = false
                        onEditNotebook(notebookId)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_views)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = null) },
                onClick = {
                    open = false
                    onManage()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                onClick = {
                    open = false
                    onSettings()
                },
            )
        }
    }
}

@Composable
private fun SheetHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
    )
}

/**
 * The notes of Pinned, a notebook or a database: rows of filter chips on top, then the notes as cards, with pull-to-refresh, loading placeholders and empty
 * states. Moving a note to the trash asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesPane(
    state: MainUiState,
    previews: Map<String, String>,
    onLoadPreviews: (List<NoteSummary>) -> Unit,
    onToggleChip: (property: String?, key: String) -> Unit,
    onClearChips: () -> Unit,
    onLoadChipTitles: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onTrash: (String) -> Unit,
    onReordered: (List<String>) -> Unit,
    onClearRow: ((property: String) -> Unit)? = null,
) {
    var confirmTrash by remember { mutableStateOf<NoteSummary?>(null) }
    LaunchedEffect(state.chipRelationDatabases) { state.chipRelationDatabases.forEach(onLoadChipTitles) }
    // Previews are only downloaded for cards that show the content.
    LaunchedEffect(state.pinned, state.others, state.propertyLayouts) {
        onLoadPreviews((state.pinned + state.others).filter { PropertyLayouts.showsContent(state.propertyLayouts[it.databaseId]) })
    }
    Column(Modifier.fillMaxSize()) {
        if (state.chipGroups.isNotEmpty()) ChipRows(state, onToggle = onToggleChip, onClear = onClearChips, onClearRow = onClearRow)
        PullToRefreshBox(
            isRefreshing = state.refreshing && !state.loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading && state.pinned.isEmpty() && state.others.isEmpty() -> LoadingGrid()
                state.pinned.isEmpty() && state.others.isEmpty() -> when {
                    state.filterCount > 0 || state.chipGroups.any { it.selected.isNotEmpty() } ->
                        EmptyState(Icons.Rounded.FilterAltOff, stringResource(R.string.empty_filtered)) {
                        Button(onClick = onClearChips) { Text(stringResource(R.string.empty_clear_filters)) }
                    }
                    state.isHome -> EmptyState(Icons.Rounded.PushPin, stringResource(R.string.empty_home_title), body = stringResource(R.string.empty_home))
                    state.error != null -> LoadFailed(state.error, onRefresh)
                    else -> EmptyState(
                        Icons.AutoMirrored.Rounded.StickyNote2,
                        stringResource(R.string.empty_database_title),
                        body = stringResource(R.string.empty_database),
                    )
                }
                else -> NoteGrid(
                    state = state,
                    previews = previews,
                    onOpen = onOpen,
                    onTogglePin = onTogglePin,
                    onTrash = { confirmTrash = it },
                    onReordered = onReordered,
                )
            }
        }
    }
    confirmTrash?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmTrash = null },
            title = { Text(stringResource(R.string.trash_title)) },
            text = { Text(stringResource(R.string.trash_body, InlineMarkdown.stripped(note.title).ifBlank { stringResource(R.string.untitled) })) },
            confirmButton = {
                TextButton(onClick = {
                    onTrash(note.id)
                    confirmTrash = null
                }) { Text(stringResource(R.string.trash_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmTrash = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * The chips above the notes. A notebook: one row per quick filter, named after its property
 * ("Clear filters" is in the Filters panel). Pinned (its sources) and a database on its own: All,
 * then the chips.
 */
@Composable
private fun ChipRows(
    state: MainUiState,
    onToggle: (property: String?, key: String) -> Unit,
    onClear: () -> Unit,
    onClearRow: ((property: String) -> Unit)?,
) {
    val anySelected = state.chipGroups.any { it.selected.isNotEmpty() }
    val inNotebook = state.notebook != null
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.chipGroups.forEachIndexed { index, group ->
            val property = group.property
            // A notebook's row with something selected ends in × (that row doesn't filter any more).
            val clearRow = onClearRow?.takeIf { inNotebook && property != null && group.selected.isNotEmpty() }
            Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = if (clearRow != null) 4.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!inNotebook && index == 0) {
                    item(key = "__all") {
                        FilterChip(
                            selected = !anySelected,
                            onClick = onClear,
                            label = { Text(stringResource(R.string.chip_all)) },
                        )
                    }
                }
                if (inNotebook && property != null) {
                    item(key = "__name") {
                        Text(
                            property.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp),
                        )
                    }
                }
                items(group.chips, key = { it.key }) { chip ->
                    val selected = chip.key in group.selected
                    val icon = if (state.isHome) state.databaseIcons[chip.key] else null
                    FilterChip(
                        selected = selected,
                        onClick = { onToggle(property?.name, chip.key) },
                        label = { ChipText(chipLabel(chip, property?.name, state), chip.count) },
                        leadingIcon = if (state.isHome) {
                            { Icon(DatabaseIcons.icon(icon), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else if (chip.isCheckbox || chip.isDate || chip.label.isEmpty()) {
                            null
                        } else {
                            { OptionDot(chip.color) }
                        },
                    )
                }
            }
            if (clearRow != null && property != null) {
                IconButton(onClick = { clearRow(property.name) }, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_row, property.name))
                }
            }
            }
        }
    }
}

/**
 * A chip's words: fixed ones for checkbox and date filters (named after [property]), a linked
 * page's title, else its own label.
 */
@Composable
internal fun chipLabel(chip: Chip, property: String?, state: MainUiState): String {
    val name = property.orEmpty()
    return when (chip.key) {
        ChipFilter.CHECKED -> stringResource(R.string.chip_checked, name)
        ChipFilter.UNCHECKED -> stringResource(R.string.chip_unchecked, name)
        ChipFilter.OVERDUE -> stringResource(R.string.chip_overdue)
        ChipFilter.TODAY -> stringResource(R.string.chip_today)
        ChipFilter.NEXT_7_DAYS -> stringResource(R.string.chip_next_7_days)
        ChipFilter.LATER -> stringResource(R.string.chip_later)
        ChipFilter.NO_DATE -> stringResource(R.string.chip_no_date)
        else -> when {
            chip.label.isNotEmpty() -> chip.label
            // Pinned's chips are the sources; the one without a name is the pages on their own.
            state.isHome -> stringResource(R.string.source_pages)
            else -> state.chipTitles[chip.key]?.ifBlank { null } ?: stringResource(R.string.untitled)
        }
    }
}

@Composable
private fun OptionDot(color: String) {
    val colors = be.wimvandonck.noter.ui.common.optionColors(color)
    androidx.compose.foundation.Canvas(Modifier.size(10.dp)) { drawCircle(colors.container) }
}

/** A grid item: a section header or a note. */
private sealed interface GridItem {
    val key: String

    data class Header(override val key: String, val pinned: Boolean) : GridItem

    data class Note(val note: NoteSummary) : GridItem {
        override val key: String get() = note.id
    }
}

/**
 * The notes as cards in a staggered grid (two columns on a phone, more on wider screens).
 * Long-press and drag moves a note within its section; a long press without moving opens the
 * note's menu. The order is kept on this phone.
 *
 * While dragging, the card follows the finger exactly: its place on screen is worked out from
 * where the finger is, not from where the grid put it, so it doesn't jump when cards of
 * different heights change places. Near the top or bottom edge the grid scrolls.
 */
@Composable
internal fun NoteGrid(
    state: MainUiState,
    previews: Map<String, String>,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onTrash: (NoteSummary) -> Unit,
    onReordered: (List<String>) -> Unit,
) {
    // Working copies, so dragging moves cards at once; they follow the state when not dragging.
    val pinned = remember { mutableStateListOf<NoteSummary>() }
    val others = remember { mutableStateListOf<NoteSummary>() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    // The order just dropped, until the state has it too (so the cards don't flash back).
    val dropped = remember { DroppedOrder() }
    if (draggingKey == null) {
        val ids = state.pinned.map { it.id } + state.others.map { it.id }
        val waiting = dropped.ids
        if (waiting == null || waiting == ids || waiting.toSet() != ids.toSet()) {
            dropped.ids = null
            if (pinned.toList() != state.pinned) { pinned.clear(); pinned.addAll(state.pinned) }
            if (others.toList() != state.others) { others.clear(); others.addAll(state.others) }
        }
    }
    val showHeaders = pinned.isNotEmpty() && others.isNotEmpty()
    val items = buildList {
        if (showHeaders) add(GridItem.Header("__pinned", pinned = true))
        pinned.forEach { add(GridItem.Note(it)) }
        if (showHeaders) add(GridItem.Header("__others", pinned = false))
        others.forEach { add(GridItem.Note(it)) }
    }
    val gridState = rememberLazyStaggeredGridState()
    // The finger, in the grid's item coordinates, and where it held the card.
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    // The card just swapped with: not again until the finger has left it (no back and forth).
    var swappedWith by remember { mutableStateOf<String?>(null) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val edge = with(density) { AUTO_SCROLL_EDGE.toPx() }
    val maxStep = with(density) { AUTO_SCROLL_STEP.toPx() }
    val currentState by rememberUpdatedState(state)

    fun groupOf(key: String): MutableList<NoteSummary>? = when {
        pinned.any { it.id == key } -> pinned
        others.any { it.id == key } -> others
        else -> null
    }

    fun changed(): Boolean = pinned.map { it.id } != currentState.pinned.map { it.id } || others.map { it.id } != currentState.others.map { it.id }

    fun save(key: String) {
        val group = groupOf(key) ?: return
        dropped.ids = pinned.map { it.id } + others.map { it.id }
        onReordered(group.map { it.id })
    }

    fun move(key: String, by: Int) {
        val group = groupOf(key) ?: return
        val from = group.indexOfFirst { it.id == key }
        val to = from + by
        if (to !in group.indices) return
        group.add(to, group.removeAt(from))
        save(key)
    }

    /** Moves the dragged card to the place of the card under the finger (same section only). */
    fun reorderUnderFinger() {
        val key = draggingKey ?: return
        val visible = gridState.layoutInfo.visibleItemsInfo
        val under = visible.firstOrNull { it.key != key && it.contains(finger) }
        if (swappedWith != null && visible.none { it.key == swappedWith && it.contains(finger) }) swappedWith = null
        val targetKey = under?.key as? String ?: return
        if (targetKey == swappedWith) return
        val group = groupOf(key) ?: return
        if (groupOf(targetKey) !== group) return
        val from = group.indexOfFirst { it.id == key }
        val to = group.indexOfFirst { it.id == targetKey }
        group.add(to, group.removeAt(from))
        swappedWith = targetKey
    }

    // Near an edge while dragging: scroll, faster closer to the edge.
    LaunchedEffect(draggingKey) {
        if (draggingKey == null) return@LaunchedEffect
        while (true) {
            val info = gridState.layoutInfo
            val step = when {
                finger.y < info.viewportStartOffset + edge -> -maxStep * (1 - (finger.y - info.viewportStartOffset) / edge).coerceIn(0f, 1f)
                finger.y > info.viewportEndOffset - edge -> maxStep * (1 - (info.viewportEndOffset - finger.y) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step != 0f && gridState.scrollBy(step) != 0f) reorderUnderFinger()
            withFrameNanos { }
        }
    }
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items,
            key = { it.key },
            span = { if (it is GridItem.Header) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane },
        ) { item ->
            when (item) {
                is GridItem.Header -> Text(
                    stringResource(if (item.pinned) R.string.section_pinned else R.string.section_others),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .animateItem()
                        .padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        .semantics { heading() },
                )
                is GridItem.Note -> {
                    val key = item.key
                    val isDragging = draggingKey == key
                    val pinnedNote = key in state.pinnedIds
                    NoteCard(
                        note = item.note,
                        preview = previews[key],
                        showDatabaseIcon = state.isHome,
                        databaseIcon = state.databaseIcons[item.note.databaseId],
                        chipProperty = state.cardProperty,
                        layout = state.propertyLayouts[item.note.databaseId],
                        lifted = isDragging,
                        // The card's own click also ends a long press; that one opened the menu or moved the card.
                        onOpen = { if (draggingKey != key) onOpen(key) },
                        menu = {
                            DropdownMenu(expanded = menuKey == key, onDismissRequest = { menuKey = null }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (pinnedNote) R.string.action_unpin else R.string.action_pin)) },
                                    leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                                    onClick = { menuKey = null; onTogglePin(key) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_trash)) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                    onClick = { menuKey = null; onTrash(item.note) },
                                )
                            }
                        },
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            // Outside the card's own layer, so moving the card doesn't move the touch.
                            .pointerInput(key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { touch ->
                                        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                                            ?: return@detectDragGesturesAfterLongPress
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        grab = touch
                                        finger = Offset(info.offset.x.toFloat(), info.offset.y.toFloat()) + touch
                                        dragDistance = 0f
                                        swappedWith = null
                                        draggingKey = key
                                    },
                                    onDragEnd = {
                                        if (draggingKey == key) {
                                            if (dragDistance < touchSlop) {
                                                // A long press without moving: the note's menu.
                                                menuKey = key
                                            } else if (changed()) {
                                                save(key)
                                            }
                                        }
                                        draggingKey = null
                                    },
                                    onDragCancel = { draggingKey = null },
                                    onDrag = { change, amount ->
                                        if (draggingKey != key) return@detectDragGesturesAfterLongPress
                                        change.consume()
                                        dragDistance += amount.getDistance()
                                        finger += amount
                                        reorderUnderFinger()
                                    },
                                )
                            }
                            .graphicsLayer {
                                if (isDragging) {
                                    // Under the finger, wherever the grid put the card.
                                    gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { info ->
                                        translationX = finger.x - grab.x - info.offset.x
                                        translationY = finger.y - grab.y - info.offset.y
                                    }
                                    shadowElevation = 12f
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                }
                            }
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(moveUp) { move(key, -1); true },
                                    CustomAccessibilityAction(moveDown) { move(key, 1); true },
                                )
                            },
                    )
                }
            }
        }
    }
}

/** The order of the notes just dropped, while the saved order is on its way (not state: no redraw). */
private class DroppedOrder {
    var ids: List<String>? = null
}

/** True when [point] (in the grid's item coordinates) is on this item. */
private fun LazyStaggeredGridItemInfo.contains(point: Offset): Boolean =
    point.x >= offset.x && point.x < offset.x + size.width && point.y >= offset.y && point.y < offset.y + size.height

/** Height of a header image on a card. */
private val CARD_COVER_HEIGHT = 96.dp

/** How close to the top or bottom a dragged card makes the grid scroll, and its fastest step per frame. */
private val AUTO_SCROLL_EDGE = 64.dp
private val AUTO_SCROLL_STEP = 12.dp

/** A note as a card: its title, the start of its content and its labels, like a Keep note. */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(
    note: NoteSummary,
    preview: String?,
    showDatabaseIcon: Boolean,
    databaseIcon: String?,
    chipProperty: String?,
    layout: PropertyLayout?,
    lifted: Boolean,
    onOpen: () -> Unit,
    menu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = InlineMarkdown.stripped(note.title)
    OutlinedCard(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (lifted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box {
            Column {
                if (PropertyLayouts.showsCover(layout, note.cover)) {
                    NotionImage(
                        url = note.cover!!,
                        expiry = note.coverExpiry,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(CARD_COVER_HEIGHT),
                    )
                }
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (title.isNotBlank() || note.icon != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            note.icon?.let { PageIconView(it, size = SMALL_PAGE_ICON) }
                            if (title.isNotBlank()) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    val showContent = PropertyLayouts.showsContent(layout)
                    when {
                        showContent && preview != null -> Text(
                            preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis,
                        )
                        title.isBlank() -> Text(
                            stringResource(R.string.untitled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    NoteLabels(note, chipProperty, layout, showDatabaseIcon, databaseIcon)
                }
            }
            menu()
        }
    }
}

/**
 * The card's footer: the properties chosen for cards (see PropertyLayouts), else the filter
 * property's options; the database (on Pinned); "not synced yet".
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NoteLabels(
    note: NoteSummary,
    chipProperty: String?,
    layout: PropertyLayout?,
    showDatabaseIcon: Boolean,
    databaseIcon: String?,
) {
    val chosen = remember(note.properties, layout) { PropertyLayouts.cardProperties(note.properties, layout).filterNot { it.isEmpty } }
    val shown = chosen.ifEmpty { listOfNotNull(chipProperty?.let { note.property(it) }) }
    if (shown.all { it.isEmpty } && !note.isLocal && !showDatabaseIcon) return
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDatabaseIcon) {
            Icon(
                DatabaseIcons.icon(databaseIcon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        shown.forEach { prop ->
            when (val value = prop.value) {
                is PropertyValue.Select -> value.option?.let { OptionChip(it, small = true) }
                is PropertyValue.MultiSelect -> value.options.take(3).forEach { OptionChip(it, small = true) }
                else -> {
                    val text = when (value) {
                        is PropertyValue.Date -> NotionDates.displayRange(value.start, value.end, locale)
                        is PropertyValue.Checkbox -> if (value.checked) "✓ ${prop.name}" else ""
                        else -> PropertyCodec.displayText(value)
                    }
                    if (text.isNotBlank()) {
                        Text(
                            text,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (note.isLocal) {
            Icon(
                Icons.Rounded.CloudSync,
                contentDescription = stringResource(R.string.status_not_synced),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun LoadingGrid() {
    val loading = stringResource(R.string.loading)
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { this.contentDescription = loading },
        contentPadding = PaddingValues(12.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(SKELETON_HEIGHTS) { height -> SkeletonBlock(height = height.dp) }
    }
}

private val SKELETON_HEIGHTS = listOf(120, 88, 96, 140, 72, 110, 88, 120)

/** The empty state of a list that couldn't load: the reason and a Retry button. */
@Composable
internal fun LoadFailed(reason: String, onRetry: () -> Unit) {
    EmptyState(Icons.Rounded.CloudOff, stringResource(R.string.empty_error_title), body = errorText(reason)) {
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
