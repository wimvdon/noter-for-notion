package com.impca201.noter.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impca201.noter.R
import com.impca201.noter.data.RepoEvent
import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.data.notion.InlineMarkdown
import com.impca201.noter.ui.common.DatabaseIcons
import com.impca201.noter.ui.common.OptionChip
import com.impca201.noter.ui.theme.SkeletonBlock
import kotlinx.coroutines.launch

/** More destinations than a navigation bar holds: they go in a navigation drawer instead. */
private const val MAX_BAR_ITEMS = 5

/**
 * The overview, in the style of Google Keep: notes as cards in a staggered grid with a preview
 * of their content, pinned notes in their own section on top, and long-press-and-drag to move a
 * note. Home and each database are top-level destinations in Material's navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenNote: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = androidx.compose.ui.platform.LocalResources.current
    var choosingDatabase by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf<NoteSummary?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val useDrawer = state.tabs.size > MAX_BAR_ITEMS

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is RepoEvent.SaveFailed -> resources.getString(
                    R.string.sync_failed,
                    event.noteTitle.ifBlank { resources.getString(R.string.untitled) },
                    event.detail,
                )
                RepoEvent.KeyRejected -> resources.getString(R.string.sync_key_rejected)
            }
            snackbar.showSnackbar(text)
        }
    }
    LaunchedEffect(state.pinned, state.others) { viewModel.loadPreviews(state.pinned + state.others) }

    val homeLabel = stringResource(R.string.tab_home)
    val title = state.tabs.firstOrNull { it.key == state.currentTab }?.name?.ifEmpty { null } ?: homeLabel
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val screen: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (useDrawer) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.open_navigation))
                            }
                        }
                    },
                    actions = {
                        SyncStatusIcon(state.pendingCount, state.offline)
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                if (state.tabs.size > 1 && !useDrawer) {
                    NavigationBar {
                        state.tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = tab.key == state.currentTab,
                                onClick = { viewModel.selectTab(tab.key) },
                                icon = { Icon(tab.iconVector(), contentDescription = null) },
                                label = { Text(tab.label(homeLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (state.hasKey && state.hasDatabases) {
                    FloatingActionButton(
                        onClick = {
                            val dbs = viewModel.databases()
                            when {
                                !state.isHome -> viewModel.createNote(onCreated = onOpenNote)
                                dbs.size == 1 -> viewModel.createNote(dbs.first().id, onOpenNote)
                                else -> choosingDatabase = true
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_new_note))
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (state.chips.isNotEmpty()) {
                    ChipRow(state, onToggle = viewModel::toggleChip, onClear = viewModel::clearChips)
                }
                PullToRefreshBox(
                    isRefreshing = state.refreshing && !state.loading,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        !state.ready -> Unit
                        !state.hasKey -> EmptyState(
                            stringResource(R.string.empty_no_key),
                            stringResource(R.string.empty_no_key_action),
                            onOpenSettings,
                        )
                        !state.hasDatabases -> EmptyState(
                            stringResource(R.string.empty_no_databases),
                            stringResource(R.string.empty_no_databases_action),
                            onOpenSettings,
                        )
                        state.loading && state.pinned.isEmpty() && state.others.isEmpty() -> LoadingGrid()
                        state.pinned.isEmpty() && state.others.isEmpty() -> EmptyState(
                            when {
                                state.selectedChips.isNotEmpty() -> stringResource(R.string.empty_filtered)
                                state.isHome -> stringResource(R.string.empty_home)
                                state.error != null -> stringResource(R.string.empty_error, state.error ?: "")
                                else -> stringResource(R.string.empty_database)
                            },
                            null,
                            null,
                        )
                        else -> NoteGrid(
                            state = state,
                            previews = previews,
                            onOpen = onOpenNote,
                            onTogglePin = viewModel::togglePin,
                            onTrash = { confirmTrash = it },
                            onReordered = viewModel::saveOrder,
                        )
                    }
                }
            }
        }
    }

    if (useDrawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.padding(top = 12.dp))
                    state.tabs.forEach { tab ->
                        NavigationDrawerItem(
                            label = { Text(tab.label(homeLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(tab.iconVector(), contentDescription = null) },
                            selected = tab.key == state.currentTab,
                            onClick = {
                                viewModel.selectTab(tab.key)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            },
            content = screen,
        )
    } else {
        screen()
    }

    if (choosingDatabase) {
        ModalBottomSheet(onDismissRequest = { choosingDatabase = false }) {
            Text(
                stringResource(R.string.new_note_in),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            viewModel.databases().forEach { db ->
                ListItem(
                    headlineContent = { Text(db.name) },
                    leadingContent = { Icon(DatabaseIcons.icon(db.icon), contentDescription = null) },
                    modifier = Modifier.clickable {
                        choosingDatabase = false
                        viewModel.createNote(db.id, onOpenNote)
                    },
                )
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    confirmTrash?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmTrash = null },
            title = { Text(stringResource(R.string.trash_title)) },
            text = { Text(stringResource(R.string.trash_body, InlineMarkdown.stripped(note.title).ifBlank { stringResource(R.string.untitled) })) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.trash(note.id)
                    confirmTrash = null
                }) { Text(stringResource(R.string.trash_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmTrash = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun Tab.isHome(): Boolean = name.isEmpty() && icon == null

private fun Tab.label(homeLabel: String): String = if (isHome()) homeLabel else name

private fun Tab.iconVector(): ImageVector = if (isHome()) Icons.Rounded.Home else DatabaseIcons.icon(icon)

@Composable
private fun SyncStatusIcon(pending: Int, offline: Boolean) {
    AnimatedVisibility(visible = pending > 0 || offline, enter = fadeIn(), exit = fadeOut()) {
        val description = if (offline) {
            stringResource(R.string.status_offline)
        } else {
            pluralStringResource(R.plurals.status_pending, pending, pending)
        }
        Box(Modifier.size(48.dp).semantics { this.contentDescription = description }, contentAlignment = Alignment.Center) {
            Icon(
                if (offline) Icons.Rounded.CloudOff else Icons.Rounded.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            FilterChip(
                selected = selected,
                onClick = { onToggle(chip.key) },
                label = { Text(if (chip.count > 0) "${chip.label}  ${chip.count}" else chip.label) },
                leadingIcon = if (state.isHome) {
                    { Icon(DatabaseIcons.icon(icon), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    { OptionDot(chip.color) }
                },
            )
        }
    }
}

@Composable
private fun OptionDot(color: String) {
    val colors = com.impca201.noter.ui.common.optionColors(color)
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
private fun NoteGrid(
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
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (title.isNotBlank() || note.icon != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        note.icon?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
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
                when {
                    preview != null -> Text(
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
                NoteLabels(note, chipProperty, showDatabaseIcon, databaseIcon)
            }
            menu()
        }
    }
}

/** The card's footer: the chip property's options, the database (on Home), "not synced yet". */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NoteLabels(note: NoteSummary, chipProperty: String?, showDatabaseIcon: Boolean, databaseIcon: String?) {
    val chipValue = chipProperty?.let { note.property(it)?.value }
    val options = when (chipValue) {
        is PropertyValue.Select -> listOfNotNull(chipValue.option)
        is PropertyValue.MultiSelect -> chipValue.options
        else -> emptyList()
    }
    if (options.isEmpty() && !note.isLocal && !showDatabaseIcon) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showDatabaseIcon) {
            Icon(
                DatabaseIcons.icon(databaseIcon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        options.take(3).forEach { OptionChip(it, small = true) }
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

@Composable
private fun EmptyState(text: String, action: String?, onAction: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Keeps pull-to-refresh working on an empty screen.
        LazyColumn(Modifier.widthIn(max = 480.dp)) {
            item {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (action != null && onAction != null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.FilledTonalButton(onClick = onAction) { Text(action) }
                    }
                }
            }
        }
    }
}
