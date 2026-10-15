package com.impca201.noter.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impca201.noter.R
import com.impca201.noter.data.RepoEvent
import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.data.notion.InlineMarkdown
import com.impca201.noter.logic.NotionDates
import com.impca201.noter.ui.common.DatabaseIcons
import com.impca201.noter.ui.common.OptionChip
import com.impca201.noter.ui.theme.AppShapes
import com.impca201.noter.ui.theme.SkeletonBlock
import java.util.Locale

/** How far (in pixels) a long-pressed note may move and still count as a press (opens the menu). */
private const val MENU_SLOP_PX = 24f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenNote: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = androidx.compose.ui.platform.LocalResources.current
    var choosingDatabase by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf<NoteSummary?>(null) }

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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val title = state.tabs.firstOrNull { it.key == state.currentTab }?.name?.ifEmpty { null }
        ?: stringResource(R.string.tab_home)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 80.dp)) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    SyncStatusIcon(state.pendingCount, state.offline)
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            Column(Modifier.fillMaxSize()) {
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
                        state.loading && state.pinned.isEmpty() && state.others.isEmpty() -> LoadingList()
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
                        else -> NoteList(
                            state = state,
                            onOpen = onOpenNote,
                            onTogglePin = viewModel::togglePin,
                            onTrash = { confirmTrash = it },
                            onReordered = viewModel::saveOrder,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                if (state.tabs.size > 1) {
                    FloatingTabBar(
                        tabs = state.tabs,
                        current = state.currentTab,
                        homeLabel = stringResource(R.string.tab_home),
                        onSelect = viewModel::selectTab,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
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
            }
        }
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
                    modifier = Modifier.combinedClickable(onClick = {
                        choosingDatabase = false
                        viewModel.createNote(db.id, onOpenNote)
                    }),
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
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

/** A list item: a section header or a note. */
private sealed interface Row {
    val key: String

    data class Header(override val key: String, val pinned: Boolean) : Row

    data class Note(val note: NoteSummary, val pinned: Boolean) : Row {
        override val key: String get() = note.id
    }
}

@Composable
private fun NoteList(
    state: MainUiState,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onTrash: (NoteSummary) -> Unit,
    onReordered: (List<String>) -> Unit,
) {
    // A working copy, so dragging moves rows at once; it follows the state when not dragging.
    val pinned = remember { mutableStateListOf<NoteSummary>() }
    val others = remember { mutableStateListOf<NoteSummary>() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    if (draggingKey == null) {
        if (pinned.toList() != state.pinned) { pinned.clear(); pinned.addAll(state.pinned) }
        if (others.toList() != state.others) { others.clear(); others.addAll(state.others) }
    }
    val showHeaders = !state.isHome && pinned.isNotEmpty() && others.isNotEmpty()
    val rows = buildList {
        if (showHeaders) add(Row.Header("__pinned", pinned = true))
        pinned.forEach { add(Row.Note(it, pinned = true)) }
        if (showHeaders) add(Row.Header("__others", pinned = false))
        others.forEach { add(Row.Note(it, pinned = false)) }
    }
    val listState = rememberLazyListState()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentRows by rememberUpdatedState(rows)

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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is Row.Header -> Text(
                    stringResource(if (row.pinned) R.string.section_pinned else R.string.section_others),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
                is Row.Note -> {
                    val isDragging = draggingKey == row.key
                    val moveUp = stringResource(R.string.action_move_up)
                    val moveDown = stringResource(R.string.action_move_down)
                    NoteCard(
                        note = row.note,
                        showDatabaseIcon = state.isHome,
                        databaseIcon = state.databaseIcons[row.note.databaseId],
                        pinned = row.note.id in state.pinnedIds,
                        chipProperty = state.chipProperty,
                        menuOpen = menuFor == row.key,
                        onMenuDismiss = { menuFor = null },
                        onOpen = { onOpen(row.note.id) },
                        onTogglePin = { onTogglePin(row.note.id) },
                        onTrash = { onTrash(row.note) },
                        onMoveUp = { move(row.key, -1) },
                        onMoveDown = { move(row.key, 1) },
                        dragHandle = Modifier.pointerInput(row.key) {
                            // Long-press and drag moves the note; long-press without moving opens its menu.
                            var moved = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingKey = row.key; dragOffset = 0f; moved = 0f },
                                onDragEnd = {
                                    if (moved < MENU_SLOP_PX) {
                                        menuFor = row.key
                                    } else {
                                        groupOf(row.key)?.let { group -> onReordered(group.map { it.id }) }
                                    }
                                    draggingKey = null
                                    dragOffset = 0f
                                },
                                onDragCancel = { draggingKey = null; dragOffset = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    moved += kotlin.math.abs(amount.y)
                                    dragOffset += amount.y
                                    dragOffset = swapIfNeeded(listState, row.key, dragOffset, currentRows, ::groupOf)
                                },
                            )
                        },
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                shadowElevation = if (isDragging) 8f else 0f
                            }
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(moveUp) { move(row.key, -1); true },
                                    CustomAccessibilityAction(moveDown) { move(row.key, 1); true },
                                )
                            },
                    )
                }
            }
        }
    }
}

