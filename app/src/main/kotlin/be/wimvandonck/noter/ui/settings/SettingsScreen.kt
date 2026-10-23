package be.wimvandonck.noter.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.noter.R
import coil3.compose.AsyncImage
import be.wimvandonck.noter.ui.common.errorText
import be.wimvandonck.noter.data.model.ThemeMode
import be.wimvandonck.noter.data.notion.TokenKind
import be.wimvandonck.noter.logic.WorkspaceWay
import be.wimvandonck.noter.ui.theme.AppearanceSettings
import be.wimvandonck.noter.ui.theme.DragHandle
import be.wimvandonck.noter.ui.theme.ReorderableColumn
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
 * Every workspace (#58), each added its own way: Connect to Notion (the button), or under Advanced
 * ([AddWorkspaceDialog]) a personal access token or a connection's token. Each can be removed; a
 * new token is tested before it is kept. They are sorted by dragging; the top one is the main
 * token, with its person's profile picture (also Home's).
 */
@Composable
private fun WorkspaceSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var adding by rememberSaveable { mutableStateOf(false) }
    Text(
        stringResource(R.string.workspaces_help),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val avatar = stringResource(R.string.workspace_avatar)
    ReorderableColumn(
        items = state.workspaces,
        key = { it.id },
        onMove = viewModel::moveWorkspace,
    ) { workspace ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The top workspace's person is the one whose picture Home shows.
            if (workspace == state.workspaces.first() && state.avatarUrl != null) {
                AsyncImage(
                    model = state.avatarUrl,
                    contentDescription = avatar,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(12.dp))
            }
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
            if (state.workspaces.size > 1) DragHandle(handleModifier)
        }
    }
    when (val status = state.keyStatus) {
        is KeyStatus.Ok -> {
            val name = status.info.workspace?.ifBlank { null } ?: status.info.name
            StatusText(
                if (status.replaced == null) {
                    stringResource(R.string.workspace_added, name)
                } else {
                    stringResource(R.string.workspace_replaced, name, wayTitle(status.way), wayTitle(status.replaced))
                },
                error = false,
            )
        }
        is KeyStatus.Refused -> StatusText(stringResource(R.string.workspace_refused, status.name, wayTitle(status.way)), error = true)
        is KeyStatus.Failed -> StatusText(stringResource(R.string.key_failed, errorText(status.message)), error = true)
        KeyStatus.Cancelled -> StatusText(stringResource(R.string.sign_in_cancelled), error = false)
        is KeyStatus.Removed -> StatusText(
            if (status.last) stringResource(R.string.key_removed) else stringResource(R.string.workspace_removed, status.name),
            error = false,
        )
        else -> Unit
    }
    val testing = state.keyStatus == KeyStatus.Testing
    // Without Connect to Notion in this build, the tokens are the only way: then they get the button.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = {
                if (state.canSignIn) {
                    val url = viewModel.startSignIn()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                } else {
                    adding = true
                }
            },
            enabled = !testing,
        ) {
            if (testing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(if (state.canSignIn) R.string.sign_in else R.string.add_workspace))
        }
        if (state.canSignIn) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { adding = true }, enabled = !testing) { Text(stringResource(R.string.advanced)) }
        }
    }
    if (adding) {
        AddWorkspaceDialog(
            onAddToken = { token ->
                adding = false
                viewModel.addWorkspace(token)
            },
            onOpenNotion = { way ->
                val url = if (way == WorkspaceWay.PERSONAL) TOKENS_URL else CONNECTIONS_URL
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            },
            onDismiss = { adding = false },
        )
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
