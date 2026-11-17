package io.github.tancan1998.xviewtv.core.designsystem

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

/** TV Material owns focus/keys/semantics. Its TV Surface has no touch-click handler. */
@Composable
fun XButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scale: ButtonScale = ButtonDefaults.scale(),
    shape: ButtonShape = ButtonDefaults.shape(),
    colors: ButtonColors = ButtonDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val action by rememberUpdatedState(onClick)
    val interactions = remember { MutableInteractionSource() }
    val registry = LocalTvHomeFocusRegistry.current
    val leafFocus = remember { FocusRequester() }
    val trackedFocus = if (registry != null && isTelevision()) Modifier.focusRequester(leafFocus).onFocusChanged {
        if (it.isFocused) registry.last = leafFocus
    } else Modifier
    val touch = if (isTelevision()) Modifier else Modifier.pointerInput(enabled) {
        if (enabled) detectTapGestures(
            onPress = { position ->
                val press = PressInteraction.Press(position)
                interactions.emit(press)
                if (tryAwaitRelease()) interactions.emit(PressInteraction.Release(press))
                else interactions.emit(PressInteraction.Cancel(press))
            },
            onTap = { action() },
        )
    }
    Button(onClick = onClick, modifier = modifier.then(trackedFocus).then(touch), enabled = enabled,
        scale = scale, shape = shape, colors = colors, contentPadding = contentPadding,
        interactionSource = interactions, content = content)
}
