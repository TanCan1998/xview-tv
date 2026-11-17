package io.github.tancan1998.xviewtv

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import io.github.tancan1998.xviewtv.core.network.*
import io.github.tancan1998.xviewtv.feature.home.LibraryScreen
import kotlinx.coroutines.*
import androidx.compose.ui.platform.LocalLocale
import io.github.tancan1998.xviewtv.core.designsystem.*
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR

@Composable
fun AppContent() {
    val ui = rememberStrings()
    val tv = isTelevision()
    AppShell { account, proxy ->
        val mediaOverlay = LocalMediaOverlay.current
        val context = LocalContext.current.applicationContext
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        if (account == null) DetailPage(ui(UiR.string.welcome), ui(UiR.string.welcome_subtitle)) {
            Text(ui(if (tv) UiR.string.welcome_tv else UiR.string.welcome_phone))
        } else key(account.id) { if (started) {
            var client by remember(account, proxy) { mutableStateOf<XWebClient?>(null) }
            val owned = remember(account, proxy) { java.util.concurrent.atomic.AtomicReference<XWebClient?>(null) }
            val images = remember(account, proxy) { MediaImages(proxy) }
            var error by remember(account, proxy) { mutableStateOf(false) }
            LaunchedEffect(account, proxy) {
                try {
                    val saved = withContext(Dispatchers.IO) { SessionVault(context).load() }
                    check(saved?.account?.id == account.id)
                    val created = XWebClient(proxy, saved!!.session, account.id)
                    owned.set(created); client = created
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = true }
            }
            DisposableEffect(account, proxy) { onDispose { owned.getAndSet(null)?.close(); images.close() } }
            val locale = LocalLocale.current.platformLocale
            val lang = if (locale.language == "zh") {
                if (locale.country in setOf("TW","HK","MO") || locale.script == "Hant") "zh-Hant" else "zh"
            } else locale.language.ifBlank { "en" }
            client?.let { web ->
                LibraryScreen(web, web, web, lang, onMediaVisibility = { mediaOverlay?.value = it },
                    image = { url, modifier -> RemoteImage(images, url, modifier) },
                    media = { post, queue, back -> MediaViewer(post, queue, proxy, back) })
            } ?: DetailPage(ui(UiR.string.app_title), ui(if (error) UiR.string.restore_failed else UiR.string.restoring)) {
                if (!error) { LoadingIndicator(label = ui(UiR.string.restoring)); MediaSkeleton() }
            }
        } }
    }
}
