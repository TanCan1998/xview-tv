package io.github.tancan1998.xviewtv.core.provider

import io.github.tancan1998.xviewtv.core.model.*

interface ContentProvider {
    val id: ProviderId
    val mode: AccessMode
    val capabilities: Set<ProviderCapability>
    suspend fun getCurrentAccount(): Account?
    suspend fun getPublicProfile(username: String): Author
    suspend fun getPost(postId: String): Post?
    suspend fun getTimeline(request: TimelineRequest): Page<Post>
    suspend fun getUserTimeline(userId: String, request: TimelineRequest): Page<Post>
    suspend fun getFollowing(cursor: String?): Page<Author>
    suspend fun getMediaSource(media: MediaRef): PlayableSource
}

sealed class ProviderException(message: String) : Exception(message) {
    class Unsupported(val capability: ProviderCapability) : ProviderException("Unsupported capability: $capability")
    class Unavailable : ProviderException("Selected provider is unavailable")
    class AuthenticationRequired : ProviderException("Account authorization is required")
    class InvalidCursor : ProviderException("Invalid or expired cursor")
    class NotFound : ProviderException("Content is unavailable")
}
