package be.wimvandonck.noter.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
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
import be.wimvandonck.noter.ui.theme.AppShapes
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
        item(key = "connection") {
            SettingsSection(title = stringResource(R.string.section_connection)) {
                SettingsBlock { ConnectionSettings(state, viewModel) }
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
    // How to make a personal access token, step by step (Notion's form changes rarely).
    listOf(R.string.connection_step_1, R.string.connection_step_2, R.string.connection_step_3).forEachIndexed { index, step ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(step), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
    TextButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, TOKENS_URL.toUri())) }
    }) {
        Text(stringResource(R.string.connection_open_tokens))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(stringResource(R.string.api_key)) },
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
        is KeyStatus.Ok -> if (status.info.kind == TokenKind.PERSONAL) {
            StatusText(stringResource(R.string.key_ok, status.info.name), error = false)
        }
        is KeyStatus.Failed -> StatusText(stringResource(R.string.key_failed, errorText(status.message)), error = true)
        KeyStatus.Removed -> StatusText(stringResource(R.string.key_removed), error = false)
        else -> Unit
    }
    // An internal connection's token works, but sees less: say what, and what to do about it.
    if (state.hasKey && state.tokenKind == TokenKind.CONNECTION) ConnectionTokenWarning()
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

/** Shown while the saved token is an internal connection's: what it can't do, and the fix. */
@Composable
private fun ConnectionTokenWarning() {
    Surface(
        shape = AppShapes.Item,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Info, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.connection_token_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.connection_token_body), style = MaterialTheme.typography.bodyMedium)
            }
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

/** Notion's page for making personal access tokens (its Developer portal). */
private const val TOKENS_URL = "https://www.notion.so/developers/tokens"
private const val PRIVACY_URL = "https://github.com/wimvdon/noter-for-notion/blob/main/PRIVACY.md"
