package io.github.tancan1998.xviewtv.core.designsystem

import android.content.res.Resources
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration

class UiStrings(private val resources: Resources) {
    operator fun invoke(id: Int, vararg args: Any): String = resources.getString(id, *args)
}

@Composable
fun rememberStrings(): UiStrings {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { UiStrings(context.resources) }
}

@Composable
fun isTelevision(): Boolean = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

/** Suppresses shell navigation while the full-window media viewer owns input. */
val LocalMediaOverlay = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.MutableState<Boolean>?> { null }

/** Map protocol reasons to localized, safe UI text without exposing response bodies or credentials. */
fun UiStrings.failure(reason: String): String = invoke(when {
    "失效" in reason -> R.string.error_expired
    "限流" in reason -> R.string.error_limited
    "受限" in reason || "人工验证" in reason -> R.string.error_restricted
    "重定向" in reason -> R.string.error_redirect
    "身份" in reason || "账号状态" in reason -> R.string.error_identity
    "协议" in reason || "结构" in reason || "响应" in reason -> R.string.error_protocol
    else -> R.string.network_error
})
