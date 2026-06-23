package tv.own.owntv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.theme.OwnTVTheme

private val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0)
private val TEAL = Color(0xFF52DBC8)

private enum class HudDialog { NONE, AUDIO, SUBS, SPEED, ZOOM, VOLUME }

@Composable
fun PlayerHud(
    player: PlaybackEngine,
    onBack: () -> Unit,
    onPip: (() -> Unit)? = null,
    onChannelUp: (() -> Unit)? = null,
    onChannelDown: (() -> Unit)? = null,
    // Live rewind / timeshift (catch-up channels). onRewindLive non-null = this live channel can rewind;
    // timeshiftOffsetSec non-null = currently watching that many seconds behind the live edge.
    onRewindLive: (() -> Unit)? = null,
    onForwardLive: (() -> Unit)? = null,
    onGoToLive: (() -> Unit)? = null,
    onScrubLive: ((Int) -> Unit)? = null, // timeline scrub: +sec = back, −sec = toward live
    timeshiftOffsetSec: Int? = null,
    // True picture-in-picture corner controls — shown only while a second (corner) stream is running.
    // onCornerClose non-null = a corner is active; cornerAudioOn = the corner currently has the sound.
    onCornerSwap: (() -> Unit)? = null,   // swap the corner stream into the main window (and vice versa)
    onCornerAudio: (() -> Unit)? = null,  // move the audio between the main and corner windows
    onCornerClose: (() -> Unit)? = null,  // close the corner window
    cornerAudioOn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val position by player.position.collectAsStateWithLifecycle()
    val duration by player.duration.collectAsStateWithLifecycle()
    val buffering by player.buffering.collectAsStateWithLifecycle()
    val error by player.error.collectAsStateWithLifecycle()
    val errorInfo by player.errorInfo.collectAsStateWithLifecycle()
    val nav by player.nav.collectAsStateWithLifecycle()
    val volume by player.volume.collectAsStateWithLifecycle()
    val videoRes by player.videoRes.collectAsStateWithLifecycle()
    val audioCount by player.audioCount.collectAsStateWithLifecycle()
    val subCount by player.subCount.collectAsStateWithLifecycle()
    val zoomMode by player.zoomMode.collectAsStateWithLifecycle()
    val speed by player.speed.collectAsStateWithLifecycle()
    val isLive = player.isLiveContent

    var dialog by remember { mutableStateOf(HudDialog.NONE) }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val catchFocus = remember { FocusRequester() }

    var controlsVisible by remember { mutableStateOf(true) }
    var wakeTick by remember { mutableIntStateOf(0) }
    val forceShow = error != null || dialog != HudDialog.NONE
    // First Back hides the controls (instead of leaving the channel); with the controls already hidden
    // this handler is disabled, so Back falls through to the shell, which exits the player. Also disabled
    // while an error/dialog is up (a dialog handles its own Back; an error should exit).
    BackHandler(enabled = controlsVisible && !forceShow) { controlsVisible = false }
    // Channel zap (live only): a brief "now watching" card on up/down without revealing the full HUD.
    val canZap = onChannelUp != null && onChannelDown != null
    var channelFlash by remember { mutableIntStateOf(0) }
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(channelFlash) { if (channelFlash > 0) { showFlash = true; delay(3000); showFlash = false } }
    val zap: (Int) -> Unit = { d -> (if (d < 0) onChannelUp else onChannelDown)?.invoke(); channelFlash++ }

    LaunchedEffect(forceShow) { if (forceShow) controlsVisible = true }
    LaunchedEffect(controlsVisible, wakeTick, forceShow) {
        if (controlsVisible && !forceShow) { delay(4500); controlsVisible = false }
    }
    LaunchedEffect(controlsVisible, error) {
        if (controlsVisible) {
            if (error != null) runCatching { retryFocus.requestFocus() } else runCatching { playFocus.requestFocus() }
        } else runCatching { catchFocus.requestFocus() }
    }

    Box(
        modifier = modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                // Channel surfing: dedicated CH+/CH- and media prev/next keys always zap. D-pad Up/Down
                // zap ONLY while the HUD is hidden (when it's visible, Up/Down navigate the controls) —
                // this is the only way to change channels on remotes without CH keys (e.g. Fire TV).
                canZap && (e.key == Key.ChannelUp || e.key == Key.MediaPrevious) -> { zap(-1); true }
                canZap && (e.key == Key.ChannelDown || e.key == Key.MediaNext) -> { zap(1); true }
                canZap && !controlsVisible && e.key == Key.DirectionUp -> { zap(-1); true }
                canZap && !controlsVisible && e.key == Key.DirectionDown -> { zap(1); true }
                controlsVisible -> { wakeTick++; false }
                else -> false
            }
        },
    ) {
        if (!controlsVisible) {
            Box(
                Modifier.fillMaxSize().focusRequester(catchFocus).focusable()
                    .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key != Key.Back) { controlsVisible = true; true } else false },
            )
        }

        // Channel flash card (zapping with the HUD hidden) — shown independently of the full controls.
        if (isLive && showFlash && !controlsVisible) {
            ChannelCard(player, modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 28.dp))
        }

        if (controlsVisible) {
            // Scrim gradients top + bottom for legibility.
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent))))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(240.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))

            TopBar(player, isLive, videoRes, duration, onBack, modifier = Modifier.align(Alignment.TopStart))
            if (isLive) ChannelCard(player, modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 92.dp))

            // Hide the transport (play/seek/prev/next) and bottom bar while an error is up — the error
            // overlay owns the screen with its own Retry, so the play/rewind/forward must not show behind it.
            if (error == null) {
                CenterControls(player, nav, isPlaying, isLive, onRewindLive, onForwardLive, onGoToLive, timeshiftOffsetSec, playFocus, modifier = Modifier.align(Alignment.Center))

                BottomBar(
                    player = player, isLive = isLive, position = position, duration = duration,
                    volume = volume, audioCount = audioCount, subCount = subCount, zoomMode = zoomMode,
                    speedLabel = formatSpeed(speed),
                    onScrubLive = onScrubLive, timeshiftOffsetSec = timeshiftOffsetSec,
                    onOpenDialog = { dialog = it }, onPip = onPip, onBack = onBack,
                    onCornerSwap = onCornerSwap, onCornerAudio = onCornerAudio, onCornerClose = onCornerClose,
                    cornerAudioOn = cornerAudioOn,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }

        // Status overlay (always shown).
        when {
            error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Playback error", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                // Structured technical detail so a user can report the real cause without adb/logcat:
                // plain reason → media spec (codec • resolution • decoder) → raw engine/codec line.
                errorInfo?.let { info ->
                    info.reason?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                    info.spec?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
                    }
                    info.raw?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("err: $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                }
                Spacer(Modifier.height(18.dp))
                OwnTVButton("Retry", onClick = { player.retry() }, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(retryFocus))
            }
            buffering -> OwnTVSpinner(modifier = Modifier.align(Alignment.Center), sizeDp = 56)
        }
    }

    when (dialog) {
        HudDialog.AUDIO -> TrackDialog("Audio Track", player.audioTracks(), onSelect = { player.selectAudio(it.mpvId); dialog = HudDialog.NONE }, onOff = null, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.SUBS -> TrackDialog("Subtitles", player.textTracks(), onSelect = { player.selectSubtitle(it.mpvId); dialog = HudDialog.NONE }, onOff = { player.disableSubtitles(); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.SPEED -> SpeedDialog(current = speed, onSelect = { player.setSpeed(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.ZOOM -> ZoomDialog(current = zoomMode, onSelect = { player.setZoomMode(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.VOLUME -> VolumeDialog(player, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.NONE -> Unit
    }
}

// ---------------- Top bar ----------------

@Composable
private fun TopBar(
    player: PlaybackEngine, isLive: Boolean, videoRes: String?, duration: Long,
    onBack: () -> Unit, modifier: Modifier = Modifier,
) {
    // Reactive meta so the title row updates instantly on a channel zap (the plain vars aren't observed).
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    Row(modifier = modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(OwnTVIcon.BACK, size = 40, onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            meta.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(meta.title ?: "", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val durMin = (duration / 60000)
                val parts = buildList {
                    meta.year?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (!isLive && durMin > 0) add("$durMin min")
                    videoRes?.let { add(it) }
                }
                parts.forEachIndexed { i, label ->
                    if (i > 0) Box(Modifier.size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
                }
                if (isLive) {
                    if (parts.isNotEmpty()) Box(Modifier.size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                    LiveBadge()
                }
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xCCDC3232)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChannelCard(player: PlaybackEngine, modifier: Modifier = Modifier) {
    // Collect the reactive meta so the card refreshes the instant a zap changes the channel.
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    Row(
        modifier = modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF004F46)), contentAlignment = Alignment.Center) {
            val logo = meta.logoUrl
            if (!logo.isNullOrBlank()) AsyncImage(model = logo, contentDescription = null, modifier = Modifier.fillMaxSize())
            else Text((meta.title ?: "?").take(3).uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF6FF8E4), fontWeight = FontWeight.Bold)
        }
        Column {
            Text(meta.title ?: "", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            meta.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------- Center transport ----------------

@Composable
private fun CenterControls(
    player: PlaybackEngine, nav: NavState, isPlaying: Boolean, isLive: Boolean,
    onRewindLive: (() -> Unit)?, onForwardLive: (() -> Unit)?, onGoToLive: (() -> Unit)?, timeshiftOffsetSec: Int?,
    playFocus: FocusRequester, modifier: Modifier = Modifier,
) {
    val rewindMode = onRewindLive != null // this is a catch-up-capable Live channel
    val timeshifting = timeshiftOffsetSec != null
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (timeshifting) {
            // Counts down as the archive catches up to the live edge; grows if you pause.
            Text(
                if (timeshiftOffsetSec!! <= 1) "● At the live edge" else "● ${mmss(timeshiftOffsetSec)} behind live",
                style = MaterialTheme.typography.labelLarge,
                color = OwnTVTheme.colors.accent,
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.focusGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (nav.hasPrev) CircleButton(OwnTVIcon.SKIP_PREVIOUS, size = 52) { player.previous() }
            when {
                rewindMode -> CircleButton(OwnTVIcon.REWIND, size = 52) { onRewindLive!!() } // step back into the archive
                !isLive -> CircleButton(OwnTVIcon.REWIND, size = 52) { player.seekBy(-10_000) }
            }
            CircleButton(if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, size = 72, primary = true, modifier = Modifier.focusRequester(playFocus)) { player.togglePlayPause() }
            when {
                rewindMode && timeshifting -> CircleButton(OwnTVIcon.FORWARD, size = 52) { onForwardLive!!() } // toward live
                !isLive && !rewindMode -> CircleButton(OwnTVIcon.FORWARD, size = 52) { player.seekBy(10_000) }
            }
            if (rewindMode && timeshifting && onGoToLive != null) {
                CircleButton(OwnTVIcon.LIVE_TV, size = 52, primary = true) { onGoToLive() } // jump to the live edge
            }
            if (nav.hasNext) CircleButton(OwnTVIcon.SKIP_NEXT, size = 52) { player.next() }
        }
    }
}

/** mm:ss for a seconds offset (e.g. 150 → "2:30"). */
private fun mmss(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

// ---------------- Bottom bar ----------------

@Composable
private fun BottomBar(
    player: PlaybackEngine, isLive: Boolean, position: Long, duration: Long,
    volume: Int, audioCount: Int, subCount: Int, zoomMode: ZoomMode, speedLabel: String,
    onScrubLive: ((Int) -> Unit)?, timeshiftOffsetSec: Int?,
    onOpenDialog: (HudDialog) -> Unit, onPip: (() -> Unit)?, onBack: () -> Unit,
    onCornerSwap: (() -> Unit)? = null, onCornerAudio: (() -> Unit)? = null, onCornerClose: (() -> Unit)? = null,
    cornerAudioOn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)) {
        when {
            // Catch-up live channel → a scrubbable live timeline (last LIVE_WINDOW up to the live edge).
            onScrubLive != null -> {
                LiveTimelineBar(offsetSec = timeshiftOffsetSec ?: 0, onScrub = onScrubLive)
                Spacer(Modifier.height(10.dp))
            }
            !isLive && duration > 0 -> {
                SeekBar(positionMs = position, durationMs = duration, onSeek = { player.seekBy(it) })
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(formatTime(position), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().focusGroup()) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CtrlButton(volumeIcon(volume)) { onOpenDialog(HudDialog.VOLUME) }
                SpeedButton(label = speedLabel, active = speedLabel != "1.0x") { onOpenDialog(HudDialog.SPEED) }
                CtrlButton(OwnTVIcon.SUBTITLE, badge = subCount.takeIf { it > 0 }) { onOpenDialog(HudDialog.SUBS) }
                CtrlButton(OwnTVIcon.AUDIO, badge = audioCount.takeIf { it > 1 }) { onOpenDialog(HudDialog.AUDIO) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Aspect/zoom works in every mode now — direct mode resizes the surface view itself
                // (see MpvVideoSurface), GL mode scales internally.
                CtrlButton(OwnTVIcon.ASPECT, active = zoomMode != ZoomMode.FIT) { onOpenDialog(HudDialog.ZOOM) }
                // Corner (true PiP) controls — present only while a second stream is in the corner.
                if (onCornerClose != null) {
                    if (onCornerAudio != null) {
                        CtrlButton(if (cornerAudioOn) OwnTVIcon.VOLUME_HIGH else OwnTVIcon.VOLUME_MUTE, active = cornerAudioOn) { onCornerAudio?.invoke() }
                    }
                    if (onCornerSwap != null) CtrlButton(OwnTVIcon.PIP, active = true) { onCornerSwap?.invoke() }
                    CtrlButton(OwnTVIcon.CLOSE) { onCornerClose?.invoke() }
                }
                if (onPip != null) CtrlButton(OwnTVIcon.PIP) { onPip() }
                CtrlButton(OwnTVIcon.FULLSCREEN_EXIT) { onBack() }
            }
        }
    }
}

private fun volumeIcon(volume: Int): OwnTVIcon = when {
    volume == 0 -> OwnTVIcon.VOLUME_MUTE
    volume < 50 -> OwnTVIcon.VOLUME_LOW
    else -> OwnTVIcon.VOLUME_HIGH
}

// ---------------- Buttons ----------------

@Composable
private fun CircleButton(icon: OwnTVIcon, size: Int, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(size.dp),
        shape = CircleShape,
        focusedScale = 1.1f,
        focusedContainerColor = if (primary) Color.White else Color.White.copy(alpha = 0.22f),
        unfocusedContainerColor = if (primary) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.10f),
        selectedContainerColor = Color.White.copy(alpha = 0.10f),
        contentAlignment = Alignment.Center,
    ) { _ ->
        OwnTVIcon(icon, tint = if (primary) Color(0xFF0E1513) else Color.White, filled = true, modifier = Modifier.size((size * 0.42f).dp))
    }
}

@Composable
private fun SpeedButton(label: String, active: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        focusedContainerColor = Color.White.copy(alpha = 0.16f),
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OwnTVIcon(OwnTVIcon.FORWARD, tint = if (active) TEAL else if (focused) Color.White else Color.White.copy(alpha = 0.78f), filled = true, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) TEAL else if (focused) Color.White else Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatSpeed(speed: Double): String = if (speed == 1.0) "1.0x" else "${speed}x"

@Composable
private fun CtrlButton(icon: OwnTVIcon, badge: Int? = null, active: Boolean = false, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(12.dp),
        focusedContainerColor = Color.White.copy(alpha = 0.16f),
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Box(contentAlignment = Alignment.Center) {
            OwnTVIcon(icon, tint = if (active) TEAL else if (focused) Color.White else Color.White.copy(alpha = 0.78f), filled = true, modifier = Modifier.size(22.dp))
            if (badge != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(15.dp).clip(CircleShape).background(TEAL),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$badge", style = MaterialTheme.typography.labelSmall, color = Color(0xFF003730), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------- Seekbar ----------------

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) when (e.key) {
                    Key.DirectionLeft -> { onSeek(-10_000); true }
                    Key.DirectionRight -> { onSeek(10_000); true }
                    else -> false
                } else false
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(if (focused) 6.dp else 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (focused) 0.4f else 0.22f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(50)).background(TEAL))
        }
        if (focused) {
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(TEAL))
            }
            // Scrub-time bubble above the thumb.
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier.padding(bottom = 30.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(formatTime(positionMs), style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
    }
}

private const val LIVE_WINDOW_SEC = 2 * 3600   // the live timeline shows the last 2 hours up to the edge
private const val LIVE_SCRUB_STEP_SEC = 60     // per Left/Right press (hold to scrub fast); buttons stay 30 s

/** Scrubbable live timeline for a catch-up channel: spans the last [LIVE_WINDOW_SEC] up to the live edge.
 *  Left = back in time, Right = toward live; the thumb is the watched point and the gap to the red LIVE dot
 *  on the right is how far behind live you are. Holding a key scrubs freely; the archive loads when you
 *  settle (the VM debounces). Going past the window keeps working via the ⏪ button — the bar just pins left. */
@Composable
private fun LiveTimelineBar(offsetSec: Int, onScrub: (Int) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frac = (1f - offsetSec.toFloat() / LIVE_WINDOW_SEC).coerceIn(0f, 1f) // 1 = live edge, 0 = far edge
    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) when (e.key) {
                    Key.DirectionLeft -> { onScrub(LIVE_SCRUB_STEP_SEC); true }    // back in time
                    Key.DirectionRight -> { onScrub(-LIVE_SCRUB_STEP_SEC); true }  // toward live
                    else -> false
                } else false
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(if (focused) 6.dp else 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (focused) 0.4f else 0.22f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(50)).background(TEAL))
        }
        // Live-edge marker (red dot) at the far right.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF4D4D)))
        }
        if (focused) {
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(TEAL))
            }
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.padding(bottom = 30.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(if (offsetSec <= 1) "LIVE" else "-${mmss(offsetSec)}", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
    }
}

// ---------------- Dialogs ----------------

@Composable
private fun TrackDialog(title: String, tracks: List<TrackOption>, onSelect: (TrackOption) -> Unit, onOff: (() -> Unit)?, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    BackHandler { onDismiss() }
    // Open with focus on the CURRENTLY-selected track (so re-opening to change it lands on the right row),
    // else the "Off" row if nothing's selected, else the first track. The requestFocus must run from
    // INSIDE the target row (below) — a top-level LaunchedEffect fires before the LazyColumn has composed
    // that row, so requestFocus would throw "not initialized" and focus would fall back to the first item.
    val selectedIndex = tracks.indexOfFirst { it.selected }
    val focusOff = onOff != null && selectedIndex < 0
    DialogScaffold(title = title, onDismiss = onDismiss) {
        if (tracks.isEmpty() && onOff == null) {
            item { Text("No tracks available.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        }
        if (onOff != null) {
            item {
                if (focusOff) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos {}; runCatching { focus.requestFocus() } }
                OptionRow(label = "Off", selected = selectedIndex < 0, modifier = if (focusOff) Modifier.focusRequester(focus) else Modifier, onClick = onOff)
            }
        }
        items(tracks.size) { index ->
            val track = tracks[index]
            val focusThis = index == selectedIndex || (selectedIndex < 0 && onOff == null && index == 0)
            if (focusThis) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos {}; runCatching { focus.requestFocus() } }
            OptionRow(
                // Image-based subs (PGS/VOBSUB/DVB) play via the ExoPlayer handoff on VOD — mark them so
                // it's clear they're a different kind of track, but they're fully selectable.
                label = if (!track.image) track.label else "${track.label}  ·  image",
                selected = track.selected,
                modifier = if (focusThis) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(track) },
            )
        }
    }
}

@Composable
private fun SpeedDialog(current: Double, onSelect: (Double) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    val selectedIndex = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01 }.coerceAtLeast(0)
    DialogScaffold(title = "Playback Speed", onDismiss = onDismiss) {
        items(SPEEDS.size) { index ->
            val speed = SPEEDS[index]
            OptionRow(
                label = if (speed == 1.0) "1.0x (Normal)" else "${speed}x",
                selected = kotlin.math.abs(speed - current) < 0.01,
                modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(speed) },
            )
        }
    }
}

@Composable
private fun ZoomDialog(current: ZoomMode, onSelect: (ZoomMode) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    // Land focus on the current mode (not always the first row) so re-opening starts on your selection.
    val selectedIndex = ZoomMode.entries.indexOf(current).coerceAtLeast(0)
    DialogScaffold(title = "Player Zoom", onDismiss = onDismiss) {
        items(ZoomMode.entries.size) { index ->
            val mode = ZoomMode.entries[index]
            OptionRow(label = mode.label, selected = mode == current, modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier, onClick = { onSelect(mode) })
        }
    }
}

@Composable
private fun VolumeDialog(player: PlaybackEngine, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val volume by player.volume.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(440.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Volume", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StepButton("–", enabled = volume > 0, modifier = Modifier.focusRequester(focus)) { player.adjustVolume(-5) }
                Text("$volume%", style = MaterialTheme.typography.headlineLarge, color = TEAL, modifier = Modifier.width(120.dp), textAlign = TextAlign.Center)
                StepButton("+", enabled = volume < 150) { player.adjustVolume(5) }
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(if (volume == 0) "Unmute" else "Mute", onClick = { player.toggleMute() }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Done", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, enabled = enabled, modifier = modifier.size(64.dp), shape = RoundedCornerShape(18.dp), contentAlignment = Alignment.Center) { _ ->
        Text(label, style = MaterialTheme.typography.headlineMedium, color = if (enabled) OwnTVTheme.colors.onSurface else OwnTVTheme.colors.outline)
    }
}

@Composable
private fun DialogScaffold(title: String, onDismiss: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val colors = OwnTVTheme.colors
    BackHandler { onDismiss() }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).focusGroup(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(440.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick, modifier = modifier.fillMaxWidth(), selected = selected, shape = RoundedCornerShape(12.dp),
        selectedContainerColor = colors.primaryContainer, contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.onPrimaryContainer else if (focused) colors.primary else colors.onSurface)
            if (selected) {
                Spacer(Modifier.weight(1f))
                OwnTVIcon(OwnTVIcon.STAR, tint = colors.onPrimaryContainer, filled = true, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
