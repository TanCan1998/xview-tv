package io.github.tancan1998.xviewtv.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@Composable
fun XViewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.res.colorResource(R.color.xview_primary),
            onPrimary = androidx.compose.ui.res.colorResource(R.color.xview_on_primary),
            onBackground = XViewColors.text,
            onSurface = XViewColors.text,
            onSurfaceVariant = XViewColors.muted,
            secondary = androidx.compose.ui.res.colorResource(R.color.xview_secondary),
            error = androidx.compose.ui.res.colorResource(R.color.xview_error),
            background = XViewColors.background,
            surface = XViewColors.surface,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}
