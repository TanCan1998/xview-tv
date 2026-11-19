package io.github.tancan1998.xviewtv.core.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

enum class ProxyMode { DIRECT, HTTP, SOCKS5 }

// Deliberately no credential fields or generated toString containing secrets.
data class ProxyConfig(val mode: ProxyMode = ProxyMode.DIRECT, val host: String = "", val port: Int = 0) {
    fun validate(): String? {
        if (mode == ProxyMode.DIRECT) return null
        if (host.isBlank() || host.length > 253 || host.any { it.isWhitespace() || it in "/@?#\\" } || host.contains("://"))
            return "服务器地址仅填写主机名或 IP，不含协议、路径或账号。"
        if (port !in 1..65535) return "端口必须为 1–65535。"
        return null
    }
}

/** Shared clients never use system/global proxy state or fall back to direct. */
object NetworkPolicy {
    /** Short resource-only work, no Activity/job owner retained; TLS close may perform network I/O. */
    fun dispose(client: OkHttpClient) {
        kotlinx.coroutines.Dispatchers.IO.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable {
            try { client.dispatcher.cancelAll(); client.connectionPool.evictAll() }
            finally { client.dispatcher.executorService.shutdown() }
        })
    }
    fun cancel(call: okhttp3.Call) {
        kotlinx.coroutines.Dispatchers.IO.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable { call.cancel() })
    }
    fun dispose(response: okhttp3.Response) {
        kotlinx.coroutines.Dispatchers.IO.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable { response.close() })
    }
    fun client(config: ProxyConfig): OkHttpClient {
        require(config.validate() == null) { "Invalid proxy configuration" }
        val proxy = when (config.mode) {
            ProxyMode.DIRECT -> Proxy.NO_PROXY
            ProxyMode.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(config.host, config.port))
            ProxyMode.SOCKS5 -> Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(config.host, config.port))
        }
        return OkHttpClient.Builder().proxy(proxy)
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).build()
    }

    // This action is explicit; no account credentials, response bodies, or request logs.
    fun diagnose(config: ProxyConfig): String {
        val client = client(config)
        return try {
            client.newCall(Request.Builder().url("https://www.gstatic.com/generate_204").build()).execute().use { response ->
                when (response.code) {
                    204 -> "连接成功：已通过当前草稿配置完成 HTTPS 检测。"
                    407 -> "代理需要认证；当前版本仅支持无认证代理。"
                    else -> "服务器返回 HTTP ${response.code}，未判定连接成功。"
                }
            }
        } catch (_: Exception) {
            "连接失败：请检查服务器、端口、网络与代理协议。配置未自动切换为直连。"
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}

/** Only the authenticated web origin may receive these session fields. */
class WebSession private constructor(val authToken: String, val csrf: String) {
    override fun toString(): String = "WebSession([redacted])"
    companion object {
        fun parse(cookie: String): WebSession {
            require(cookie.length in 1..32768 && cookie.none { it.code < 32 || it.code == 127 }) { "Cookie 格式无效。" }
            val values = mutableMapOf<String, String>()
            val parts = cookie.trim().replace(Regex("^Cookie\\s*:\\s*", RegexOption.IGNORE_CASE), "").split(';').toMutableList()
            if (parts.last().isBlank()) parts.removeAt(parts.lastIndex)
            for (part in parts) {
                val index = part.indexOf('=')
                require(index > 0) { "Cookie 格式无效。" }
                val name = part.substring(0, index).trim()
                val value = part.substring(index + 1).trim()
                require(name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) { "Cookie 格式无效。" }
                require(name.lowercase() !in setOf("domain", "path", "expires", "max-age", "secure", "httponly", "samesite", "partitioned")) { "请粘贴请求 Cookie，不是 Set-Cookie。" }
                if (name != "auth_token" && name != "ct0") continue
                require(value.isNotEmpty()) { "Cookie 缺少必需字段。" }
                require(value.all { it.code in 33..126 && it !in "\";,\\" }) { "Cookie 格式无效。" }
                require(values[name] == null || values[name] == value) { "Cookie 必需字段存在冲突。" }
                values[name] = value
            }
            return WebSession(requireNotNull(values["auth_token"]) { "缺少 auth_token。" }, requireNotNull(values["ct0"]) { "缺少 ct0。" })
        }
    }
}

class WebSessionFailure(val reason: String) : io.github.tancan1998.xviewtv.core.domain.ContentFailure(reason)

fun NetworkPolicy.readAuthenticatedHome(config: ProxyConfig, session: WebSession): String {
    val client = client(config)
    try {
        val request = Request.Builder().url("https://x.com/home")
            .header("Cookie", "auth_token=${session.authToken}; ct0=${session.csrf}")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/157.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html").build()
        return client.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> throw WebSessionFailure("会话已失效，请重新导入 Cookie。")
                403 -> throw WebSessionFailure("访问受限或需要人工验证，请先在浏览器处理。")
                429 -> throw WebSessionFailure("请求受到限流，请稍后再试。")
                in 300..399 -> throw WebSessionFailure("会话被重定向，请先在浏览器确认登录状态。")
                200 -> {
                    val source = response.body?.source() ?: throw WebSessionFailure("网页响应为空。")
                    source.request(4L * 1024 * 1024 + 1)
                    if (source.buffer.size > 4L * 1024 * 1024) throw WebSessionFailure("网页响应超出预期。")
                    source.readUtf8()
                }
                else -> throw WebSessionFailure("网页返回 HTTP ${response.code}，账号未保存。")
            }
        }
    } catch (failure: WebSessionFailure) { throw failure }
    catch (_: Exception) { throw WebSessionFailure("网络连接失败，请检查代理；账号未改变。") }
    finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
}
