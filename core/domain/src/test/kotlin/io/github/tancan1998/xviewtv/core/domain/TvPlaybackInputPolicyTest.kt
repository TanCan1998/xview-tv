package io.github.tancan1998.xviewtv.core.domain

import org.junit.Assert.*
import org.junit.Test

class TvPlaybackInputPolicyTest {
    @Test fun initialSeekAlwaysWorksInEitherDirection() {
        assertTrue(TvPlaybackInputPolicy.shouldSeek(0, 101, 100))
    }
    @Test fun heldSeekIsRateLimitedButContinuous() {
        assertFalse(TvPlaybackInputPolicy.shouldSeek(1, 1179, 1000))
        assertTrue(TvPlaybackInputPolicy.shouldSeek(2, 1180, 1000))
        assertTrue(TvPlaybackInputPolicy.shouldSeek(20, 2000, 1180))
    }
    @Test fun heldOkMustNotToggleBackAndForth() {
        assertTrue(TvPlaybackInputPolicy.shouldToggle(0))
        assertFalse(TvPlaybackInputPolicy.shouldToggle(1))
        assertFalse(TvPlaybackInputPolicy.shouldToggle(30))
    }
    @Test fun playerControlsTimeoutIsFinite() {
        assertEquals(3500L, TvPlaybackInputPolicy.CONTROLS_TIMEOUT_MS)
    }
}
