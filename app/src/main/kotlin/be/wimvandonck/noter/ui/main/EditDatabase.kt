package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.logic.EditedProperty
import be.wimvandonck.noter.logic.PropertyKind
import be.wimvandonck.noter.logic.SchemaEdits
import be.wimvandonck.noter.ui.common.PageIconView
import be.wimvandonck.noter.ui.common.errorText
import be.wimvandonck.noter.ui.common.kindNames
import be.wimvandonck.noter.ui.note.PageIconSheet
import be.wimvandonck.noter.ui.theme.AppShapes

/** The properties being edited, kept across rotation as "id, name, type, kind" strings. */
private val EditedSaver = listSaver<List<EditedProperty>, String>(
    save = { list -> list.map { listOf(it.id.orEmpty(), it.name, it.type, it.kind?.name.orEmpty()).joinToString("\u0000") } },
    restore = { saved ->
        saved.mapNotNull { line ->
            line.split("\u0000").takeIf { it.size == 4 }?.let { (id, name, type, kind) ->
                EditedProperty(id.ifBlank { null }, name, type, kind.ifBlank { null }?.let(PropertyKind::valueOf))
            }
        }
    },
)

/**
 * Edit database (⋮, or a tap on the database's name or icon): its icon, its name and the data
 * source's properties in one screen. A property is renamed in its field, removed with ×, added
 * with Add property (the kinds a new database can have). Save sends everything to Notion at once,
 * after asking when properties are removed, since their values go with them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDatabaseScreen(
    state: DatabaseEditState,
    title: String,
    icon: PageIcon?,
    onSave: (title: String, emoji: String?, iconChanged: Boolean, properties: List<EditedProperty>, names: Map<PropertyKind, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val names = kindNames()
    val properties = state.properties
    var name by rememberSaveable { mutableStateOf(title) }
    var emoji by rememberSaveable { mutableStateOf(icon?.emoji) }
    var iconChanged by rememberSaveable { mutableStateOf(false) }
    var edited by rememberSaveable(stateSaver = EditedSaver) { mutableStateOf(SchemaEdits.start(properties)) }
    var choosingIcon by remember { mutableStateOf(false) }
    var choosingKind by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val enabled = !state.saving
    val removed = properties.filter { p -> edited.none { it.id == p.id } }.map { it.name }
    val save = { onSave(name, emoji, iconChanged, edited, names) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.edit_database)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = enabled) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    actions = {
                        TextButton(onClick = { if (removed.isEmpty()) save() else confirming = true }, enabled = enabled && name.isNotBlank()) {
                            if (state.saving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.action_save))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val shown = if (iconChanged) emoji?.let { PageIcon(emoji = it) } else icon
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(AppShapes.Item)
                                .clickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(if (shown != null) R.string.change_icon else R.string.add_icon)) {
                                    choosingIcon = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (shown != null) {
                                PageIconView(shown, size = 32.dp)
                            } else {
                                Icon(Icons.Rounded.AddReaction, contentDescription = stringResource(R.string.add_icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.new_database_name)) },
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        stringResource(R.string.edit_database_properties),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    edited.forEachIndexed { index, property ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = property.name,
                                onValueChange = { typed -> edited = edited.toMutableList().also { it[index] = property.copy(name = typed) } },
                                label = { Text(typeLabel(property.type, names)) },
                                singleLine = true,
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                            )
                            if (property.removable) {
                                IconButton(onClick = { edited = edited.filterIndexed { i, _ -> i != index } }, enabled = enabled) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.new_database_remove_property, property.name))
                                }
                            } else {
                                // The title property stays: the same room, so the fields line up.
                                Spacer(Modifier.size(48.dp))
                            }
                        }
                    }
                    Box {
                        TextButton(onClick = { choosingKind = true }, enabled = enabled) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.new_database_add_property))
                        }
                        DropdownMenu(expanded = choosingKind, onDismissRequest = { choosingKind = false }) {
                            PropertyKind.entries.forEach { kind ->
                                DropdownMenuItem(
                                    text = { Text(names.getValue(kind)) },
                                    onClick = {
                                        choosingKind = false
                                        edited = SchemaEdits.add(edited, kind, names.getValue(kind))
                                    },
                                )
                            }
                        }
                    }
                    state.error?.let {
                        Text(
                            stringResource(R.string.database_edit_failed, errorText(it)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (choosingIcon) {
        PageIconSheet(
            hasIcon = if (iconChanged) emoji != null else icon != null,
            onPick = { picked ->
                choosingIcon = false
                emoji = picked
                iconChanged = true
            },
            onRemove = {
                choosingIcon = false
                emoji = null
                iconChanged = true
            },
            onDismiss = { choosingIcon = false },
        )
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.remove_properties_title)) },
            text = { Text(stringResource(R.string.remove_properties_body, removed.joinToString(", "))) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    save()
                }) { Text(stringResource(R.string.remove_and_save)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** What a property is, as the label of its field: its kind, or Notion's name for a type the app can't add. */
@Composable
private fun typeLabel(type: String, names: Map<PropertyKind, String>): String =
    PropertyKind.entries.firstOrNull { it.type == type }?.let(names::getValue) ?: stringResource(
        when (type) {
            "title" -> R.string.property_kind_title
            "status" -> R.string.property_kind_status
            "formula" -> R.string.property_kind_formula
            "relation" -> R.string.property_kind_relation
            "rollup" -> R.string.property_kind_rollup
            "unique_id" -> R.string.property_kind_id
            else -> R.string.property_kind_other
        },
    )
