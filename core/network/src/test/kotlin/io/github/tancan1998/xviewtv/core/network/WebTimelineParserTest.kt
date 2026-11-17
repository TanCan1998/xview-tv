package io.github.tancan1998.xviewtv.core.network

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebTimelineParserTest {
    private val tweet = """{"rest_id":"123","legacy":{"full_text":"Hello &amp; media","lang":"en","created_at":"Mon Nov 16 12:00:00 +0000 2026","extended_entities":{"media":[{"id_str":"1","type":"photo","media_url_https":"https://pbs.twimg.com/a.jpg","original_info":{"width":800,"height":600}},{"id_str":"2","type":"video","media_url_https":"https://pbs.twimg.com/b.jpg","video_info":{"duration_millis":2000,"variants":[{"url":"https://video.twimg.com/a.mp4","content_type":"video/mp4","bitrate":256000},{"url":"http://evil.test/a.mp4","content_type":"video/mp4"}]}}]}},"core":{"user_results":{"result":{"rest_id":"9","legacy":{"screen_name":"user","name":"Name"}}}}}"""
    @Test fun parsesPhotoVideoAndLanguage() {
        val post = WebTimelineParser.tweet(JSONObject(tweet))!!
        assertEquals("Hello & media",post.text); assertEquals("en",post.language)
        assertEquals(2,post.media.size)
        assertEquals(1,(post.media[1] as io.github.tancan1998.xviewtv.core.model.Video).variants.size)
    }
    @Test fun onlyInstructionEntriesAndBottomCursor() {
        val root = JSONObject("""{"data":{"home":{"home_timeline_urt":{"instructions":[{"type":"TimelineAddEntries","entries":[{"entryId":"tweet-123","content":{"itemContent":{"tweet_results":{"result":$tweet}}}},{"entryId":"cursor-bottom","content":{"cursorType":"Bottom","value":"opaque-next"}}]}]}}}}""")
        val page = WebTimelineParser.page(root)
        assertEquals(1,page.items.size); assertEquals("opaque-next",page.nextCursor); assertTrue(page.hasMore)
    }
    @Test fun repostKeepsOriginalVideoAuthorAndTranslationId() {
        val wrapper = JSONObject("""{"rest_id":"999","legacy":{"retweeted_status_result":{"result":$tweet}}}""")
        val parsed = WebTimelineParser.tweet(wrapper)!!
        assertEquals("123", parsed.id); assertEquals("123", parsed.repostedPostId)
        assertEquals("user", parsed.author.username)
        assertTrue(parsed.media.any { it is io.github.tancan1998.xviewtv.core.model.Video })
        assertEquals("https://x.com/user/status/123", parsed.canonicalUrl)
    }
    @Test fun unavailableRepostedVideoIsSkipped() {
        assertNull(WebTimelineParser.tweet(JSONObject("""{"rest_id":"999","legacy":{"retweeted_status_result":{"result":{"__typename":"TweetUnavailable"}}}}""")))
    }
    @Test fun restrictedOrUnavailableTweetDoesNotCrash() {
        assertNull(WebTimelineParser.tweet(JSONObject("""{"__typename":"TweetUnavailable"}""")))
    }
    @Test fun rejectsUntrustedMediaOrigins() {
        assertNull(WebTimelineParser.safeUrl("https://video.twimg.com.evil.test/a"))
        assertNull(WebTimelineParser.safeUrl("https://secret@pbs.twimg.com/a"))
        assertNull(WebTimelineParser.safeUrl("http://pbs.twimg.com/a"))
        assertNotNull(WebTimelineParser.safeUrl("https://video.twimg.com/a.mp4?tag=1"))
    }
}
