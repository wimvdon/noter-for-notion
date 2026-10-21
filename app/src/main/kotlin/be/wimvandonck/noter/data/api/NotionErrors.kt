package be.wimvandonck.noter.data.api

/**
 * What went wrong talking to Notion, as kept in the screens' state: a fixed key for the common
 * cases (the screen says them in the user's language, see ui/common/ErrorText.kt), else Notion's
 * own message (its validation errors are specific and only in English).
 */
object NotionErrors {
    const val OFFLINE = "error:offline"
    const val NOT_FOUND = "error:not_found"
    const val BUSY = "error:busy"
    const val KEY_REJECTED = "error:key_rejected"
    const val NO_DATA_SOURCE = "error:no_data_source"

    /** "Connect to Notion" didn't work (Notion refused it, or what came back couldn't be read). */
    const val SIGN_IN_FAILED = "error:sign_in"

    fun of(e: NotionException): String = when {
        e.code == NO_DATA_SOURCE -> NO_DATA_SOURCE
        e.isOffline -> OFFLINE
        e.status == 401 -> KEY_REJECTED
        e.status == 404 -> NOT_FOUND
        e.isTemporary -> BUSY
        else -> e.message.orEmpty()
    }
}
