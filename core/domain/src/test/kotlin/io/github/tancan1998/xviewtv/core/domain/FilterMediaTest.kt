package io.github.tancan1998.xviewtv.core.domain

import io.github.tancan1998.xviewtv.core.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class FilterMediaTest {
    private fun post(sensitive: Boolean) = Post("p", ProviderId("test"), Author("a", "demo", "Demo"), "Synthetic",
        Instant.EPOCH, listOf(Photo("m", "demo://photo")), "demo://post", sensitive)

    @Test fun includesSensitivePostsAndStillFiltersByMediaType() {
        val input = listOf(post(false).copy(id = "safe"), post(true).copy(id = "sensitive"))
        assertEquals(listOf("safe", "sensitive"), FilterMedia()(input, MediaFilter.ALL).map { it.id })
        assertEquals(2, FilterMedia()(input, MediaFilter.IMAGES).size)
        assertTrue(FilterMedia()(input, MediaFilter.VIDEOS).isEmpty())
    }
}
