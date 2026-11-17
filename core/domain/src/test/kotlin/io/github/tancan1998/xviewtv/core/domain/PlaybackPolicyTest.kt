package io.github.tancan1998.xviewtv.core.domain

import io.github.tancan1998.xviewtv.core.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    private fun post(id: String, vararg media: MediaAttachment) = Post(id, ProviderId("x-web"), Author("u", "user", "User"), "", Instant.EPOCH, media.toList(), "https://x.com/user/status/$id")
    @Test fun videoNavigationSkipsPhotosAcrossPostsAndStopsAtEdges() {
        val queue = PlaybackPolicy.queue(listOf(
            post("1", Photo("p", "https://pbs.twimg.com/p"), Video("v1", emptyList())),
            post("2", Photo("p2", "https://pbs.twimg.com/q")), post("3", AnimatedGif("v2", emptyList()))))
        assertEquals(1, PlaybackPolicy.adjacentVideo(queue, 0, 1))
        assertEquals(3, PlaybackPolicy.adjacentVideo(queue, 1, 1))
        assertEquals(1, PlaybackPolicy.adjacentVideo(queue, 3, -1))
        assertNull(PlaybackPolicy.adjacentVideo(queue, 1, -1))
        assertNull(PlaybackPolicy.adjacentVideo(queue, 3, 1))
    }
    @Test fun emptyQueueAndPhotoOnlyQueueHaveNoVideo() {
        assertNull(PlaybackPolicy.adjacentVideo(emptyList(), 0, 1))
        val queue = PlaybackPolicy.queue(listOf(post("1", Photo("p", "https://pbs.twimg.com/p"))))
        assertNull(PlaybackPolicy.adjacentVideo(queue, 0, 1))
    }
    @Test fun holdRestoresOriginalNonDefaultSpeedOnReleaseOrCancel() {
        val boost = PlaybackBoost()
        assertEquals(3f, boost.begin(1.5f), .001f)
        assertEquals(3f, boost.begin(3f), .001f)
        assertTrue(boost.active)
        assertEquals(1.5f, boost.end()!!, .001f)
        assertFalse(boost.active)
        assertNull(boost.end())
        assertEquals(2f, boost.begin(.75f), .001f)
        assertEquals(.75f, boost.end()!!, .001f)
    }
    @Test fun seekingClampsBothEndsAndSupportsUnknownDuration() {
        assertEquals(0L, PlaybackPolicy.seek(5000, 100000, -10000))
        assertEquals(100000L, PlaybackPolicy.seek(98000, 100000, 30000))
        assertEquals(40000L, PlaybackPolicy.seek(10000, -1, 30000))
    }
    @Test fun invalidSavedIntervalsUseSafeDefault() {
        assertEquals(10, PlaybackPolicy.seekSeconds(-1))
        assertEquals(10, PlaybackPolicy.seekSeconds(999))
        PlaybackPolicy.seekChoices.forEach { assertEquals(it, PlaybackPolicy.seekSeconds(it)) }
    }
}
