package io.github.tancan1998.xviewtv.core.network

import org.junit.Assert.*
import org.junit.Test

class WebProtocolDiscoveryTest {
    @Test fun resolvesHashedHomeBundlesOnly() {
        val html = """x.u=e=>({53852:"shared~bundle.Compose~bundle.HomeTimeline~bundle.LoggedInMain",99:"bundle.HomeTimeline",3:"loader.Other"}[e])+({53852:"58eb4ae5eb4c7e31",99:"1234567890abcdef",3:"abcdef0123456789"}[e])+"a.js""""
        val urls = WebProtocolDiscovery.timelineChunks(html)
        assertEquals(2,urls.size)
        assertTrue(urls.first().contains("shared~")); assertTrue(urls.all { it.startsWith("https://abs.twimg.com/") && it.endsWith("a.js") })
    }
    @Test fun rejectsExecutableOrMissingMappings() {
        assertTrue(WebProtocolDiscovery.timelineChunks("99:\"bundle.HomeTimeline/../../x\"").isEmpty())
        assertTrue(WebProtocolDiscovery.timelineChunks("99:\"bundle.HomeTimeline\"").isEmpty())
    }
}
