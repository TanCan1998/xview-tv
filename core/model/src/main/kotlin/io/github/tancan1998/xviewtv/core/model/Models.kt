package io.github.tancan1998.xviewtv.core.model

import java.time.Instant

@JvmInline
value class ProviderId(val value: String) {
    init { require(value.isNotBlank()) }
}

enum class AccessMode { GUEST, ACCOUNT }
enum class ProviderCapability {
    PUBLIC_TIMELINE, FOLLOWING_TIMELINE, USER_TIMELINE, USER_FOLLOWING_LIST,
    MEDIA_SEARCH, REMOTE_BOOKMARKS, LOCAL_SUBSCRIPTIONS, MEDIA_PLAYBACK, PAGINATION, COST_ESTIMATE,
}
data class Account(val id: String, val providerId: ProviderId, val username: String)
data class Author(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val description: String? = null,
    val verified: Boolean = false,
)
sealed interface MediaAttachment {
    val id: String
    val previewUrl: String?
    val width: Int?
    val height: Int?
}
data class Photo(
    override val id: String,
    val imageUrl: String,
    override val previewUrl: String? = null,
    override val width: Int? = null,
    override val height: Int? = null,
) : MediaAttachment
data class VideoVariant(val url: String, val mimeType: String, val bitrate: Long? = null)
data class Video(
    override val id: String,
    val variants: List<VideoVariant>,
    val durationMs: Long? = null,
    override val previewUrl: String? = null,
    override val width: Int? = null,
    override val height: Int? = null,
) : MediaAttachment
data class AnimatedGif(
    override val id: String,
    val variants: List<VideoVariant>,
    override val previewUrl: String? = null,
    override val width: Int? = null,
    override val height: Int? = null,
) : MediaAttachment
data class Post(
    val id: String,
    val providerId: ProviderId,
    val author: Author,
    val text: String,
    val createdAt: Instant,
    val media: List<MediaAttachment>,
    val canonicalUrl: String,
    val sensitive: Boolean = false,
    val quotedPostId: String? = null,
    val repostedPostId: String? = null,
    val language: String? = null,
)
data class RequestUsage(val requestCount: Int, val remainingRequests: Long? = null)
data class Page<T>(
    val items: List<T>,
    val nextCursor: String?,
    val fetchedAt: Instant,
    val hasMore: Boolean,
    val usage: RequestUsage? = null,
) {
    init { require(!hasMore || !nextCursor.isNullOrBlank()) { "More pages require an opaque cursor" } }
}
data class TimelineRequest(
    val limit: Int = 20,
    val cursor: String? = null,
    val sinceId: String? = null,
    val excludeReplies: Boolean = true,
    val excludeReposts: Boolean = false,
) {
    init { require(limit in 1..100) { "Limit must be between 1 and 100" } }
}
data class MediaRef(val providerId: ProviderId, val postId: String, val mediaId: String)
data class PlayableSource(val url: String, val mimeType: String, val expiresAt: Instant? = null)
