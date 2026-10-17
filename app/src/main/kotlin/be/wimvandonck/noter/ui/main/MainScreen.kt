package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
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
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.IntOffset
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
import be.wimvandonck.noter.ui.views.rememberWindowWidthDp
import be.wimvandonck.noter.logic.ChipFilter
import be.wimvandonck.noter.logic.FilterChip as Chip
import be.wimvandonck.noter.logic.Destinations
import be.wimvandonck.noter.logic.NoteTargets
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material.icons.rounded.Description
import be.wimvandonck.noter.data.NotesRepository
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import be.wimvandonck.noter.ui.theme.EmptyState
import be.wimvandonck.noter.ui.theme.SkeletonBlock
import kotlinx.coroutines.launch

/**
 * The overview, in the style of Google Keep. Top-level destinations: Pinned (the pinned notes of
 * everything), Browse (the pages and databases shared with the integration) and the user's views
 * (a database with its filters). All are in the navigation drawer, with Manage views and
 * Settings; the navigation bar holds Pinned, Browse and the views pinned to it, as many as fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenNote: (String) -> Unit,
    onOpenDatabase: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenViews: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = androidx.compose.ui.platform.LocalResources.current
    var choosingDatabase by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is RepoEvent.SaveFailed -> resources.getString(
                    R.string.sync_failed,
                    event.noteTitle.ifBlank { resources.getString(R.string.untitled) },
                    event.detail,
                )
                RepoEvent.KeyRejected -> resources.getString(R.string.sync_key_rejected)
                is RepoEvent.CreateFailed ->
                    if (event.topLevel) resources.getString(R.string.create_top_level_failed) else resources.getString(R.string.create_failed, event.detail)
            }
            snackbar.showSnackbar(text)
        }
    }

    val labels = TabLabels(stringResource(R.string.tab_home), stringResource(R.string.tab_browse))
    val title = state.tabs.firstOrNull { it.key == state.currentTab }?.let { labels.of(it) } ?: labels.pinned
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val width = rememberWindowWidthDp()
    val barIds = Destinations.barViews(state.views, width).map { it.id }.toSet()
    val barTabs = state.tabs.filter { it.isPinned || it.isBrowse || it.key in barIds }
    val targets = NoteTargets.of(state.views, if (state.isBrowse) browse.items else null)

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    // Material's modal drawer opens from its menu button; swiping only closes it, so the swipe
    // from the edge stays Android's back gesture.
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    state.tabs.forEachIndexed { index, tab ->
                        if (index == 2) {
                            // Pinned and Browse, then the views under their own header.
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            Text(
                                stringResource(R.string.section_views),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        NavigationDrawerItem(
                            label = { Text(labels.of(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(tab.iconVector(), contentDescription = null) },
                            selected = tab.key == state.currentTab,
                            onClick = { closeDrawerThen { viewModel.selectTab(tab.key) } },
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.manage_views)) },
                        icon = { Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = null) },
                        selected = false,
                        onClick = { closeDrawerThen(onOpenViews) },
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.settings_title)) },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        selected = false,
                        onClick = { closeDrawerThen(onOpenSettings) },
                    )
                }
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.open_navigation))
                        }
                    },
                    actions = {
                        SyncButton(state.pendingCount, state.offline, syncing = false, onSync = viewModel::refresh, alwaysShown = false)
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                NavigationBar {
                    barTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tab.key == state.currentTab,
                            onClick = { viewModel.selectTab(tab.key) },
                            icon = { Icon(tab.iconVector(), contentDescription = null) },
                            label = { Text(labels.of(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            },
            floatingActionButton = {
                // A view adds to its database; Pinned and Browse ask which database (when there is a choice).
                // Browse also makes pages: at the top of the workspace or inside a page.
                val inView = !state.isHome && !state.isBrowse
                if (state.hasKey && (inView || state.isBrowse || targets.isNotEmpty())) {
                    FloatingActionButton(
                        onClick = {
                            when {
                                inView -> viewModel.createNote(onCreated = onOpenNote)
                                state.isBrowse -> choosingDatabase = true
                                targets.size == 1 -> viewModel.createNote(targets.first().databaseId, onOpenNote)
                                else -> choosingDatabase = true
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_new_note))
                    }
                }
            },
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
                    state.isBrowse -> BrowsePane(
                        state = browse,
                        onSearch = viewModel::search,
                        onRefresh = viewModel::refresh,
                        onOpenPage = { viewModel.openItem(it, onOpenNote) },
                        onOpenDatabase = onOpenDatabase,
                    )
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
                    )
                }
            }
        }
    }

    if (choosingDatabase) {
        ModalBottomSheet(onDismissRequest = { choosingDatabase = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.isBrowse) {
                    SheetHeader(stringResource(R.string.new_page))
                    // Notion only lets a personal access token add pages at the top of the workspace.
                    if (state.canAddTopLevel) {
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
                    browse.items.orEmpty().filter { !it.isDatabase && it.note?.databaseId == NotesRepository.PAGES }.forEach { page ->
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.new_page_inside, page.title.ifBlank { stringResource(R.string.untitled) })) },
                            leadingContent = {
                                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    if (page.icon != null) PageIconView(page.icon, size = 24.dp) else Icon(Icons.Rounded.Description, contentDescription = null)
                                }
                            },
                            modifier = Modifier.clickable {
                                choosingDatabase = false
                                viewModel.createPage(page.id, onOpenNote)
                            },
                        )
                    }
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

@Composable
private fun SheetHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** The names of the fixed destinations (views have their own). */
private class TabLabels(val pinned: String, val browse: String) {
    fun of(tab: Tab): String = when {
        tab.isPinned -> pinned
        tab.isBrowse -> browse
        else -> tab.name
    }
}

