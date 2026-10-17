package be.wimvandonck.noter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.noter.data.store.AppSettings
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
    }

    override fun onStart() {
        super.onStart()
        app.repository.onForeground()
    }
}
