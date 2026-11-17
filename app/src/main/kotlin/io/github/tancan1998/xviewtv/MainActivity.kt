package io.github.tancan1998.xviewtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.tancan1998.xviewtv.core.designsystem.XViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppLocale { XViewTheme { AppContent() } } }
    }
}
