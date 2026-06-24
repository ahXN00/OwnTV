@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package tv.own.owntv.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * Hosts the [CornerEngine]'s video on its own [SurfaceView], **z-ordered above** the main player's
 * surface (`setZOrderMediaOverlay(true)`) so the corner draws on top of the full-screen stream behind it.
 *
 * Two overlapping SurfaceViews are the right tool here: both are hardware-overlay candidates, so on a TV
 * with ≥2 overlay planes (most) neither stream falls back to GPU composition. (Both engines document that a
 * *regular* view over a video surface knocks 4K off the direct scan-out path — a second SurfaceView avoids
 * that on capable hardware; on a single-plane device the compositor blends them, which still works, warmer.)
 */
@Composable
fun SecondaryVideoSurface(engine: CornerEngine, modifier: Modifier = Modifier, keepAwake: Boolean = true) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                // Must be set before the surface is created — lifts this surface above the main one behind it.
                setZOrderMediaOverlay(true)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = engine.setSurface(holder.surface)
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) = engine.setSurface(null)
                })
            }
        },
        update = { it.keepScreenOn = keepAwake },
    )
}

/**
 * The picture-in-picture corner window: the second stream's video in a rounded, bordered box with a title
 * and a loading spinner, plus — when [showControls] is set — a focusable D-pad control row (audio · swap ·
 * close). During the browse UI the corner carries its own controls (navigate to it with the remote, like
 * the docked mini-player); while the main player is full-screen the player HUD drives these instead, so the
 * corner is rendered video-only ([showControls] = false).
 */
@Composable
fun PipCornerWindow(
    engine: CornerEngine,
    showControls: Boolean,
    audioOnCorner: Boolean,
    onToggleAudio: () -> Unit,
    onSwap: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val meta by engine.meta.collectAsStateWithLifecycle()
    val loading = state == CornerState.LOADING

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(
                width = if (audioOnCorner) 2.dp else 1.dp,
                color = if (audioOnCorner) tv.own.owntv.ui.theme.OwnTVTheme.colors.primary else Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        if (state == CornerState.ERROR) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Couldn't play", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        } else {
            SecondaryVideoSurface(engine = engine, modifier = Modifier.fillMaxSize())
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                tv.own.owntv.ui.components.OwnTVSpinner(sizeDp = 22)
            }
        }

        // Title strip (top) — channel name on a slight scrim, with a small "PiP" marker.
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (audioOnCorner) "PiP · 🔊" else "PiP",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                meta.title ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
            )
        }

        if (showControls) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(8.dp).focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PipBtn(if (audioOnCorner) OwnTVIcon.VOLUME_HIGH else OwnTVIcon.VOLUME_MUTE, onClick = onToggleAudio)
                Spacer(Modifier.weight(1f))
                PipBtn(OwnTVIcon.FULLSCREEN, onClick = onSwap) // swap the corner stream into the main window
                PipBtn(OwnTVIcon.CLOSE, onClick = onClose)
            }
        }
    }
}

@Composable
private fun PipBtn(icon: OwnTVIcon, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        focusedScale = 1.12f,
        focusedContainerColor = Color.White.copy(alpha = 0.28f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
        selectedContainerColor = Color.White.copy(alpha = 0.12f),
        contentAlignment = Alignment.Center,
    ) { _ ->
        OwnTVIcon(icon, tint = Color.White, filled = true, modifier = Modifier.size(16.dp))
    }
}
