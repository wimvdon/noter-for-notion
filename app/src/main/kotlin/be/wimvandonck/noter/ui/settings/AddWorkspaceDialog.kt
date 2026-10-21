package be.wimvandonck.noter.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.R
import be.wimvandonck.noter.logic.WorkspaceWay
import be.wimvandonck.noter.logic.WorkspaceWays

/**
 * Add workspace: first how ([WorkspaceWay], with what each is best for), then, for a token, where
 * Notion makes one and a field to paste it. Connect to Notion goes to Notion at once ([onSignIn]).
 */
@Composable
fun AddWorkspaceDialog(
    canSignIn: Boolean,
    onSignIn: () -> Unit,
    onAddToken: (String) -> Unit,
    onOpenNotion: (WorkspaceWay) -> Unit,
    onDismiss: () -> Unit,
) {
    var way by rememberSaveable { mutableStateOf(WorkspaceWays.default(canSignIn)) }
    var pasting by rememberSaveable { mutableStateOf(false) }
    var token by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pasting) title(way) else stringResource(R.string.add_workspace)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!pasting) {
                    Column(Modifier.selectableGroup()) {
                        WorkspaceWays.available(canSignIn).forEach { option ->
                            WayRow(option, selected = option == way) { way = option }
                        }
                    }
                } else {
                    Text(
                        stringResource(if (way == WorkspaceWay.PERSONAL) R.string.personal_steps else R.string.connection_steps),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onOpenNotion(way) }) {
                        Text(stringResource(if (way == WorkspaceWay.PERSONAL) R.string.make_personal_token else R.string.make_connection))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(stringResource(R.string.workspace_token)) },
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
                }
            }
        },
        confirmButton = {
            when {
                pasting -> TextButton(onClick = { onAddToken(token) }, enabled = token.isNotBlank()) {
                    Text(stringResource(R.string.add_workspace))
                }
                WorkspaceWays.needsToken(way) -> TextButton(onClick = { pasting = true }) {
                    Text(stringResource(R.string.action_continue))
                }
                else -> TextButton(onClick = onSignIn) { Text(stringResource(R.string.action_continue)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** One way to add a workspace: a radio button, its name and what it is best for. */
@Composable
private fun WayRow(way: WorkspaceWay, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title(way), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(
                    when (way) {
                        WorkspaceWay.SIGN_IN -> R.string.way_sign_in_text
                        WorkspaceWay.PERSONAL -> R.string.way_personal_text
                        WorkspaceWay.CONNECTION -> R.string.way_connection_text
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun title(way: WorkspaceWay): String = stringResource(
    when (way) {
        WorkspaceWay.SIGN_IN -> R.string.sign_in
        WorkspaceWay.PERSONAL -> R.string.workspace_personal
        WorkspaceWay.CONNECTION -> R.string.way_connection_title
    },
)
