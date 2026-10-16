package com.impca201.noter.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impca201.noter.ui.main.DatabaseScreen
import com.impca201.noter.ui.main.MainScreen
import com.impca201.noter.ui.main.MainViewModel
import com.impca201.noter.ui.note.NoteScreen
import com.impca201.noter.ui.settings.SettingsScreen
import com.impca201.noter.ui.settings.SettingsViewModel
import com.impca201.noter.ui.views.ViewsScreen
import com.impca201.noter.ui.views.ViewsViewModel
import com.impca201.noter.ui.theme.Motion
import com.impca201.noter.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
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
 * The app's screens as a small back stack: the main screen at the bottom, notes, databases and
 * the settings on top. Four screens don't need a navigation library.
 */
@Composable
fun AppNavigation() {
    // Entries: "main", "settings", "views", "note:<page id>" or "database:<database id>".
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
        // Cleared after the closing animation, which still draws the screen.
        scope.launch {
            delay(CLEAR_DELAY_MS)
            if (closed !in stack) stores.clear(closed)
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

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
        if (top == MAIN) {
            MainScreen(
                viewModel = mainViewModel,
                onOpenNote = { push(NOTE + it) },
                onOpenDatabase = { push(DATABASE + it) },
                onOpenSettings = { push(SETTINGS) },
                onOpenViews = { push(VIEWS) },
            )
        } else {
            CompositionLocalProvider(LocalViewModelStoreOwner provides ScreenOwner(stores.store(top), application)) {
                when {
                    top == SETTINGS -> SettingsScreen(
                        viewModel = viewModel(factory = SettingsViewModel.Factory),
                        onBack = ::pop,
                        onOpenViews = { push(VIEWS) },
                    )
                    top == VIEWS || top.startsWith(EDIT_VIEW) -> ViewsScreen(
                        viewModel = viewModel(factory = ViewsViewModel.Factory),
                        onBack = ::pop,
                        editViewId = top.removePrefix(EDIT_VIEW).takeIf { top.startsWith(EDIT_VIEW) },
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
                        onEditView = { push(EDIT_VIEW + it) },
                    )
                }
            }
        }
    }
}

private const val CLEAR_DELAY_MS = 1_000L
private const val MAIN = "main"
private const val SETTINGS = "settings"
private const val VIEWS = "views"

/** Manage views with one view's setup open. */
private const val EDIT_VIEW = "views:"
private const val NOTE = "note:"
private const val DATABASE = "database:"
