package io.github.tancan1998.xviewtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import io.github.tancan1998.xviewtv.core.designsystem.*
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Destination { HOME, ACCOUNTS, LOGIN, PROXY, SETTINGS }

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun AppShell(home: @Composable (WebAccount?, io.github.tancan1998.xviewtv.core.network.ProxyConfig) -> Unit) {
    val mediaOverlay = remember { mutableStateOf(false) }
    val ui = rememberStrings()
    val tv = isTelevision()
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context) }
    var account by remember { mutableStateOf<WebAccount?>(null) }
    var restoring by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        try { account = withContext(Dispatchers.IO) { runCatching { SessionVault(context).load()?.account }.getOrNull() } }
        finally { restoring = false }
    }
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var settingsVisible by remember { mutableStateOf(false) }
    var seekBack by remember { mutableIntStateOf(preferences.seekBack()) }
    var seekForward by remember { mutableIntStateOf(preferences.seekForward()) }
    var language by remember { mutableStateOf(preferences.language()) }
    var proxy by remember { mutableStateOf(preferences.readProxy()) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val pageState = rememberSaveableStateHolder()
    val homeFocus = remember { FocusRequester() }
    val homeRegistry = remember { TvHomeFocusRegistry() }
    val settingsFocus = remember { FocusRequester() }
    val pageFocus = remember { FocusRequester() }
    val go: (Destination) -> Unit = { destination = it; settingsVisible = false; message = "" }
    val back: () -> Unit = {
        if (destination == Destination.LOGIN) go(Destination.ACCOUNTS)
        else if (destination == Destination.ACCOUNTS || destination == Destination.PROXY) go(Destination.SETTINGS)
        else { go(Destination.HOME); settingsVisible = false }
    }
    val dismissSettings: () -> Unit = { settingsVisible = false }
    LaunchedEffect(destination, settingsVisible) {
        when {
            destination != Destination.HOME -> runCatching { pageFocus.requestFocus() }
            settingsVisible -> runCatching { settingsFocus.requestFocus() }
            else -> runCatching { if (!homeRegistry.restore()) homeFocus.requestFocus() }
        }
    }
    BackHandler(enabled = destination != Destination.HOME || settingsVisible) {
        if (destination == Destination.HOME && settingsVisible) dismissSettings() else back()
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().onPreviewKeyEvent { event ->
        if (tv && !mediaOverlay.value && destination == Destination.HOME && event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU) {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                if (settingsVisible) dismissSettings() else settingsVisible = true
            }
            true
        } else if (tv && settingsVisible && !mediaOverlay.value && destination == Destination.HOME &&
            event.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) dismissSettings()
            true
        } else false
    }) {
        pageState.SaveableStateProvider(destination.name) {
            when (destination) {
                Destination.HOME -> CompositionLocalProvider(LocalMediaOverlay provides mediaOverlay,
                    LocalTvHomeFocusRegistry provides homeRegistry) {
                    Box(Modifier.fillMaxSize().focusRequester(homeFocus).focusGroup()) {
                        if (restoring) DetailPage(ui(UiR.string.app_title), ui(UiR.string.restoring)) {
                            LoadingIndicator(label = ui(UiR.string.restoring)); MediaSkeleton()
                        } else home(account, proxy)
                    }
                }
                Destination.ACCOUNTS -> AccountsPage(account, pageFocus, onAccount = {
                    if (it?.id != account?.id) { homeRegistry.last = null; pageState.removeState(Destination.HOME.name) }
                    account = it
                }, add = { go(Destination.LOGIN) }, back = back)
                Destination.LOGIN -> LoginPage(proxy, pageFocus, account, onAccount = {
                    if (it?.id != account?.id) { homeRegistry.last = null; pageState.removeState(Destination.HOME.name) }
                    account = it
                }, back = back)
                Destination.PROXY -> ProxyPage(proxy, initialFocus = pageFocus, onSave = { candidate ->
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { preferences.saveProxy(candidate) }
                        if (saved) { proxy = candidate; message = ui(UiR.string.proxy_applied) }
                        else message = ui(UiR.string.save_failed)
                    }
                }, saveMessage = message)
                Destination.SETTINGS -> DetailPage(ui(UiR.string.settings), ui(UiR.string.settings_subtitle)) {
                    XButton(onClick = { go(Destination.ACCOUNTS) }, modifier = Modifier.focusRequester(pageFocus)) { Text(ui(UiR.string.account_link, account?.let { "@${it.username}" } ?: ui(UiR.string.not_signed))) }
                    XButton(onClick = { go(Destination.PROXY) }) { Text(ui(UiR.string.network_link, ui(when (proxy.mode) { io.github.tancan1998.xviewtv.core.network.ProxyMode.DIRECT -> UiR.string.direct; io.github.tancan1998.xviewtv.core.network.ProxyMode.HTTP -> UiR.string.http_proxy; io.github.tancan1998.xviewtv.core.network.ProxyMode.SOCKS5 -> UiR.string.socks_proxy }))) }
                    SettingsSection(ui(UiR.string.language)) {
                        val languages = context.resources.getStringArray(UiR.array.supported_language_tags).toList()
                        val labels = context.resources.getStringArray(UiR.array.supported_language_labels)
                        ChoiceRow(languages, language, label = { labels[languages.indexOf(it)] }) { value -> scope.launch {
                            if (withContext(Dispatchers.IO) { preferences.saveLanguage(value) }) language = value
                            else message = ui(UiR.string.save_failed)
                        } }
                    }
                    SettingsSection(ui(UiR.string.playback)) {
                        Text(ui(UiR.string.seek_back))
                        ChoiceRow(io.github.tancan1998.xviewtv.core.domain.PlaybackPolicy.seekChoices, seekBack,
                            label = { ui(UiR.string.seconds, it) }) { value -> scope.launch {
                            if (withContext(Dispatchers.IO) { preferences.saveSeek(value, seekForward) }) seekBack = value
                            else message = ui(UiR.string.save_failed)
                        } }
                        Text(ui(UiR.string.seek_forward))
                        ChoiceRow(io.github.tancan1998.xviewtv.core.domain.PlaybackPolicy.seekChoices, seekForward,
                            label = { ui(UiR.string.seconds, it) }) { value -> scope.launch {
                            if (withContext(Dispatchers.IO) { preferences.saveSeek(seekBack, value) }) seekForward = value
                            else message = ui(UiR.string.save_failed)
                        } }
                        Text(ui(UiR.string.hardware), color = MaterialTheme.colorScheme.primary)
                        Text(ui(UiR.string.hold_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(ui(UiR.string.about), style = MaterialTheme.typography.bodySmall)
                    if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
                    XButton(onClick = back) { Text(ui(UiR.string.back_home)) }
                }
            }
        }
        if (destination == Destination.HOME && !mediaOverlay.value && (settingsVisible || !tv)) {
            XButton(onClick = { go(Destination.SETTINGS) }, modifier = Modifier.align(Alignment.TopEnd).padding(if (tv) 24.dp else 14.dp).focusRequester(settingsFocus)) {
                Text(ui(UiR.string.settings))
            }
        }
    }
}

@Composable
internal fun DetailPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val tv = isTelevision()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = if (compact) 20.dp else 48.dp, vertical = if (tv) 36.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(XViewShapes.section).background(MaterialTheme.colorScheme.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(items: List<T>, selected: T, label: (T) -> String, choose: (T) -> Unit) {
    val ui = rememberStrings()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            XButton(onClick = { choose(item) }, colors = ButtonDefaults.colors(
                containerColor = if (item == selected) MaterialTheme.colorScheme.primary.copy(alpha = .2f) else MaterialTheme.colorScheme.background,
                contentColor = if (item == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) {
                Text(if (item == selected) ui(UiR.string.selected_option, label(item)) else label(item))
            }
        }
    }
}
