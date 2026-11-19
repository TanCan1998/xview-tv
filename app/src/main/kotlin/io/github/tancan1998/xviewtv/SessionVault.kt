package io.github.tancan1998.xviewtv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.tancan1998.xviewtv.core.network.WebSession
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class UnreadableSessionVault(cause: Exception? = null) : Exception(cause)

/** One encrypted document keeps account list, sessions and active selection atomic. */
internal class SessionVault(context: Context, storeName: String = "account_vault") {
    private val preferences = context.applicationContext.getSharedPreferences(storeName, Context.MODE_PRIVATE)
    private val alias = if (storeName == "account_vault") "xview_account_aes_v1" else "xview_test_$storeName"
    private data class State(val entries: List<VerifiedSession>, val active: String?)
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(create: Boolean): SecretKey {
        val store = keyStore()
        val old = store.getKey(alias, null) as? SecretKey
        if (old != null) return old
        if (!create) throw UnreadableSessionVault()
        if (store.containsAlias(alias)) store.deleteEntry(alias)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun json(candidate: VerifiedSession) = JSONObject().put("auth_token", candidate.session.authToken)
        .put("ct0", candidate.session.csrf).put("id", candidate.account.id)
        .put("username", candidate.account.username).put("name", candidate.account.name)
    private fun parse(json: JSONObject): VerifiedSession {
        val id = json.getString("id"); require(id.matches(Regex("[0-9]{1,30}")))
        val username = json.getString("username"); require(username.matches(Regex("[A-Za-z0-9_]{1,30}")))
        return VerifiedSession(WebSession.parse("auth_token=${json.getString("auth_token")}; ct0=${json.getString("ct0")}"),
            WebAccount(id, username, json.getString("name").take(100)))
    }
    private fun read(): State {
        val v2 = preferences.getString("accounts_payload", null)
        val payload = v2 ?: preferences.getString("payload", null) ?: return State(emptyList(), null)
        val storedIv = preferences.getString(if (v2 != null) "accounts_iv" else "iv", null)
            ?: throw UnreadableSessionVault()
        val iv: ByteArray
        val encrypted: ByteArray
        try {
            iv = Base64.decode(storedIv, Base64.NO_WRAP)
            encrypted = Base64.decode(payload, Base64.NO_WRAP)
            require(iv.size == 12 && encrypted.size >= 16)
        } catch (failure: IllegalArgumentException) {
            throw UnreadableSessionVault(failure)
        }
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, iv))
                updateAAD((if (v2 != null) "xview-accounts-v2" else "xview-account-v1").toByteArray())
            }
            cipher.doFinal(encrypted)
        } catch (failure: KeyPermanentlyInvalidatedException) {
            throw UnreadableSessionVault(failure)
        } catch (failure: AEADBadTagException) {
            throw UnreadableSessionVault(failure)
        }
        try {
            val data = JSONObject(String(plain))
            if (v2 == null) { val old = parse(data); return State(listOf(old), old.account.id) }
            val items = data.getJSONArray("accounts")
            require(items.length() <= 20)
            val entries = (0 until items.length()).map { parse(items.getJSONObject(it)) }
            require(entries.map { it.account.id }.distinct().size == entries.size)
            val active = if (data.isNull("active")) null else data.getString("active")
            require(active == null || entries.any { it.account.id == active })
            return State(entries, active)
        } catch (failure: org.json.JSONException) {
            throw UnreadableSessionVault(failure)
        } catch (failure: IllegalArgumentException) {
            throw UnreadableSessionVault(failure)
        } finally { plain.fill(0) }
    }
    private fun write(state: State) {
        val entries = JSONArray(); state.entries.forEach { entries.put(json(it)) }
        val plain = JSONObject().put("accounts", entries).put("active", state.active ?: JSONObject.NULL).toString().toByteArray()
        try {
            val newVault = !preferences.contains("accounts_payload") && !preferences.contains("payload")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            try {
                cipher.init(Cipher.ENCRYPT_MODE, key(newVault))
            } catch (failure: KeyPermanentlyInvalidatedException) {
                if (!newVault) throw UnreadableSessionVault(failure)
                keyStore().deleteEntry(alias)
                cipher.init(Cipher.ENCRYPT_MODE, key(true))
            }
            cipher.updateAAD("xview-accounts-v2".toByteArray())
            val encrypted = cipher.doFinal(plain)
            check(preferences.edit().putString("accounts_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("accounts_payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .remove("iv").remove("payload").commit()) { "Secure storage write failed" }
        } finally { plain.fill(0) }
    }
    fun save(candidate: VerifiedSession) = synchronized(lock) {
        val old = read()
        require(old.entries.size < 20 || old.entries.any { it.account.id == candidate.account.id }) { "Account limit reached" }
        val entries = old.entries.toMutableList()
        val index = entries.indexOfFirst { it.account.id == candidate.account.id }
        if (index < 0) entries += candidate else entries[index] = candidate
        write(State(entries, candidate.account.id))
    }
    fun load(): VerifiedSession? = synchronized(lock) { val state = read(); state.entries.firstOrNull { it.account.id == state.active } }
    fun accountList(): Pair<List<WebAccount>, WebAccount?> = synchronized(lock) {
        val state = read()
        val accounts = state.entries.map { it.account }
        accounts to accounts.firstOrNull { it.id == state.active }
    }

    /** Recheck before deleting so a recovered vault stays intact. */
    fun resetUnreadable(): Boolean = synchronized(lock) {
        try {
            read()
            false
        } catch (_: UnreadableSessionVault) {
            check(preferences.edit().remove("accounts_payload").remove("accounts_iv")
                .remove("payload").remove("iv").commit()) { "Secure storage reset failed" }
            true
        }
    }

    fun select(id: String): WebAccount = synchronized(lock) {
        val state = read(); val selected = state.entries.firstOrNull { it.account.id == id } ?: error("Account not saved")
        write(state.copy(active = id)); selected.account
    }
    /** Logout removes only the active session; other saved accounts remain available. */
    fun clear() = synchronized(lock) {
        val state = read(); val remaining = state.entries.filterNot { it.account.id == state.active }
        write(State(remaining, remaining.firstOrNull()?.account?.id))
    }
    fun remove(id: String) = synchronized(lock) {
        val state = read(); val remaining = state.entries.filterNot { it.account.id == id }
        write(State(remaining, if (state.active == id) remaining.firstOrNull()?.account?.id else state.active))
    }
    companion object { private val lock = Any() }
}
