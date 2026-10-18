package be.wimvandonck.noter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.api.NotionErrors
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.model.ThemeMode
import be.wimvandonck.noter.data.notion.TokenInfo
import be.wimvandonck.noter.data.notion.TokenKind
import be.wimvandonck.noter.data.store.ApiKeyStore
import be.wimvandonck.noter.data.store.AppSettings
import be.wimvandonck.noter.logic.WorkspaceToken
import be.wimvandonck.noter.logic.WorkspaceTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The result of the last key test, for the Connection section. */
sealed interface KeyStatus {
    data object None : KeyStatus

    data object Testing : KeyStatus

    /** Notion accepted the token: who it acts as (a person, or a connection's name). */
    data class Ok(val info: TokenInfo) : KeyStatus

    data class Failed(val message: String) : KeyStatus

    data object Removed : KeyStatus
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val hasKey: Boolean = false,
    /** The stored token's kind (null: not known yet). */
    val tokenKind: TokenKind? = null,
    val keyStatus: KeyStatus = KeyStatus.None,
    val pendingCount: Int = 0,
    /** Tokens for other workspaces (#58). */
    val extras: List<WorkspaceToken> = emptyList(),
    /** The result of the last token added for another workspace. */
    val extraStatus: KeyStatus = KeyStatus.None,
)

class SettingsViewModel(
    private val repository: NotesRepository,
    private val keyStore: ApiKeyStore,
) : ViewModel() {
    private val keyStatus = MutableStateFlow<KeyStatus>(KeyStatus.None)
    private val extraStatus = MutableStateFlow<KeyStatus>(KeyStatus.None)

    val state: StateFlow<SettingsUiState> = combine(
        combine(
            repository.settingsFlow(),
            keyStore.hasKey,
            keyStore.kind,
            keyStatus,
            repository.pendingCount,
        ) { settings, hasKey, kind, status, pending ->
            SettingsUiState(settings, hasKey, kind, status, pending)
        },
        keyStore.extras,
        extraStatus,
    ) { state, extras, status -> state.copy(extras = extras, extraStatus = status) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(hasKey = keyStore.get().isNotBlank()))

    fun currentKey(): String = keyStore.get()

    fun saveKey(key: String) {
        val trimmed = key.trim()
        keyStatus.value = KeyStatus.Testing
        viewModelScope.launch {
            keyStatus.value = try {
                val info = repository.tokenInfo(trimmed)
                keyStore.set(trimmed, info.kind)
                keyStore.setAvatarUrl(info.avatarUrl)
                repository.onForeground()
                KeyStatus.Ok(info)
            } catch (e: NotionException) {
                KeyStatus.Failed(NotionErrors.of(e))
            }
        }
    }

    fun removeKey() {
        viewModelScope.launch {
            keyStore.set("")
            repository.clearData()
            keyStatus.value = KeyStatus.Removed
        }
    }

    /** Tests [token] with Notion and keeps it next to the main one, with the workspace's name. */
    fun addWorkspace(token: String) {
        val trimmed = token.trim()
        extraStatus.value = KeyStatus.Testing
        viewModelScope.launch {
            extraStatus.value = try {
                val info = repository.tokenInfo(trimmed)
                val added = WorkspaceToken(trimmed, info.name, info.workspace.orEmpty())
                keyStore.setExtras(WorkspaceTokens.add(keyStore.extras.value, keyStore.get(), added))
                refreshAfterChange()
                KeyStatus.Ok(info)
            } catch (e: NotionException) {
                KeyStatus.Failed(NotionErrors.of(e))
            }
        }
    }

    fun removeWorkspace(token: String) {
        keyStore.setExtras(WorkspaceTokens.remove(keyStore.extras.value, token))
        extraStatus.value = KeyStatus.None
        viewModelScope.launch { refreshAfterChange() }
    }

    /** Home's recent databases and pages come from every workspace: ask again after a change. */
    private suspend fun refreshAfterChange() {
        repository.onForeground()
        try {
            repository.refreshRecent()
        } catch (_: NotionException) {
            // Home asks again at the next start.
        }
    }

    fun syncNow() = repository.onForeground()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.updateSettings { it.copy(themeMode = mode) } }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.updateSettings { it.copy(dynamicColor = enabled) } }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NoterApp
                SettingsViewModel(app.repository, app.apiKeyStore)
            }
        }
    }
}
