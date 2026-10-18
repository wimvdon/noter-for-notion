package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
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
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.PinnedDatabase
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.data.notion.InlineMarkdown
import be.wimvandonck.noter.data.notion.PropertyCodec
import be.wimvandonck.noter.logic.ChipFilter
import be.wimvandonck.noter.logic.FilterChip as Chip
import be.wimvandonck.noter.logic.NotionDates
import be.wimvandonck.noter.logic.PropertyLayouts
import be.wimvandonck.noter.ui.common.NotionImage
import be.wimvandonck.noter.ui.common.OptionChip
import be.wimvandonck.noter.ui.common.PageIconView
import be.wimvandonck.noter.ui.common.SMALL_PAGE_ICON
import be.wimvandonck.noter.ui.common.SyncButton
import be.wimvandonck.noter.ui.common.errorText
import be.wimvandonck.noter.ui.theme.EmptyState
import be.wimvandonck.noter.ui.theme.SkeletonBlock
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background

/**
 * Home: a large search bar (it opens Search) with the sync and settings buttons, then everything
 * pinned (databases as rows, notes and pages as cards in the style of Google Keep), then the
 * databases and pages Notion lists as most recently edited. + makes a note in a database or a page
 * at the top of the workspace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenNote: (String) -> Unit,
    onOpenDatabase: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = androidx.compose.ui.platform.LocalResources.current
    var choosingTarget by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf<NoteSummary?>(null) }
    val notes = state.notes

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
    LoadPreviews(notes, viewModel::loadPreviews)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            HomeSearchBar(
                enabled = notes.hasKey,
                onSearch = onOpenSearch,
                actions = {
                    SyncButton(notes.pendingCount, notes.offline, syncing = false, onSync = viewModel::refresh, alwaysShown = false)
                    SettingsButton(state.avatarUrl, onOpenSettings)
                },
            )
        },
        floatingActionButton = {
            if (notes.hasKey && state.targetGroups.isNotEmpty()) {
                FloatingActionButton(onClick = { choosingTarget = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_new))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val nothing = notes.pinned.isEmpty() && state.pinnedDatabases.isEmpty() && state.recent?.isEmpty() == true
            when {
                !notes.ready -> Unit
                !notes.hasKey -> EmptyState(
                    Icons.Rounded.Key,
                    stringResource(R.string.empty_no_key_title),
                    body = stringResource(R.string.empty_no_key),
                ) {
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.empty_no_key_action)) }
                }
                nothing -> EmptyState(Icons.Rounded.Explore, stringResource(R.string.browse_empty_title), body = stringResource(R.string.browse_empty))
                else -> PullToRefreshBox(
                    isRefreshing = notes.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    NoteGrid(
                        state = notes,
                        previews = previews,
                        onOpen = onOpenNote,
                        onTogglePin = viewModel::togglePin,
                        onTrash = { confirmTrash = it },
                        onReordered = viewModel::saveOrder,
                        databases = state.pinnedDatabases,
                        databaseWorkspaces = state.pinnedWorkspaces,
                        onOpenDatabase = onOpenDatabase,
                        onUnpinDatabase = viewModel::togglePinDatabase,
                        onDatabasesReordered = viewModel::savePinnedDatabaseOrder,
                        header = {
                            pinnedSection(hasDatabases = state.pinnedDatabases.isNotEmpty(), anyNotes = notes.pinned.isNotEmpty())
                        },
                        footer = {
                            recentSection(
                                state = state,
                                onOpenDatabase = onOpenDatabase,
                                onOpenPage = { viewModel.openItem(it, onOpenNote) },
                                onTogglePin = viewModel::togglePinItem,
                                onRetry = viewModel::refresh,
                                onToggleWorkspace = viewModel::toggleCollapsed,
                            )
                        },
                    )
                }
            }
        }
    }

    confirmTrash?.let { note ->
        TrashNoteDialog(note, onConfirm = { viewModel.trash(note.id); confirmTrash = null }, onDismiss = { confirmTrash = null })
    }

    if (choosingTarget) {
        ModalBottomSheet(onDismissRequest = { choosingTarget = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // With several workspaces, each has its own part: a page at its top and its databases.
                state.targetGroups.forEach { group ->
                    val several = state.severalWorkspaces
                    if (several) {
                        SheetHeader(group.workspace?.name?.ifBlank { null } ?: stringResource(if (group.workspace == null) R.string.new_note_in else R.string.workspace_unnamed))
                    }
                    // Notion doesn't let a connection add pages at the top of a workspace.
                    if (group.topLevel) {
                        if (!several) SheetHeader(stringResource(R.string.new_page))
                        ListItem(
                            headlineContent = { Text(stringResource(if (several) R.string.new_page_at_top else R.string.new_page_top_level)) },
                            supportingContent = { Text(stringResource(R.string.new_page_top_level_help)) },
                            leadingContent = { Icon(Icons.Rounded.Workspaces, contentDescription = null) },
                            modifier = Modifier.clickable {
                                choosingTarget = false
                                viewModel.createPage(group.workspace?.id, onOpenNote)
                            },
                        )
                    }
                    if (!several && group.targets.isNotEmpty()) SheetHeader(stringResource(R.string.new_note_in))
                    group.targets.forEach { target ->
                        ListItem(
                            headlineContent = { Text(target.name.ifBlank { stringResource(R.string.untitled) }) },
                            leadingContent = {
                                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    if (target.icon != null) {
                                        PageIconView(target.icon, size = 24.dp)
                                    } else {
                                        Icon(Icons.Rounded.TableChart, contentDescription = null)
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                choosingTarget = false
                                viewModel.createNote(target.databaseId, onOpenNote)
                            },
                        )
                    }
                }
                Spacer(Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

/**
 * Opens Settings: the profile picture of the person the token acts as when Notion tells it (the
 * cogwheel while it loads, and when there is none or it can't be shown).
 */
