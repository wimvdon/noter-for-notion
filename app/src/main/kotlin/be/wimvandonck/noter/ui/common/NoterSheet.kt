package be.wimvandonck.noter.ui.common

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch

/** Whether the keyboard is open in the sheet, and how to close it. */
private class SheetKeyboard {
    var open = false
    var hide: () -> Unit = {}
}

/**
 * A bottom sheet, the way every sheet in the app opens:
 * - always fully open (no half-open stop that jumps up as soon as the list is scrolled);
 * - it stops just below the status bar, so a tall sheet doesn't jump around the status bar's
 *   padding while it grows or is dragged;
 * - scrolling its list never closes it ([SheetScrollGuard]): only a swipe down that starts with the
 *   list at its top, or on the handle, a tap beside it or Back do;
 * - with the keyboard open, or just after it opened or closed, those close the keyboard first
 *   ([SheetDismissGuard]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoterSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismissGuard = remember { SheetDismissGuard() }
    val scrollGuard = remember { SheetScrollGuard() }
    val keepScrollInSheet = remember(scrollGuard) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                return Offset(0f, scrollGuard.afterDrag(consumed.y, available.y))
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(0f, scrollGuard.afterFling(available.y))
        }
    }
    // Read inside the sheet (its own window has the keyboard), used by the close below.
    val keyboard = remember { SheetKeyboard() }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    ModalBottomSheet(
        onDismissRequest = {
            if (dismissGuard.allowsDismiss(SystemClock.uptimeMillis(), keyboard.open)) {
                currentOnDismiss()
            } else {
                keyboard.hide()
                scope.launch { sheetState.show() }
            }
        },
        sheetState = sheetState,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        val imeOpen = WindowInsets.isImeVisible
        val focus = LocalFocusManager.current
        val softKeyboard = LocalSoftwareKeyboardController.current
        SideEffect {
            keyboard.open = imeOpen
            keyboard.hide = {
                focus.clearFocus()
                softKeyboard?.hide()
            }
        }
        LaunchedEffect(imeOpen) { dismissGuard.changed(SystemClock.uptimeMillis()) }
        // Back with the keyboard open closes the keyboard, not the sheet.
        BackHandler(enabled = imeOpen) { keyboard.hide() }
        Column(Modifier.nestedScroll(keepScrollInSheet)) { content() }
    }
}
