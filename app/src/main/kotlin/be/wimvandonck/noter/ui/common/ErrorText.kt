package be.wimvandonck.noter.ui.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.api.NotionErrors

/** An error kept in the state (see NotionErrors) in the user's language. */
fun errorText(resources: Resources, error: String): String = when (error) {
    NotionErrors.OFFLINE -> resources.getString(R.string.error_offline)
    NotionErrors.NOT_FOUND -> resources.getString(R.string.error_not_found)
    NotionErrors.BUSY -> resources.getString(R.string.error_busy)
    NotionErrors.KEY_REJECTED -> resources.getString(R.string.sync_key_rejected)
    NotionErrors.NO_DATA_SOURCE -> resources.getString(R.string.error_no_data_source)
    else -> error
}

@Composable
fun errorText(error: String): String = errorText(LocalResources.current, error)
