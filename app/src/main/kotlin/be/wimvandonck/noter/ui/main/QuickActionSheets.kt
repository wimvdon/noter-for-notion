package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.EditMode
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PropertyEdit
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.QuickAction
import be.wimvandonck.noter.logic.QuickActions
import be.wimvandonck.noter.ui.common.NoterSheet
import java.util.UUID
import kotlin.math.abs

/**
 * ⋮ › Quick actions (#119): the database's quick actions, to change or delete, and New quick
 * action. They run from a note: a button each under its properties.
 */
@Composable
internal fun QuickActionsSheet(
    state: NotesUiState,
    properties: List<PropertySchema>,
    lookups: QuickLookups,
    onEdit: (QuickAction?) -> Unit,
    onDelete: (QuickAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val actions = state.setup?.quickActions.orEmpty()
    NoterSheet(onDismiss = onDismiss) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.quick_actions),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.quick_actions_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )
            actions.forEach { action ->
                val schema = properties.toSchema()
                val summary = QuickActions.editsOf(action, schema).mapNotNull { edit ->
                    properties.firstOrNull { it.name == edit.property }?.let { editSummary(edit, it, lookups) }
                }.joinToString(", ")
                ListItem(
                    headlineContent = { Text(action.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = summary.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                    leadingContent = { Icon(Icons.Rounded.Bolt, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { onDelete(action) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.quick_action_delete, action.name))
                        }
                    },
                    modifier = Modifier.clickable(onClickLabel = stringResource(R.string.quick_action_edit)) { onEdit(action) },
                )
            }
            Button(onClick = { onEdit(null) }, modifier = Modifier.padding(start = 24.dp, top = 8.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.quick_action_new))
            }
        }
    }
}

