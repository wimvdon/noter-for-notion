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
import be.wimvandonck.noter.logic.Placement
import be.wimvandonck.noter.logic.Workspace
import be.wimvandonck.noter.logic.WorkspaceWay
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

    /**
     * Notion accepted the token: who it acts as and its workspace. [replaced]: the workspace was
     * already added another way, which this one took the place of.
     */
    data class Ok(val info: TokenInfo, val replaced: WorkspaceWay? = null, val way: WorkspaceWay? = null) : KeyStatus

    /** Not added: workspace [name] is already added in a way that sees more ([way]). */
    data class Refused(val name: String, val way: WorkspaceWay?) : KeyStatus

    data class Failed(val message: String) : KeyStatus

    /** The person refused access in Notion ("Connect to Notion"). */
    data object Cancelled : KeyStatus

    /** A workspace was removed ([name]); [last]: it was the only one, and the notes here were deleted. */
    data class Removed(val name: String, val last: Boolean) : KeyStatus
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** Every workspace with a token, the main one first. */
    val workspaces: List<Workspace> = emptyList(),
    val keyStatus: KeyStatus = KeyStatus.None,
    /** "Connect to Notion" (OAuth) is set up in this build. */
    val canSignIn: Boolean = false,
    /** The profile picture of the person of the top (main) workspace, also shown on Home. */
    val avatarUrl: String? = null,
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
                    is SignInStatus.Done -> keyStatus.value = KeyStatus.Ok(status.info, status.replaced, WorkspaceWay.SIGN_IN)
                    is SignInStatus.Refused -> keyStatus.value = KeyStatus.Refused(status.name, status.way)
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
        keyStore.avatarUrl,
    ) { settings, workspaces, status, avatarUrl ->
        SettingsUiState(settings, workspaces, status, signIn.available, avatarUrl)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(canSignIn = signIn.available),
    )

    /** The address of Notion's page for "Connect to Notion" (opened in the browser). */
    fun startSignIn(): String {
        keyStatus.value = KeyStatus.None
        return signIn.start()
    }

    /**
     * Tests [token] with Notion and keeps it, with its workspace's name: as the main token when
     * there is none yet, in place of the token of the same workspace, or next to the others; not
     * when its workspace is already added in a way that sees more ([WorkspaceTokens.place]).
     */
    fun addWorkspace(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return
        keyStatus.value = KeyStatus.Testing
        viewModelScope.launch {
            keyStatus.value = try {
                val info = repository.tokenInfo(trimmed)
                val added = WorkspaceToken(trimmed, info.name, info.workspace.orEmpty(), info.kind, workspaceId = info.workspaceId)
                when (val placement = keyStore.keep(added, info.avatarUrl)) {
                    is Placement.Refused -> KeyStatus.Refused(WorkspaceTokens.label(placement.existing), WorkspaceTokens.way(placement.existing))
                    is Placement.Added -> {
                        refreshAfterChange()
                        KeyStatus.Ok(info, placement.replaced?.let(WorkspaceTokens::way), WorkspaceTokens.way(added))
                    }
                }
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

    /**
     * Moves the workspace at [from] to [to] (positions in [SettingsUiState.workspaces]). The one on
     * top becomes the main token, and Home and Settings show its person's profile picture.
     */
    fun moveWorkspace(from: Int, to: Int) {
        val main = keyStore.main.value
        val (newMain, others) = WorkspaceTokens.move(main, keyStore.extras.value, from, to)
        if (newMain.token != main.token) keyStore.setMain(newMain)
        keyStore.setExtras(others)
        if (newMain.token == main.token) return
        viewModelScope.launch {
            runCatching { keyStore.setAvatarUrl(repository.tokenInfo(newMain.token).avatarUrl) }
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
