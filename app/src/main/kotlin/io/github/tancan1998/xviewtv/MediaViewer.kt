package io.github.tancan1998.xviewtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.tv.material3.*
import io.github.tancan1998.xviewtv.core.model.*
import io.github.tancan1998.xviewtv.core.network.*
import io.github.tancan1998.xviewtv.core.domain.*
import io.github.tancan1998.xviewtv.core.designsystem.*
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaViewer(post: Post, posts: List<Post>, config: ProxyConfig, back: () -> Unit) {
    val ui = rememberStrings()
    val queue = remember(post.id) { PlaybackPolicy.queue(posts.ifEmpty { listOf(post) }) }
    var index by rememberSaveable(post.id) { mutableIntStateOf(queue.indexOfFirst { it.post.id == post.id }.coerceAtLeast(0)) }
    val exit = remember { FocusRequester() }
    val context = LocalContext.current.applicationContext
    val preferences = remember { AppPreferences(context) }
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val short = LocalConfiguration.current.screenHeightDp < 480
    LaunchedEffect(post.id) { runCatching { exit.requestFocus() } }
    BackHandler { back() }
    val entry = queue.getOrNull(index)
    val previous = PlaybackPolicy.adjacentVideo(queue, index, -1)
    val next = PlaybackPolicy.adjacentVideo(queue, index, 1)
    Column(Modifier.fillMaxSize().background(XViewColors.player).padding(if (compact || short) 8.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            XButton(onClick = back, modifier = Modifier.focusRequester(exit)) { Text(ui(UiR.string.back_feed)) }
            XButton(onClick = { previous?.let { index = it } }, enabled = previous != null) { Text(ui(UiR.string.previous_video)) }
            XButton(onClick = { next?.let { index = it } }, enabled = next != null) { Text(ui(UiR.string.next_video)) }
        }
        if (!short) Text(ui(UiR.string.player_queue, entry?.post?.author?.username.orEmpty(), index + 1, queue.size), style = MaterialTheme.typography.labelSmall)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val attachment = entry?.media) {
                is Photo -> Text(ui(UiR.string.media_unavailable))
                null -> Text(ui(UiR.string.media_unavailable))
                else -> key(entry.post.id, attachment.id, config) {
                    OpenSourcePlayer(attachment, config, preferences.seekBack(), preferences.seekForward())
                }
            }
        }
    }
}

