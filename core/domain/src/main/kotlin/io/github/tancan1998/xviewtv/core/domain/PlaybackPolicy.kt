package io.github.tancan1998.xviewtv.core.domain

import io.github.tancan1998.xviewtv.core.model.*

/** Queue order follows the currently filtered feed; video navigation skips photos and never wraps. */
data class PlaybackEntry(val post: Post, val media: MediaAttachment)
object PlaybackPolicy {
    val seekChoices = listOf(5, 10, 15, 30, 60)
    fun seekSeconds(value: Int): Int = value.takeIf { it in seekChoices } ?: 10
    fun seek(position: Long, duration: Long, offset: Long): Long =
        (position + offset).coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
    fun queue(posts: List<Post>): List<PlaybackEntry> = posts.flatMap { post -> post.media.map { PlaybackEntry(post, it) } }
    fun adjacentVideo(queue: List<PlaybackEntry>, index: Int, direction: Int): Int? {
        require(direction == -1 || direction == 1)
        var next = index + direction
        while (next in queue.indices) {
            if (queue[next].media is Video || queue[next].media is AnimatedGif) return next
            next += direction
        }
        return null
    }
}

/** A hold is temporary and idempotent, including cancel, repeated up, and disposal paths. */
class PlaybackBoost {
    private var original: Float? = null
    fun begin(speed: Float): Float {
        if (original == null) original = speed
        return maxOf(2f, original!! * 2f).coerceAtMost(4f)
    }
    fun end(): Float? = original.also { original = null }
    val active: Boolean get() = original != null
}
