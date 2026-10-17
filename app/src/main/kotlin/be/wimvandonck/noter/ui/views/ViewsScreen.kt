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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.logic.PropertyLayouts
import be.wimvandonck.noter.data.model.CardLayout
import be.wimvandonck.noter.data.model.DatabaseSchema
import be.wimvandonck.noter.ui.common.DatabaseIcons
import be.wimvandonck.noter.ui.common.IconPicker
import be.wimvandonck.noter.ui.common.errorText
import be.wimvandonck.noter.ui.theme.DragHandle
import be.wimvandonck.noter.ui.theme.ReorderableColumn
import be.wimvandonck.noter.ui.theme.SettingsBlock
import be.wimvandonck.noter.ui.theme.SettingsScaffold

/**
 * Manage notebooks: the notebooks in their order (the order of the tabs under Notebooks). Tapping
 * one edits it; "Add notebook" opens Search to pick a database (all its notes) or a page (its
 * subpages).
 */
@Composable
fun ViewsScreen(
    viewModel: ViewsViewModel,
    onBack: () -> Unit,
    onAddNotebook: () -> Unit,
    editViewId: String? = null,
    addDatabaseId: String? = null,
    addTitle: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val views = state.views
    // Opened from "Edit notebook" or "Add as notebook" on a database's page: that step, once.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editViewId, addDatabaseId) {
        if (opened) return@LaunchedEffect
        opened = true
        when {
            editViewId != null -> viewModel.startEdit(editViewId)
            addDatabaseId != null -> viewModel.startAddFor(addDatabaseId, addTitle)
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.manage_views),
        backLabel = stringResource(R.string.back),
        onBack = onBack,
    ) {
        item(key = "notebooks") {
            ReorderableColumn(
                items = views,
                key = { it.id },
                onMove = { from, to -> viewModel.moveDatabase(views[from].id, to) },
            ) { db ->
                DatabaseRow(
                    config = db,
                    onClick = { viewModel.startEdit(db) },
                    handle = { DragHandle(handleModifier) },
                )
            }
        }
        item(key = "add") {
            SettingsBlock {
                FilledTonalButton(
                    onClick = onAddNotebook,
                    enabled = state.hasKey,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_database))
                }
            }
        }
    }

    state.draft?.let { draft ->
        DatabaseEditorSheet(
            draft = draft,
            onChange = viewModel::editDraft,
            onCards = viewModel::editCards,
            onSave = viewModel::saveDraft,
            onCancel = viewModel::cancelDraft,
            onRemove = { viewModel.removeDatabase(draft.config.id) },
        )
    }
}

@Composable
private fun DatabaseRow(
    config: DatabaseConfig,
    onClick: () -> Unit,
    handle: @Composable () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(DatabaseIcons.icon(config.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(config.name) },
        trailingContent = handle,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DatabaseEditorSheet(
    draft: DatabaseDraft,
    onChange: ((DatabaseConfig) -> DatabaseConfig) -> Unit,
    onCards: (id: String, ((CardLayout) -> CardLayout)) -> Unit,
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
            val focused = draft.focused
            if (focused != null) {
                // One sub-notebook on its own: only what belongs to it (its cards).
                Text(stringResource(R.string.edit_named, focused.name), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.sub_notebook_of, draft.config.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (draft.schemaError != null) {
                    Text(stringResource(R.string.key_failed, errorText(draft.schemaError)), color = MaterialTheme.colorScheme.error)
                }
                CardOptions(stringResource(R.string.cards_title), focused.cards ?: CardLayout(), draft.childSchemas[focused.id]) {
                    onCards(focused.id, it)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = onSave) { Text(stringResource(R.string.save)) }
                }
                return@Column
            }
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
            if (draft.schemaError != null) {
                Text(stringResource(R.string.key_failed, errorText(draft.schemaError)), color = MaterialTheme.colorScheme.error)
            }
            val children = draft.config.children
            if (children.isEmpty()) {
                CardOptions(stringResource(R.string.cards_title), draft.cards, draft.schema) { onCards(draft.config.id, it) }
            } else {
                // A notebook of several data sources: the cards of each sub-notebook.
                children.forEach { child ->
                    CardOptions(stringResource(R.string.cards_of, child.name), child.cards ?: CardLayout(), draft.childSchemas[child.id]) {
                        onCards(child.id, it)
                    }
                }
            }
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
 * What this notebook's cards in the overview show: the header image, the start of the content,
 * and up to three properties.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardOptions(title: String, cards: CardLayout, schema: DatabaseSchema?, onCards: ((CardLayout) -> CardLayout) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    SwitchRow(stringResource(R.string.card_cover), cards.cover) { on -> onCards { it.copy(cover = on) } }
    SwitchRow(stringResource(R.string.card_content), cards.content) { on -> onCards { it.copy(content = on) } }
    val properties = schema?.properties.orEmpty().filter { it.type != "title" }
    if (properties.isNotEmpty()) {
        Text(stringResource(R.string.cards_properties, PropertyLayouts.MAX_ON_CARD), style = MaterialTheme.typography.bodyLarge)
        val onCard = cards.properties
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            properties.forEach { property ->
                val selected = property.name in onCard
                FilterChip(
                    selected = selected,
                    enabled = selected || onCard.size < PropertyLayouts.MAX_ON_CARD,
                    onClick = { onCards { PropertyLayouts.toggleOnCard(it, property.name) } },
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
