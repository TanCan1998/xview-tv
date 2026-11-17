package io.github.tancan1998.xviewtv

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@Composable
internal fun AppLocale(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val system = LocalConfiguration.current
    val preferences = remember { AppPreferences(base) }
    var language by remember { mutableStateOf(preferences.language()) }
    DisposableEffect(base) {
        val prefs = base.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "language") language = preferences.language()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val config = remember(language, system) {
        Configuration(system).apply {
            setLocale(Locale.forLanguageTag(language))
        }
    }
    val context = remember(base, config) { base.createConfigurationContext(config) }
    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config, content = content)
}