/**
 * While dragging: when the dragged note's middle passes a neighbour of the same group, swap them
 * and correct the offset so the note stays under the finger. Returns the new offset.
 */
private fun swapIfNeeded(
    listState: LazyListState,
    key: String,
    offset: Float,
    rows: List<Row>,
    groupOf: (String) -> MutableList<NoteSummary>?,
): Float {
    val visible = listState.layoutInfo.visibleItemsInfo
    val current = visible.firstOrNull { it.key == key } ?: return offset
    val middle = current.offset + offset + current.size / 2f
    val target = visible.firstOrNull { item ->
        item.key != key && middle > item.offset && middle < item.offset + item.size &&
            rows.any { it is Row.Note && it.key == item.key }
    } ?: return offset
    val group = groupOf(key) ?: return offset
    val targetKey = target.key as String
    if (groupOf(targetKey) !== group) return offset
    val from = group.indexOfFirst { it.id == key }
    val to = group.indexOfFirst { it.id == targetKey }
    group.add(to, group.removeAt(from))
    return offset + (current.offset - target.offset)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteSummary,
    showDatabaseIcon: Boolean,
    databaseIcon: String?,
    pinned: Boolean,
    chipProperty: String?,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onTrash: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    dragHandle: Modifier,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = AppShapes.Item,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dragHandle)
                    .clickable(onClick = onOpen)
                    .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    note.icon != null -> Text(note.icon, style = MaterialTheme.typography.titleLarge)
                    showDatabaseIcon -> Icon(
                        DatabaseIcons.icon(databaseIcon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        InlineMarkdown.stripped(note.title).ifBlank { stringResource(R.string.untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (note.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    NoteMeta(note, chipProperty)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (pinned) R.string.action_unpin else R.string.action_pin)) },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                    onClick = { onMenuDismiss(); onTogglePin() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_move_up)) },
                    leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null) },
                    onClick = { onMenuDismiss(); onMoveUp() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_move_down)) },
                    leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) },
                    onClick = { onMenuDismiss(); onMoveDown() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_trash)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { onMenuDismiss(); onTrash() },
                )
            }
        }
    }
}

@Composable
private fun NoteMeta(note: NoteSummary, chipProperty: String?) {
    val chipValue = chipProperty?.let { note.property(it)?.value }
    val options = when (chipValue) {
        is PropertyValue.Select -> listOfNotNull(chipValue.option)
        is PropertyValue.MultiSelect -> chipValue.options
        else -> emptyList()
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val edited = NotionDates.display(note.lastEditedTime.takeIf { it.isNotBlank() }, locale)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        options.take(3).forEach { OptionChip(it, small = true) }
        if (note.isLocal) {
            Icon(
                Icons.Rounded.CloudSync,
                contentDescription = stringResource(R.string.status_not_synced),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (edited.isNotBlank() && options.isEmpty()) {
            Text(
                edited,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LoadingList() {
    val loading = stringResource(R.string.loading)
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .semantics(mergeDescendants = true) { this.contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(6) { SkeletonBlock(height = 64.dp) }
    }
}

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
