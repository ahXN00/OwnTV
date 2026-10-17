package tv.own.owntv.features.pip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import org.koin.compose.koinInject
import tv.own.owntv.player.PlaybackEngine
import kotlin.math.roundToInt

/**
 * What the shell needs from picture-in-picture: [inWindow] is true while the activity is in the small
 * window, where the shell shows only the video (no HUD, mini-player chrome, subtitles or prompts).
 * [source] goes on the player's box, so the window opens from where the video is.
 */
internal data class ShellPictureInPictureState(val inWindow: Boolean, val source: Modifier)

/**
 * The shell's half of picture-in-picture: keeps [PictureInPicture.offer] in step with what is on
 * screen and reports where the player is laid out.
 *
 * [onReturnToApp] runs when the user brings the window back to full screen. Closing the window also
 * leaves picture-in-picture, but then the activity has already stopped; that case is told apart by the
 * lifecycle and left to `onStop`, which releases the players.
 */
@Composable
internal fun rememberShellPictureInPicture(
    playerShown: Boolean,
    multiview: Boolean,
    engine: PlaybackEngine,
    videoAspect: StateFlow<Float?>,
    onReturnToApp: () -> Unit,
): ShellPictureInPictureState {
    val pip = koinInject<PictureInPicture>()
    val optionOn by pip.enabled.collectAsStateWithLifecycle()
    val inWindow by pip.inWindow.collectAsStateWithLifecycle()
    val playing by engine.isPlaying.collectAsStateWithLifecycle()
    val buffering by engine.buffering.collectAsStateWithLifecycle()
    val audioOnlyMedia by engine.audioOnlyMedia.collectAsStateWithLifecycle()
    val audioMode by engine.audioOnly.collectAsStateWithLifecycle()
    val aspect by videoAspect.collectAsStateWithLifecycle()
    val conditions = PipConditions(
        optionOn = optionOn,
        supported = pip.supported,
        playerShown = playerShown,
        multiview = multiview,
        audioOnly = audioOnlyMedia || audioMode,
        playing = playing || buffering,
    )
    val offer = if (PipPolicy.eligible(conditions)) PipPolicy.aspect(aspect) else null
    LaunchedEffect(offer) { pip.offer(offer) }
    DisposableEffect(pip) { onDispose { pip.offer(null) } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val returnToApp by rememberUpdatedState(onReturnToApp)
    LaunchedEffect(pip, lifecycle) {
        pip.inWindow.drop(1).filter { !it }.collect {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) returnToApp()
        }
    }
    val source = remember(pip) {
        Modifier.onGloballyPositioned { coordinates ->
            // In the window the player fills it; only the full-screen layout says where the window opens from.
            if (pip.inWindow.value) return@onGloballyPositioned
            val r = coordinates.boundsInWindow()
            pip.onPlayerBounds(PipBounds(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt()))
        }
    }
    return ShellPictureInPictureState(inWindow, source)
}
