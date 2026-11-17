package io.github.tancan1998.xviewtv

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import io.github.tancan1998.xviewtv.core.domain.PlaybackBoost

/** Detect only gestures on the video surface; native transport/time-bar children keep their events. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class GesturePlayerView(context: Context, attrs: android.util.AttributeSet? = null) : PlayerView(context, attrs) {
    var seek: ((Boolean) -> Unit)? = null
    var speed: (() -> Float)? = null
    var changeSpeed: ((Float) -> Unit)? = null
    var feedback: ((Float?) -> Unit)? = null
    private val boost = PlaybackBoost()
    private var tracking = false
    private var doubleTap = false
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isControllerFullyVisible) hideController() else showController()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            doubleTap = true
            if (e.x < width * .4f || e.x > width * .6f) seek?.invoke(e.x > width / 2f)
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (e.x > width * .6f && !doubleTap) {
                val accelerated = boost.begin(speed?.invoke() ?: 1f)
                changeSpeed?.invoke(accelerated); feedback?.invoke(accelerated)
                if (BuildConfig.DEBUG) android.util.Log.d("XViewPlayback", "boost_begin speed=$accelerated")
            }
        }
    })
    private fun interactiveChildAt(x: Float, y: Float): Boolean {
        fun hit(view: android.view.View, px: Float, py: Float): Boolean {
            if (view.visibility != android.view.View.VISIBLE || px < 0 || py < 0 || px >= view.width || py >= view.height) return false
            if (view is android.view.ViewGroup) for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (hit(child, px + view.scrollX - child.x, py + view.scrollY - child.y)) return true
            }
            return view !== this && (view.isClickable || view is androidx.media3.ui.TimeBar)
        }
        return hit(this, x, y)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            tracking = !interactiveChildAt(event.x, event.y); doubleTap = false
        }
        if (!tracking) return super.dispatchTouchEvent(event)
        if (event.pointerCount > 1) {
            finishBoost()
            val cancelled = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            detector.onTouchEvent(cancelled); cancelled.recycle()
            return true
        }
        detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            finishBoost(); tracking = false
        }
        return true
    }
    fun finishBoost() {
        boost.end()?.let { changeSpeed?.invoke(it); feedback?.invoke(null)
            if (BuildConfig.DEBUG) android.util.Log.d("XViewPlayback", "boost_end speed=$it")
        }
    }
    override fun onDetachedFromWindow() { finishBoost(); super.onDetachedFromWindow() }
}
