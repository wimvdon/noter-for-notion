package be.wimvandonck.noter.ui.note

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.PropertyLayout
import be.wimvandonck.noter.logic.PropertyLayouts

/**
 * Arranging a database's properties in place, where they are shown in the note: drag a property
 * by its handle to change the order, and past the "Hidden" line to hide it (or back to show it);
 * the eye does the same with one tap. Applies to every note of the database. A notebook's Filters
 * panel arranges its rows of chips the same way ([shownTitle], no [onDone]).
 */
@Composable
fun PropertyArranger(
    names: List<String>,
    layout: PropertyLayout?,
    onArrange: (shown: List<String>, hidden: List<String>) -> Unit,
    onDone: (() -> Unit)?,
    shownTitle: String = stringResource(R.string.properties_shown),
) {
    val sections = remember(names, layout) { PropertyLayouts.sections(names, layout) }
    // A working copy (shown, the "Hidden" line, hidden), so a dragged row moves at once; it
    // follows the layout when nothing is being dragged.
    val entries = remember { mutableStateListOf<String>() }
    val fromLayout = sections.first + HIDDEN_LINE + sections.second
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    if (dragging == null && entries.toList() != fromLayout) {
        entries.clear()
        entries.addAll(fromLayout)
    }
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    val haptics = LocalHapticFeedback.current
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)

    fun move(name: String, by: Int): Boolean {
        val from = entries.indexOf(name)
        val to = from + by
        if (from < 0 || to !in entries.indices) return false
        entries.add(to, entries.removeAt(from))
        return true
    }

    fun save() {
        val line = entries.indexOf(HIDDEN_LINE)
        onArrange(entries.subList(0, line).toList(), entries.subList(line + 1, entries.size).toList())
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shownTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .then(if (onDone == null) Modifier.height(ROW_HEIGHT).padding(top = 20.dp) else Modifier)
                    .semantics { heading() },
            )
            if (onDone != null) TextButton(onClick = onDone) { Text(stringResource(R.string.done)) }
        }
        entries.forEach { name ->
            key(name) {
                if (name == HIDDEN_LINE) {
                    Text(
                        stringResource(R.string.properties_hidden),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .height(ROW_HEIGHT)
                            .padding(top = 20.dp)
                            .semantics { heading() },
                    )
                } else {
                    val isDragging = dragging == name
                    val isShown = entries.indexOf(name) < entries.indexOf(HIDDEN_LINE)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT)
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffset
                                    shadowElevation = 8f
                                }
                            }
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(moveUp) { move(name, -1).also { if (it) save() } },
                                    CustomAccessibilityAction(moveDown) { move(name, 1).also { if (it) save() } },
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.DragIndicator,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(end = 8.dp, top = 12.dp, bottom = 12.dp)
                                .pointerInput(name) {
                                    detectDragGestures(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragging = name
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            // Past half a row: swap with the neighbour (or the "Hidden" line)
                                            // and keep the row under the finger.
                                            if (dragOffset > rowPx / 2 && move(name, 1)) dragOffset -= rowPx
                                            if (dragOffset < -rowPx / 2 && move(name, -1)) dragOffset += rowPx
                                        },
                                        onDragEnd = {
                                            dragging = null
                                            dragOffset = 0f
                                            save()
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            dragOffset = 0f
                                        },
                                    )
                                },
                        )
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isShown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconToggleButton(
                            checked = isShown,
                            onCheckedChange = {
                                val shown = entries.subList(0, entries.indexOf(HIDDEN_LINE)).toList()
                                val hidden = entries.subList(entries.indexOf(HIDDEN_LINE) + 1, entries.size).toList()
                                if (isShown) onArrange(shown - name, listOf(name) + hidden) else onArrange(shown + name, hidden - name)
                            },
                        ) {
                            Icon(
                                if (isShown) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = stringResource(R.string.property_shown, name),
                                tint = if (isShown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The line between the shown and the hidden properties in the working list (not a property name Notion allows). */
private const val HIDDEN_LINE = "\u0000hidden"

private val ROW_HEIGHT = 48.dp