/** Native Media3 controls remain accessible for touch, D-pad, speed, tracks and seek. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun OpenSourcePlayer(media: MediaAttachment, config: ProxyConfig, rewind: Int, forward: Int) {
    val ui = rememberStrings()
    val context = LocalContext.current.applicationContext
    val tv = isTelevision()
    val short = LocalConfiguration.current.screenHeightDp < 480
    val client = remember(config) { NetworkPolicy.client(config).newBuilder().readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build() }
    val variants = when(media) { is Video -> media.variants; is AnimatedGif -> media.variants; else -> emptyList() }
    val mp4 = remember(media.id) { variants.filter { it.mimeType == "video/mp4" }.sortedBy { it.bitrate ?: 0 }.distinctBy { it.url } }
    val hls = variants.firstOrNull { it.mimeType.contains("mpegurl", true) }
    var quality by remember(media.id) { mutableIntStateOf(-1) }
    val initial = mp4.lastOrNull { (it.bitrate ?: 0) <= 4_000_000 } ?: variants.firstOrNull()
    val player = remember(media.id, client) {
        // Codec capability filtering remains with Media3; stable sorting keeps platform order within each group.
        val selector = MediaCodecSelector { mime, secure, tunneling ->
            MediaCodecUtil.getDecoderInfos(mime, secure, tunneling).sortedByDescending { it.hardwareAccelerated }
        }
        val renderers = DefaultRenderersFactory(context).setMediaCodecSelector(selector)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers)
            .setSeekBackIncrementMs(rewind * 1000L).setSeekForwardIncrementMs(forward * 1000L)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(5000,15000,750,1500)
                .setTargetBufferBytes(16 * 1024 * 1024).setPrioritizeTimeOverSizeThresholds(false).build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(client)))
            .build().apply {
                if (BuildConfig.DEBUG) PlayerDiagnostics.active.incrementAndGet()
                val source = hls ?: initial
                if (source != null && WebTimelineParser.safeUrl(source.url) != null) {
                    setMediaItem(MediaItem.Builder().setUri(source.url).setMimeType(source.mimeType).build())
                    setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                    repeatMode = if (media is AnimatedGif) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    prepare(); playWhenReady = true
                }
            }
    }
    var error by remember { mutableStateOf("") }
    var decoder by remember { mutableStateOf("") }
    var position by remember { mutableLongStateOf(0) }
    var duration by remember { mutableLongStateOf(0) }
    var boost by remember { mutableStateOf<Float?>(null) }
    var feedback by remember { mutableStateOf("") }
    var feedbackSerial by remember { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<GesturePlayerView?>(null) }
    val currentUi by rememberUpdatedState(ui)
    LaunchedEffect(feedbackSerial) { if (feedback.isNotEmpty()) { delay(900); feedback = "" } }
    LaunchedEffect(player) {
        while (true) { position = player.currentPosition; duration = player.duration.coerceAtLeast(0); delay(500) }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val listener = object: Player.Listener {
            override fun onPlayerError(failure: PlaybackException) { error = failure.errorCodeName }
        }
        val analytics = object: AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                decoder = decoderName
                if (BuildConfig.DEBUG) android.util.Log.d("XViewPlayback", "decoder=$decoderName")
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { view?.finishBoost(); player.pause() }
        }
        player.addListener(listener); player.addAnalyticsListener(analytics); lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer); view?.finishBoost()
            view?.apply { seek = null; speed = null; changeSpeed = null; this.feedback = null; this.player = null }; view = null
            player.removeListener(listener); player.removeAnalyticsListener(analytics)
            player.stop(); player.clearMediaItems(); player.release()
            if (BuildConfig.DEBUG) PlayerDiagnostics.active.decrementAndGet()
            NetworkPolicy.dispose(client)
        }
    }
    fun changeQuality(source: VideoVariant, value: Int) {
        view?.finishBoost()
        val old = player.currentPosition; val running = player.playWhenReady; val speed = player.playbackParameters
        player.setMediaItem(MediaItem.Builder().setUri(source.url).setMimeType(source.mimeType).build(), old)
        player.prepare(); player.playbackParameters = speed; player.playWhenReady = running; quality = value; error = ""
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().background(XViewColors.scrim)) {
            AndroidView(factory = { ctx -> GesturePlayerView(ctx).apply {
                useController = true; controllerShowTimeoutMs = if (tv) 0 else 2500; controllerAutoShow = true
                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowFastForwardButton(true); setShowRewindButton(true); setShowNextButton(false); setShowPreviousButton(false)
                this.player = player
                speed = { player.playbackParameters.speed }; changeSpeed = { player.setPlaybackSpeed(it) }
                this.feedback = { boost = it }
                seek = { right ->
                    val seconds = if (right) forward else rewind
                    player.seekTo(PlaybackPolicy.seek(player.currentPosition, player.duration, seconds * (if (right) 1000L else -1000L)))
                    if (BuildConfig.DEBUG) android.util.Log.d("XViewPlayback", "seek offset=${if (right) seconds else -seconds} position=${player.currentPosition}")
                    feedback = currentUi(UiR.string.seek_feedback, if (right) "+" else "−", seconds); feedbackSerial++
                }
                view = this; showController()
            } }, modifier = Modifier.fillMaxSize(), update = { it.player = player },
                onRelease = { it.finishBoost(); it.player = null; if (view === it) view = null })
            val notice = boost?.let { ui(UiR.string.boost, String.format(java.util.Locale.ROOT, "%.1f", it)) } ?: feedback
            if (notice.isNotEmpty()) Text(notice, Modifier.align(Alignment.TopCenter).padding(20.dp).background(XViewColors.scrim.copy(alpha = .75f)).padding(12.dp), color = XViewColors.text)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ui(UiR.string.quality), style = MaterialTheme.typography.labelMedium)
            hls?.let { source -> XButton(onClick = { changeQuality(source, -1) }) { Text(if (quality == -1) ui(UiR.string.selected_option, ui(UiR.string.auto_quality)) else ui(UiR.string.auto_quality)) } }
            mp4.forEachIndexed { i, variant ->
                XButton(onClick = { changeQuality(variant, i) }) {
                    val resolution = Regex("/(\\d{2,4})x(\\d{2,4})/").find(variant.url)?.groupValues?.get(2)
                    val label = resolution?.let { ui(UiR.string.quality_resolution, it) } ?: ui(UiR.string.quality_bitrate, (variant.bitrate ?: 0) / 1000)
                    Text(if (quality == i) ui(UiR.string.selected_option, label) else label)
                }
            }
        }
        Text(ui(UiR.string.playback_status, formatTime(position), formatTime(duration)), style = MaterialTheme.typography.labelSmall)
        if (!tv && !short) Text(ui(UiR.string.hold_hint), style = MaterialTheme.typography.labelSmall)
        if (BuildConfig.DEBUG && !short) Text(if (decoder.isBlank()) ui(UiR.string.decoder_wait) else ui(UiR.string.decoder, decoder), style = MaterialTheme.typography.labelSmall)
        if (error.isNotEmpty()) Text(ui(UiR.string.player_error, error), color = MaterialTheme.colorScheme.error)
    }
}

private fun formatTime(value: Long): String = "%d:%02d".format(java.util.Locale.ROOT, value / 60000, value / 1000 % 60)
