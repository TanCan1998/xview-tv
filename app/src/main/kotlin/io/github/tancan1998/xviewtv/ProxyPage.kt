package io.github.tancan1998.xviewtv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import io.github.tancan1998.xviewtv.core.designsystem.*
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR
import io.github.tancan1998.xviewtv.core.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProxyPage(saved: ProxyConfig, initialFocus: FocusRequester, onSave: (ProxyConfig) -> Unit, saveMessage: String) {
    val ui = rememberStrings()
    var mode by rememberSaveable { mutableStateOf(saved.mode) }
    var host by rememberSaveable { mutableStateOf(saved.host) }
    var port by rememberSaveable { mutableStateOf(if (saved.port == 0) "" else saved.port.toString()) }
    var result by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val candidate = ProxyConfig(mode, host.trim(), port.toIntOrNull() ?: 0)
    DetailPage(ui(UiR.string.proxy_title), ui(UiR.string.proxy_subtitle)) {
        Text(ui(UiR.string.proxy_saved, ui(when (saved.mode) { ProxyMode.DIRECT -> UiR.string.direct; ProxyMode.HTTP -> UiR.string.http_proxy; ProxyMode.SOCKS5 -> UiR.string.socks_proxy })))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProxyMode.entries.forEach { item ->
                XButton(onClick = { mode = item; result = "" }, enabled = !testing,
                    modifier = if (item == ProxyMode.DIRECT) Modifier.focusRequester(initialFocus) else Modifier) {
                    val label = ui(when (item) { ProxyMode.DIRECT -> UiR.string.direct; ProxyMode.HTTP -> UiR.string.http_proxy; ProxyMode.SOCKS5 -> UiR.string.socks_proxy })
                    Text(if (mode == item) ui(UiR.string.selected_option, label) else label)
                }
            }
        }
        if (mode != ProxyMode.DIRECT) {
            ProxyField(ui(UiR.string.host), host, { host = it; result = "" }, false, !testing)
            ProxyField(ui(UiR.string.port), port, { port = it; result = "" }, true, !testing)
            Text(ui(UiR.string.proxy_help))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            XButton(onClick = {
                val error = candidate.validate()
                if (error != null) result = ui(if (candidate.port !in 1..65535 && candidate.mode != ProxyMode.DIRECT) UiR.string.port_invalid else UiR.string.host_invalid) else { result = ""; onSave(candidate) }
            }, enabled = !testing) { Text(ui(UiR.string.save_config)) }
            XButton(onClick = {
                val error = candidate.validate()
                if (error != null) result = ui(if (candidate.port !in 1..65535 && candidate.mode != ProxyMode.DIRECT) UiR.string.port_invalid else UiR.string.host_invalid)
                else {
                    testing = true
                    val snapshot = candidate
                    scope.launch {
                        try { val response = withContext(Dispatchers.IO) { NetworkPolicy.diagnose(snapshot) }; result = ui(if (response.startsWith("连接成功")) UiR.string.network_ok else UiR.string.network_failed) }
                        finally { testing = false }
                    }
                }
            }, enabled = !testing) { Text(if (testing) ui(UiR.string.testing) else ui(UiR.string.test_config)) }
        }
        Text(ui(UiR.string.proxy_test_help))
        if (testing) LoadingIndicator(label = ui(UiR.string.testing))
        if (result.isNotEmpty()) Text(result, color = MaterialTheme.colorScheme.primary)
        if (saveMessage.isNotEmpty()) Text(saveMessage, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ProxyField(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean, enabled: Boolean) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label)
        BasicTextField(value, onValueChange = { if (it.length <= 253 && it.none { c -> c.code < 32 || c.code == 127 }) onChange(it) },
            enabled = enabled, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Ascii, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            textStyle = TextStyle(color = color, fontSize = 20.sp), cursorBrush = SolidColor(color),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().onFocusChanged { focused = it.isFocused }
                .background(MaterialTheme.colorScheme.surface, XViewShapes.medium)
                .border(if (focused) 3.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else color, XViewShapes.medium)
                .padding(16.dp))
    }
}
