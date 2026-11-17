package io.github.tancan1998.xviewtv.core.network

import io.github.tancan1998.xviewtv.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Only traverse timeline instruction entries; do not collect quoted/promoted tweets recursively. */
object WebTimelineParser {
    val provider = ProviderId("x-web")
    fun page(root: JSONObject): Page<Post> {
        val instructions = locateInstructions(root) ?: throw WebSessionFailure("X 时间线结构已变化，未找到分页指令。")
        val posts = LinkedHashMap<String, Post>()
        var cursor: String? = null
        fun entry(entry: JSONObject) {
            if (entry.optString("entryId").startsWith("promoted")) return
            val content = entry.optJSONObject("content") ?: entry
            if (content.optString("cursorType").equals("Bottom", true)) {
                cursor = content.optString("value").takeIf { it.isNotBlank() }; return
            }
            val item = content.optJSONObject("itemContent") ?: content
            if (item.has("promotedMetadata")) return
            val result = item.optJSONObject("tweet_results")?.optJSONObject("result")
            result?.let { tweet(it) }?.let { posts[it.id] = it }
            content.optJSONArray("items")?.objects()?.forEach { nested ->
                entry(nested.optJSONObject("item") ?: nested)
            }
        }
        instructions.objects().forEach { instruction ->
            instruction.optJSONArray("entries")?.objects()?.forEach(::entry)
            instruction.optJSONObject("entry")?.let(::entry)
        }
        return Page(posts.values.toList(), cursor, Instant.now(), !cursor.isNullOrBlank(), RequestUsage(1))
    }
    fun author(input: JSONObject): Author? {
        val user = input.optJSONObject("user") ?: input
        val legacy = user.optJSONObject("legacy")
        val core = user.optJSONObject("core")
        val id = user.optString("rest_id")
        val handle = core?.optString("screen_name")?.takeIf { it.isNotBlank() } ?: legacy?.optString("screen_name").orEmpty()
        if (!id.matches(Regex("[0-9]{1,30}")) || !handle.matches(Regex("[A-Za-z0-9_]{1,50}"))) return null
        val name = core?.optString("name")?.takeIf { it.isNotBlank() } ?: legacy?.optString("name", handle) ?: handle
        val avatar = user.optJSONObject("avatar")?.optString("image_url")?.takeIf { it.isNotBlank() }
            ?: legacy?.optString("profile_image_url_https").orEmpty()
        return Author(id, handle, decode(name), safeUrl(avatar), legacy?.optString("description")?.take(500),
            user.optBoolean("is_blue_verified") || legacy?.optBoolean("verified") == true)
    }
    fun authors(root: JSONObject): Page<Author> {
        val instructions = locateInstructions(root) ?: throw WebSessionFailure("X 关注列表结构已变化。")
        val users = LinkedHashMap<String, Author>(); var cursor: String? = null
        fun entry(entry: JSONObject) {
            if (entry.optString("entryId").startsWith("promoted")) return
            val content = entry.optJSONObject("content") ?: entry
            if (content.optString("cursorType").equals("Bottom", true)) {
                cursor = content.optString("value").takeIf { it.isNotBlank() }; return
            }
            val item = content.optJSONObject("itemContent") ?: content
            if (item.has("promotedMetadata")) return
            item.optJSONObject("user_results")?.optJSONObject("result")?.let(::author)?.let { users[it.id] = it }
            content.optJSONArray("items")?.objects()?.forEach { entry(it.optJSONObject("item") ?: it) }
        }
        instructions.objects().forEach { instruction ->
            instruction.optJSONArray("entries")?.objects()?.forEach(::entry)
            instruction.optJSONObject("entry")?.let(::entry)
        }
        return Page(users.values.toList(), cursor, Instant.now(), cursor != null, RequestUsage(1))
    }
    private fun locateInstructions(value: Any?, depth: Int = 0): JSONArray? {
        if (depth > 12) return null
        if (value is JSONObject) {
            value.optJSONArray("instructions")?.let { return it }
            for (key in value.keys()) locateInstructions(value.opt(key), depth + 1)?.let { return it }
        }
        return null
    }
    fun tweet(input: JSONObject): Post? {
        val result = input.optJSONObject("tweet") ?: input
        val legacy = result.optJSONObject("legacy") ?: return null
        val repost = legacy.optJSONObject("retweeted_status_result")?.optJSONObject("result")
        if (repost != null) return tweet(repost)?.let { original -> original.copy(repostedPostId = original.id) }
        val id = result.optString("rest_id", legacy.optString("id_str"))
        if (!id.matches(Regex("[0-9]{1,30}"))) return null
        val user = result.optJSONObject("core")?.optJSONObject("user_results")?.optJSONObject("result") ?: return null
        val author = author(user) ?: return null
        val handle = author.username
        val note = result.optJSONObject("note_tweet")?.optJSONObject("note_tweet_results")?.optJSONObject("result")
        val text = decode(note?.optString("text")?.takeIf { it.isNotBlank() } ?: legacy.optString("full_text"))
        val media = (legacy.optJSONObject("extended_entities") ?: legacy.optJSONObject("entities"))
            ?.optJSONArray("media")?.objects()?.mapNotNull(::attachment)?.take(8) ?: emptyList()
        val created = runCatching { ZonedDateTime.parse(legacy.optString("created_at"),
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)).toInstant() }.getOrDefault(Instant.EPOCH)
        return Post(id, provider, author, text.take(30000), created, media,
            "https://x.com/$handle/status/$id", legacy.optBoolean("possibly_sensitive"),
            language = legacy.optString("lang").takeIf { it.isNotBlank() })
    }
    private fun attachment(m: JSONObject): MediaAttachment? {
        val id = m.optString("id_str"); val image = safeUrl(m.optString("media_url_https")) ?: return null
        val size = m.optJSONObject("original_info") ?: m.optJSONObject("sizes")?.optJSONObject("large")
        val w = size?.optInt("width", size.optInt("w"))?.takeIf { it > 0 }
        val h = size?.optInt("height", size.optInt("h"))?.takeIf { it > 0 }
        if (m.optString("type") == "photo") return Photo(id, image, "$image?name=small", w, h)
        val info = m.optJSONObject("video_info") ?: return null
        val variants = info.optJSONArray("variants")?.objects()?.mapNotNull { v ->
            val url = safeUrl(v.optString("url")); val mime = v.optString("content_type")
            if (url != null && mime in setOf("video/mp4", "application/x-mpegURL", "application/vnd.apple.mpegurl"))
                VideoVariant(url, mime, v.optLong("bitrate").takeIf { it > 0 }) else null
        } ?: emptyList()
        if (variants.isEmpty()) return null
        return if (m.optString("type") == "animated_gif") AnimatedGif(id, variants, image, w, h)
        else Video(id, variants, info.optLong("duration_millis").takeIf { it > 0 }, image, w, h)
    }
    fun safeUrl(value: String): String? = runCatching {
        val uri = java.net.URI(value)
        value.takeIf { uri.scheme == "https" && uri.userInfo == null &&
            (uri.host == "pbs.twimg.com" || uri.host == "video.twimg.com") }
    }.getOrNull()
    private fun decode(text: String) = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
}
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
