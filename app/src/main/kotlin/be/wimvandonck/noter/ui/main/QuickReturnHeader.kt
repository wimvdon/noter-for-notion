package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import be.wimvandonck.noter.logic.QuickReturn
import kotlin.math.roundToInt

/** How far the header has scrolled away; hand [connection] to the scrolling content's parent. */
@Stable
class QuickReturnHeaderState {
    var offset by mutableFloatStateOf(0f)
        private set
    private var height by mutableFloatStateOf(0f)

    /** Moves the header before the content scrolls, and only lets the content have what is left. */
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val before = offset
            offset = QuickReturn.offset(offset, available.y, height)
            return Offset(0f, offset - before)
        }
    }

    fun show() {
        offset = 0f
    }

    internal fun measured(height: Int) {
        this.height = height.toFloat()
        offset = QuickReturn.offset(offset, 0f, this.height)
    }
}

@Composable
fun rememberQuickReturnHeaderState(): QuickReturnHeaderState = remember { QuickReturnHeaderState() }

/** [content], shortened from the top by how far it has scrolled away. */
@Composable
fun QuickReturnHeader(state: QuickReturnHeaderState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(
        content = { Box(Modifier.onSizeChanged { state.measured(it.height) }) { content() } },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val offset = state.offset.roundToInt().coerceIn(-placeable.height, 0)
        layout(placeable.width, placeable.height + offset) { placeable.place(0, offset) }
    }
}
