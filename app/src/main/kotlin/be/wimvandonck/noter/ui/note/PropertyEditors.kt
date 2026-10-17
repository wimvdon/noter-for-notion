package be.wimvandonck.noter.ui.note

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PageRef
import be.wimvandonck.noter.data.model.PropertySchema
import be.wimvandonck.noter.data.model.PropertyValue
import be.wimvandonck.noter.data.model.SelectOption
import be.wimvandonck.noter.data.notion.PropertyCodec
import be.wimvandonck.noter.logic.NotionDates
import be.wimvandonck.noter.ui.common.OptionChip
import java.time.LocalTime
import java.util.Locale

/** Lookups some editors need (people, related pages), loaded when first opened. */
class PropertyLookups(
    val users: List<NotionUser>,
    val relatedPages: Map<String, List<PageRef>?>,
    val loadUsers: () -> Unit,
    val loadRelated: (String) -> Unit,
)

/** One property of the note: its name and an editor that fits its type. */
@Composable
fun PropertyRow(
    property: NoteProperty,
    schema: PropertySchema?,
    lookups: PropertyLookups,
    onChange: (NoteProperty, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            property.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp).padding(end = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            if (property.isEditable) {
                PropertyEditor(property, schema, lookups, onChange)
            } else {
                ReadOnlyValue(property)
            }
        }
    }
}

@Composable
private fun PropertyEditor(
    property: NoteProperty,
    schema: PropertySchema?,
    lookups: PropertyLookups,
    onChange: (NoteProperty, Boolean) -> Unit,
) {
    fun set(value: PropertyValue, debounce: Boolean = false) = onChange(property.copy(value = value), debounce)
    when (val value = property.value) {
        is PropertyValue.Text -> InlineTextField(value.markdown, KeyboardType.Text, singleLine = false) {
            set(PropertyValue.Text(it), debounce = true)
        }
        is PropertyValue.Number -> InlineTextField(
            value.value?.let { PropertyCodec.formatNumber(it) }.orEmpty(),
            KeyboardType.Decimal,
            singleLine = true,
        ) { text ->
            val parsed = text.replace(',', '.').toDoubleOrNull()
            if (text.isBlank() || parsed != null) set(PropertyValue.Number(parsed), debounce = true)
        }
        is PropertyValue.Plain -> {
            val keyboard = when (property.type) {
                "url" -> KeyboardType.Uri
                "email" -> KeyboardType.Email
                else -> KeyboardType.Phone
            }
            val context = LocalContext.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    InlineTextField(value.value.orEmpty(), keyboard, singleLine = true) { set(PropertyValue.Plain(it), debounce = true) }
                }
                val target = value.value?.trim().orEmpty()
                if (target.isNotEmpty()) {
                    IconButton(onClick = {
                        val uri = when (property.type) {
                            "email" -> "mailto:$target"
                            "phone_number" -> "tel:$target"
                            else -> if (target.contains("://")) target else "https://$target"
                        }
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri())) }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.action_open_link))
                    }
                }
            }
        }
        is PropertyValue.Checkbox -> Checkbox(checked = value.checked, onCheckedChange = { set(PropertyValue.Checkbox(it)) })
        is PropertyValue.Select -> SelectEditor(
            selected = listOfNotNull(value.option),
            options = schema?.options.orEmpty(),
            multiple = false,
            title = property.name,
        ) { set(PropertyValue.Select(it.firstOrNull())) }
        is PropertyValue.MultiSelect -> SelectEditor(
            selected = value.options,
            options = schema?.options.orEmpty(),
            multiple = true,
            title = property.name,
        ) { set(PropertyValue.MultiSelect(it)) }
        is PropertyValue.Date -> DateEditor(value) { set(it) }
        is PropertyValue.People -> PeopleEditor(value.users, lookups, property.name) { set(PropertyValue.People(it)) }
        is PropertyValue.Relation -> RelationEditor(value.ids, schema?.relationDatabaseId, lookups, property.name) {
            set(PropertyValue.Relation(it))
        }
        is PropertyValue.Files, is PropertyValue.ReadOnly -> ReadOnlyValue(property)
    }
}

@Composable
fun ReadOnlyValue(property: NoteProperty) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val text = when (property.type) {
        "created_time", "last_edited_time" -> NotionDates.display((property.value as? PropertyValue.ReadOnly)?.display, locale)
        else -> PropertyCodec.displayText(property.value)
    }
    Text(
        text.ifBlank { "—" },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A borderless text field that keeps its own text while typing and reports every change. */
@Composable
private fun InlineTextField(
    value: String,
    keyboardType: KeyboardType,
    singleLine: Boolean,
    onChange: (String) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!focused && value != text) text = value }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            if (text.isEmpty()) {
                Text(stringResource(R.string.property_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
            inner()
        },
    )
}