@Composable
private fun SettingsButton(avatarUrl: String?, onClick: () -> Unit) {
    val label = stringResource(R.string.settings_title)
    IconButton(onClick = onClick) {
        if (avatarUrl == null) {
            Icon(Icons.Rounded.Settings, contentDescription = label)
        } else {
            SubcomposeAsyncImage(
                model = avatarUrl,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape),
                loading = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                error = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            )
        }
    }
}

private val AVATAR_SIZE = 32.dp

/**
 * Home's search bar: a large, rounded field that opens Search, with the screen's buttons at its
 * end (Material 3's search bar on a main screen).
 */
@Composable
private fun HomeSearchBar(enabled: Boolean, onSearch: () -> Unit, actions: @Composable () -> Unit) {
    Row(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onSearch,
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.browse_search),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
                actions()
            }
        }
    }
}

/**
 * Home's pinned section: its title. The pinned databases (as rows) and the notes (as cards) follow
 * in the grid itself ([NoteGrid]), which lets the user drag them.
 */
private fun LazyStaggeredGridScope.pinnedSection(hasDatabases: Boolean, anyNotes: Boolean) {
    if (!hasDatabases && !anyNotes) return
    item(key = "__pinned", span = StaggeredGridItemSpan.FullLine) { ListHeader(stringResource(R.string.section_pinned)) }
}

/**
 * Home's list of databases and pages, most recently edited first (from Notion's search, kept on
 * this phone): loading lines until known; what failed, with Retry, when nothing is kept.
 */
