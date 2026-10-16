package com.impca201.noter.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impca201.noter.R
import com.impca201.noter.data.model.DatabaseConfig
import com.impca201.noter.data.model.PropertySchema
import com.impca201.noter.data.model.ThemeMode
import com.impca201.noter.ui.common.DatabaseIcons
import com.impca201.noter.ui.theme.AppShapes
import com.impca201.noter.ui.theme.AppearanceSettings
import com.impca201.noter.ui.theme.SettingsBlock
import com.impca201.noter.ui.theme.SettingsSection
import com.impca201.noter.ui.theme.SettingsFooter
import com.impca201.noter.ui.theme.SettingsScaffold
import com.impca201.noter.ui.theme.rememberAppVersion

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingDatabase by remember { mutableStateOf(false) }
    val context = LocalContext.current

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        backLabel = stringResource(R.string.back),
        onBack = onBack,
    ) {
        item(key = "connection") {
            SettingsSection(title = stringResource(R.string.section_connection)) {
                SettingsBlock { ConnectionSettings(state, viewModel) }
            }
        }
        item(key = "databases") {
            SettingsSection(title = stringResource(R.string.section_databases)) {
                SettingsBlock {
                    Text(
                        stringResource(R.string.databases_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.settings.databases.forEachIndexed { index, db ->
                    DatabaseRow(
                        config = db,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.settings.databases.lastIndex,
                        onClick = { viewModel.startEdit(db) },
                        onMove = { up -> viewModel.moveDatabase(db.id, up) },
                    )
                }
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
        item(key = "appearance") {
            val themes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
            val labels = listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark))
            SettingsSection(title = stringResource(R.string.section_appearance)) {
                AppearanceSettings(
                    themeLabel = stringResource(R.string.theme),
                    themeOptions = labels,
                    selectedTheme = themes.indexOf(state.settings.themeMode),
                    onThemeSelect = { viewModel.setTheme(themes[it]) },
                    dynamicColorTitle = stringResource(R.string.dynamic_color),
                    dynamicColorSummary = stringResource(R.string.dynamic_color_summary),
                    dynamicColor = state.settings.dynamicColor,
                    onDynamicColorChange = viewModel::setDynamicColor,
                    dynamicColorSupported = true,
                )
            }
        }
        item(key = "footer") {
            SettingsFooter(stringResource(R.string.footer, stringResource(R.string.app_name), rememberAppVersion())) {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) }
                }) { Text(stringResource(R.string.privacy_policy)) }
            }
        }
    }

    if (addingDatabase) {
        AddDatabaseSheet(
            state = state,
            configured = state.settings.databases.map { it.id.replace("-", "") }.toSet(),
            onRetry = viewModel::loadAvailable,
            onDismiss = { addingDatabase = false },
            onPick = {
                addingDatabase = false
                viewModel.startAdd(it)
            },
        )
    }
    state.draft?.let { draft ->
        DatabaseEditorSheet(
            draft = draft,
            onChange = viewModel::editDraft,
            onSave = viewModel::saveDraft,
            onCancel = viewModel::cancelDraft,
            onRemove = { viewModel.removeDatabase(draft.config.id) },
        )
    }
}

@Composable
private fun ConnectionSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf(viewModel.currentKey()) }
    var visible by rememberSaveable { mutableStateOf(false) }
    Text(
        stringResource(R.string.connection_help),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, INTEGRATIONS_URL.toUri())) }
    }) {
        Text(stringResource(R.string.connection_open_integrations))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(stringResource(R.string.api_key)) },
        placeholder = { Text(stringResource(R.string.api_key_placeholder)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(if (visible) R.string.hide_key else R.string.show_key),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    when (val status = state.keyStatus) {
        is KeyStatus.Ok -> StatusText(stringResource(R.string.key_ok, status.botName), error = false)
        is KeyStatus.Failed -> StatusText(stringResource(R.string.key_failed, status.message), error = true)
        KeyStatus.Removed -> StatusText(stringResource(R.string.key_removed), error = false)
        else -> Unit
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { viewModel.saveKey(key) }, enabled = key.isNotBlank() && state.keyStatus != KeyStatus.Testing) {
            if (state.keyStatus == KeyStatus.Testing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.test_and_save))
        }
        if (state.hasKey) {
            OutlinedButton(onClick = {
                key = ""
                viewModel.removeKey()
            }) { Text(stringResource(R.string.remove_key)) }
        }
    }
    if (state.hasKey) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.pendingCount == 0) {
                    stringResource(R.string.sync_all_saved)
                } else {
                    pluralStringResource(R.plurals.status_pending, state.pendingCount, state.pendingCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::syncNow) { Text(stringResource(R.string.sync_now)) }
        }
    }
}

@Composable
private fun StatusText(text: String, error: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun DatabaseRow(
    config: DatabaseConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Boolean) -> Unit,
) {
    ListItem(
            leadingContent = { Icon(DatabaseIcons.icon(config.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(config.name) },
            supportingContent = config.chipProperty?.let { { Text(stringResource(R.string.chips_from, it)) } },
            trailingContent = {
                Row {
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
    state: SettingsUiState,
    configured: Set<String>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onPick: (com.impca201.noter.data.model.PageRef) -> Unit,
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
                list.isEmpty() -> Text(
                    stringResource(R.string.no_databases_found),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    list.forEach { db ->
                        val added = db.id.replace("-", "") in configured
                        ListItem(
                            headlineContent = { Text(db.title.ifBlank { stringResource(R.string.untitled) }) },
                            supportingContent = if (added) ({ Text(stringResource(R.string.already_added)) }) else null,
                            leadingContent = { Icon(painterResource(R.drawable.ic_database), contentDescription = null) },
                            modifier = Modifier.clickable(enabled = !added) { onPick(db) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DatabaseEditorSheet(
    draft: DatabaseDraft,
    onChange: ((DatabaseConfig) -> DatabaseConfig) -> Unit,
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DatabaseIcons.all.forEachIndexed { index, (key, icon) ->
                    val selected = draft.config.icon == key
                    val label = stringResource(R.string.icon_option, index + 1)
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .selectable(selected = selected, role = Role.RadioButton) { onChange { it.copy(icon = key) } }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.chip_property), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.chip_property_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val schema = draft.schema
            when {
                draft.schemaError != null -> Text(stringResource(R.string.key_failed, draft.schemaError), color = MaterialTheme.colorScheme.error)
                schema == null -> CircularProgressIndicator(Modifier.size(24.dp))
                else -> {
                    val candidates = schema.properties.filter { it.isChipCandidate }
                    ChipChoice(null, stringResource(R.string.chip_none), draft.config.chipProperty == null) {
                        onChange { it.copy(chipProperty = null) }
                    }
                    candidates.forEach { prop ->
                        ChipChoice(prop, prop.name, draft.config.chipProperty == prop.name) {
                            onChange { it.copy(chipProperty = prop.name) }
                        }
                    }
                    if (candidates.isEmpty()) {
                        Text(stringResource(R.string.chip_property_none_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun ChipChoice(property: PropertySchema?, label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (property != null) {
                Text(
                    stringResource(
                        when (property.type) {
                            "status" -> R.string.type_status
                            "select" -> R.string.type_select
                            "multi_select" -> R.string.type_multi_select
                            else -> R.string.type_people
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val INTEGRATIONS_URL = "https://www.notion.so/profile/integrations"
private const val PRIVACY_URL = "https://github.com/wimvdon/noter-for-notion/blob/main/PRIVACY.md"
