package be.wimvandonck.noter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.SignIn
import be.wimvandonck.noter.data.SignInStatus
import be.wimvandonck.noter.data.api.NotionErrors
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.model.ThemeMode
import be.wimvandonck.noter.data.notion.TokenInfo
import be.wimvandonck.noter.data.store.ApiKeyStore
import be.wimvandonck.noter.data.store.AppSettings
import be.wimvandonck.noter.logic.Workspace
import be.wimvandonck.noter.logic.WorkspaceToken
import be.wimvandonck.noter.logic.WorkspaceTokens
import be.wimvandonck.noter.logic.Workspaces
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The result of the last token added or removed, for the Workspaces section. */
sealed interface KeyStatus {
    data object None : KeyStatus

    data object Testing : KeyStatus

    /** Notion accepted the token: who it acts as and its workspace. */
    data class Ok(val info: TokenInfo) : KeyStatus

    data class Failed(val message: String) : KeyStatus

    /** The person refused access in Notion ("Connect to Notion"). */
    data object Cancelled : KeyStatus

    /** A workspace was removed ([name]); [last]: it was the only one, and the notes here were deleted. */
    data class Removed(val name: String, val last: Boolean) : KeyStatus
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val hasKey: Boolean = false,
    /** Every workspace with a token, the main one first. */
    val workspaces: List<Workspace> = emptyList(),
    val keyStatus: KeyStatus = KeyStatus.None,
    val pendingCount: Int = 0,
    /** "Connect to Notion" (OAuth) is set up in this build. */
    val canSignIn: Boolean = false,
)

class SettingsViewModel(
    private val repository: NotesRepository,
    private val keyStore: ApiKeyStore,
    private val signIn: SignIn,
) : ViewModel() {
    private val keyStatus = MutableStateFlow<KeyStatus>(KeyStatus.None)

    init {
        // "Connect to Notion" finishes after the browser, possibly with this screen already open.
        viewModelScope.launch {
            signIn.status.collect { status ->
                when (status) {
                    SignInStatus.Idle -> Unit
                    SignInStatus.Working -> keyStatus.value = KeyStatus.Testing
                    is SignInStatus.Done -> keyStatus.value = KeyStatus.Ok(status.info)
                    SignInStatus.Cancelled -> keyStatus.value = KeyStatus.Cancelled
                    is SignInStatus.Failed -> keyStatus.value = KeyStatus.Failed(status.message)
                }
                signIn.shown()
            }
        }
    }

    val state: StateFlow<SettingsUiState> = combine(
        repository.settingsFlow(),
        combine(keyStore.main, keyStore.extras) { main, extras -> Workspaces.of(listOf(main) + extras) },
        keyStatus,
        repository.pendingCount,
    ) { settings, workspaces, status, pending ->
        SettingsUiState(settings, workspaces.isNotEmpty(), workspaces, status, pending, signIn.available)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(hasKey = keyStore.get().isNotBlank(), canSignIn = signIn.available),
    )

    /** The address of Notion's page for "Connect to Notion" (opened in the browser). */
    fun startSignIn(): String {
        keyStatus.value = KeyStatus.None
        return signIn.start()
    }

    /**
     * Tests [token] with Notion and keeps it: as the main token when there is none yet, else next
     * to the others, with its workspace's name.
     */
    fun addWorkspace(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return
        keyStatus.value = KeyStatus.Testing
        viewModelScope.launch {
            keyStatus.value = try {
                val info = repository.tokenInfo(trimmed)
                val main = keyStore.get()
                when {
                    main.isBlank() -> {
                        keyStore.set(trimmed, info.kind, info.name, info.workspace.orEmpty())
                        keyStore.setAvatarUrl(info.avatarUrl)
                    }
                    main == trimmed -> keyStore.setMainInfo(info.kind, info.name, info.workspace.orEmpty())
                    else -> {
                        val added = WorkspaceToken(trimmed, info.name, info.workspace.orEmpty(), info.kind)
                        keyStore.setExtras(WorkspaceTokens.add(keyStore.extras.value, main, added))
                    }
                }
                refreshAfterChange()
                KeyStatus.Ok(info)
            } catch (e: NotionException) {
                KeyStatus.Failed(NotionErrors.of(e))
            }
        }
    }

    /**
     * Removes workspace [id]. The main one makes way for the next; without any token left, the
     * notes copied to this phone are deleted. Another workspace's notes stay (Notion answers "not
     * found" for them from then on).
     */
    fun removeWorkspace(id: String) {
        val main = keyStore.main.value
        val extras = keyStore.extras.value
        val removed = (listOf(main) + extras).firstOrNull { it.token.isNotBlank() && Workspaces.id(it.token) == id } ?: return
        keyStore.setCollapsed(keyStore.collapsed.value - id)
        viewModelScope.launch {
            when {
                removed.token != main.token -> {
                    keyStore.setExtras(WorkspaceTokens.remove(extras, removed.token))
                    repository.forgetWorkspace(id)
                }
                extras.isEmpty() -> {
                    keyStore.set("")
                    repository.clearData()
                    keyStatus.value = KeyStatus.Removed(WorkspaceTokens.label(removed), last = true)
                    return@launch
                }
                else -> {
                    val next = extras.first()
                    keyStore.setMain(next)
                    keyStore.setExtras(extras.drop(1))
                    repository.forgetWorkspace(id)
                    // Home shows the new main token's owner's picture.
                    runCatching { keyStore.setAvatarUrl(repository.tokenInfo(next.token).avatarUrl) }
                }
            }
            keyStatus.value = KeyStatus.Removed(WorkspaceTokens.label(removed), last = false)
            refreshAfterChange()
        }
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
                SettingsViewModel(app.repository, app.apiKeyStore, app.signIn)
            }
        }
    }
}