/** Shows the chosen values; tapping opens a sheet to change them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValueChips(empty: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (empty) {
            Text(stringResource(R.string.property_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        } else {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectEditor(
    selected: List<SelectOption>,
    options: List<SelectOption>,
    multiple: Boolean,
    title: String,
    onChange: (List<SelectOption>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ValueChips(empty = selected.isEmpty(), onClick = { open = true }) {
        selected.forEach { OptionChip(it) }
    }
    if (open) {
        val all = options + selected.filter { s -> options.none { it.name == s.name } }
        ChoiceSheet(
            title = title,
            items = all,
            key = { it.name },
            isSelected = { item -> selected.any { it.name == item.name } },
            multiple = multiple,
            label = { OptionChip(it) },
            searchText = { it.name },
            onDismiss = { open = false },
            onToggle = { item ->
                if (multiple) {
                    onChange(if (selected.any { it.name == item.name }) selected.filterNot { it.name == item.name } else selected + item)
                } else {
                    onChange(if (selected.any { it.name == item.name }) emptyList() else listOf(item))
                    open = false
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleEditor(
    selected: List<NotionUser>,
    lookups: PropertyLookups,
    title: String,
    onChange: (List<NotionUser>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ValueChips(empty = selected.isEmpty(), onClick = { open = true; lookups.loadUsers() }) {
        selected.forEach { AssistChip(onClick = { open = true; lookups.loadUsers() }, label = { Text(it.name.ifBlank { "?" }) }) }
    }
    if (open) {
        val all = (selected + lookups.users).distinctBy { it.id }
        ChoiceSheet(
            title = title,
            items = all,
            key = { it.id },
            isSelected = { user -> selected.any { it.id == user.id } },
            multiple = true,
            label = { Text(it.name.ifBlank { "?" }) },
            searchText = { it.name },
            onDismiss = { open = false },
            onToggle = { user ->
                onChange(if (selected.any { it.id == user.id }) selected.filterNot { it.id == user.id } else selected + user)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelationEditor(
    ids: List<String>,
    relatedDatabaseId: String?,
    lookups: PropertyLookups,
    title: String,
    onChange: (List<String>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val pages = relatedDatabaseId?.let { lookups.relatedPages[it] }
    LaunchedEffect(relatedDatabaseId, ids.isNotEmpty()) {
        if (relatedDatabaseId != null && ids.isNotEmpty()) lookups.loadRelated(relatedDatabaseId)
    }
    val untitled = stringResource(R.string.untitled)
    ValueChips(empty = ids.isEmpty(), onClick = { if (relatedDatabaseId != null) { open = true; lookups.loadRelated(relatedDatabaseId) } }) {
        if (pages == null) {
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.linked_pages, ids.size, ids.size),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            ids.forEach { id ->
                val name = pages.firstOrNull { it.id.replace("-", "") == id.replace("-", "") }?.title?.ifBlank { untitled } ?: "…"
                AssistChip(onClick = { open = true }, label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }
    }
    if (open) {
        val normalized = ids.map { it.replace("-", "") }.toSet()
        ChoiceSheet(
            title = title,
            items = pages.orEmpty(),
            key = { it.id },
            isSelected = { it.id.replace("-", "") in normalized },
            multiple = true,
            label = { Text(it.title.ifBlank { untitled }) },
            searchText = { it.title },
            loading = pages == null,
            onDismiss = { open = false },
            onToggle = { page ->
                val id = page.id.replace("-", "")
                onChange(if (id in normalized) ids.filterNot { it.replace("-", "") == id } else ids + page.id)
            },
        )
    }
}

/** A bottom sheet to pick one or more items, with a search field for long lists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(
    title: String,
    items: List<T>,
    key: (T) -> String,
    isSelected: (T) -> Boolean,
    multiple: Boolean,
    label: @Composable (T) -> Unit,
    searchText: (T) -> String,
    onDismiss: () -> Unit,
    onToggle: (T) -> Unit,
    loading: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (items.size > 8) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (loading) {
                Text(
                    stringResource(R.string.loading),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (items.isEmpty()) {
                Text(
                    stringResource(R.string.no_options),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val filtered = if (query.isBlank()) items else items.filter { searchText(it).contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(filtered, key = key) { item ->
                    val selected = isSelected(item)
                    ListItem(
                        headlineContent = { label(item) },
                        trailingContent = {
                            if (multiple) {
                                Checkbox(checked = selected, onCheckedChange = null)
                            } else if (selected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable(role = if (multiple) Role.Checkbox else Role.RadioButton) { onToggle(item) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateEditor(value: PropertyValue.Date, onChange: (PropertyValue.Date) -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    ValueChips(empty = value.start == null, onClick = { pickDate = true }) {
        Text(NotionDates.displayRange(value.start, value.end, locale), style = MaterialTheme.typography.bodyLarge)
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = NotionDates.pickerMillis(value.start) ?: System.currentTimeMillis())
        var withTime by remember { mutableStateOf(value.start?.let { NotionDates.hasTime(it) } == true) }
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    pickDate = false
                    if (millis != null) {
                        val oldTime = value.start?.let { NotionDates.parseTime(it, java.time.ZoneId.systemDefault()) }
                        val time = if (withTime) oldTime ?: LocalTime.of(9, 0) else null
                        onChange(PropertyValue.Date(NotionDates.fromPicker(millis, time), value.end))
                        if (withTime) pickTime = true
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pickDate = false
                        onChange(PropertyValue.Date(null, null))
                    }) { Text(stringResource(R.string.clear)) }
                    TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { withTime = !withTime }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.include_time), modifier = Modifier.weight(1f))
                Switch(checked = withTime, onCheckedChange = null)
            }
        }
    }
    if (pickTime && value.start != null) {
        val initial = NotionDates.parseTime(value.start, java.time.ZoneId.systemDefault()) ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initial.hour, initial.minute, android.text.format.DateFormat.is24HourFormat(LocalContext.current))
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    pickTime = false
                    onChange(PropertyValue.Date(NotionDates.withTime(value.start, LocalTime.of(state.hour, state.minute)), value.end))
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
fun Bullet(size: androidx.compose.ui.unit.Dp = 6.dp) {
    val color = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.Canvas(Modifier.size(size)) { drawCircle(color) }
}
