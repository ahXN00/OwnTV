package tv.own.owntv.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.ui.theme.stackBlur
import java.util.LinkedHashMap

/** Access-ordered LRU. [get] refreshes recency. Evicts eldest when size exceeds [maxEntries]. */
internal class LruMap<V>(private val maxEntries: Int) {
    private val map = object : LinkedHashMap<String, V>(maxEntries.coerceAtLeast(1), 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean =
            size > maxEntries
    }

    fun get(url: String): V? = synchronized(map) { map[url] }

    fun put(url: String, value: V) {
        synchronized(map) { map[url] = value }
    }

    fun size(): Int = synchronized(map) { map.size }
}

internal class BlurCache(maxEntries: Int = 8) {
    private val lru = LruMap<Bitmap>(maxEntries)

    fun get(url: String): Bitmap? = lru.get(url)

    fun put(url: String, bmp: Bitmap) = lru.put(url, bmp)

    fun size(): Int = lru.size()
}

internal val blurCache = BlurCache(8)

/**
 * 160×90 blur off the main thread. Radius 3 matches the old blur(36.mpx).
 * Previous backdrop stays until the new one is ready.
 */
@Composable
fun BlurredPoster(url: String, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current
    LaunchedEffect(url) {
        val bmp = blurCache.get(url) ?: withContext(Dispatchers.Default) {
            val result = SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(160, 90)
                    .allowHardware(false)
                    .build(),
            )
            if (result is SuccessResult) {
                val blurred = stackBlur(result.image.toBitmap(), radius = 3)
                blurCache.put(url, blurred)
                blurred
            } else {
                null
            }
        }
        if (bmp != null) shown = bmp.asImageBitmap()
    }
    Crossfade(targetState = shown, modifier = modifier, animationSpec = tween(200)) { img ->
        if (img != null) {
            Image(
                img,
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}
