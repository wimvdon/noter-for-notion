package be.wimvandonck.noter.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.api.NotionErrors
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.logic.NewDatabases
import be.wimvandonck.noter.logic.StarterProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The New database dialog while it is open (#71): where the database goes ([parentPageId], null:
 * the top of [workspace]), whether Notion is making it, and what went wrong.
 */
data class NewDatabaseState(
    val parentPageId: String?,
    val workspace: String? = null,
    val creating: Boolean = false,
    val error: String? = null,
)

/** Opens, sends and closes the New database dialog for a ViewModel (Home's +, a page's ⋮). */
class DatabaseCreation(private val scope: CoroutineScope, private val repository: NotesRepository) {
    private val _state = MutableStateFlow<NewDatabaseState?>(null)

    /** The open dialog, null when it is closed. */
    val state: StateFlow<NewDatabaseState?> get() = _state

    fun open(parentPageId: String?, workspace: String? = null) {
        _state.value = NewDatabaseState(parentPageId, workspace)
    }

    fun dismiss() {
        if (_state.value?.creating != true) _state.value = null
    }

    /** Asks Notion for the database; [onCreated] gets the ID to open it by. The dialog stays open with the error when it fails. */
    fun create(title: String, titleName: String, starters: List<StarterProperty>, names: Map<StarterProperty, String>, onCreated: (String) -> Unit) {
        val target = _state.value ?: return
        if (target.creating || title.isBlank()) return
        _state.value = target.copy(creating = true, error = null)
        scope.launch {
            try {
                val id = repository.createDatabase(NewDatabases.request(title, target.parentPageId, titleName, starters, names), target.workspace)
                _state.value = null
                onCreated(id)
            } catch (e: NotionException) {
                _state.value = target.copy(creating = false, error = NotionErrors.of(e))
            }
        }
    }
}

/** A database's name and the properties it starts with, besides its title. */
@Composable
fun NewDatabaseDialog(
    state: NewDatabaseState,
    onCreate: (title: String, titleName: String, starters: List<StarterProperty>, names: Map<StarterProperty, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(listOf(StarterProperty.TAGS.name, StarterProperty.DATE.name)) }
    val names = mapOf(
        StarterProperty.TAGS to stringResource(R.string.new_database_tags),
        StarterProperty.DATE to stringResource(R.string.new_database_date),
        StarterProperty.DONE to stringResource(R.string.new_database_done),
        StarterProperty.LINK to stringResource(R.string.new_database_link),
    )
    val titleName = stringResource(R.string.new_database_title_property)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_database)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.new_database_name)) },
                    singleLine = true,
                    enabled = !state.creating,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.new_database_properties),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    StarterProperty.entries.forEach { starter ->
                        val on = starter.name in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !state.creating, role = Role.Checkbox) {
                                    picked = if (on) picked - starter.name else picked + starter.name
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, enabled = !state.creating)
                            Spacer(Modifier.width(12.dp))
                            Text(names.getValue(starter), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                state.error?.let {
                    Text(
                        stringResource(R.string.new_database_failed, errorText(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title, titleName, StarterProperty.entries.filter { it.name in picked }, names) },
                enabled = title.isNotBlank() && !state.creating,
            ) {
                if (state.creating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.new_database_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.creating) { Text(stringResource(R.string.cancel)) } },
    )
}
