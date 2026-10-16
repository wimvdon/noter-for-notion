package com.impca201.noter.ui.note

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.impca201.noter.R
import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.PropertyLayout
import com.impca201.noter.logic.PropertyLayouts

/**
 * Chooses how a database's properties are shown (for every note of the database): drag the
 * handle to change the order, the eye to show a property or collapse it, and the card icon to
 * show it on the note's card in the overview (at most three).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyLayoutSheet(
    properties: List<NoteProperty>,
    layout: PropertyLayout?,
    onReorder: (List<String>) -> Unit,
    onToggleShown: (String) -> Unit,
    onToggleOnCard: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val names = remember(properties) { properties.filter { it.type != "title" }.map { it.name } }
    val ordered = remember(names, layout) { PropertyLayouts.ordered(names, layout) }
    // A working copy, so a dragged row moves at once; it follows the layout when not dragging.
    val items = remember { mutableStateListOf<String>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    if (dragging == null && items.toList() != ordered) {
        items.clear()
        items.addAll(ordered)
    }
    val shown = PropertyLayouts.shownNames(names, layout)
    val onCard = layout?.onCard.orEmpty()
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    val haptics = LocalHapticFeedback.current
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)

    fun move(name: String, by: Int): Boolean {
        val from = items.indexOf(name)
        val to = from + by
        if (from < 0 || to !in items.indices) return false
        items.add(to, items.removeAt(from))
        return true
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.properties_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.properties_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
            )
            items.forEach { name ->
                key(name) {
                    val isDragging = dragging == name
                    val isShown = name in shown
                    val isOnCard = name in onCard
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
                                    CustomAccessibilityAction(moveUp) { move(name, -1).also { if (it) onReorder(items.toList()) } },
                                    CustomAccessibilityAction(moveDown) { move(name, 1).also { if (it) onReorder(items.toList()) } },
                                )
                            }
                            .padding(start = 8.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.DragIndicator,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(12.dp)
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
                                            // Past half a row: swap with the neighbour and keep the row under the finger.
                                            if (dragOffset > rowPx / 2 && move(name, 1)) dragOffset -= rowPx
                                            if (dragOffset < -rowPx / 2 && move(name, -1)) dragOffset += rowPx
                                        },
                                        onDragEnd = {
                                            dragging = null
                                            dragOffset = 0f
                                            onReorder(items.toList())
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
                        IconToggleButton(checked = isShown, onCheckedChange = { onToggleShown(name) }) {
                            Icon(
                                if (isShown) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = stringResource(R.string.property_shown, name),
                                tint = if (isShown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconToggleButton(
                            checked = isOnCard,
                            onCheckedChange = { onToggleOnCard(name) },
                            enabled = isOnCard || onCard.size < PropertyLayouts.MAX_ON_CARD,
                        ) {
                            Icon(
                                if (isOnCard) Icons.Rounded.Dashboard else Icons.Outlined.Dashboard,
                                contentDescription = stringResource(R.string.property_on_card, name),
                                tint = if (isOnCard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ROW_HEIGHT = 56.dp
