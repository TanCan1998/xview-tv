package io.github.tancan1998.xviewtv.core.domain

import io.github.tancan1998.xviewtv.core.model.*

/** UI owns this bounded window; opaque cursors never become page numbers. */
class TimelineWindow(private val capacity: Int = 160) {
    init { require(capacity >= 20) }
    var posts: List<Post> = emptyList(); private set
    var cursor: String? = null; private set
    var hasMore: Boolean = true; private set
    private val cursors = LinkedHashSet<String>()
    fun clear() { posts = emptyList(); cursor = null; hasMore = true; cursors.clear() }
    fun append(page: Page<Post>) {
        posts = (posts + page.items).distinctBy { it.id }.takeLast(capacity)
        val next = page.nextCursor
        hasMore = page.hasMore && !next.isNullOrBlank() && next != cursor && next !in cursors
        cursor?.let { cursors.add(it) }
        while (cursors.size > 32) cursors.remove(cursors.first())
        cursor = if (hasMore) next else null
    }
}

data class PostTranslation(val text: String, val targetLanguage: String, val provider: String)
interface PostTranslator { suspend fun translate(post: Post, language: String): PostTranslation }

/** Providers expose only curated messages; UI must not print arbitrary HTTP/exception details. */
open class ContentFailure(val userMessage: String): Exception(userMessage)
