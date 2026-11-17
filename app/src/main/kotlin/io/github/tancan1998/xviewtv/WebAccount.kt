package io.github.tancan1998.xviewtv

import io.github.tancan1998.xviewtv.core.network.*
import org.json.JSONObject

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
