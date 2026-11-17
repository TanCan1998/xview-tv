package io.github.tancan1998.xviewtv.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/** Keeps the actual leaf target, not a nested group's synthetic focus target. */
class TvHomeFocusRegistry {
    var last: FocusRequester? = null
    fun restore(): Boolean = runCatching { last?.requestFocus() == true }.getOrDefault(false)
}
val LocalTvHomeFocusRegistry = staticCompositionLocalOf<TvHomeFocusRegistry?> { null }
