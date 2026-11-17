package io.github.tancan1998.xviewtv.core.domain

/** Stateless TV key repeat policy, independent of UI/toolkit and player implementation. */
object TvPlaybackInputPolicy {
    const val CONTROLS_TIMEOUT_MS = 3500L
    const val SEEK_REPEAT_INTERVAL_MS = 180L
    fun shouldSeek(repeat: Int, now: Long, last: Long): Boolean =
        repeat == 0 || now - last >= SEEK_REPEAT_INTERVAL_MS
    fun shouldToggle(repeat: Int): Boolean = repeat == 0
}