@Composable
private fun Tab.iconVector(): ImageVector = when {
    isPinned -> Icons.Rounded.PushPin
    isBrowse -> Icons.Rounded.Explore
    else -> DatabaseIcons.icon(icon)
}

/**
 * The notes of Pinned, a view or a database: filters on top, then the notes as cards, with
 * pull-to-refresh, loading placeholders and empty states. Moving a note to the trash asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesPane(
    state: MainUiState,
    previews: Map<String, String>,
    onLoadPreviews: (List<NoteSummary>) -> Unit,
    onToggleChip: (String) -> Unit,
    onClearChips: () -> Unit,
    onLoadChipTitles: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onTrash: (String) -> Unit,
    onReordered: (List<String>) -> Unit,
) {
    var confirmTrash by remember { mutableStateOf<NoteSummary?>(null) }
    LaunchedEffect(state.chipRelationDatabase) { state.chipRelationDatabase?.let(onLoadChipTitles) }
    // Previews are only downloaded for cards that show the content.
    LaunchedEffect(state.pinned, state.others, state.propertyLayouts) {
        onLoadPreviews((state.pinned + state.others).filter { PropertyLayouts.showsContent(state.propertyLayouts[it.databaseId]) })
    }
    Column(Modifier.fillMaxSize()) {
        if (state.chips.isNotEmpty()) {
            ChipRow(state, onToggle = onToggleChip, onClear = onClearChips)
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing && !state.loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading && state.pinned.isEmpty() && state.others.isEmpty() -> LoadingGrid()
                state.pinned.isEmpty() && state.others.isEmpty() -> when {
                    state.selectedChips.isNotEmpty() -> EmptyState(Icons.Rounded.FilterAltOff, stringResource(R.string.empty_filtered)) {
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

@Composable
private fun ChipRow(state: MainUiState, onToggle: (String) -> Unit, onClear: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "__all") {
            FilterChip(
                selected = state.selectedChips.isEmpty(),
                onClick = onClear,
                label = { Text(stringResource(R.string.chip_all)) },
            )
        }
        items(state.chips, key = { it.key }) { chip ->
            val selected = chip.key in state.selectedChips
            val icon = if (state.isHome) state.databaseIcons[chip.key] else null
            val label = chipLabel(chip, state)
            FilterChip(
                selected = selected,
                onClick = { onToggle(chip.key) },
                label = { Text(if (chip.count > 0) "$label  ${chip.count}" else label) },
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
}

/** A chip's words: fixed ones for checkbox and date filters, a linked page's title, else its own label. */
@Composable
internal fun chipLabel(chip: Chip, state: MainUiState): String {
    val property = state.chipProperty.orEmpty()
    return when (chip.key) {
        ChipFilter.CHECKED -> stringResource(R.string.chip_checked, property)
        ChipFilter.UNCHECKED -> stringResource(R.string.chip_unchecked, property)
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
    if (draggingKey == null) {
        if (pinned.toList() != state.pinned) { pinned.clear(); pinned.addAll(state.pinned) }
        if (others.toList() != state.others) { others.clear(); others.addAll(state.others) }
    }
    val showHeaders = pinned.isNotEmpty() && others.isNotEmpty()
    val items = buildList {
        if (showHeaders) add(GridItem.Header("__pinned", pinned = true))
        pinned.forEach { add(GridItem.Note(it)) }
        if (showHeaders) add(GridItem.Header("__others", pinned = false))
        others.forEach { add(GridItem.Note(it)) }
    }
    val gridState = rememberLazyStaggeredGridState()
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragDistance by remember { mutableStateOf(0f) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    fun groupOf(key: String): MutableList<NoteSummary>? = when {
        pinned.any { it.id == key } -> pinned
        others.any { it.id == key } -> others
        else -> null
    }

    fun move(key: String, by: Int) {
        val group = groupOf(key) ?: return
        val from = group.indexOfFirst { it.id == key }
        val to = from + by
        if (to !in group.indices) return
        group.add(to, group.removeAt(from))
        onReordered(group.map { it.id })
    }
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)
    val currentItems by rememberUpdatedState(items)

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
                        chipProperty = state.chipProperty,
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
                            .graphicsLayer {
                                if (isDragging) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    shadowElevation = 12f
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                }
                            }
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .pointerInput(key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingKey = key
                                        dragOffset = Offset.Zero
                                        dragDistance = 0f
                                    },
                                    onDragEnd = {
                                        if (dragDistance < DRAG_SLOP_PX) {
                                            // A long press without moving: the note's menu.
                                            menuKey = key
                                        } else {
                                            groupOf(key)?.let { group -> onReordered(group.map { it.id }) }
                                        }
                                        draggingKey = null
                                        dragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDistance += amount.getDistance()
                                        dragOffset += amount
                                        dragOffset = swapIfNeeded(gridState, key, dragOffset, currentItems, ::groupOf)
                                    },
                                )
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