private fun LazyStaggeredGridScope.recentSection(
    state: HomeUiState,
    onOpenDatabase: (String) -> Unit,
    onOpenPage: (WorkspaceItem) -> Unit,
    onTogglePin: (WorkspaceItem) -> Unit,
    onRetry: () -> Unit,
    onToggleWorkspace: (String) -> Unit,
) {
    val recent = state.recent
    if (state.severalWorkspaces && recent != null) {
        workspaceSections(state, onOpenDatabase, onOpenPage, onTogglePin, onToggleWorkspace)
        return
    }
    item(key = "__recent", span = StaggeredGridItemSpan.FullLine) { ListHeader(stringResource(R.string.section_recent)) }
    val error = state.recentError
    when {
        recent == null && error != null -> item(key = "__recent_error", span = StaggeredGridItemSpan.FullLine) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(errorText(error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
        recent == null -> item(key = "__recent_loading", span = StaggeredGridItemSpan.FullLine) { LoadingRows(count = 4) }
        else -> {
            val pinnedDatabases = state.pinnedDatabases.map { it.id.replace("-", "") }.toSet()
            val pinnedPages = state.notes.pinnedIds.map { it.replace("-", "") }.toSet()
            items(recent, key = { "r:" + it.id }, span = { StaggeredGridItemSpan.FullLine }) { item ->
                val plain = item.id.replace("-", "")
                WorkspaceRow(
                    item = item,
                    pinned = if (item.isDatabase) plain in pinnedDatabases else plain in pinnedPages,
                    onClick = { if (item.isDatabase) onOpenDatabase(item.id) else onOpenPage(item) },
                    onTogglePin = { onTogglePin(item) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * Home's databases and pages with several workspaces: a part per workspace, each folded away or
 * shown with a tap on its title; what isn't known to be in one comes last, under the usual title.
 */
private fun LazyStaggeredGridScope.workspaceSections(
    state: HomeUiState,
    onOpenDatabase: (String) -> Unit,
    onOpenPage: (WorkspaceItem) -> Unit,
    onTogglePin: (WorkspaceItem) -> Unit,
    onToggleWorkspace: (String) -> Unit,
) {
    val pinnedDatabases = state.pinnedDatabases.map { it.id.replace("-", "") }.toSet()
    val pinnedPages = state.notes.pinnedIds.map { it.replace("-", "") }.toSet()
    state.recentGroups.forEach { group ->
        val workspace = group.workspace
        val groupKey = workspace?.id ?: "__unknown"
        val collapsed = workspace != null && workspace.id in state.collapsed
        item(key = "w:$groupKey", span = StaggeredGridItemSpan.FullLine) {
            if (workspace != null) {
                WorkspaceHeader(
                    workspace.name.ifBlank { stringResource(R.string.workspace_unnamed) },
                    collapsed = collapsed,
                    onToggle = { onToggleWorkspace(workspace.id) },
                    modifier = Modifier.animateItem(),
                )
            } else {
                ListHeader(stringResource(R.string.section_recent), Modifier.animateItem())
            }
        }
        if (!collapsed) {
            items(group.items, key = { "r:$groupKey:" + it.id }, span = { StaggeredGridItemSpan.FullLine }) { item ->
                val plain = item.id.replace("-", "")
                WorkspaceRow(
                    item = item,
                    pinned = if (item.isDatabase) plain in pinnedDatabases else plain in pinnedPages,
                    onClick = { if (item.isDatabase) onOpenDatabase(item.id) else onOpenPage(item) },
                    onTogglePin = { onTogglePin(item) },
                    modifier = Modifier.animateItem(),
                )
            }
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
 * A database's notes as cards (its rows of filter chips are in the screen's header, see
 * [ChipRows]), with pull-to-refresh, loading placeholders and empty states. Moving a note to the
 * trash asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesPane(
    state: NotesUiState,
    previews: Map<String, String>,
    onLoadPreviews: (List<NoteSummary>) -> Unit,
    onToggleChip: (property: String, key: String) -> Unit,
    onClearChips: () -> Unit,
    onLoadChipTitles: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onTrash: (String) -> Unit,
    onReordered: (List<String>) -> Unit,
) {
    var confirmTrash by remember { mutableStateOf<NoteSummary?>(null) }
    LaunchedEffect(state.chipRelationDatabases) { state.chipRelationDatabases.forEach(onLoadChipTitles) }
    LoadPreviews(state, onLoadPreviews)
    Column(Modifier.fillMaxSize()) {
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
        TrashNoteDialog(note, onConfirm = { onTrash(note.id); confirmTrash = null }, onDismiss = { confirmTrash = null })
    }
}

/** Downloads the previews of the notes whose cards show the start of their content. */
@Composable
internal fun LoadPreviews(state: NotesUiState, onLoadPreviews: (List<NoteSummary>) -> Unit) {
    LaunchedEffect(state.pinned, state.others, state.propertyLayouts) {
        onLoadPreviews((state.pinned + state.others).filter { PropertyLayouts.showsContent(state.propertyLayouts[it.databaseId]) })
    }
}

/** Asks before moving [note] to the trash. */
@Composable
internal fun TrashNoteDialog(note: NoteSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_title)) },
        text = { Text(stringResource(R.string.trash_body, InlineMarkdown.stripped(note.title).ifBlank { stringResource(R.string.untitled) })) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.trash_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The chips above the notes: one row per quick filter, named after its property, ending in × while
 * something in it is selected ("Clear filters" is in the Filters panel).
 */
@Composable
internal fun ChipRows(
    state: NotesUiState,
    onToggle: (property: String, key: String) -> Unit,
    onClearRow: (property: String) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.chipGroups.forEach { group ->
            val property = group.property
            val clearable = group.selected.isNotEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = if (clearable) 4.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    items(group.chips, key = { it.key }) { chip ->
                        FilterChip(
                            selected = chip.key in group.selected,
                            onClick = { onToggle(property.name, chip.key) },
                            label = { ChipText(chipLabel(chip, property.name, state), chip.count) },
                            leadingIcon = if (chip.isCheckbox || chip.isDate || chip.label.isEmpty()) null else {
                                { OptionDot(chip.color) }
                            },
                        )
                    }
                }
                if (clearable) {
                    IconButton(onClick = { onClearRow(property.name) }, modifier = Modifier.padding(end = 4.dp)) {
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
internal fun chipLabel(chip: Chip, property: String, state: NotesUiState): String {
    val name = property
    return when (chip.key) {
        ChipFilter.CHECKED -> stringResource(R.string.chip_checked, name)
        ChipFilter.UNCHECKED -> stringResource(R.string.chip_unchecked, name)
        ChipFilter.OVERDUE -> stringResource(R.string.chip_overdue)
        ChipFilter.TODAY -> stringResource(R.string.chip_today)
        ChipFilter.NEXT_7_DAYS -> stringResource(R.string.chip_next_7_days)
        ChipFilter.LATER -> stringResource(R.string.chip_later)
        ChipFilter.NO_DATE -> stringResource(R.string.chip_no_date)
        else -> chip.label.ifEmpty { state.chipTitles[chip.key]?.ifBlank { null } ?: stringResource(R.string.untitled) }
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
 * The notes as cards in a staggered grid (two columns on a phone, more on wider screens), with
 * full-width items of the screen's own before ([header]) and after them ([footer]).
 * Long-press and drag moves a note within its section; a long press without moving opens the
 * note's menu. The order is kept on this phone.
 *
 * While dragging, the card follows the finger exactly: its place on screen is worked out from
 * where the finger is, not from where the grid put it, so it doesn't jump when cards of
 * different heights change places. Near the top or bottom edge the grid scrolls.
 */
@Composable
internal fun NoteGrid(
    state: NotesUiState,
    previews: Map<String, String>,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onTrash: (NoteSummary) -> Unit,
    onReordered: (List<String>) -> Unit,
    /** Home: the pinned databases, as rows above the notes; dragged like the cards. */
    databases: List<PinnedDatabase> = emptyList(),
    /** The workspace's name of each pinned database, by its ID (with several workspaces). */
    databaseWorkspaces: Map<String, String> = emptyMap(),
    onOpenDatabase: (String) -> Unit = {},
    onUnpinDatabase: (PinnedDatabase) -> Unit = {},
    onDatabasesReordered: (List<String>) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
    header: LazyStaggeredGridScope.() -> Unit = {},
    footer: LazyStaggeredGridScope.() -> Unit = {},
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
    // The pinned databases likewise: dragged at once, then followed by the saved order.
    val pinnedDatabases = remember { mutableStateListOf<PinnedDatabase>() }
    val droppedDatabases = remember { DroppedOrder() }
    if (draggingKey == null) {
        val ids = databases.map { it.id }
        val waiting = droppedDatabases.ids
        if (waiting == null || waiting == ids || waiting.toSet() != ids.toSet()) {
            droppedDatabases.ids = null
            if (pinnedDatabases.toList() != databases) { pinnedDatabases.clear(); pinnedDatabases.addAll(databases) }
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

    val currentDatabases by rememberUpdatedState(databases)

    fun databasesChanged(): Boolean = pinnedDatabases.map { it.id } != currentDatabases.map { it.id }

    fun saveDatabases() {
        droppedDatabases.ids = pinnedDatabases.map { it.id }
        onDatabasesReordered(pinnedDatabases.map { it.id })
    }

    fun moveDatabase(key: String, by: Int) {
        val from = pinnedDatabases.indexOfFirst { DATABASE_KEY + it.id == key }
        val to = from + by
        if (from < 0 || to !in pinnedDatabases.indices) return
        pinnedDatabases.add(to, pinnedDatabases.removeAt(from))
        saveDatabases()
    }

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
        if (key.startsWith(DATABASE_KEY)) {
            // A pinned database only changes places with another one.
            if (!targetKey.startsWith(DATABASE_KEY)) return
            val from = pinnedDatabases.indexOfFirst { DATABASE_KEY + it.id == key }
            val to = pinnedDatabases.indexOfFirst { DATABASE_KEY + it.id == targetKey }
            if (from < 0 || to < 0) return
            pinnedDatabases.add(to, pinnedDatabases.removeAt(from))
            swappedWith = targetKey
            return
        }
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

    val unpin = stringResource(R.string.action_unpin)

    /**
     * Long press, then drag moves the item [key] (a card or a pinned database); a long press without
     * moving opens its menu. Outside the item's own layer, so moving it doesn't move the touch.
     */
    fun Modifier.dragToReorder(key: String, changed: () -> Boolean, save: () -> Unit): Modifier =
        pointerInput(key) {
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
                            // A long press without moving: the item's menu.
                            menuKey = key
                        } else if (changed()) {
                            save()
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

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        header()
        items(pinnedDatabases, key = { DATABASE_KEY + it.id }, span = { StaggeredGridItemSpan.FullLine }) { pin ->
            val key = DATABASE_KEY + pin.id
            val isDragging = draggingKey == key
            Box(
                Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .dragToReorder(key, changed = { databasesChanged() }, save = { saveDatabases() })
                    .graphicsLayer {
                        if (isDragging) {
                            // Up and down only, under the finger.
                            gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { info ->
                                translationY = finger.y - grab.y - info.offset.y
                            }
                            shadowElevation = 12f
                        }
                    }
                    .then(if (isDragging) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(moveUp) { moveDatabase(key, -1); true },
                            CustomAccessibilityAction(moveDown) { moveDatabase(key, 1); true },
                            CustomAccessibilityAction(unpin) { onUnpinDatabase(pin); true },
                        )
                    },
            ) {
                // No long press of its own: the drag above opens the menu when the finger doesn't move.
                WorkspaceRow(
                    title = pin.title,
                    icon = pin.icon,
                    isDatabase = true,
                    pinned = false,
                    onClick = { if (draggingKey != key) onOpenDatabase(pin.id) },
                    onTogglePin = null,
                    workspace = databaseWorkspaces[pin.id],
                )
                DropdownMenu(expanded = menuKey == key, onDismissRequest = { menuKey = null }) {
                    DropdownMenuItem(
                        text = { Text(unpin) },
                        leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                        onClick = { menuKey = null; onUnpinDatabase(pin) },
                    )
                }
            }
        }
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
                        databaseIcon = state.sourceIcons[item.note.databaseId],
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
                            .dragToReorder(key, changed = { changed() }, save = { save(key) })
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
        footer()
    }
}

/** Prefix of a pinned database's key in Home's grid (note IDs never start with it). */
private const val DATABASE_KEY = "db:"

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
    databaseIcon: PageIcon?,
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
 * The card's footer: the database's icon (on Home, when known), the properties chosen for cards
 * (see PropertyLayouts), else the filter property's options; "not synced yet".
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NoteLabels(
    note: NoteSummary,
    chipProperty: String?,
    layout: PropertyLayout?,
    showDatabaseIcon: Boolean,
    databaseIcon: PageIcon?,
) {
    val chosen = remember(note.properties, layout) { PropertyLayouts.cardProperties(note.properties, layout).filterNot { it.isEmpty } }
    val shown = chosen.ifEmpty { listOfNotNull(chipProperty?.let { note.property(it) }) }
    val sourceIcon = databaseIcon?.takeIf { showDatabaseIcon }
    if (shown.all { it.isEmpty } && !note.isLocal && sourceIcon == null) return
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (sourceIcon != null) PageIconView(sourceIcon, size = 16.dp)
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
internal fun LoadingGrid() {
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
