package tv.own.owntv.player

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Zoom/aspect sizing shared by both video surfaces: [MpvVideoSurface] (mpv / VOD / Live-on-mpv) and
 * [ExoPreviewSurface] (ExoPlayer live preview + promoted full-screen). The video engine always fills its
 * surface edge-to-edge, so zoom / letterbox / crop is done entirely by **sizing the surface view itself**
 * inside a clipped black box (the approach ExoPlayer/YouTube use). Identical math for both engines ⇒
 * identical zoom behavior whether a Live channel plays on ExoPlayer or mpv.
 *
 * Must be called inside a [androidx.compose.foundation.layout.BoxWithConstraints] scope so the caller can
 * pass the incoming [maxWidth]/[maxHeight] (the on-screen container).
 *
 * - [aspect] is the video's display aspect ratio (w/h, PAR-corrected). null/≤0 ⇒ no dimensions yet → fill.
 * - [videoSize] is the video's native pixel (w, h); used only by ORIGINAL (1:1) to render at true size.
 */
private const val ZOOM_CROP_FACTOR = 1.2f

@Composable
fun Modifier.videoZoom(
    zoom: ZoomMode,
    aspect: Float?,
    videoSize: Pair<Int, Int>?,
    maxWidth: Dp,
    maxHeight: Dp,
): Modifier {
    if (aspect == null || aspect <= 0f) {
        // No video dimensions yet — just fill the slot.
        return fillMaxSize()
    }
    val cw = maxWidth
    val ch = maxHeight
    val containerAspect = cw.value / ch.value
    return when (zoom) {
        ZoomMode.STRETCH -> fillMaxSize()
        ZoomMode.ORIGINAL -> {
            val vs = videoSize
            if (vs != null && vs.first > 0 && vs.second > 0) {
                // requiredSize (not size): native pixel dims can exceed the container (e.g. a 4K frame on
                // a smaller output), and a plain size()/width()/height() modifier is clamped back down to
                // the incoming BoxWithConstraints max — it can shrink a box but never grow one past the
                // container. requiredSize ignores that ceiling so the view genuinely overflows and the
                // parent's clipToBounds() does the cropping.
                val density = LocalDensity.current
                with(density) { requiredSize(vs.first.toDp(), vs.second.toDp()) }
            } else {
                aspectRatio(aspect)
            }
        }
        ZoomMode.FILL -> {
            // TV-style "zoom/crop": take the letterboxed FIT box, then scale it up by a fixed factor so
            // it overflows the container on every side; the parent's clipToBounds() crops the excess.
            // Always visible, even when the video's native aspect already matches the container's (unlike
            // a plain aspect-correct "cover", which is a no-op in that case). requiredWidth/Height (not
            // width/height): the whole point is to exceed the container, and plain width()/height() get
            // silently clamped back to the incoming max constraint.
            val fitWidthDriven = aspect >= containerAspect
            val fitW = if (fitWidthDriven) cw.value else ch.value * aspect
            val fitH = if (fitWidthDriven) cw.value / aspect else ch.value
            requiredWidth((fitW * ZOOM_CROP_FACTOR).dp).requiredHeight((fitH * ZOOM_CROP_FACTOR).dp)
        }
        else -> {
            // Target box aspect for the view; the surface stretches to fill it.
            val targetAspect = zoom.forcedAspect ?: aspect // FIT keeps the video aspect
            // contain: largest box of targetAspect fitting inside the container.
            val widthDriven = targetAspect >= containerAspect
            if (widthDriven) {
                width(cw).height((cw.value / targetAspect).dp)
            } else {
                height(ch).width((ch.value * targetAspect).dp)
            }
        }
    }
}
