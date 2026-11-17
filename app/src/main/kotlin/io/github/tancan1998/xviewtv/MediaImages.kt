package io.github.tancan1998.xviewtv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.tancan1998.xviewtv.core.designsystem.rememberStrings
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR
import io.github.tancan1998.xviewtv.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import java.io.Closeable
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** App-context only; sampled decode and byte-budget LRU, never a bitmap per full-size source. */
internal class MediaImages(config: ProxyConfig): Closeable {
    private val client = NetworkPolicy.client(config)
    private val workers = Semaphore(2)
    private val cache = object: LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    @Volatile private var closed = false
    suspend fun load(url: String): Bitmap? = workers.withPermit {
        check(!closed)
        WebTimelineParser.safeUrl(url) ?: return@withPermit null
        cache.get(url)?.let { return@withPermit it }
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = client.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation { NetworkPolicy.cancel(call) }
            call.enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _, r, _ -> NetworkPolicy.dispose(r) } }
            })
        }
        try {
            withContext(Dispatchers.IO) {
                if (!response.isSuccessful) return@withContext null
                val source = response.body?.source() ?: return@withContext null
                source.request(6L * 1024 * 1024 + 1)
                if (source.buffer.size > 6L * 1024 * 1024) return@withContext null
                val bytes = source.readByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..30000 || bounds.outHeight !in 1..30000) return@withContext null
                var sample = 1
                while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
                ensureActive()
                if (!closed && bitmap != null) cache.put(url, bitmap)
                bitmap
            }
        } finally { NetworkPolicy.dispose(response) }
    }
    override fun close() { if (closed) return; closed = true; cache.evictAll(); NetworkPolicy.dispose(client) }
}

@Composable
internal fun RemoteImage(images: MediaImages, url: String?, modifier: Modifier, full: Boolean = false) {
    val ui = rememberStrings()
    var loading by remember(images, url) { mutableStateOf(url != null) }
    val bitmap by produceState<Bitmap?>(null, images, url) {
        value = null; loading = url != null
        if (url != null) {
            try { value = images.load(url) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = null }
            finally { loading = false }
        }
    }
    Box(modifier) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(),
            contentScale = if (full) ContentScale.Fit else ContentScale.Crop) }
            ?: if (loading) io.github.tancan1998.xviewtv.core.designsystem.SkeletonBlock(Modifier.fillMaxSize())
            else Text(ui(UiR.string.media_unavailable))
    }
}
