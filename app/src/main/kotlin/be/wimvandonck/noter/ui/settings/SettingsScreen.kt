package be.wimvandonck.noter.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.noter.R
import be.wimvandonck.noter.ui.common.errorText
import be.wimvandonck.noter.data.model.ThemeMode
import be.wimvandonck.noter.data.notion.TokenKind
import be.wimvandonck.noter.ui.theme.AppearanceSettings
import be.wimvandonck.noter.ui.theme.SettingsBlock
import be.wimvandonck.noter.ui.theme.SettingsSection
import be.wimvandonck.noter.ui.theme.SettingsFooter
import be.wimvandonck.noter.ui.theme.SettingsScaffold
import be.wimvandonck.noter.ui.theme.rememberAppVersion

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        backLabel = stringResource(R.string.back),
        onBack = onBack,
    ) {
        item(key = "workspaces") {
            SettingsSection(title = stringResource(R.string.section_workspaces)) {
                SettingsBlock { WorkspaceSettings(state, viewModel) }
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

}

/**
 * Every workspace with a token (#58): a personal access token for a workspace of one's own, or a
 * connection's token for pages someone shared. Each can be removed; a new one is tested before it
 * is kept. The first becomes the main token.
 */
@Composable
private fun WorkspaceSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var token by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    if (state.canSignIn) {
        // "Connect to Notion" (OAuth): the person chooses the pages in Notion, no token to copy.
        Text(
            stringResource(R.string.sign_in_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                val url = viewModel.startSignIn()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            },
            enabled = state.keyStatus != KeyStatus.Testing,
        ) { Text(stringResource(R.string.sign_in)) }
    }
    Text(
        stringResource(if (state.canSignIn) R.string.workspaces_help_tokens else R.string.workspaces_help),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.workspaces.forEach { workspace ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(workspace.name.ifBlank { stringResource(R.string.workspace_unnamed) }, style = MaterialTheme.typography.titleSmall)
                val kind = when {
                    workspace.signedIn -> stringResource(R.string.workspace_signed_in)
                    else -> when (workspace.kind) {
                        TokenKind.PERSONAL -> stringResource(R.string.workspace_personal)
                        TokenKind.CONNECTION -> stringResource(R.string.workspace_connection)
                        null -> ""
                    }
                }
                val person = workspace.person.takeIf { it.isNotBlank() && it != workspace.name }.orEmpty()
                val details = listOf(kind, person).filter { it.isNotBlank() }.joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = { viewModel.removeWorkspace(workspace.id) }) {
                Text(stringResource(R.string.remove_workspace))
            }
        }
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
    when (val status = state.keyStatus) {
        is KeyStatus.Ok -> StatusText(stringResource(R.string.workspace_added, status.info.workspace?.ifBlank { null } ?: status.info.name), error = false)
        is KeyStatus.Failed -> StatusText(stringResource(R.string.key_failed, errorText(status.message)), error = true)
        KeyStatus.Cancelled -> StatusText(stringResource(R.string.sign_in_cancelled), error = false)
        is KeyStatus.Removed -> StatusText(
            if (status.last) stringResource(R.string.key_removed) else stringResource(R.string.workspace_removed, status.name),
            error = false,
        )
        else -> Unit
    }
    val onAdd = {
        viewModel.addWorkspace(token)
        token = ""
    }
    val addContent: @Composable () -> Unit = {
        if (state.keyStatus == KeyStatus.Testing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(R.string.add_workspace))
    }
    val canAdd = token.isNotBlank() && state.keyStatus != KeyStatus.Testing
    // With "Connect to Notion" above, that is the main action and adding a token the second one.
    if (state.canSignIn) {
        FilledTonalButton(onClick = onAdd, enabled = canAdd) { addContent() }
    } else {
        Button(onClick = onAdd, enabled = canAdd) { addContent() }
    }
    // The two kinds of token, each with what it sees and where Notion makes one.
    TokenKindHelp(stringResource(R.string.workspaces_help_personal), stringResource(R.string.make_personal_token)) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, TOKENS_URL.toUri())) }
    }
    TokenKindHelp(stringResource(R.string.workspaces_help_connection), stringResource(R.string.make_connection)) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, CONNECTIONS_URL.toUri())) }
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

/** What a kind of token sees, with the link to where Notion makes one right below it. */
@Composable
private fun TokenKindHelp(text: String, link: String, onOpen: () -> Unit) {
    Column {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinkButton(link, onOpen)
    }
}

/** A link to one of Notion's pages, with the "opens elsewhere" arrow. */
@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
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

/** Notion's page for making personal access tokens (its Developer portal). */
private const val TOKENS_URL = "https://www.notion.so/developers/tokens"

/** Notion's page for making connections (internal integrations), whose pages are shared with them. */
private const val CONNECTIONS_URL = "https://www.notion.so/profile/integrations"
private const val PRIVACY_URL = "https://github.com/wimvdon/noter-for-notion/blob/main/PRIVACY.md"