/**
 * Making or changing a quick action: its name and its changes, each a property, what to do with
 * it (as Notion's button step "Edit property") and the value. Cancel or Save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickActionEditor(
    action: QuickAction?,
    state: NotesUiState,
    properties: List<PropertySchema>,
    lookups: QuickLookups,
    onSave: (QuickAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val editable = remember(properties) { QuickActions.properties(properties.toSchema()) }
    var name by rememberSaveable(action?.id) { mutableStateOf(action?.name.orEmpty()) }
    var edits by remember(action?.id) { mutableStateOf(action?.let { QuickActions.editsOf(it, properties.toSchema()) }.orEmpty()) }
    var choosing by remember { mutableStateOf(false) }
    val complete = edits.isNotEmpty() && edits.all { edit ->
        editable.firstOrNull { it.name == edit.property }?.let { QuickActions.isComplete(edit, it.type) } ?: true
    }
    NoterSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(if (action == null) R.string.quick_action_new else R.string.quick_action_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.quick_action_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.quick_action_changes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            if (editable.isEmpty()) {
                Text(
                    stringResource(R.string.quick_action_no_properties),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            edits.forEachIndexed { index, edit ->
                val property = editable.firstOrNull { it.name == edit.property } ?: return@forEachIndexed
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                EditRow(
                    edit = edit,
                    property = property,
                    lookups = lookups,
                    chipTitles = state.chipTitles,
                    onChange = { changed -> edits = edits.toMutableList().also { it[index] = changed } },
                    onRemove = { edits = edits.toMutableList().also { it.removeAt(index) } },
                )
            }
            if (editable.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { choosing = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.quick_action_add_change))
                    }
                    DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                        editable.forEach { property ->
                            DropdownMenuItem(
                                text = { Text(property.name) },
                                onClick = {
                                    choosing = false
                                    edits = edits + QuickActions.newEdit(property)
                                },
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = name.isNotBlank() && complete,
                    onClick = { onSave(QuickAction(action?.id ?: UUID.randomUUID().toString(), name, edits = edits)) },
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

/** One change: the property with a remove button, what to do with it, and its value. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditRow(
    edit: PropertyEdit,
    property: PropertySchema,
    lookups: QuickLookups,
    chipTitles: Map<String, String>,
    onChange: (PropertyEdit) -> Unit,
    onRemove: () -> Unit,
) {
    val type = property.type
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(property.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.quick_action_remove_change, property.name))
            }
        }
        val modes = QuickActions.modes(type)
        if (modes.size > 1) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = edit.mode == mode,
                        onClick = { onChange(QuickActions.withMode(edit, type, mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        icon = {},
                    ) { Text(modeLabel(mode, type), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        if (edit.mode == EditMode.CLEAR || edit.mode == EditMode.TOGGLE) return@Column
        when (type) {
            "select", "status", "multi_select" -> Choices(
                property.options.map { it.name to it.name },
                selected = edit.values,
                onToggle = { onChange(QuickActions.toggleValue(edit, type, it)) },
            )
            "relation" -> {
                val pages = property.relationDatabaseId?.let { lookups.pages[it] }.orEmpty()
                Choices(
                    pages.map { it.id to it.title.ifBlank { chipTitles[it.id] ?: "" }.ifBlank { stringResource(R.string.untitled) } },
                    selected = edit.values,
                    onToggle = { onChange(QuickActions.toggleValue(edit, type, it)) },
                )
            }
            "people" -> {
                val me = NotionUser(PropertyEdit.ME, stringResource(R.string.quick_me))
                val people = listOf(me) + lookups.users
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { user ->
                        FilterChip(
                            selected = edit.people.any { it.id == user.id },
                            onClick = { onChange(QuickActions.togglePerson(edit, if (user.id == PropertyEdit.ME) NotionUser(PropertyEdit.ME, "") else user)) },
                            label = { Text(user.name.ifBlank { "?" }) },
                        )
                    }
                }
            }
            "checkbox" -> Row(
                Modifier.fillMaxWidth().clickable {
                    onChange(edit.copy(values = listOf(if (edit.values.firstOrNull() == PropertyEdit.UNCHECKED) PropertyEdit.CHECKED else PropertyEdit.UNCHECKED)))
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.quick_checked), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = edit.values.firstOrNull() != PropertyEdit.UNCHECKED, onCheckedChange = null)
            }
            "date" -> DaysField(edit, onChange)
            "number" -> OutlinedTextField(
                value = edit.text,
                onValueChange = { onChange(edit.copy(text = it)) },
                label = { Text(stringResource(R.string.quick_action_value)) },
                singleLine = true,
                isError = edit.text.isNotEmpty() && QuickActions.number(edit.text) == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            else -> OutlinedTextField(
                value = edit.text,
                onValueChange = { onChange(edit.copy(text = it)) },
                label = { Text(stringResource(R.string.quick_action_value)) },
                singleLine = type != "rich_text",
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (type) {
                        "url" -> KeyboardType.Uri
                        "email" -> KeyboardType.Email
                        "phone_number" -> KeyboardType.Phone
                        else -> KeyboardType.Text
                    },
                    capitalization = if (type == "title" || type == "rich_text") KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Options, people or pages to choose (by key, with their words). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(choices: List<Pair<String, String>>, selected: List<String>, onToggle: (String) -> Unit) {
    if (choices.isEmpty()) {
        Text(
            stringResource(R.string.quick_action_no_values),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (key, label) ->
            FilterChip(selected = key in selected, onClick = { onToggle(key) }, label = { Text(label) })
        }
    }
}

/**
 * A date: how many days from today (Set) or from the note's date (Move), typed or picked from a
 * few common ones, with what it means in words below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysField(edit: PropertyEdit, onChange: (PropertyEdit) -> Unit) {
    var text by remember(edit.days) { mutableStateOf(edit.days.toString()) }
    val quick = if (edit.mode == EditMode.SET) listOf(0, 1, 7) else listOf(1, 7, -1)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        quick.forEach { days ->
            FilterChip(
                selected = edit.days == days,
                onClick = { onChange(edit.copy(days = days)) },
                label = { Text(daysWords(days, edit.mode)) },
            )
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            typed.trim().toIntOrNull()?.let { onChange(edit.copy(days = it)) }
        },
        label = { Text(stringResource(R.string.quick_action_days)) },
        supportingText = { Text(daysWords(edit.days, edit.mode)) },
        singleLine = true,
        isError = text.trim().toIntOrNull() == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A date change in words: "Today", "Today + 7 days", "7 days later", "1 day earlier". */
@Composable
private fun daysWords(days: Int, mode: EditMode): String {
    val count = pluralStringResource(R.plurals.quick_days, abs(days), abs(days))
    return when {
        mode == EditMode.ADD && days >= 0 -> stringResource(R.string.quick_date_later, count)
        mode == EditMode.ADD -> stringResource(R.string.quick_date_earlier, count)
        days == 0 -> stringResource(R.string.quick_date_today)
        days > 0 -> stringResource(R.string.quick_date_after_today, count)
        else -> stringResource(R.string.quick_date_before_today, count)
    }
}

