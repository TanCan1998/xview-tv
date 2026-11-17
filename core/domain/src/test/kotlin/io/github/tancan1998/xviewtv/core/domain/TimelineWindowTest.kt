package io.github.tancan1998.xviewtv.core.domain

import io.github.tancan1998.xviewtv.core.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TimelineWindowTest {
    private fun post(id: Int) = Post(id.toString(), ProviderId("x-web"), Author("u", "user", "User"), "text", Instant.EPOCH,
        listOf(Photo("m$id", "https://pbs.twimg.com/a.jpg")), "https://x.com/user/status/$id")
    private fun page(ids: IntRange, cursor: String?) = Page(ids.map(::post), cursor, Instant.EPOCH, cursor != null)
    @Test fun deduplicatesAndBoundsWindow() {
        val window = TimelineWindow(20)
        window.append(page(1..18,"a")); window.append(page(15..30,"b"))
        assertEquals(20,window.posts.size); assertEquals("11",window.posts.first().id)
        assertEquals(20, window.posts.distinctBy { it.id }.size)
    }
    @Test fun cursorCyclesStop() {
        val window = TimelineWindow()
        window.append(page(1..3,"a")); window.append(page(3..5,"b")); window.append(page(5..8,"a"))
        assertFalse(window.hasMore); assertNull(window.cursor)
    }
    @Test fun emptyPageMayHaveNextCursor() {
        val window = TimelineWindow(); window.append(Page(emptyList(),"a",Instant.EPOCH,true))
        assertTrue(window.hasMore); assertTrue(window.posts.isEmpty())
    }
    @Test fun refreshClearsAccountScopedWindow() {
        val window = TimelineWindow(); window.append(page(1..3,"a")); window.clear()
        assertTrue(window.posts.isEmpty()); assertNull(window.cursor); assertTrue(window.hasMore)
    }
}
