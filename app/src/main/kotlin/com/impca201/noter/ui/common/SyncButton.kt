package com.impca201.noter.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.impca201.noter.R

/**
 * The sync button in an app bar: its icon tells whether changes are waiting ([pending]) or Notion
 * can't be reached ([offline]); tapping it sends what is waiting and gets the latest from Notion.
 * With [alwaysShown] false it only appears while something is waiting or the phone is offline.
 */
@Composable
fun SyncButton(
    pending: Int,
    offline: Boolean,
    syncing: Boolean,
    onSync: () -> Unit,
    alwaysShown: Boolean = true,
) {
    AnimatedVisibility(visible = alwaysShown || pending > 0 || offline || syncing, enter = fadeIn(), exit = fadeOut()) {
        val description = when {
            offline -> stringResource(R.string.status_offline)
            pending > 0 -> pluralStringResource(R.plurals.status_pending, pending, pending)
            else -> stringResource(R.string.sync_now)
        }
        IconButton(onClick = onSync, enabled = !syncing) {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    when {
                        offline -> Icons.Rounded.CloudOff
                        pending > 0 -> Icons.Rounded.CloudSync
                        else -> Icons.Rounded.CloudDone
                    },
                    contentDescription = description,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