@Composable
private fun modeLabel(mode: EditMode, type: String): String = stringResource(
    when (mode) {
        EditMode.SET -> if (type in setOf("multi_select", "people", "relation")) R.string.quick_mode_replace else R.string.quick_mode_set
        EditMode.ADD -> when (type) {
            "number" -> R.string.quick_mode_increase
            "date" -> R.string.quick_mode_move
            else -> R.string.quick_mode_add
        }
        EditMode.REMOVE -> if (type == "number") R.string.quick_mode_decrease else R.string.quick_mode_remove
        EditMode.CLEAR -> R.string.quick_mode_clear
        EditMode.TOGGLE -> R.string.quick_mode_toggle
    },
)

/** One change in a few words, for the list of quick actions: "Status: Done", "Due: Today + 7 days". */
@Composable
private fun editSummary(edit: PropertyEdit, property: PropertySchema, lookups: QuickLookups): String {
    val name = property.name
    val type = property.type
    if (edit.mode == EditMode.CLEAR) return stringResource(R.string.quick_edit_clear, name)
    if (edit.mode == EditMode.TOGGLE) return stringResource(R.string.quick_edit_toggle, name)
    val me = stringResource(R.string.quick_me)
    val untitled = stringResource(R.string.untitled)
    val what = when (type) {
        "people" -> edit.people.joinToString(", ") { if (it.id == PropertyEdit.ME) me else it.name.ifBlank { lookups.users.firstOrNull { u -> u.id == it.id }?.name ?: "?" } }
        "relation" -> {
            val pages = property.relationDatabaseId?.let { lookups.pages[it] }.orEmpty()
            edit.values.joinToString(", ") { id -> pages.firstOrNull { it.id == id }?.title?.ifBlank { null } ?: untitled }
        }
        "checkbox" -> stringResource(if (edit.values.firstOrNull() == PropertyEdit.UNCHECKED) R.string.quick_unchecked else R.string.quick_checked)
        "date" -> return stringResource(R.string.quick_edit_set, name, daysWords(edit.days, edit.mode))
        "select", "status", "multi_select" -> edit.values.joinToString(", ")
        else -> edit.text
    }
    return stringResource(
        when (edit.mode) {
            EditMode.ADD -> R.string.quick_edit_add
            EditMode.REMOVE -> R.string.quick_edit_remove
            else -> R.string.quick_edit_set
        },
        name,
        what,
    )
}

/** The properties as a schema, for [QuickActions], which reads them by name. */
private fun List<PropertySchema>.toSchema() = be.wimvandonck.noter.data.model.DatabaseSchema("", "", this)

/**
 * A database's quick actions: the list, and the editor for a new or changed one (back to the list
 * when it closes). The people and linked pages to choose from load when it opens.
 */
@Composable
internal fun QuickActionsFlow(viewModel: DatabaseViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lookups by viewModel.quickLookups.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<QuickAction?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.loadQuickLookups() }
    if (editorOpen) {
        QuickActionEditor(
            action = editing,
            state = state.content,
            properties = state.properties,
            lookups = lookups,
            onSave = { action ->
                viewModel.saveQuickAction(action)
                editorOpen = false
            },
            onDismiss = { editorOpen = false },
        )
    } else {
        QuickActionsSheet(
            state = state.content,
            properties = state.properties,
            lookups = lookups,
            onEdit = { action ->
                editing = action
                editorOpen = true
            },
            onDelete = viewModel::deleteQuickAction,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Quick actions from a note's ⋮ (#119): those of data source [databaseId], the note's, made and
 * changed there as in the database. A deleted one offers Undo in the note's [snackbar] ([scope]:
 * the note's, so it outlives the sheet).
 */
@Composable
internal fun NoteQuickActions(databaseId: String, snackbar: SnackbarHostState, scope: CoroutineScope, onDismiss: () -> Unit) {
    val viewModel: DatabaseViewModel = viewModel(key = "database-$databaseId", factory = DatabaseViewModel.factory(databaseId))
    val deleted = stringResource(R.string.quick_action_deleted)
    val undo = stringResource(R.string.undo)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is DatabaseEvent.QuickActionDeleted) {
                scope.launch {
                    val result = snackbar.showSnackbar(deleted, actionLabel = undo, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteQuickAction(event)
                }
            }
        }
    }
    QuickActionsFlow(viewModel, onDismiss)
}
