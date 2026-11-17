package io.github.tancan1998.xviewtv

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.media3.common.*
import androidx.tv.material3.Text
import io.github.tancan1998.xviewtv.core.designsystem.*
import java.lang.reflect.Proxy

/** Offline, synthetic fixture in debug source set only; never in the production APK. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TvFocusFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var playing by mutableStateOf(true)
        var position by mutableLongStateOf(30000L)
        var speed = 1f
        val listeners = mutableSetOf<Player.Listener>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getPlayWhenReady", "isPlaying" -> playing
                "getPlaybackState" -> Player.STATE_READY
                "getPlaybackParameters" -> PlaybackParameters(speed)
                "getApplicationLooper" -> mainLooper
                "getDuration" -> 300000L
                "getCurrentPosition" -> position
                "isCommandAvailable" -> true
                "getAvailableCommands" -> Player.Commands.Builder().addAllCommands().build()
                "addListener" -> { listeners += args!![0] as Player.Listener; null }
                "removeListener" -> { listeners -= args!![0] as Player.Listener; null }
                "pause", "play" -> {
                    playing = method.name == "play"
                    listeners.toList().forEach { it.onPlayWhenReadyChanged(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
                    null
                }
                "seekBack" -> { position = (position - 10000).coerceAtLeast(0); null }
                "seekForward" -> { position = (position + 10000).coerceAtMost(300000); null }
                "seekTo" -> { position = args!!.last() as Long; null }
                "setPlaybackSpeed" -> { speed = args!![0] as Float; null }
                "equals" -> false
                "hashCode" -> 1
                "toString" -> "SyntheticFixturePlayer"
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    else -> null
                }
            }
        } as Player
        setContent {
            val config = Configuration(LocalConfiguration.current).apply { uiMode = Configuration.UI_MODE_TYPE_TELEVISION }
            CompositionLocalProvider(LocalConfiguration provides config) {
                XViewTheme {
                    TvPlayerSurface(player, "Offline TV focus fixture", position, 300000, 10, 10,
                        back = { finish() }, previous = null, next = {},
                        qualities = { TvControl("Auto", click = {}); TvControl("720p", click = {}) },
                        surface = { Box(Modifier.fillMaxSize().background(XViewColors.player)) {
                            Text("Fixture position=$position playing=$playing", Modifier.padding(12.dp))
                        } })
                }
            }
        }
    }
}
