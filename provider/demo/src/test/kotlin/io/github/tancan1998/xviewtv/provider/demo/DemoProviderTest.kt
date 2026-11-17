package io.github.tancan1998.xviewtv.provider.demo

import io.github.tancan1998.xviewtv.core.model.*
import io.github.tancan1998.xviewtv.core.provider.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DemoProviderTest {
    private val time = Instant.parse("2026-01-02T00:00:00Z")
    private val provider = DemoProvider(Clock.fixed(time, ZoneOffset.UTC))

    @Test fun paginationVisitsEveryPostOnce() = runTest {
        val ids = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = provider.getTimeline(TimelineRequest(limit = 3, cursor = cursor))
            assertEquals(time, page.fetchedAt)
            assertEquals(page.hasMore, page.nextCursor != null)
            ids += page.items.map { it.id }
            cursor = page.nextCursor
        } while (cursor != null)
        assertEquals(8, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun foreignCursorIsRejected() = runTest {
        try {
            provider.getTimeline(TimelineRequest(cursor = "another-provider:1"))
            fail("Foreign cursor must fail")
        } catch (_: ProviderException.InvalidCursor) { }
    }

    @Test fun profileTimelineContainsOnlyRequestedAuthor() = runTest {
        val page = provider.getUserTimeline("demo-a", TimelineRequest())
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.items.all { it.author.id == "demo-a" })
    }

    @Test fun demoNeverClaimsFollowingOrPlayback() = runTest {
        assertNull(provider.getCurrentAccount())
        assertFalse(ProviderCapability.FOLLOWING_TIMELINE in provider.capabilities)
        assertFalse(ProviderCapability.MEDIA_PLAYBACK in provider.capabilities)
        try {
            provider.getFollowing(null)
            fail("Guest demo must not fabricate account following")
        } catch (_: ProviderException.Unsupported) { }
    }
}
