package be.wimvandonck.noter.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import be.wimvandonck.noter.NoterApp
import be.wimvandonck.noter.logic.LauncherShortcuts
import be.wimvandonck.noter.ui.main.DatabaseScreen
import be.wimvandonck.noter.ui.main.MainScreen
import be.wimvandonck.noter.ui.main.MainViewModel
import be.wimvandonck.noter.ui.main.SearchScreen
import be.wimvandonck.noter.ui.note.NoteScreen
import be.wimvandonck.noter.ui.settings.SettingsScreen
import be.wimvandonck.noter.ui.settings.SettingsViewModel
import be.wimvandonck.noter.ui.theme.Motion
import be.wimvandonck.noter.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** One ViewModelStore per open screen, so a note's ViewModel is cleared (and saves) when it closes. */
class ScreenStores : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun store(entry: String): ViewModelStore = stores.getOrPut(entry) { ViewModelStore() }

    fun clear(entry: String) {
        stores.remove(entry)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

private class ScreenOwner(
    override val viewModelStore: ViewModelStore,
    private val application: Application,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply { set(APPLICATION_KEY, application) }
}

/**
 * The app's screens as a small back stack: Home at the bottom; Search, notes, databases and the
 * settings on top. Five screens don't need a navigation library.
 */
@Composable
fun AppNavigation() {
    // Entries: "main", "search", "settings", "note:<page id>" or "database:<database id>".
    var stack by rememberSaveable { mutableStateOf(listOf(MAIN)) }
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
    val stores: ScreenStores = viewModel()
    val application = LocalContext.current.applicationContext as Application
    val offset = with(LocalDensity.current) { 30.dp.roundToPx() }
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()

    fun push(entry: String) {
        stack = stack + entry
    }

    fun pop() {
        if (stack.size <= 1) return
        val closed = stack.last()
        stack = stack.dropLast(1)
        // Search starts afresh each time it is opened.
        if (closed == SEARCH && SEARCH !in stack) mainViewModel.resetSearch()
        // Cleared after the closing animation, which still draws the screen.
        scope.launch {
            delay(CLEAR_DELAY_MS)
            if (closed !in stack) stores.clear(closed)
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // A launcher shortcut (#76): its note or database, on top of Home.
    val opens = (application as? NoterApp)?.opens
    val opening by (opens ?: MutableStateFlow(null)).collectAsStateWithLifecycle()
    LaunchedEffect(opening) {
        val key = opening ?: return@LaunchedEffect
        opens?.value = null
        while (stack.size > 1) pop()
        push(key)
    }

    // Something shared from another app (#63): back to Home, which asks where it goes.
    val sharing by mainViewModel.sharing.collectAsStateWithLifecycle()
    LaunchedEffect(sharing) {
        if (sharing != null) while (stack.size > 1) pop()
    }

    AnimatedContent(
        targetState = stack,
        transitionSpec = {
            if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                Motion.sharedAxisX(forward = targetState.size >= initialState.size, offsetPx = offset)
            }
        },
        contentKey = { it.last() },
        label = "screens",
    ) { entries ->
        val top = entries.last()
        when (top) {
            MAIN -> MainScreen(
                viewModel = mainViewModel,
                onOpenNote = { push(NOTE + it) },
                onOpenDatabase = { push(DATABASE + it) },
                onOpenSearch = { push(SEARCH) },
                onOpenSettings = { push(SETTINGS) },
            )
            // Shares Home's ViewModel: what is pinned, and the search kept while a result is open.
            SEARCH -> SearchScreen(
                viewModel = mainViewModel,
                onBack = ::pop,
                onOpenNote = { push(NOTE + it) },
                onOpenDatabase = { push(DATABASE + it) },
            )
            else -> CompositionLocalProvider(LocalViewModelStoreOwner provides ScreenOwner(stores.store(top), application)) {
                when {
                    top == SETTINGS -> SettingsScreen(
                        viewModel = viewModel(factory = SettingsViewModel.Factory),
                        onBack = ::pop,
                    )
                    top.startsWith(NOTE) -> NoteScreen(
                        pageId = top.removePrefix(NOTE),
                        onBack = ::pop,
                        onOpenNote = { push(NOTE + it) },
                        onOpenDatabase = { push(DATABASE + it) },
                    )
                    top.startsWith(DATABASE) -> DatabaseScreen(
                        databaseId = top.removePrefix(DATABASE),
                        onBack = ::pop,
                        onOpenNote = { push(NOTE + it) },
                    )
                }
            }
        }
    }
}

private const val CLEAR_DELAY_MS = 1_000L
private const val MAIN = "main"
private const val SEARCH = "search"
private const val SETTINGS = "settings"
// The same keys as launcher shortcuts use (#76).
private const val NOTE = LauncherShortcuts.NOTE
private const val DATABASE = LauncherShortcuts.DATABASE
