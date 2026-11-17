package io.github.tancan1998.xviewtv.core.domain

import io.github.tancan1998.xviewtv.core.model.*

/** Account-scoped remote browsing. No mutation or guest fallback. */
interface VideoLibrary {
    suspend fun searchVideos(query: String, request: TimelineRequest): Page<Post>
    suspend fun likedVideos(request: TimelineRequest): Page<Post>
    suspend fun following(request: TimelineRequest): Page<Author>
    suspend fun authorVideos(authorId: String, request: TimelineRequest): Page<Post>
}
