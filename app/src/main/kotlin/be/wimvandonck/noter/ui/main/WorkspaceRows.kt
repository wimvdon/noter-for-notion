package be.wimvandonck.noter.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.logic.NotionDates
import be.wimvandonck.noter.ui.common.PageIconView
import be.wimvandonck.noter.ui.theme.SkeletonLine

/**
 * A database or page in a list (Home, Search): its icon, name, the workspace it is in ([workspace],
 * with several) and when it was last edited. Tap opens it; a long press opens its menu (pin or
 * unpin). A pin at the end shows it is pinned.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceRow(
    title: String,
    icon: PageIcon?,
    isDatabase: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: (() -> Unit)?,
    modifier: Modifier = Modifier,
    edited: String = "",
    workspace: String? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val locale = LocalConfiguration.current.locales[0]
    val pinLabel = stringResource(if (pinned) R.string.action_unpin else R.string.action_pin)
    Box(modifier) {
        ListItem(
            headlineContent = {
                Text(title.ifBlank { stringResource(R.string.untitled) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = if (edited.isNotBlank() || !workspace.isNullOrBlank()) {
                {
                    val editedText = if (edited.isNotBlank()) stringResource(R.string.browse_edited, NotionDates.display(edited, locale)) else ""
                    Text(listOf(workspace.orEmpty(), editedText).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                null
            },
            leadingContent = {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (icon != null) {
                        PageIconView(icon, size = 24.dp)
                    } else {
                        Icon(
                            if (isDatabase) Icons.Rounded.TableChart else Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            trailingContent = if (pinned) {
                {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = stringResource(R.string.section_pinned),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onTogglePin?.let {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        }
                    },
                )
                .semantics {
                    if (onTogglePin != null) customActions = listOf(CustomAccessibilityAction(pinLabel) { onTogglePin(); true })
                },
        )
        if (onTogglePin != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(pinLabel) },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                    onClick = {
                        menu = false
                        onTogglePin()
                    },
                )
            }
        }
    }
}

/** A row of [WorkspaceRow] for [item] from Notion's search. */
@Composable
internal fun WorkspaceRow(
    item: WorkspaceItem,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: (() -> Unit)?,
    modifier: Modifier = Modifier,
    workspace: String? = null,
) = WorkspaceRow(item.title, item.icon, item.isDatabase, pinned, onClick, onTogglePin, modifier, item.lastEditedTime, workspace)

/**
 * A workspace's title on Home, with several: tap folds its databases and pages away, or shows
 * them again.
 */
@Composable
internal fun WorkspaceHeader(name: String, collapsed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    val stateText = stringResource(if (collapsed) R.string.section_collapsed else R.string.section_expanded)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .semantics {
                heading()
                stateDescription = stateText
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.rotate(rotation))
    }
}

/** A section's title in a list. */
@Composable
internal fun ListHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** Placeholder lines while a list loads. */
@Composable
internal fun LoadingRows(modifier: Modifier = Modifier, count: Int = 6) {
    val loading = stringResource(R.string.loading)
    Column(
        modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        repeat(count) { SkeletonLine(Modifier.fillMaxWidth(if (it % 2 == 0) 0.7f else 0.5f)) }
    }
}
