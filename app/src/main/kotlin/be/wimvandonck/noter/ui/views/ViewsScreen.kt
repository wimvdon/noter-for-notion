package be.wimvandonck.noter.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.logic.Destinations
import be.wimvandonck.noter.logic.PropertyLayouts
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.ui.common.DatabaseIcons
import be.wimvandonck.noter.ui.common.IconPicker
import be.wimvandonck.noter.ui.theme.SettingsBlock
import be.wimvandonck.noter.ui.theme.SettingsScaffold

/** The window's width in dp, for how many destinations fit in the navigation bar. */
@Composable
fun rememberWindowWidthDp(): Int {
    val width = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    return remember(width, density) { with(density) { width.toDp().value.toInt() } }
}

/**
 * Manage notebooks: the notebooks in their order, each in the navigation drawer and, when pinned,
 * in the navigation bar (as many as fit). Tapping one edits it; "Add notebook" picks a database
 * (all its notes) or a page (its subpages).
 */
@Composable
fun ViewsScreen(viewModel: ViewsViewModel, onBack: () -> Unit, editViewId: String? = null, addDatabaseId: String? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingDatabase by remember { mutableStateOf(false) }
    val width = rememberWindowWidthDp()
    val views = state.views
    val inBar = Destinations.barViews(views, width).map { it.id }.toSet()
    val room = Destinations.barRoom(views, width)
    // Opened from "Edit notebook" or "Add as notebook" on a database's page: that step, once.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editViewId, addDatabaseId) {
        if (opened) return@LaunchedEffect
        opened = true
        when {
            editViewId != null -> viewModel.startEdit(editViewId)
            addDatabaseId != null -> viewModel.startAddFor(addDatabaseId, roomInBar = room > 0)
        }
    }
    val capacity = Destinations.barNotebooks(width)

    SettingsScaffold(
        title = stringResource(R.string.manage_views),
        backLabel = stringResource(R.string.back),
        onBack = onBack,
    ) {
        item(key = "help") {
            SettingsBlock {
                Text(
                    stringResource(R.string.databases_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pluralStringResource(R.plurals.bar_room, capacity, capacity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        views.forEachIndexed { index, db ->
            item(key = db.id) {
                DatabaseRow(
                    config = db,
                    canMoveUp = index > 0,
                    canMoveDown = index < views.lastIndex,
                    shownInBar = db.id in inBar,
                    canPin = room > 0,
                    onClick = { viewModel.startEdit(db) },
                    onMove = { up -> viewModel.moveDatabase(db.id, up) },
                    onPin = { viewModel.setInBar(db.id, it) },
                )
            }
        }
        item(key = "add") {
            SettingsBlock {
                FilledTonalButton(
                    onClick = {
                        addingDatabase = true
                        viewModel.loadAvailable()
                    },
                    enabled = state.hasKey,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_database))
                }
            }
        }
    }

    if (addingDatabase) {
        AddDatabaseSheet(
            state = state,
            configured = views.map { it.sourceId.replace("-", "") }.toSet(),
            onRetry = viewModel::loadAvailable,
            onDismiss = { addingDatabase = false },
            onPick = {
                addingDatabase = false
                viewModel.startAdd(it, roomInBar = room > 0)
            },
            onPickPage = {
                addingDatabase = false
                viewModel.startAddPage(it, roomInBar = room > 0)
            },
        )
    }
    state.draft?.let { draft ->
        DatabaseEditorSheet(
            draft = draft,
            onChange = viewModel::editDraft,
            onLayout = viewModel::editLayout,
            onSave = viewModel::saveDraft,
            onCancel = viewModel::cancelDraft,
            onRemove = { viewModel.removeDatabase(draft.config.id) },
        )
    }
}

@Composable
private fun DatabaseRow(
    config: DatabaseConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    shownInBar: Boolean,
    canPin: Boolean,
    onClick: () -> Unit,
    onMove: (Boolean) -> Unit,
    onPin: (Boolean) -> Unit,
) {
    val filters = if (config.pageId != null) {
        stringResource(R.string.notebook_subpages)
    } else {
        stringResource(R.string.notebook_all_notes)
    }
    val place = when {
        shownInBar -> stringResource(R.string.view_in_bar)
        config.inBar -> stringResource(R.string.view_no_room)
        else -> stringResource(R.string.view_in_drawer)
    }
    ListItem(
        leadingContent = { Icon(DatabaseIcons.icon(config.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(config.name) },
        supportingContent = { Text(listOfNotNull(filters, place).joinToString("\n")) },
        trailingContent = {
            Row {
                IconToggleButton(checked = config.inBar, onCheckedChange = onPin, enabled = config.inBar || canPin) {
                    Icon(
                        if (config.inBar) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.pin_to_bar),
                    )
                }
                IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up))
                }
                IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down))
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDatabaseSheet(
    state: ViewsUiState,
    configured: Set<String>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onPick: (be.wimvandonck.noter.data.model.PageRef) -> Unit,
    onPickPage: (be.wimvandonck.noter.data.model.PageRef) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.add_database),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val list = state.available
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.availableError != null -> Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(stringResource(R.string.key_failed, state.availableError), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                list.isEmpty() && state.availablePages.isEmpty() -> Text(
                    stringResource(R.string.no_databases_found),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (list.isNotEmpty()) SheetSection(stringResource(R.string.browse_databases))
                    list.forEach { db ->
                        val added = db.id.replace("-", "") in configured
                        // A database can be more than one notebook (each with its own filters and sort).
                        ListItem(
                            headlineContent = { Text(db.title.ifBlank { stringResource(R.string.untitled) }) },
                            supportingContent = if (added) ({ Text(stringResource(R.string.already_added)) }) else null,
                            leadingContent = { Icon(painterResource(R.drawable.ic_database), contentDescription = null) },
                            modifier = Modifier.clickable { onPick(db) },
                        )
                    }
                    if (state.availablePages.isNotEmpty()) SheetSection(stringResource(R.string.notebook_pages))
                    state.availablePages.forEach { page ->
                        ListItem(
                            headlineContent = { Text(page.title.ifBlank { stringResource(R.string.untitled) }) },
                            supportingContent = { Text(stringResource(R.string.notebook_subpages)) },
                            leadingContent = { Icon(Icons.Rounded.Description, contentDescription = null) },
                            modifier = Modifier.clickable { onPickPage(page) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSection(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DatabaseEditorSheet(
    draft: DatabaseDraft,
    onChange: ((DatabaseConfig) -> DatabaseConfig) -> Unit,
    onLayout: ((PropertyLayout?) -> PropertyLayout) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (draft.isNew) R.string.add_database else R.string.edit_database),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = draft.config.name,
                onValueChange = { name -> onChange { it.copy(name = name) } },
                label = { Text(stringResource(R.string.database_name)) },
                singleLine = true,
                isError = draft.config.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.database_icon), style = MaterialTheme.typography.titleSmall)
            IconPicker(draft.config.icon) { name -> onChange { it.copy(icon = name) } }
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = draft.config.inBar, role = Role.Switch, onValueChange = { v -> onChange { it.copy(inBar = v) } }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pin_to_bar), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.pin_to_bar_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = draft.config.inBar, onCheckedChange = null)
            }
            // Filters and sort are set in the notebook itself (Filters, Sort); here only what it shows.
            Text(
                if (draft.config.pageId != null) {
                    stringResource(R.string.notebook_subpages)
                } else {
                    stringResource(R.string.notebook_all_notes)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft.schemaError != null) {
                Text(stringResource(R.string.key_failed, draft.schemaError), color = MaterialTheme.colorScheme.error)
            }
            CardOptions(draft, onLayout)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!draft.isNew) {
                    TextButton(onClick = { confirmRemove = true }) {
                        Text(stringResource(R.string.remove_database), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onSave, enabled = draft.config.name.isNotBlank()) { Text(stringResource(R.string.save)) }
            }
        }
    }
    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_database_title)) },
            text = { Text(stringResource(R.string.remove_database_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(R.string.remove_database)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * What the cards in the overview show for this database: the header image, the start of the
 * content, and up to three properties.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardOptions(draft: DatabaseDraft, onLayout: ((PropertyLayout?) -> PropertyLayout) -> Unit) {
    val layout = draft.layout
    Text(stringResource(R.string.cards_title), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.cards_help),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SwitchRow(stringResource(R.string.card_cover), layout?.coverOnCard == true) { on -> onLayout { PropertyLayouts.setCoverOnCard(it, on) } }
    SwitchRow(stringResource(R.string.card_content), PropertyLayouts.showsContent(layout)) { on -> onLayout { PropertyLayouts.setContentOnCard(it, on) } }
    val properties = draft.schema?.properties.orEmpty().filter { it.type != "title" }
    if (properties.isNotEmpty()) {
        Text(stringResource(R.string.cards_properties, PropertyLayouts.MAX_ON_CARD), style = MaterialTheme.typography.bodyLarge)
        val onCard = layout?.onCard.orEmpty()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            properties.forEach { property ->
                val selected = property.name in onCard
                FilterChip(
                    selected = selected,
                    enabled = selected || onCard.size < PropertyLayouts.MAX_ON_CARD,
                    onClick = { onLayout { PropertyLayouts.toggleOnCard(it, property.name) } },
                    label = { Text(property.name) },
                )
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
