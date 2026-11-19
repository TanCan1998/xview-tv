package io.github.tancan1998.xviewtv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import io.github.tancan1998.xviewtv.core.designsystem.*
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.EncodeHintType
import io.github.tancan1998.xviewtv.core.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LoginPage(config: ProxyConfig, initialFocus: FocusRequester, account: WebAccount?, onAccount: (WebAccount?) -> Unit, back: () -> Unit) {
    val ui = rememberStrings()
    val tv = isTelevision()
    val context = LocalContext.current
    var cookie by remember { mutableStateOf("") }
    val vault = remember { SessionVault(context) }
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf<LanPairingServer?>(null) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<VerifiedSession?>(null) }
    var removing by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(0) }
    val ownedServer = remember { java.util.concurrent.atomic.AtomicReference<LanPairingServer?>(null) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                ownedServer.getAndSet(null)?.close(); server = null; qr = null; candidate = null; cookie = ""
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ownedServer.getAndSet(null)?.close(); server = null; candidate = null; qr = null; cookie = ""
        }
    }
    LaunchedEffect(server) {
        val current = server ?: return@LaunchedEffect
        while (server === current) {
            remaining = ((current.expiresAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            if (remaining == 0) { current.close(); server = null; qr = null; message = ui(UiR.string.qr_expired); break }
            delay(500)
        }
    }
    fun start() {
        if (busy) return
        server?.close(); server = null; qr = null; candidate = null; busy = true
        message = ui(UiR.string.qr_creating)
        scope.launch {
            try {
                val created = withContext(Dispatchers.IO) {
                    LanPairingServer(context) { session ->
                        scope.launch {
                            busy = true; message = ui(UiR.string.qr_received)
                            server?.close(); server = null; qr = null
                            try {
                                candidate = withContext(Dispatchers.IO) { WebIdentity.verify(config, session) }
                                message = ui(UiR.string.verified)
                            } catch (failure: WebSessionFailure) { message = ui.failure(failure.reason) }
                            catch (_: Exception) { message = ui(UiR.string.login_failed) }
                            finally { busy = false }
                        }
                    }.also { ownedServer.getAndSet(it)?.close() }
                }
                val bitmap = withContext(Dispatchers.Default) {
                    val matrix = MultiFormatWriter().encode(created.url, BarcodeFormat.QR_CODE, 640, 640, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
                    Bitmap.createBitmap(640, 640, Bitmap.Config.RGB_565).apply {
                        val pixels = IntArray(640 * 640) { n -> if (matrix[n % 640, n / 640]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                        setPixels(pixels, 0, 640, 0, 0, 640, 640)
                    }
                }
                server = created; qr = bitmap; message = ui(UiR.string.qr_ready)
            } catch (_: Exception) { message = ui(UiR.string.qr_failed) }
            finally { busy = false }
        }
    }
    DetailPage(ui(UiR.string.login_title), ui(if (tv) UiR.string.login_tv_subtitle else UiR.string.login_phone_subtitle)) {
        Text(if (account == null) ui(UiR.string.logged_out) else ui(UiR.string.logged_in, account.name, account.username), style = MaterialTheme.typography.titleLarge)
        if (!tv) {
            Text(ui(UiR.string.cookie_label), style = MaterialTheme.typography.titleMedium)
            BasicTextField(value = cookie, onValueChange = { if (it.length <= 32768) cookie = it },
                enabled = !busy, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, XViewShapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.primary, XViewShapes.medium).padding(18.dp))
            Text(ui(UiR.string.cookie_help), style = MaterialTheme.typography.bodySmall)
            XButton(onClick = {
                val parsed = runCatching { WebSession.parse(cookie) }.getOrNull()
                cookie = ""; candidate = null
                if (parsed == null) message = ui(UiR.string.cookie_invalid)
                else {
                    busy = true; message = ui(UiR.string.verifying)
                    scope.launch {
                        try {
                            candidate = withContext(Dispatchers.IO) { WebIdentity.verify(config, parsed) }
                            message = ui(UiR.string.verified)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (failure: WebSessionFailure) { message = ui.failure(failure.reason) }
                        catch (_: Exception) { message = ui(UiR.string.login_failed) }
                        finally { busy = false }
                    }
                }
            }, enabled = !busy && cookie.isNotBlank(), modifier = Modifier.focusRequester(initialFocus)) { Text(ui(if (busy) UiR.string.verifying else UiR.string.cookie_verify)) }
        }
        if (tv) XButton(onClick = { start() }, enabled = !busy, modifier = Modifier.focusRequester(initialFocus)) { Text(ui(if (server == null) UiR.string.qr_start else UiR.string.qr_refresh)) }
        val current = server
        if (current != null && qr != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Image(qr!!.asImageBitmap(), contentDescription = ui(UiR.string.qr_image), modifier = Modifier.size(240.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                    Text(ui(UiR.string.qr_code, current.code), style = MaterialTheme.typography.titleLarge)
                    Text(ui(UiR.string.qr_time, remaining))
                    Text(ui(UiR.string.qr_check))
                    Text(ui(UiR.string.qr_permission), style = MaterialTheme.typography.bodySmall)
                    Text(current.url.substringBefore('#'), style = MaterialTheme.typography.bodySmall)
                    XButton(onClick = { current.close(); server = null; qr = null; message = ui(UiR.string.qr_cancelled) }) { Text(ui(UiR.string.qr_cancel)) }
                }
            }
        }
        candidate?.let { verified ->
            Text(ui(UiR.string.candidate, verified.account.name, verified.account.username), color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                XButton(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { vault.save(verified) }
                            onAccount(verified.account); candidate = null; message = ui(UiR.string.login_saved)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: UnreadableSessionVault) { message = ui(UiR.string.vault_unreadable_login) }
                        catch (_: Exception) { message = ui(UiR.string.vault_failed) }
                        finally { busy = false }
                    }
                }, enabled = !busy) { Text(ui(UiR.string.confirm_login)) }
                XButton(onClick = { candidate = null; message = ui(UiR.string.cancelled) }, enabled = !busy) { Text(ui(UiR.string.cancel)) }
            }
        }
        if (account != null) {
            XButton(onClick = {
                busy = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { vault.load()?.let { WebIdentity.verify(config, it.session) } }
                        checkNotNull(result); withContext(Dispatchers.IO) { vault.save(result) }; onAccount(result.account); message = ui(UiR.string.session_ok)
                    } catch (failure: WebSessionFailure) { message = ui.failure(failure.reason) }
                    catch (_: Exception) { message = ui(UiR.string.session_failed) }
                    finally { busy = false }
                }
            }, enabled = !busy) { Text(ui(UiR.string.reverify)) }
            XButton(onClick = { removing = true }, enabled = !busy) { Text(ui(UiR.string.logout)) }
        }
        if (removing) {
            Text(ui(UiR.string.logout_help))
            XButton(onClick = {
                busy = true
                scope.launch {
                    try { val active = withContext(Dispatchers.IO) { vault.clear(); vault.load()?.account }; onAccount(active); candidate = null; cookie = ""; removing = false; message = ui(UiR.string.removed) }
                    catch (_: Exception) { message = ui(UiR.string.remove_failed) }
                    finally { busy = false }
                }
            }, enabled = !busy) { Text(ui(UiR.string.confirm_logout)) }
            XButton(onClick = { removing = false }, enabled = !busy) { Text(ui(UiR.string.keep_account)) }
        }
        if (busy) LoadingIndicator(label = message.ifBlank { ui(UiR.string.verifying) })
        else if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
        XButton(onClick = back, enabled = !busy) { Text(ui(UiR.string.back_settings)) }
    }
}
