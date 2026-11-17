package io.github.tancan1998.xviewtv

import android.app.Instrumentation
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import io.github.tancan1998.xviewtv.core.network.WebSession
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises a detached view. It never injects input into any display or opens an Activity. */
class PlaybackComponentInstrumentation : Instrumentation() {
    private var verifySavedSession = false
    private var verifyLibrary = false
    private var verifyReposts = false
    override fun onCreate(arguments: Bundle?) {
        verifySavedSession = arguments?.getString("verifySavedSession") == "true"
        verifyLibrary = arguments?.getString("verifyLibrary") == "true"
        verifyReposts = arguments?.getString("verifyReposts") == "true"
        super.onCreate(arguments); start()
    }
    override fun onStart() {
        var count = 0
        val results = mutableListOf<String>()
        fun passed(name: String) { count++; results += "PASS $name"; sendStatus(0, Bundle().apply { putString("stream", "PASS $name\n") }) }
        try {
            val store = "synthetic_multi_account_test"
            val prefs = targetContext.getSharedPreferences(store, android.content.Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            try {
                val vault = SessionVault(targetContext, store)
                fun sample(id: String) = VerifiedSession(WebSession.parse("auth_token=synthetic-$id; ct0=csrf-$id"), WebAccount(id, "user$id", "Synthetic $id"))
                vault.save(sample("1")); vault.save(sample("2"))
                check(vault.accounts().map { it.id } == listOf("1", "2")); check(vault.load()?.account?.id == "2")
                passed("multi account encrypted append and selected account")
                vault.select("1"); check(vault.load()?.account?.id == "1")
                check(SessionVault(targetContext, store).load()?.account?.id == "1")
                passed("multi account switch survives vault recreation")
                vault.save(sample("1")); check(vault.accounts().size == 2)
                passed("same account updates without duplication")
                vault.remove("2"); check(vault.load()?.account?.id == "1")
                vault.save(sample("2")); vault.clear(); check(vault.load()?.account?.id == "1")
                passed("logout preserves other saved accounts")
            } finally {
                prefs.edit().clear().commit()
                java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("xview_test_$store") }
            }
            lateinit var view: GesturePlayerView
            var speed = 1.5f
            val seeks = mutableListOf<Boolean>()
            runOnMainSync {
                view = GesturePlayerView(targetContext)
                view.layout(0, 0, 1000, 600)
                view.speed = { speed }; view.changeSpeed = { speed = it }; view.seek = { seeks += it }
            }
            fun event(action: Int, x: Float, down: Long, time: Long) {
                runOnMainSync {
                    val e = MotionEvent.obtain(down, time, action, x, 200f, 0)
                    view.dispatchTouchEvent(e); e.recycle()
                }
            }
            fun doubleTap(x: Float) {
                val now = SystemClock.uptimeMillis()
                event(MotionEvent.ACTION_DOWN, x, now, now)
                event(MotionEvent.ACTION_UP, x, now, now + 20)
                event(MotionEvent.ACTION_DOWN, x, now + 100, now + 100)
                event(MotionEvent.ACTION_UP, x, now + 100, now + 120)
            }
            doubleTap(100f)
            check(seeks == listOf(false)); passed("double tap left seeks backward")
            doubleTap(900f)
            check(seeks == listOf(false, true)); passed("double tap right seeks forward")
            doubleTap(500f)
            check(seeks.size == 2); passed("double tap center never seeks")
            fun hold(original: Float, ending: Int) {
                runOnMainSync { speed = original }
                val down = SystemClock.uptimeMillis()
                event(MotionEvent.ACTION_DOWN, 900f, down, down)
                val latch = CountDownLatch(1)
                Handler(Looper.getMainLooper()).postDelayed({ latch.countDown() }, 800)
                check(latch.await(3, TimeUnit.SECONDS))
                val expected = maxOf(2f, original * 2f).coerceAtMost(4f)
                check(kotlin.math.abs(speed - expected) < .001f) { "hold did not accelerate" }
                event(ending, 900f, down, SystemClock.uptimeMillis())
                check(kotlin.math.abs(speed - original) < .001f) { "hold did not restore" }
            }
            hold(1.5f, MotionEvent.ACTION_UP); passed("hold 1.5x to 3x then release restores 1.5x")
            hold(.75f, MotionEvent.ACTION_CANCEL); passed("hold 0.75x to 2x then cancel restores 0.75x")
            runOnMainSync { view.finishBoost(); view.seek = null; view.speed = null; view.changeSpeed = null }
            passed("repeated cleanup is safe")
            check(runCatching { WebSession.parse("auth_token=synthetic-only") }.isFailure)
            passed("incomplete Cookie rejected")
            // Optional verification uses the already authorized app vault in memory only, never logs identity or tokens.
            val saved = if (verifySavedSession) SessionVault(targetContext).load() else null
            if (saved != null) {
                val parsed = WebSession.parse("auth_token=${saved.session.authToken}; ct0=${saved.session.csrf}")
                val verified = WebIdentity.verify(AppPreferences(targetContext).readProxy(), parsed)
                check(verified.account.id == saved.account.id)
                passed("direct Cookie parse and real web identity verification")
            }
            if (verifyReposts) kotlinx.coroutines.runBlocking {
                sendStatus(0, Bundle().apply { putString("stream", "STAGE repost saved session load\n") })
                val vault = SessionVault(targetContext).load() ?: error("No saved session")
                sendStatus(0, Bundle().apply { putString("stream", "STAGE repost session loaded\n") })
                val client = io.github.tancan1998.xviewtv.core.network.XWebClient(AppPreferences(targetContext).readProxy(), vault.session, vault.account.id) { evidence ->
                    sendStatus(0, Bundle().apply { putString("stream", "HTTP $evidence\n") })
                }
                try {
                    val request = io.github.tancan1998.xviewtv.core.model.TimelineRequest(limit = 40)
                    val creators = client.following(request).items
                    var repostVideos = 0
                    for ((index, creator) in creators.take(5).withIndex()) {
                        val first = client.authorTimeline(creator.id, request)
                        fun count(page: io.github.tancan1998.xviewtv.core.model.Page<io.github.tancan1998.xviewtv.core.model.Post>): Int = page.items.count {
                            it.repostedPostId != null && it.media.any { media -> media is io.github.tancan1998.xviewtv.core.model.Video || media is io.github.tancan1998.xviewtv.core.model.AnimatedGif }
                        }
                        repostVideos += count(first)
                        passed("creator ${index + 1} timeline count=${first.items.size} repostVideos=${count(first)} more=${first.hasMore}")
                        if (first.hasMore) {
                            val next = client.authorTimeline(creator.id, request.copy(cursor = first.nextCursor))
                            check(next.nextCursor != first.nextCursor)
                            repostVideos += count(next)
                            passed("creator ${index + 1} second page count=${next.items.size} repostVideos=${count(next)} cursor advanced")
                        }
                        if (repostVideos > 0) break
                    }
                    check(repostVideos > 0); passed("real creator timeline includes reposted playable videos")
                } finally { client.close() }
            }
            if (verifyLibrary) {
                sendStatus(0, Bundle().apply { putString("stream", "STAGE entering library checks\n") })
                kotlinx.coroutines.runBlocking {
                sendStatus(0, Bundle().apply { putString("stream", "STAGE loading saved session\n") })
                val vault = SessionVault(targetContext).load() ?: error("No saved session")
                sendStatus(0, Bundle().apply { putString("stream", "STAGE saved session loaded\n") })
                val client = io.github.tancan1998.xviewtv.core.network.XWebClient(AppPreferences(targetContext).readProxy(), vault.session, vault.account.id) { evidence ->
                    sendStatus(0, Bundle().apply { putString("stream", "HTTP $evidence\n") })
                }
                try {
                    val request = io.github.tancan1998.xviewtv.core.model.TimelineRequest(limit = 20)
                    val search = client.searchVideos("nature", request)
                    check(search.items.isNotEmpty()); passed("real global video search count=${search.items.size} more=${search.hasMore}")
                    val likes = client.likedVideos(request)
                    passed("real likes endpoint count=${likes.items.size} more=${likes.hasMore}")
                    val following = client.following(request)
                    check(following.items.isNotEmpty()); passed("real following list count=${following.items.size} more=${following.hasMore}")
                    if (likes.hasMore) {
                        val next = client.likedVideos(request.copy(cursor = likes.nextCursor))
                        check(next.nextCursor != likes.nextCursor); passed("real likes cursor advances count=${next.items.size}")
                    }
                    if (following.hasMore) {
                        val next = client.following(request.copy(cursor = following.nextCursor))
                        check(next.nextCursor != following.nextCursor); passed("real following cursor advances count=${next.items.size}")
                    }
                    var foundVideo = false
                    for (creator in following.items.take(4)) {
                        val videos = client.authorVideos(creator.id, request)
                        passed("real creator media count=${videos.items.size} more=${videos.hasMore}")
                        if (videos.hasMore) {
                            val next = client.authorVideos(creator.id, request.copy(cursor = videos.nextCursor))
                            check(next.nextCursor != videos.nextCursor); passed("real creator cursor advances count=${next.items.size}")
                            foundVideo = foundVideo || next.items.isNotEmpty()
                        }
                        foundVideo = foundVideo || videos.items.isNotEmpty()
                        if (foundVideo) break
                    }
                    check(foundVideo); passed("real followed creator has playable videos")
                    if (search.hasMore) {
                        val next = client.searchVideos("nature", request.copy(cursor = search.nextCursor))
                        check(next.nextCursor != search.nextCursor); passed("real search cursor advances count=${next.items.size}")
                    }
                } finally { client.close() }
                }
            }
            finish(RESULT_OK, Bundle().apply { putString("stream", results.joinToString("\n") + "\n$count component checks passed\n") })
        } catch (failure: Throwable) {
            val safe = when(failure) {
                is io.github.tancan1998.xviewtv.core.network.WebHttpFailure -> "HTTP ${failure.status} stage=${failure.stage}"
                is io.github.tancan1998.xviewtv.core.domain.ContentFailure -> failure.userMessage
                else -> failure.javaClass.simpleName + " at " + (failure.stackTrace.firstOrNull { it.className.startsWith("io.github.tancan1998.xviewtv") }?.let { "${it.fileName}:${it.lineNumber}" } ?: "unknown")
            }
            finish(RESULT_CANCELED, Bundle().apply { putString("stream", results.joinToString("\n") + "\nFAILED check ${count + 1}; $safe\n") })
        }
    }
    companion object { private const val RESULT_OK = -1; private const val RESULT_CANCELED = 0 }
}
