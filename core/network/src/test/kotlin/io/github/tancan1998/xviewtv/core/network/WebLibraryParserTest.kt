package io.github.tancan1998.xviewtv.core.network

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebLibraryParserTest {
    @Test fun parsesModernUserCoreWithLegacyDescription() {
        val user = WebTimelineParser.author(JSONObject("""{"rest_id":"42","core":{"screen_name":"creator","name":"A &amp; B"},"avatar":{"image_url":"https://pbs.twimg.com/avatar.jpg"},"legacy":{"description":"hello"},"is_blue_verified":true}"""))!!
        assertEquals("creator", user.username); assertEquals("A & B", user.displayName)
        assertEquals("hello", user.description); assertTrue(user.verified)
    }
    @Test fun followingDeduplicatesAndIgnoresPromotionsAndUnusedUserObjects() {
        val user = """{"rest_id":"42","legacy":{"screen_name":"creator","name":"Creator"}}"""
        val entry = """{"entryId":"user-42","content":{"itemContent":{"user_results":{"result":$user}}}}"""
        val root = JSONObject("""{"data":{"user":{"result":{"timeline":{"timeline":{"instructions":[{"entries":[$entry,$entry,{"entryId":"promoted-43","content":{"itemContent":{"user_results":{"result":{"rest_id":"43","core":{"screen_name":"ad"}}}}}},{"entryId":"cursor-bottom","content":{"cursorType":"Bottom","value":"opaque-next"}}]}]},"unused":$user}}}}}""")
        val page = WebTimelineParser.authors(root)
        assertEquals(listOf("42"), page.items.map { it.id }); assertEquals("opaque-next", page.nextCursor)
    }
    @Test fun followingRecognizesFinalEmptyPage() {
        val page = WebTimelineParser.authors(JSONObject("""{"data":{"instructions":[{"entries":[]}]}}"""))
        assertTrue(page.items.isEmpty()); assertFalse(page.hasMore)
    }
    @Test fun unavailableUsersAndUnsafeAvatarsAreRejected() {
        assertNull(WebTimelineParser.author(JSONObject("""{"__typename":"UserUnavailable"}""")))
        assertNull(WebTimelineParser.author(JSONObject("""{"rest_id":"42","core":{"screen_name":"bad/handle"}}""")))
        assertNull(WebTimelineParser.author(JSONObject("""{"rest_id":"42","core":{"screen_name":"ok"},"avatar":{"image_url":"https://evil.test/a"}}"""))!!.avatarUrl)
    }
    @Test fun locatesProfileAndSearchBundlesWithoutUsingUnrelatedChunks() {
        val html = """{1:"bundle.Profile",2:"bundle.SearchTimeline",3:"bundle.HomeTimeline",4:"loader.Profile",1:"0123456789abcdef",2:"abcdef0123456789",3:"1234567890abcdef",4:"fedcba0123456789"}"""
        assertTrue(WebProtocolDiscovery.operationChunks(html, "Likes").single().contains("bundle.Profile"))
        assertTrue(WebProtocolDiscovery.operationChunks(html, "SearchTimeline").single().contains("SearchTimeline"))
        assertTrue(WebProtocolDiscovery.timelineChunks(html).single().contains("HomeTimeline"))
    }
}