/** Height of a header image on a card. */
private val CARD_COVER_HEIGHT = 96.dp

/** Movement (px) below which a long press counts as a press, not a drag. */
private const val DRAG_SLOP_PX = 24f

/**
 * While dragging: when the middle of the dragged card is over another card of the same section,
 * the two swap places and the offset is corrected so the card stays under the finger. Returns
 * the new offset.
 */
private fun swapIfNeeded(
    gridState: LazyStaggeredGridState,
    key: String,
    offset: Offset,
    items: List<GridItem>,
    groupOf: (String) -> MutableList<NoteSummary>?,
): Offset {
    val visible = gridState.layoutInfo.visibleItemsInfo
    val current = visible.firstOrNull { it.key == key } ?: return offset
    val middle = Offset(
        current.offset.x + offset.x + current.size.width / 2f,
        current.offset.y + offset.y + current.size.height / 2f,
    )
    val target = visible.firstOrNull { info ->
        info.key != key &&
            middle.x > info.offset.x && middle.x < info.offset.x + info.size.width &&
            middle.y > info.offset.y && middle.y < info.offset.y + info.size.height &&
            items.any { it is GridItem.Note && it.key == info.key }
    } ?: return offset
    val group = groupOf(key) ?: return offset
    val targetKey = target.key as String
    if (groupOf(targetKey) !== group) return offset
    val from = group.indexOfFirst { it.id == key }
    val to = group.indexOfFirst { it.id == targetKey }
    group.add(to, group.removeAt(from))
    val shift: IntOffset = current.offset - target.offset
    return offset + Offset(shift.x.toFloat(), shift.y.toFloat())
}

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
    EmptyState(Icons.Rounded.CloudOff, stringResource(R.string.empty_error_title), body = reason) {
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
