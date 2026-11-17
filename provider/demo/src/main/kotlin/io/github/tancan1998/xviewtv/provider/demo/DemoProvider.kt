package io.github.tancan1998.xviewtv.provider.demo

import io.github.tancan1998.xviewtv.core.model.*
import io.github.tancan1998.xviewtv.core.provider.*
import java.time.Clock
import java.time.Instant

/** Offline synthetic fixtures. No X requests, remote images or playable media. */
class DemoProvider(private val clock: Clock = Clock.systemUTC()) : ContentProvider {
    override val id = ProviderId("demo")
    override val mode = AccessMode.GUEST
    override val capabilities = setOf(
        ProviderCapability.PUBLIC_TIMELINE,
        ProviderCapability.USER_TIMELINE,
        ProviderCapability.PAGINATION,
    )
    private val authors = listOf(
        Author("demo-a", "landscape_demo", "风景记录"),
        Author("demo-b", "motion_demo", "光影片刻"),
    )
    private val posts = List(8) { index ->
        val postId = "demo-${index + 1}"
        val photo = Photo("$postId-photo", "demo://media/$postId/photo", width = 1920, height = 1080)
        val video = Video("$postId-video", emptyList(), durationMs = 30_000, width = 1920, height = 1080)
        Post(
            id = postId,
            providerId = id,
            author = authors[index % authors.size],
            text = listOf("山海之间", "城市夜色", "晴空与远方", "微光时刻")[index % 4],
            createdAt = Instant.parse("2026-01-01T12:00:00Z").minusSeconds(index * 3600L),
            media = when (index % 3) { 0 -> listOf(photo); 1 -> listOf(video); else -> listOf(photo, video) },
            canonicalUrl = "demo://posts/$postId",
        )
    }

    override suspend fun getCurrentAccount(): Account? = null
    override suspend fun getPublicProfile(username: String): Author =
        authors.firstOrNull { it.username.equals(username.removePrefix("@"), ignoreCase = true) }
            ?: throw ProviderException.NotFound()
    override suspend fun getPost(postId: String): Post? = posts.firstOrNull { it.id == postId }
    override suspend fun getTimeline(request: TimelineRequest): Page<Post> = page(posts, request)
    override suspend fun getUserTimeline(userId: String, request: TimelineRequest): Page<Post> {
        if (authors.none { it.id == userId }) throw ProviderException.NotFound()
        return page(posts.filter { it.author.id == userId }, request)
    }
    override suspend fun getFollowing(cursor: String?): Page<Author> =
        throw ProviderException.Unsupported(ProviderCapability.USER_FOLLOWING_LIST)
    override suspend fun getMediaSource(media: MediaRef): PlayableSource =
        throw ProviderException.Unsupported(ProviderCapability.MEDIA_PLAYBACK)

    private fun page(source: List<Post>, request: TimelineRequest): Page<Post> {
        val filtered = request.sinceId?.let { sinceId ->
            val boundary = source.indexOfFirst { it.id == sinceId }
            if (boundary < 0) throw ProviderException.NotFound()
            source.take(boundary)
        } ?: source
        val offset = request.cursor?.let { cursor ->
            if (!cursor.startsWith("demo:")) throw ProviderException.InvalidCursor()
            cursor.removePrefix("demo:").toIntOrNull()?.takeIf { it in 0..filtered.size }
                ?: throw ProviderException.InvalidCursor()
        } ?: 0
        val items = filtered.drop(offset).take(request.limit)
        val next = offset + items.size
        return Page(items, if (next < filtered.size) "demo:$next" else null, clock.instant(), next < filtered.size)
    }
}
