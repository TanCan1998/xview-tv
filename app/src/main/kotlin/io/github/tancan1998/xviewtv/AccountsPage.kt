package io.github.tancan1998.xviewtv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import io.github.tancan1998.xviewtv.core.designsystem.*
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AccountsPage(account: WebAccount?, initialFocus: FocusRequester, onAccount: (WebAccount?) -> Unit,
    add: () -> Unit, back: () -> Unit) {
    val ui = rememberStrings()
    val context = LocalContext.current
    val vault = remember { SessionVault(context) }
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<WebAccount>>(emptyList()) }
    var removing by remember { mutableStateOf<WebAccount?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    suspend fun reload() { accounts = withContext(Dispatchers.IO) { vault.accounts() } }
    LaunchedEffect(Unit) {
        try { reload() } catch (_: Exception) { message = ui(UiR.string.vault_failed) }
    }
    DetailPage(ui(UiR.string.accounts_title), ui(UiR.string.accounts_help)) {
        XButton(onClick = add, enabled = !busy, modifier = Modifier.focusRequester(initialFocus)) { Text(ui(UiR.string.account_add)) }
        accounts.forEach { saved ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                XButton(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val selected = withContext(Dispatchers.IO) { vault.select(saved.id) }
                            onAccount(selected); message = ui(UiR.string.account_switched)
                        } catch (_: Exception) { message = ui(UiR.string.vault_failed) }
                        finally { busy = false }
                    }
                }, enabled = !busy && saved.id != account?.id, modifier = Modifier.weight(1f)) {
                    Text(ui(if (saved.id == account?.id) UiR.string.account_current else UiR.string.account_switch,
                        saved.name, saved.username))
                }
                XButton(onClick = { removing = saved }, enabled = !busy) { Text(ui(UiR.string.account_remove)) }
            }
        }
        if (accounts.isEmpty()) Text(ui(UiR.string.logged_out))
        removing?.let { saved ->
            Text(ui(UiR.string.account_remove_confirm, saved.username))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                XButton(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val active = withContext(Dispatchers.IO) { vault.remove(saved.id); vault.load()?.account }
                            onAccount(active); reload(); removing = null; message = ui(UiR.string.removed)
                        } catch (_: Exception) { message = ui(UiR.string.remove_failed) }
                        finally { busy = false }
                    }
                }, enabled = !busy) { Text(ui(UiR.string.account_remove)) }
                XButton(onClick = { removing = null }, enabled = !busy) { Text(ui(UiR.string.cancel)) }
            }
        }
        if (busy) LoadingIndicator(label = ui(UiR.string.verifying))
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
        XButton(onClick = back, enabled = !busy) { Text(ui(UiR.string.back_settings)) }
    }
}
