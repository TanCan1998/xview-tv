package io.github.tancan1998.xviewtv.core.network

import io.github.tancan1998.xviewtv.core.domain.*
import io.github.tancan1998.xviewtv.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Account/config scoped; no Activity, global job, cookie jar, response or raw JSON retained. */
class XWebClient(config: ProxyConfig, private val session: WebSession, private val accountId: String? = null,
    private val onEvidence: ((String) -> Unit)? = null) : TimelineRepository, VideoLibrary, PostTranslator, Closeable {
    private val http = NetworkPolicy.client(config)
    private val protocolLock = Mutex()
    private val protocols = java.util.concurrent.ConcurrentHashMap<String, Protocol>()
    @Volatile private var closed = false
    private val translationLock = Mutex()
    private val translations = object : LinkedHashMap<String, PostTranslation>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PostTranslation>?) = size > 160
    }
    private var translationBlockedUntil = 0L
    private data class Protocol(val query: String, val bearer: String, val features: JSONObject, val toggles: JSONObject)
    private fun base(url: String, authenticated: Boolean = true): Request.Builder {
        val builder = Request.Builder().url(url).header("User-Agent", WEB_UA)
        if (authenticated) {
            require(builder.build().url.host == "x.com")
            builder.header("Cookie", "auth_token=${session.authToken}; ct0=${session.csrf}")
                .header("x-csrf-token", session.csrf).header("x-twitter-auth-type", "OAuth2Session")
                .header("x-twitter-active-user", "yes").header("Referer", "https://x.com/home")
        }
        return builder
    }
    private suspend fun text(request: Request, maxBytes: Long = 4L * 1024 * 1024): String {
        check(!closed)
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { NetworkPolicy.cancel(call) }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(WebSessionFailure("网络请求失败，请检查代理或稍后重试。"))
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> NetworkPolicy.dispose(value) }
                }
            })
        }
        try {
            return withContext(Dispatchers.IO) {
                response.use {
                if (request.url.host == "x.com" && request.url.encodedPath.startsWith("/i/api/graphql/")) {
                    onEvidence?.invoke("${request.method} ${request.url.encodedPath} HTTP=${it.code}")
                }
                if (it.code != 200) throw WebHttpFailure(it.code, retryDelay(it),
                    if (it.code in setOf(401,403)) "X 会话失效或受限，请在浏览器确认账号。" else "X 返回 HTTP ${it.code}，请稍后重试。",
                    if (request.url.host == "abs.twimg.com") "asset:" + request.url.encodedPath.substringAfterLast('/') else request.url.encodedPath.substringAfterLast('/'))
                val source = it.body?.source() ?: throw WebSessionFailure("X 响应为空。")
                source.request(maxBytes + 1)
                if (source.buffer.size > maxBytes) throw WebSessionFailure("X 响应超出安全大小限制。")
                source.readUtf8()
                }
            }
        } finally { NetworkPolicy.dispose(response) }
    }
    private var discoveryHtml: String? = null
    private var discoveredBearer: String? = null
    private suspend fun discover(operation: String = "HomeLatestTimeline"): Protocol = protocolLock.withLock {
        protocols[operation]?.let { cached -> discoveredBearer?.let { return@withLock cached.copy(bearer = it) } }
        val html = discoveryHtml ?: text(base("https://x.com/home").build()).also { discoveryHtml = it }
        onEvidence?.invoke("DISCOVERY home loaded operation=$operation")
        val urls = (Regex("https://abs\\.twimg\\.com/responsive-web/client-web(?:-legacy)?/[^\"'<>\\s]+\\.js")
            .findAll(html).map { it.value }.distinct().filter { it.contains("/main.") }.take(2).toList() +
            WebProtocolDiscovery.operationChunks(html, operation)).distinct()
        var bearer = discoveredBearer
        for (url in urls) {
            if (protocols.containsKey(operation) && bearer != null) break
            val js = text(base(url, false).build(), 8L * 1024 * 1024)
            onEvidence?.invoke("DISCOVERY bundle loaded operation=$operation")
            if (bearer == null) bearer = Regex("AAAAA[A-Za-z0-9%_-]{60,200}").find(js)?.value
            for (match in Regex("queryId:\\s*\"([A-Za-z0-9_-]+)\",\\s*operationName:\\s*\"([A-Za-z]+)\"").findAll(js)) {
                val name = match.groupValues[2]
                if (name !in setOf("HomeLatestTimeline", "SearchTimeline", "Likes", "Following", "UserMedia", "UserTweets")) continue
                val part = js.substring(match.range.first, (match.range.first + 12000).coerceAtMost(js.length)).substringBefore("}}")
                fun names(key: String) = Regex("$key:\\s*\\[([^]]*)]").find(part)?.groupValues?.get(1)
                    ?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() } ?: emptyList()
                val features = JSONObject(); names("featureSwitches").forEach { features.put(it, it !in FALSE_FEATURES) }
                val toggles = JSONObject(); names("fieldToggles").forEach { toggles.put(it, false) }
                protocols[name] = Protocol(match.groupValues[1], "", features, toggles)
            }
        }
        discoveredBearer = bearer
        val found = protocols[operation]
        if (bearer == null || found == null) throw WebSessionFailure("X 网页协议已变化，无法发现所需接口。")
        found.copy(bearer = bearer).also { protocols[operation] = it }
    }
    override suspend fun getTimeline(request: TimelineRequest): Page<Post> {
        val p = discover()
        val variables = JSONObject().put("count", request.limit).put("includePromotedContent", false)
            .put("latestControlAvailable", true).put("requestContext", if (request.cursor == null) "launch" else "scroll")
        request.cursor?.let { variables.put("cursor", it) }
        val body = JSONObject().put("variables", variables).put("features", p.features).put("fieldToggles", p.toggles).put("queryId", p.query)
        val response = text(base("https://x.com/i/api/graphql/${p.query}/HomeLatestTimeline")
            .header("Authorization", "Bearer ${p.bearer}").header("x-twitter-client-language", "en")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build())
        return withContext(Dispatchers.Default) {
            val json = JSONObject(response)
            if (json.optJSONArray("errors")?.length()?.let { it > 0 } == true) throw WebSessionFailure("X 未返回可用时间线，请检查账号状态。")
            WebTimelineParser.page(json)
        }
    }
    private suspend fun query(operation: String, variables: JSONObject): JSONObject {
        val p = discover(operation)
        val url = HttpUrl.Builder().scheme("https").host("x.com")
            .addPathSegments("i/api/graphql/${p.query}/$operation")
            .addQueryParameter("variables", variables.toString())
            .addQueryParameter("features", p.features.toString())
            .addQueryParameter("fieldToggles", p.toggles.toString()).build()
        // Local protocol probe: SearchTimeline POST=200 / GET=404; the other three queries GET=200.
        val request = if (operation == "SearchTimeline") {
            val body = JSONObject().put("variables", variables).put("features", p.features)
                .put("fieldToggles", p.toggles).put("queryId", p.query)
            base("https://x.com/i/api/graphql/${p.query}/$operation")
                .header("Authorization", "Bearer ${p.bearer}").header("x-twitter-client-language", "en")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        } else base(url.toString()).header("Authorization", "Bearer ${p.bearer}")
            .header("x-twitter-client-language", "en").build()
        val raw = text(request)
        return withContext(Dispatchers.Default) {
            val json = JSONObject(raw)
            val errors = json.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val codes = errors.objects().map { it.optInt("code") }
                throw WebSessionFailure(when {
                    codes.any { it in setOf(88, 420, 429) } -> "X 请求限流，请稍后重试。"
                    codes.any { it in setOf(32, 89, 215) } -> "X 会话失效，请重新登录。"
                    else -> "X 接口受限或需要人工验证，请在浏览器确认账号。"
                })
            }
            json
        }
    }
    private fun variables(request: TimelineRequest) = JSONObject().put("count", request.limit)
        .put("includePromotedContent", false).apply { request.cursor?.let { put("cursor", it) } }
    private fun ownId(): String = accountId?.takeIf { it.matches(Regex("[0-9]{1,30}")) }
        ?: throw WebSessionFailure("无法确认当前账号身份，请重新登录。")
    private suspend fun videoPage(operation: String, variables: JSONObject): Page<Post> =
        withContext(Dispatchers.Default) { WebTimelineParser.page(query(operation, variables)).let { page ->
            page.copy(items = page.items.mapNotNull { post ->
                val media = post.media.filter { it is Video || it is AnimatedGif }
                if (media.isEmpty()) null else post.copy(media = media)
            })
        } }
    override suspend fun searchVideos(query: String, request: TimelineRequest): Page<Post> {
        val keyword = query.trim()
        require(keyword.isNotEmpty() && keyword.length <= 500 && keyword.none { it.isISOControl() })
        return videoPage("SearchTimeline", variables(request).put("rawQuery", "($keyword) filter:videos")
            .put("querySource", "typed_query").put("product", "Latest"))
    }
    override suspend fun likedVideos(request: TimelineRequest): Page<Post> = videoPage("Likes",
        variables(request).put("userId", ownId()).put("withClientEventToken", false)
            .put("withBirdwatchNotes", false).put("withVoice", true))
    override suspend fun following(request: TimelineRequest): Page<Author> = withContext(Dispatchers.Default) {
        WebTimelineParser.authors(query("Following", variables(request).put("userId", ownId())
            .put("withSuperFollowsUserFields", true)))
    }
    override suspend fun authorVideos(authorId: String, request: TimelineRequest): Page<Post> {
        require(authorId.matches(Regex("[0-9]{1,30}")))
        return authorTimeline(authorId, request).let { page -> page.copy(items = page.items.mapNotNull { post ->
            val videos = post.media.filter { it is Video || it is AnimatedGif }
            if (videos.isEmpty()) null else post.copy(media = videos)
        }) }
    }
    /** GET verified locally with advancing cursors and a playable repost on page two. */
    suspend fun authorTimeline(authorId: String, request: TimelineRequest): Page<Post> {
        require(authorId.matches(Regex("[0-9]{1,30}")))
        return withContext(Dispatchers.Default) { WebTimelineParser.page(query("UserTweets", variables(request)
            .put("userId", authorId).put("withQuickPromoteEligibilityTweetFields", true)
            .put("withVoice", true).put("withV2Timeline", true))) }
    }
    override suspend fun translate(post: Post, language: String): PostTranslation = translationLock.withLock {
        require(post.id.matches(Regex("[0-9]{1,30}")) && language.matches(Regex("[A-Za-z]{2,3}(?:-[A-Za-z]{2,4})?")))
        val key = "${post.id}|$language"
        synchronized(translations) { translations[key] }?.let { return@withLock it }
        if (System.currentTimeMillis() < translationBlockedUntil) throw WebSessionFailure("X 翻译暂不可用，保留原文，稍后可重试。")
        val p = discover()
        fun authenticated(url: String) = base(url).header("Authorization", "Bearer ${p.bearer}")
            .header("x-twitter-client-language", language)
        try {
            val payload = JSONObject().put("content_type", "POST").put("id", post.id).put("dst_lang", language)
            val raw = try {
                text(authenticated("https://x.com/i/api/2/grok/translation.json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType())).build(), 128 * 1024)
            } catch (failure: WebHttpFailure) {
                if (failure.status !in setOf(404,405)) throw failure
                "{}"
            }
            val grok = JSONObject(raw).optJSONObject("result")?.optString("text")
            val translated = if (!grok.isNullOrBlank()) PostTranslation(grok.take(30000), language, "X / Grok") else {
                val url = "https://x.com/i/api/1.1/strato/column/None/tweetId=${post.id},destinationLanguage=Some($language),translationSource=Some(X),feature=None,timeout=None,onlyCached=None/translation/service/translateTweet"
                val data = JSONObject(text(authenticated(url).build(), 128 * 1024))
                val result = data.optString("translation")
                if (result.isBlank()) throw WebSessionFailure("X 暂无此帖译文，保留原文。")
                PostTranslation(result.take(30000), language, "X / ${data.optString("translationSource", "X")}")
            }
            currentCoroutineContext().ensureActive()
            synchronized(translations) { if (!closed) translations[key] = translated }
            translated
        } catch (failure: WebHttpFailure) {
            if (failure.status in setOf(401,403,429)) translationBlockedUntil = System.currentTimeMillis() + failure.retryMs.coerceAtLeast(60_000)
            throw failure
        }
    }
    override fun close() {
        if (closed) return
        closed = true; NetworkPolicy.dispose(http)
        protocols.clear(); discoveryHtml = null; discoveredBearer = null; synchronized(translations) { translations.clear() }
    }
    companion object {
        private const val WEB_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/157.0.0.0 Mobile Safari/537.36"
        private val FALSE_FEATURES = setOf("responsive_web_graphql_exclude_directive_enabled", "responsive_web_enhance_cards_enabled",
            "responsive_web_grok_analyze_button_fetch_trends_enabled", "responsive_web_grok_analyze_post_followups_enabled",
            "responsive_web_jetfuel_frame", "responsive_web_grok_share_attachment_enabled", "responsive_web_grok_image_annotation_enabled")
        private fun retryDelay(response: Response): Long = response.header("Retry-After")?.toLongOrNull()?.times(1000)?.coerceIn(1000,300000) ?: 60000
    }
}
class WebHttpFailure(val status: Int, val retryMs: Long, val displayMessage: String, val stage: String = "web"): ContentFailure(displayMessage)
