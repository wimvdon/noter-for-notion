package be.wimvandonck.noter.data.api

/**
 * "Connect to Notion" (OAuth): Noter's public connection in Notion and its sign-in helper
 * (`worker/`, see worker/README.md). Without both, Settings only offers tokens.
 */
object SignInConfig {
    /** The connection's OAuth client ID (Notion: the connection → Configuration). Not a secret. */
    const val CLIENT_ID = ""

    /** The sign-in helper's address, e.g. `https://noter-sign-in.example.workers.dev`. */
    const val HELPER_URL = ""

    val enabled: Boolean get() = CLIENT_ID.isNotBlank() && HELPER_URL.isNotBlank()
}
