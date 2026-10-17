package tv.own.owntv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.ui.theme.BlurredBackdrop
import tv.own.owntv.ui.theme.stackBlur
import kotlin.math.abs
import kotlin.math.max

/** Logcat tag for the background-image loader. */
private const val BG_TAG = "BgImage"

/**
 * Builds the single shared blurred backdrop that glass frosts. Decodes the
 * background file into a downscaled [Bitmap] (≈480px wide — enough detail to frost, tiny in memory),
 * blurs it with [stackBlur], and wraps it in a [BlurredBackdrop] that also carries the root viewport
 * size in px so glass panels can map their on-screen rect into the bitmap's coordinate space.
 *
 * Runs on a background dispatcher (called from `produceState`); returns null on any decode failure so
 * glass then draws its tint alone. The aspect ratio is locked to the screen's so the slice
 * drawn under each panel lines up with the sharp photo behind it (Coil renders that with Crop/Center).
 */
internal suspend fun produceBlurredBackdrop(
    path: String,
    rootSizePx: Size,
    buildFrostPyramid: Boolean,
): BlurredBackdrop? =
    withContext(Dispatchers.Default) {
        runCatching {
            val rootW = rootSizePx.width.takeIf { it > 0f } ?: return@runCatching null
            val rootH = rootSizePx.height.takeIf { it > 0f } ?: return@runCatching null
            val aspect = rootW / rootH
            // Blur at a DOWNSCALED size, not full root res. A blurred image upscaled bilinearly is
            // visually identical to a full-res blur, so the small bitmap is strictly better: ~0.7MB vs
            // ~8MB ARGB, ~9× fewer pixels through the CPU blur, and the same radius yields a much
            // deeper frost. (Full-res was a workaround for RGB blocks later proven to be a StackBlur
            // sign-wrap bug, since fixed — the upscale was never the culprit.)
            // Frost is intentionally low-frequency. A 384–448 px base plus geometric smaller mips is
            // visually equivalent once upscaled while staying below the former 512–768 px allocation.
            val memoryCap = if (Runtime.getRuntime().maxMemory() >= 256L * 1024L * 1024L) 448 else 384
            val targetW = (rootW / 4f).toInt().coerceIn(384, memoryCap)
            val targetH = (targetW / aspect).toInt().coerceAtLeast(2)

            // Decode bounds first, then sample down to roughly the target width.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(BG_TAG, "blur: bounds decode failed for $path")
                return@runCatching null
            }
            val sample = max(1, bounds.outWidth / targetW)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(path, opts) ?: run {
                Log.w(BG_TAG, "blur: decodeFile returned null for $path")
                return@runCatching null
            }

            try {
                // Center-crop to the screen aspect ratio so the bitmap maps 1:1 to the root viewport.
                val curAspect = decoded.width.toFloat() / decoded.height.toFloat()
                var src = decoded
                if (abs(curAspect - aspect) > 0.02f) {
                    val cw: Int; val ch: Int
                    if (curAspect > aspect) { ch = decoded.height; cw = (ch * aspect).toInt() }
                    else { cw = decoded.width; ch = (cw / aspect).toInt() }
                    val cx = ((decoded.width - cw) / 2).coerceAtLeast(0)
                    val cy = ((decoded.height - ch) / 2).coerceAtLeast(0)
                    val cropped = Bitmap.createBitmap(decoded, cx, cy, cw.coerceAtLeast(2), ch.coerceAtLeast(2))
                    if (cropped !== decoded) { decoded.recycle(); src = cropped }
                }
                // Downscale + diffuse in one canvas pass. Compressing contrast 18% toward mid-grey
                // prevents bright wallpaper patches punching through frost; the restrained saturation
                // keeps colour identity without restoring the glare. This replaces the previous 1.18
                // saturation-only filter at exactly the same one-off wallpaper-load cost.
                val contrast = 0.82f
                val translation = 255f * (1f - contrast) * 0.5f
                val diffuse = android.graphics.ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translation,
                        0f, contrast, 0f, 0f, translation,
                        0f, 0f, contrast, 0f, translation,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ).apply {
                    postConcat(android.graphics.ColorMatrix().apply { setSaturation(1.06f) })
                }
                val scaled = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(scaled).apply {
                    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(diffuse)
                    }
                    drawBitmap(src, null, android.graphics.Rect(0, 0, targetW, targetH), paint)
                }
                src.recycle()
                val levels = if (buildFrostPyramid) {
                    buildFrostMipPyramid(scaled)
                } else {
                    scaled.recycle()
                    emptyList()
                }
                BlurredBackdrop(
                    frostLevels = levels,
                    rootSizePx = rootSizePx,
                )
            } catch (t: Throwable) {
                Log.w(BG_TAG, "blur: crop/scale/blur threw ${t.javaClass.simpleName}: ${t.message}")
                decoded.recycle()
                null
            }
        }.onFailure { Log.w(BG_TAG, "blur: produceState failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull()
    }

/** Ten real blur levels at geometrically shrinking resolutions within one small memory budget. */
private fun buildFrostMipPyramid(base: Bitmap): List<androidx.compose.ui.graphics.ImageBitmap> {
    val result = ArrayList<androidx.compose.ui.graphics.ImageBitmap>(10)
    val dimensions = tv.own.owntv.ui.theme.frostMipDimensions(base.width, base.height)
    var current = stackBlur(base, radius = 4)
    dimensions.forEachIndexed { level, _ ->
        result += current.asImageBitmap()
        if (level < dimensions.lastIndex) {
            val (nextW, nextH) = dimensions[level + 1]
            val next = Bitmap.createScaledBitmap(current, nextW, nextH, true)
            current = stackBlur(next, radius = 2)
        }
    }
    return result
}
