package io.github.tancan1998.xviewtv.core.domain

import io.github.tancan1998.xviewtv.core.model.*

interface TimelineRepository {
    suspend fun getTimeline(request: TimelineRequest): Page<Post>
}

enum class MediaFilter { ALL, IMAGES, VIDEOS }

class FilterMedia {
    operator fun invoke(posts: List<Post>, filter: MediaFilter): List<Post> = posts.filter { post ->
        when (filter) {
            MediaFilter.ALL -> post.media.isNotEmpty()
            MediaFilter.IMAGES -> post.media.any { it is Photo }
            MediaFilter.VIDEOS -> post.media.any { it is Video || it is AnimatedGif }
        }
    }
}
