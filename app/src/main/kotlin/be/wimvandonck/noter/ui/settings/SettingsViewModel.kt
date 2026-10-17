package be.wimvandonck.noter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.data.NotesRepository
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.model.ThemeMode
import be.wimvandonck.noter.data.store.ApiKeyStore
import be.wimvandonck.noter.data.store.AppSettings
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

    data class Ok(val botName: String) : KeyStatus

    data class Failed(val message: String) : KeyStatus

    data object Removed : KeyStatus
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val hasKey: Boolean = false,
    val keyStatus: KeyStatus = KeyStatus.None,
    val pendingCount: Int = 0,
)

class SettingsViewModel(
    private val repository: NotesRepository,
    private val keyStore: ApiKeyStore,
) : ViewModel() {
    private val keyStatus = MutableStateFlow<KeyStatus>(KeyStatus.None)

    val state: StateFlow<SettingsUiState> = combine(
        repository.settingsFlow(),
        keyStore.hasKey,
        keyStatus,
        repository.pendingCount,
    ) { settings, hasKey, status, pending ->
        SettingsUiState(settings, hasKey, status, pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(hasKey = keyStore.get().isNotBlank()))

    fun currentKey(): String = keyStore.get()

    fun saveKey(key: String) {
        val trimmed = key.trim()
        keyStatus.value = KeyStatus.Testing
        viewModelScope.launch {
            keyStatus.value = try {
                val name = repository.testKey(trimmed)
                keyStore.set(trimmed)
                repository.onForeground()
                KeyStatus.Ok(name)
            } catch (e: NotionException) {
                KeyStatus.Failed(e.message.orEmpty())
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
