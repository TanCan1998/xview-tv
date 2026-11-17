package io.github.tancan1998.xviewtv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.tancan1998.xviewtv.core.network.*
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

internal data class WebAccount(val id: String, val username: String, val name: String)
internal class VerifiedSession(val session: WebSession, val account: WebAccount) {
    override fun toString() = "VerifiedSession([redacted])"
}

internal object WebIdentity {
    fun parse(html: String): WebAccount {
        val marker = Regex("window\\.__INITIAL_STATE__\\s*=\\s*").find(html)
            ?: throw WebSessionFailure("网页协议变化或需要浏览器验证，未发现当前账号。")
        val start = marker.range.last + 1
        var depth = 0; var quoted = false; var escaped = false; var end = -1
        for (i in start until html.length) {
            val c = html[i]
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { end = i + 1; break } }
            }
        }
        try {
            if (end < 0) throw Exception()
            val state = JSONObject(html.substring(start, end))
            val session = state.getJSONObject("session")
            if (session.optBoolean("isRestrictedSession")) throw WebSessionFailure("账号会话受限，请在浏览器完成人工验证。")
            val id = session.getString("user_id")
            if (!id.matches(Regex("[0-9]{1,30}"))) throw Exception()
            val user = state.getJSONObject("entities").getJSONObject("users").getJSONObject("entities").getJSONObject(id)
            if (user.getString("id_str") != id) throw Exception()
            val username = user.getString("screen_name")
            if (!username.matches(Regex("[A-Za-z0-9_]{1,30}"))) throw Exception()
            return WebAccount(id, username, user.optString("name", username).take(100))
        } catch (failure: WebSessionFailure) { throw failure }
        catch (_: Exception) { throw WebSessionFailure("未取得匹配的当前账号身份，原账号未改变。") }
    }
    fun verify(config: ProxyConfig, session: WebSession) = VerifiedSession(session, parse(NetworkPolicy.readAuthenticatedHome(config, session)))
}

/** API23+ AES AndroidKeyStore; fail closed when the key or secure storage is unavailable. */
internal class SessionVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("account_vault", Context.MODE_PRIVATE)
    private val alias = "xview_account_aes_v1"
    private fun key(create: Boolean): javax.crypto.SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val old = store.getKey(alias, null) as? javax.crypto.SecretKey
        if (old != null) return old
        check(create) { "安全密钥不可用，请重新登录。" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(candidate: VerifiedSession) {
        val plain = JSONObject().put("auth_token", candidate.session.authToken).put("ct0", candidate.session.csrf)
            .put("id", candidate.account.id).put("username", candidate.account.username).put("name", candidate.account.name).toString().toByteArray()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)); updateAAD("xview-account-v1".toByteArray()) }
            val encrypted = cipher.doFinal(plain)
            check(preferences.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "安全存储写入失败。" }
        } finally { plain.fill(0) }
    }
    fun load(): VerifiedSession? {
        val payload = preferences.getString("payload", null) ?: return null
        val iv = preferences.getString("iv", null) ?: error("安全存储不完整。")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))); updateAAD("xview-account-v1".toByteArray())
        }
        val plain = cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP))
        try {
            val json = JSONObject(String(plain))
            val session = WebSession.parse("auth_token=${json.getString("auth_token")}; ct0=${json.getString("ct0")}")
            return VerifiedSession(session, WebAccount(json.getString("id"), json.getString("username"), json.getString("name")))
        } finally { plain.fill(0) }
    }
    fun clear() { check(preferences.edit().clear().commit()) { "移除本地会话失败。" } }
}
