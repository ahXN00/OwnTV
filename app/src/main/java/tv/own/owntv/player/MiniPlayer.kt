package tv.own.owntv.player

import tv.own.owntv.ui.theme.hudOnAccent
import tv.own.owntv.ui.theme.hudAccent
import tv.own.owntv.ui.theme.hudShade
import tv.own.owntv.ui.theme.hudInk
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * The docked mini-player (dock-to-corner model). The mpv surface is rendered behind this by the shell;
 * this overlays the title, the window's own progress hairline and one floating control pill. Navigate
 * to it with the D-pad to use it; expand returns to fullscreen.
 */
@Composable
fun MiniPlayer(
    player: PlaybackEngine,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onCycleSize: () -> Unit = {},
    onCyclePosition: () -> Unit = {},
    onAudioMode: () -> Unit = {},
    /** Focus target for "enter the mini player" (the rail's Now Playing item, long-press Back). */
    entryFocusRequester: FocusRequester? = null,
    /** Back while the mini player holds focus — the shell returns focus to wherever it came from. */
    onBack: (() -> Unit)? = null,
) {
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val volume by player.volume.collectAsStateWithLifecycle()
    // Held as State and read only in the hairline's draw lambda, so the once-a-second tick redraws
    // that line instead of recomposing the whole window.
    val positionState = player.position.collectAsStateWithLifecycle()
    val duration by player.duration.collectAsStateWithLifecycle()

    // Show the title + controls only while the mini-player has D-pad focus; otherwise fade to just the
    // video. The controls stay composed (and focusable) even when hidden, so pressing toward the
    // mini-player lands focus on a button and reveals them.
    var focused by remember { mutableStateOf(false) }
    val controlsAlpha by animateFloatAsState(if (focused) 1f else 0f, label = "miniControls")
    // Back from inside the window hands focus back to the control the user came from — without this
    // entering the mini player stranded you, which is half of why it was unreachable in the first place.
    if (focused && onBack != null) BackHandler { onBack() }
    // Small windows show the four verbs that matter and park the window ones behind the "more" tile.
    var windowCluster by remember { mutableStateOf(false) }
    val accent = hudAccent

    BoxWithConstraints(modifier = modifier.onFocusChanged { focused = it.hasFocus }.focusGroup()) {
        // The pill carries eight tiles at full width. Below this the window cannot hold them without
        // scrolling, so collapse first and let the scroll stay as the backstop it always was.
        val compact = maxWidth < 300.dp

        // The window itself is the focus indicator: an accent rim the moment it holds focus.
        if (focused) {
            Box(Modifier.matchParentSize().border(2.dp, accent, RoundedCornerShape(14.dp)))
        }

        // Progress along the TOP edge — the bottom belongs to the pill and its shadow. Live streams
        // have no duration, so the hairline simply isn't there.
        if (duration > 0L) {
            Box(
                Modifier.align(Alignment.TopStart).fillMaxWidth().height(2.dp)
                    .background(hudInk.copy(alpha = 0.18f))
                    .drawBehind {
                        val frac = (positionState.value.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        val w = size.width * frac
                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                        drawRect(accent, topLeft = Offset(x, 0f), size = Size(w, size.height))
                    },
            )
        }

        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().alpha(controlsAlpha)
                .background(Brush.verticalGradient(listOf(hudShade(0.6f), Color.Transparent)))
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                meta.title ?: "",
                style = tv.own.owntv.ui.theme.stageText(22, 700),
                color = hudInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().then(
                    if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                ),
            )
        }

        // One floating pill inset from the bottom, not a bar welded to the edge. Play leads it as an
        // accent circle; the rest are dark tiles grouped by what they do, hairlines between groups.
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).alpha(controlsAlpha)
                .clip(CircleShape)
                .background(hudShade(0.62f))
                .border(1.dp, hudInk.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = 6.dp, vertical = 5.dp)
                .horizontalScroll(rememberScrollState())
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MiniBtn(
                if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY,
                lead = true,
                modifier = entryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
            ) { player.togglePlayPause() }

            // Volume is a plain mute toggle here on purpose — the three-step ladder belongs to the
            // full player, where there is room to show what step you are on.
            val volumeTile = @Composable {
                MiniBtn(if (volume <= 0) OwnTVIcon.VOLUME_MUTE else OwnTVIcon.VOLUME_HIGH) { player.toggleMute() }
            }
            when {
                !compact -> {
                    PillSeparator()
                    volumeTile()
                    MiniBtn(OwnTVIcon.HEADPHONES, onClick = onAudioMode)
                    PillSeparator()
                    MiniBtn(OwnTVIcon.ZOOM, onClick = onCycleSize)
                    MiniBtn(OwnTVIcon.PIP, onClick = onCyclePosition)
                    PillSeparator()
                    MiniBtn(OwnTVIcon.EXPAND, onClick = onExpand)
                    MiniBtn(OwnTVIcon.CLOSE, onClick = onClose)
                }
                windowCluster -> {
                    PillSeparator()
                    MiniBtn(OwnTVIcon.HEADPHONES, onClick = onAudioMode)
                    MiniBtn(OwnTVIcon.ZOOM, onClick = onCycleSize)
                    MiniBtn(OwnTVIcon.PIP, onClick = onCyclePosition)
                    MiniBtn(OwnTVIcon.MORE, active = true) { windowCluster = false }
                }
                else -> {
                    PillSeparator()
                    volumeTile()
                    PillSeparator()
                    MiniBtn(OwnTVIcon.EXPAND, onClick = onExpand)
                    MiniBtn(OwnTVIcon.CLOSE, onClick = onClose)
                    MiniBtn(OwnTVIcon.MORE) { windowCluster = true }
                }
            }
        }
    }
}

/** The hairline that groups the pill's verbs: sound — window — exit. */
@Composable
private fun PillSeparator() {
    Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(20.dp).background(hudInk.copy(alpha = 0.18f)))
}

@Composable
private fun MiniBtn(
    icon: OwnTVIcon,
    modifier: Modifier = Modifier,
    lead: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = hudAccent
    val size = if (lead) 38.dp else 30.dp
    StageSurface(
        onClick = onClick,
        radius = size / 2,
        modifier = modifier.size(size),
        // The dark tiles take focus as light. Play is already the accent circle, so washing it in
        // more accent would say nothing — it fills and takes a white ring instead.
        focusStyle = if (lead) StageFocus.FILLED else StageFocus.FX,
        // Play is the one accent circle; everything else is a small dark tile.
        idle = Modifier.background(if (lead) accent else hudInk.copy(alpha = 0.10f), CircleShape),
        accent = hudFocus(ring = if (lead) hudInk else accent),
        contentAlignment = Alignment.Center,
    ) { focusedTile ->
        OwnTVIcon(
            icon,
            tint = when {
                lead -> hudOnAccent
                focusedTile || active -> accent
                else -> hudInk
            },
            filled = true,
            modifier = Modifier.size(if (lead) 19.dp else 16.dp),
        )
    }
}
