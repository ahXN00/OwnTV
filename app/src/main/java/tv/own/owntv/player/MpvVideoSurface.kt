package tv.own.owntv.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private class MpvSurfaceView(context: Context, private val player: OwnTVPlayer) :
    SurfaceView(context), SurfaceHolder.Callback {

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        player.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        player.setSurfaceSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        player.detachSurface()
    }
}

/**
 * Hosts the mpv video output (a [SurfaceView]) in Compose.
 *
 * In direct render mode the decoder fills the surface edge-to-edge (no GL scaling), so zoom/aspect is
 * done by **sizing the view itself**: the surface is scaled/cropped/letterboxed by laying it out at the
 * target geometry inside a clipped black box (the same approach ExoPlayer/YouTube use). In GL mode mpv
 * scales internally (via [OwnTVPlayer.setZoomMode]'s properties) and the view just fills the slot.
 */
@Composable
fun MpvVideoSurface(player: OwnTVPlayer, modifier: Modifier = Modifier) {
    val direct by player.directRender.collectAsStateWithLifecycle()
    val aspect by player.videoAspect.collectAsStateWithLifecycle()
    val videoSize by player.videoSize.collectAsStateWithLifecycle()
    val zoom by player.zoomMode.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.background(Color.Black).clipToBounds(), contentAlignment = Alignment.Center) {
        val a = aspect
        val viewModifier = if (!direct || a == null || a <= 0f) {
            // GL mode letterboxes internally; or no video dimensions yet — just fill the slot.
            Modifier.fillMaxSize()
        } else {
            val cw = maxWidth
            val ch = maxHeight
            val containerAspect = cw.value / ch.value
            when (zoom) {
                ZoomMode.STRETCH -> Modifier.fillMaxSize()
                ZoomMode.ORIGINAL -> {
                    val vs = videoSize
                    if (vs != null && vs.first > 0 && vs.second > 0) {
                        with(density) { Modifier.size(vs.first.toDp(), vs.second.toDp()) }
                    } else {
                        Modifier.aspectRatio(a)
                    }
                }
                else -> {
                    // Target box aspect for the view; the surface stretches to fill it.
                    val targetAspect = when (zoom) {
                        ZoomMode.FORCE_16_9 -> 16f / 9f
                        ZoomMode.FORCE_4_3 -> 4f / 3f
                        else -> a // FIT, FILL keep the video aspect
                    }
                    val cover = zoom == ZoomMode.FILL // cover the container (crop) vs contain (fit)
                    // contain: largest box of targetAspect fitting inside; cover: smallest covering it.
                    val widthDriven = if (cover) targetAspect < containerAspect else targetAspect >= containerAspect
                    if (widthDriven) {
                        Modifier.width(cw).height((cw.value / targetAspect).dp)
                    } else {
                        Modifier.height(ch).width((ch.value * targetAspect).dp)
                    }
                }
            }
        }
        AndroidView(modifier = viewModifier, factory = { ctx -> MpvSurfaceView(ctx, player) })
    }
}
