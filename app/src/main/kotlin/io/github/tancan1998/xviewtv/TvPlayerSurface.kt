package io.github.tancan1998.xviewtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.tv.material3.*
import io.github.tancan1998.xviewtv.core.designsystem.*
import io.github.tancan1998.xviewtv.core.domain.TvPlaybackInputPolicy
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR
import kotlinx.coroutines.delay

/** One focus owner and one inactivity timer for every TV playback control. */
@Composable
internal fun TvPlayerSurface(player: Player, title: String, position: Long, duration: Long,
    rewind: Int, forward: Int, back: () -> Unit, previous: (() -> Unit)?, next: (() -> Unit)?,
    qualities: @Composable () -> Unit, surface: @Composable () -> Unit) {
    val ui = rememberStrings()
    val focusManager = LocalFocusManager.current
    var visible by remember { mutableStateOf(false) }
    var videoFocused by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    var stateNotice by remember { mutableStateOf("") }
    var noticeSerial by remember { mutableIntStateOf(0) }
    var lastSeekAt by remember { mutableLongStateOf(0) }
    var playing by remember { mutableStateOf(player.playWhenReady) }
    var rate by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    val progressFocus = remember { FocusRequester() }
    val externalFocus = remember { FocusRequester() }
    val hiddenFocus = remember { FocusRequester() }
    var progressFocused by remember { mutableStateOf(false) }
    val controlKeys = remember { setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter) }
    var wakeKey by remember { mutableStateOf<Key?>(null) }
    fun hide() { visible = false }
    fun togglePlayback() {
        if (player.playWhenReady) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
        stateNotice = ui(if (player.playWhenReady) UiR.string.play_video else UiR.string.pause_video)
        noticeSerial++
    }
    LaunchedEffect(noticeSerial) { if (stateNotice.isNotEmpty()) { delay(1200); stateNotice = "" } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) { playing = value }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(visible) {
        if (visible) progressFocus.requestFocus() else if (videoFocused) hiddenFocus.requestFocus()
    }
    LaunchedEffect(Unit) { hiddenFocus.requestFocus() }
    LaunchedEffect(visible, activity) { if (visible) { delay(TvPlaybackInputPolicy.CONTROLS_TIMEOUT_MS); hide() } }
    BackHandler { if (visible) hide() else back() }
    Column(Modifier.fillMaxSize().background(XViewColors.player)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TvControl(ui(UiR.string.back_feed), Modifier.focusRequester(externalFocus), compact = true, click = back)
            TvControl(ui(UiR.string.previous_video), enabled = previous != null, compact = true, click = { previous?.invoke() })
            TvControl(ui(UiR.string.next_video), enabled = next != null, compact = true, click = { next?.invoke() })
            TvControl(ui(UiR.string.playback_speed, rate), compact = true, click = {
                val rates = listOf(.5f, 1f, 1.25f, 1.5f, 2f)
                rate = rates.firstOrNull { it > rate } ?: rates.first()
                player.setPlaybackSpeed(rate)
            })
        }
    Box(Modifier.weight(1f).fillMaxWidth().background(XViewColors.player)
        .onFocusChanged { videoFocused = it.hasFocus }.focusGroup().onPreviewKeyEvent { event ->
        if (event.key == wakeKey) {
            if (event.type == KeyEventType.KeyUp) wakeKey = null
            true
        } else if (event.key in controlKeys) {
            activity++
            if (!visible) {
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val now = android.os.SystemClock.uptimeMillis()
                            if (TvPlaybackInputPolicy.shouldSeek(event.nativeKeyEvent.repeatCount, now, lastSeekAt)) {
                                if (event.key == Key.DirectionLeft) player.seekBack() else player.seekForward()
                                lastSeekAt = now
                                stateNotice = ui(UiR.string.seek_feedback, if (event.key == Key.DirectionLeft) "−" else "+", if (event.key == Key.DirectionLeft) rewind else forward)
                                noticeSerial++
                            }
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (TvPlaybackInputPolicy.shouldToggle(event.nativeKeyEvent.repeatCount)) togglePlayback()
                        else -> { wakeKey = event.key; visible = true }
                    }
                }
                true
            } else false
        } else false
    }.onKeyEvent { event ->
        if (event.key in controlKeys) {
            if (event.type == KeyEventType.KeyDown) when (event.key) {
                Key.DirectionUp -> if (!focusManager.moveFocus(FocusDirection.Up)) externalFocus.requestFocus()
                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                else -> Unit
            }
            true
        } else false
    }) {
        surface()
        // Keep the hidden focus target tiny: no full-frame GPU layer over the SurfaceView.
        if (!visible) Box(Modifier.size(1.dp).focusRequester(hiddenFocus).focusable())
        else Column(Modifier.fillMaxSize().background(XViewColors.scrim.copy(alpha = .72f)).padding(24.dp).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = XViewColors.text)
            Spacer(Modifier.weight(1f))
            Text(ui(if (playing) UiR.string.play_video else UiR.string.pause_video), color = XViewColors.primary)
            XButton(onClick = { togglePlayback() },
                modifier = Modifier.fillMaxWidth().focusRequester(progressFocus)
                    .onFocusChanged { progressFocused = it.isFocused }
                    .border(if (progressFocused) 3.dp else 1.dp, if (progressFocused) XViewColors.primary else XViewColors.outline, RoundedCornerShape(12.dp))
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                            if (event.type == KeyEventType.KeyDown) {
                                val now = android.os.SystemClock.uptimeMillis()
                                if (TvPlaybackInputPolicy.shouldSeek(event.nativeKeyEvent.repeatCount, now, lastSeekAt)) {
                                    if (event.key == Key.DirectionLeft) player.seekBack() else player.seekForward()
                                    lastSeekAt = now
                                }
                            }
                            true
                        } else false
                    },
                colors = ButtonDefaults.colors(containerColor = XViewColors.surface, contentColor = XViewColors.text,
                    focusedContainerColor = XViewColors.surface, focusedContentColor = XViewColors.text),
                scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(ui(UiR.string.playback_status, tvTime(position), tvTime(duration)))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(XViewColors.outline)) {
                        Box(Modifier.fillMaxWidth(if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f)
                            .fillMaxHeight().background(XViewColors.text))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) { qualities() }
        }
        if (!visible && (stateNotice.isNotEmpty() || !playing)) Text(
            stateNotice.ifEmpty { ui(UiR.string.pause_video) },
            Modifier.align(Alignment.Center).background(XViewColors.scrim.copy(alpha = .7f), RoundedCornerShape(12.dp)).padding(18.dp),
            color = XViewColors.primary, style = MaterialTheme.typography.titleLarge)
    }
    }
}

@Composable
internal fun TvControl(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false,
    click: () -> Unit, detail: (@Composable () -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    XButton(onClick = click, enabled = enabled,
        modifier = modifier.then(if (compact) Modifier.height(32.dp) else Modifier).onFocusChanged { focused = it.isFocused }
            .border(if (focused) 3.dp else 1.dp, if (focused) XViewColors.primary else XViewColors.outline, RoundedCornerShape(12.dp)),
        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 3.dp else 10.dp),
        colors = ButtonDefaults.colors(containerColor = XViewColors.surface, contentColor = XViewColors.text,
            focusedContainerColor = XViewColors.primary, focusedContentColor = XViewColors.background),
        scale = ButtonDefaults.scale(focusedScale = 1.04f)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label)
            detail?.invoke()
        }
    }
}

private fun tvTime(value: Long): String = "%d:%02d".format(java.util.Locale.ROOT, value / 60000, value / 1000 % 60)
