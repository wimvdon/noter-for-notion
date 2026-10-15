package com.impca201.noter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impca201.noter.data.store.AppSettings
import com.impca201.noter.ui.AppNavigation
import com.impca201.noter.ui.theme.AppTheme

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
