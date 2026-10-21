package be.wimvandonck.noter.data

import be.wimvandonck.noter.data.api.NotionErrors
import be.wimvandonck.noter.data.api.NotionException
import be.wimvandonck.noter.data.api.SignInConfig
import be.wimvandonck.noter.data.notion.TokenInfo
import be.wimvandonck.noter.data.store.ApiKeyStore
import be.wimvandonck.noter.logic.SealedBox
import be.wimvandonck.noter.logic.SignInReturn
import be.wimvandonck.noter.logic.SignIns
import be.wimvandonck.noter.logic.WorkspaceTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Base64

/** Where "Connect to Notion" stands, for Settings. */
sealed interface SignInStatus {
    data object Idle : SignInStatus

    data object Working : SignInStatus

    /** Connected: who the token acts as and its workspace. */
    data class Done(val info: TokenInfo) : SignInStatus

    /** The person refused access in Notion. */
    data object Cancelled : SignInStatus

    data class Failed(val message: String) : SignInStatus
}

/**
 * "Connect to Notion" (OAuth, see [SignIns]). [start] gives the address of Notion's page;
 * Notion and the sign-in helper send the person back to `<scheme>://oauth` (SignInActivity),
 * which hands the link to [finish]. The token is kept like a pasted one ([WorkspaceTokens.signIn]).
 */
class SignIn(
    private val keyStore: ApiKeyStore,
    private val repository: NotesRepository,
    /** The app's ID, which is also the scheme of its link back (the debug build has its own). */
    private val scheme: String,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<SignInStatus>(SignInStatus.Idle)
    val status: StateFlow<SignInStatus> get() = _status

    val available: Boolean get() = SignInConfig.enabled

    /** Settings showed the result: back to [SignInStatus.Idle] (not shown again next time). */
    fun shown() {
        val now = _status.value
        if (now !is SignInStatus.Working) _status.compareAndSet(now, SignInStatus.Idle)
    }

    /** Notion's page for choosing the pages Noter may use, with a fresh key for this sign-in. */
    fun start(): String {
        val keys = SealedBox.newKeys()
        keyStore.pendingSignIn = Base64.getEncoder().encodeToString(keys.privateKey)
        _status.value = SignInStatus.Idle
        return SignIns.authorizeUrl(SignInConfig.CLIENT_ID, SignInConfig.HELPER_URL, SignIns.state(scheme, keys.publicKey))
    }

    /** The link back from the helper ([returned]); ignored when no sign-in was started. */
    fun finish(returned: SignInReturn?) {
        val pending = keyStore.pendingSignIn ?: return
        if (returned == null) return
        keyStore.pendingSignIn = null
        when (returned) {
            is SignInReturn.Refused -> _status.value =
                if (returned.error == "access_denied" || returned.error == "cancelled") SignInStatus.Cancelled else SignInStatus.Failed(NotionErrors.SIGN_IN_FAILED)
            is SignInReturn.Sealed -> {
                val grant = SealedBox.open(Base64.getDecoder().decode(pending), returned.sealed)?.let(SignIns::grant)
                if (grant == null) {
                    _status.value = SignInStatus.Failed(NotionErrors.SIGN_IN_FAILED)
                    return
                }
                _status.value = SignInStatus.Working
                scope.launch {
                    _status.value = try {
                        val info = repository.tokenInfo(grant.accessToken)
                        val token = grant.toToken().copy(
                            kind = info.kind,
                            name = info.name.ifBlank { grant.person },
                            workspace = info.workspace?.ifBlank { null } ?: grant.workspaceName,
                        )
                        val oldMain = keyStore.main.value
                        val (main, others) = WorkspaceTokens.signIn(oldMain, keyStore.extras.value, token)
                        if (main != oldMain) {
                            keyStore.setMain(main)
                            keyStore.setAvatarUrl(info.avatarUrl)
                        }
                        keyStore.setExtras(others)
                        repository.onForeground()
                        try {
                            repository.refreshRecent()
                        } catch (_: NotionException) {
                            // Home asks again at the next start.
                        }
                        SignInStatus.Done(info)
                    } catch (e: NotionException) {
                        SignInStatus.Failed(NotionErrors.of(e))
                    }
                }
            }
        }
    }
}
