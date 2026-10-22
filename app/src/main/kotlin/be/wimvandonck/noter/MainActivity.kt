package be.wimvandonck.noter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.noter.data.store.AppSettings
import be.wimvandonck.noter.logic.LauncherShortcuts
import be.wimvandonck.noter.logic.SharedText
import be.wimvandonck.noter.ui.AppNavigation
import be.wimvandonck.noter.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as NoterApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by app.repository.settingsFlow().collectAsStateWithLifecycle(initialValue = null as AppSettings?)
            val current = settings ?: return@setContent
            AppTheme(themeMode = current.themeMode, dynamicColor = current.dynamicColor) {
                AppNavigation()
            }
        }
        // Not again when the screen turns: the share was taken then already.
        if (savedInstanceState == null) receive(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    /**
     * A launcher shortcut (#76): the note or database it names opens. Text or a link shared from
     * another app (#63): Home asks where to save it.
     */
    private fun receive(intent: Intent?) {
        if (intent?.action == Shortcuts.ACTION_OPEN) {
            intent.getStringExtra(Shortcuts.EXTRA_KEY)?.takeIf(LauncherShortcuts::isTarget)?.let { app.opens.value = it }
            return
        }
        if (intent?.action != Intent.ACTION_SEND) return
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE)
        SharedText.of(subject, intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())?.let { app.shares.value = it }
    }

    override fun onStart() {
        super.onStart()
        app.repository.onForeground()
    }
}
