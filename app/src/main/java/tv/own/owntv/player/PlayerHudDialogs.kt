package tv.own.owntv.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.displayLabel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.format.localizedDecimal
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Every dialog the HUD opens — tracks, speed, zoom, volume and subtitle timing — plus the scaffold and
 * row they share. Split out of [PlayerHud]; behaviour unchanged.
 */

private val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0)

/** A/V-sync nudge step, kept identical to the Settings stepper so a value found in the player can be
 *  reproduced there exactly. */
private const val AV_SYNC_STEP_MS = 25

@Composable
internal fun TrackDialog(
    title: String,
    tracks: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
    onOff: (() -> Unit)?,
    onDismiss: () -> Unit,
    audioDelayMs: Int? = null,                 // non-null on the Audio dialog (VOD) → show the A/V-sync nudge
    onAdjustAudioDelay: ((Int) -> Unit)? = null,
    // Whether this item already has a remembered A/V-sync offset, and the action that remembers or
    // forgets it. Both accompany [onAdjustAudioDelay].
    audioDelayRemembered: Boolean = false,
    onToggleRememberAudioDelay: (() -> Unit)? = null,
    // Non-null on the Subtitles dialog for a movie/episode → an "ADD SUBTITLES" row that opens the
    // OpenSubtitles search (subtitle plan §4). Absent for Live TV and when no item context exists.
    onSearchSubtitles: (() -> Unit)? = null,
    // Non-null on the Subtitles dialog for a movie/episode → "Select local subtitle file" (plan §7).
    onSelectLocalSubtitle: (() -> Unit)? = null,
    // Non-null on the Subtitles dialog when timing adjustment applies to the active track (plan §8) →
    // an "ADJUST" section with a "Subtitle timing" row.
    onSubtitleTiming: (() -> Unit)? = null,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    BackHandler { onDismiss() }
    // Open with focus on the CURRENTLY-selected track (so re-opening to change it lands on the right row),
    // else the "Off" row if nothing's selected, else the first track. The requestFocus must run from
    // INSIDE the target row (below) — a top-level LaunchedEffect fires before the LazyColumn has composed
    // that row, so requestFocus would throw "not initialized" and focus would fall back to the first item.
    val selectedIndex = tracks.indexOfFirst { it.selected }
    val focusOff = onOff != null && selectedIndex < 0
    // Safety net: the per-row one-shot requestFocus below can fire while the dialog window is still
    // mid-transition (seen on HDR/HDR10/DTS streams, whose surface re-layout delays window focus) or
    // before the engine has reported the tracks at all — leaving the dialog with NO focused row and
    // the D-pad locked out. Retry over a few frames, and re-run whenever the track list (re)arrives.
    // The selected row can sit beyond the LazyColumn viewport (e.g. subtitle 11 of 20): it never
    // composes, its focusRequester never attaches, and focus falls back to the first row ("Off").
    // Scroll it into view before requesting focus.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(tracks.size, focusOff) {
        val target = if (selectedIndex >= 0) selectedIndex + (if (onOff != null) 1 else 0) else 0
        repeat(10) {
            androidx.compose.runtime.withFrameNanos { }
            if (selectedIndex >= 0) runCatching { listState.scrollToItem(target) }
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    DialogScaffold(title = title, onDismiss = onDismiss, state = listState) {
        if (tracks.isEmpty() && onOff == null) {
            item { Text(stringResource(R.string.player_no_tracks), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        }
        if (onOff != null) {
            item {
                if (focusOff) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos {}; runCatching { focus.requestFocus() } }
                OptionRow(label = stringResource(R.string.common_off), selected = selectedIndex < 0, modifier = if (focusOff) Modifier.focusRequester(focus) else Modifier, onClick = onOff)
            }
        }
        items(tracks.size) { index ->
            val track = tracks[index]
            val focusThis = index == selectedIndex || (selectedIndex < 0 && onOff == null && index == 0)
            if (focusThis) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos {}; runCatching { focus.requestFocus() } }
            OptionRow(
                // Image-based subs (PGS/VOBSUB/DVB) play via the ExoPlayer handoff on VOD — mark them so
                // it's clear they're a different kind of track, but they're fully selectable.
                label = if (!track.image) track.displayLabel() else stringResource(R.string.player_image_track, track.displayLabel()),
                selected = track.selected,
                modifier = if (focusThis) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(track) },
            )
        }
        // ADD SUBTITLES (subtitles dialog, movie/episode only) — OpenSubtitles search + local file (§4/§7).
        if (onSearchSubtitles != null || onSelectLocalSubtitle != null) {
            item {
                Text(
                    stringResource(R.string.player_add_subtitles),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                )
            }
            if (onSearchSubtitles != null) {
                item { OptionRow(label = stringResource(R.string.player_search_subtitles), selected = false, onClick = onSearchSubtitles) }
            }
            if (onSelectLocalSubtitle != null) {
                item { OptionRow(label = stringResource(R.string.player_select_local_subtitle), selected = false, onClick = onSelectLocalSubtitle) }
            }
        }
        // ADJUST (subtitles dialog): timing panel for the active subtitle (plan §8).
        if (onSubtitleTiming != null) {
            item {
                Text(
                    stringResource(R.string.player_adjust),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                )
            }
            item { OptionRow(label = stringResource(R.string.player_subtitle_timing), selected = false, onClick = onSubtitleTiming) }
        }
        // A/V-sync nudge (audio dialog, VOD only) — fixes a badly-muxed file where audio leads/lags the video.
        if (onAdjustAudioDelay != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.player_av_sync), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
                    // 25 ms steps, matching Settings: what this corrects is the display's own picture-processing
                    // delay, which lands in the tens of milliseconds — 50 ms could bracket it but not hit it.
                    StepButton(stringResource(R.string.common_minus), enabled = (audioDelayMs ?: 0) > -5_000) { onAdjustAudioDelay(-AV_SYNC_STEP_MS) }
                    Text(
                        formatDelay(audioDelayMs ?: 0),
                        style = MaterialTheme.typography.bodyMedium, color = colors.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.widthIn(min = 78.dp, max = 140.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StepButton(stringResource(R.string.common_plus), enabled = (audioDelayMs ?: 0) < 5_000) { onAdjustAudioDelay(AV_SYNC_STEP_MS) }
                }
            }
            // Lip-sync error belongs to the stream, not to the user: this keeps the offset for THIS
            // film or channel, so it comes back next time without following you onto anything else.
            if (onToggleRememberAudioDelay != null) {
                item {
                    OptionRow(
                        label = stringResource(R.string.player_av_sync_remember),
                        selected = audioDelayRemembered,
                        onClick = onToggleRememberAudioDelay,
                    )
                }
            }
        }
    }
}

/**
 * Requests [focus] with retries: dialog-window content composes a frame or two after the calling
 * effect starts, so a one-shot requestFocus can fire before the target row exists and silently fail.
 */
private suspend fun requestFocusRetrying(focus: FocusRequester) {
    repeat(10) {
        androidx.compose.runtime.withFrameNanos {}
        if (runCatching { focus.requestFocus() }.isSuccess) return
        delay(50)
    }
}

@Composable
private fun formatDelay(ms: Int): String = when {
    ms == 0 -> stringResource(R.string.player_delay_zero)
    ms > 0 -> stringResource(R.string.player_delay_positive, ms)
    else -> stringResource(R.string.player_delay_negative, ms)
}

@Composable
internal fun SpeedDialog(current: Double, onSelect: (Double) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    val selectedIndex = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01 }.coerceAtLeast(0)
    DialogScaffold(title = stringResource(R.string.settings_playback_speed), onDismiss = onDismiss) {
        items(SPEEDS.size) { index ->
            val speed = SPEEDS[index]
            OptionRow(
                label = if (speed == 1.0) stringResource(R.string.player_speed_normal) else stringResource(R.string.player_speed, localizedDecimal(speed)),
                selected = kotlin.math.abs(speed - current) < 0.01,
                modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(speed) },
            )
        }
    }
}

/**
 * The sleep timer's choices (N17), the phone's sheet in the television's dialog: Off while one is
 * running, the shared minute choices, "End of programme" only when the guide says when that is, and
 * "End of movie / episode" only while one plays.
 * The title turns into the countdown while a timer runs, so re-opening it says what is set.
 */
@Composable
internal fun SleepTimerDialog(
    timer: SleepTimer,
    /** When the programme on air ends; null offers no such row. */
    programmeEndMs: Long?,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    val remaining by timer.remainingMs.collectAsStateWithLifecycle()
    val title = remaining?.let {
        stringResource(R.string.player_sleep_timer_remaining, stringResource(R.string.player_duration_minutes, SleepTimer.minutesLeft(it)))
    } ?: stringResource(R.string.player_sleep_timer)
    // Read once, as the dialog opens: the rows must not change under the D-pad while it is up.
    val running = remember { remaining != null }
    val endKind = remember { timer.itemEndKind() }
    DialogScaffold(title = title, onDismiss = onDismiss) {
        if (running) {
            item {
                OptionRow(
                    label = stringResource(R.string.common_off),
                    selected = false,
                    modifier = Modifier.focusRequester(focus),
                    onClick = { timer.cancel(); onDismiss() },
                )
            }
        }
        items(SleepTimer.CHOICES_MINUTES.size) { index ->
            val minutes = SleepTimer.CHOICES_MINUTES[index]
            OptionRow(
                label = stringResource(R.string.player_duration_minutes, minutes),
                selected = false,
                modifier = if (!running && index == 0) Modifier.focusRequester(focus) else Modifier,
                onClick = { timer.start(minutes * 60_000L); onDismiss() },
            )
        }
        programmeEndMs?.takeIf { it > System.currentTimeMillis() }?.let { endMs ->
            item {
                OptionRow(
                    label = stringResource(R.string.player_sleep_timer_end_of_programme),
                    selected = false,
                    onClick = { timer.start(endMs - System.currentTimeMillis()); onDismiss() },
                )
            }
        }
        endKind?.let { kind ->
            item {
                OptionRow(
                    label = stringResource(if (kind == SleepTimer.EndKind.EPISODE) R.string.player_sleep_timer_end_of_episode else R.string.player_sleep_timer_end_of_movie),
                    selected = false,
                    onClick = { timer.startUntilItemEnd(); onDismiss() },
                )
            }
        }
        item { ScreenOffRow() }
    }
}

/**
 * "Also turn off the screen": ticked is the system grant itself ([ScreenOff]), so it is re-read after
 * the system screen answers rather than stored. A set with no such screen says so instead.
 */
@Composable
private fun ScreenOffRow(screenOff: ScreenOff = koinInject()) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(screenOff.isAllowed()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { allowed = screenOff.isAllowed() }
    OptionRow(
        label = stringResource(R.string.player_sleep_timer_screen_off),
        selected = allowed,
        onClick = {
            if (allowed) {
                screenOff.revoke()
                allowed = false
            } else {
                runCatching { ask.launch(screenOff.requestIntent()) }.onFailure {
                    android.widget.Toast.makeText(context, R.string.player_sleep_timer_screen_off_unavailable, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        },
    )
}

@Composable
internal fun ZoomDialog(current: ZoomMode, onSelect: (ZoomMode) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    // Land focus on the current mode (not always the first row) so re-opening starts on your selection.
    val selectedIndex = ZoomMode.entries.indexOf(current).coerceAtLeast(0)
    DialogScaffold(title = stringResource(R.string.settings_player_zoom), onDismiss = onDismiss) {
        items(ZoomMode.entries.size) { index ->
            val mode = ZoomMode.entries[index]
            OptionRow(label = stringResource(mode.labelRes), selected = mode == current, modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier, onClick = { onSelect(mode) })
        }
    }
}

@Composable
internal fun VolumeDialog(player: PlaybackEngine, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val volume by player.volume.collectAsStateWithLifecycle()
    // Mute the channel and "–" disables; without the shared guard focus died there (see
    // [tv.own.owntv.ui.components.rememberStepperFocus]).
    val steppers = tv.own.owntv.ui.components.rememberStepperFocus(
        plusEnabled = volume < 150,
        minusEnabled = volume > 0,
    )
    // Real dialog window for the same focus isolation as DialogScaffold (see there).
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        // Slightly stronger than the default wash: this one sits over moving video.
        Box(
            Modifier.fillMaxSize().modalScrim(strength = 1.2f).trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.dialogPanel(padding = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_volume), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(stringResource(R.string.common_minus), enabled = volume > 0, modifier = Modifier.focusRequester(steppers.minus)) { player.adjustVolumeByUser(-5) }
                    Text(stringResource(R.string.player_percent, volume), style = MaterialTheme.typography.headlineLarge, color = colors.accent, modifier = Modifier.width(120.dp), textAlign = TextAlign.Center)
                    StepButton(stringResource(R.string.common_plus), enabled = volume < 150, modifier = Modifier.focusRequester(steppers.plus)) { player.adjustVolumeByUser(5) }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton(stringResource(if (volume == 0) R.string.player_unmute else R.string.player_mute), onClick = { player.toggleMute() }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * Subtitle-timing panel (subtitle plan §8.2/§8.3): 100 ms and 500 ms steps + Reset, applied live while
 * the video keeps playing behind (the backdrop is NOT dimmed so speech and text can be compared).
 * Positive = subtitles shown later; the direction is always spelled out. Back keeps the value.
 */
@Composable
internal fun SubtitleTimingDialog(player: PlaybackEngine, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val delay by player.subDelayMs.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        tv.own.owntv.ui.theme.PopupFontTheme {
            // No scrim by design — the video stays undimmed behind so speech and subtitles can be
            // compared while the offset is nudged. Only the focus trap is added.
            Box(
                Modifier.fillMaxSize().padding(bottom = 56.dp).trapAllFocusExit().focusGroup(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(Modifier.dialogPanel(width = 560.dp, padding = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.player_subtitle_timing), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text(formatSubDelay(delay), style = MaterialTheme.typography.headlineLarge, color = colors.accent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            delay > 0 -> stringResource(R.string.player_subtitles_later)
                            delay < 0 -> stringResource(R.string.player_subtitles_earlier)
                            else -> stringResource(R.string.player_no_offset)
                        },
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OwnTVButton(stringResource(R.string.player_subtitle_delay_negative, 0.5), onClick = { player.adjustSubtitleDelay(-500) }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                        OwnTVButton(stringResource(R.string.player_subtitle_delay_negative, 0.1), onClick = { player.adjustSubtitleDelay(-100) }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                        OwnTVButton(stringResource(R.string.common_reset), onClick = { player.resetSubtitleDelay() }, modifier = Modifier.focusRequester(focus))
                        OwnTVButton(stringResource(R.string.player_subtitle_delay_positive, 0.1), onClick = { player.adjustSubtitleDelay(100) }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                        OwnTVButton(stringResource(R.string.player_subtitle_delay_positive, 0.5), onClick = { player.adjustSubtitleDelay(500) }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                    }
                }
            }
        }
    }
}

@Composable
private fun formatSubDelay(ms: Int): String = when {
    ms == 0 -> stringResource(R.string.player_subtitle_delay_zero)
    ms > 0 -> stringResource(R.string.player_subtitle_delay_positive, ms / 1000.0)
    else -> stringResource(R.string.player_subtitle_delay_negative, -ms / 1000.0)
}

@Composable
private fun StepButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, enabled = enabled, modifier = modifier.size(64.dp), shape = RoundedCornerShape(18.dp), contentAlignment = Alignment.Center, surface = GlassSurface.DIALOGS) { _ ->
        Text(label, style = MaterialTheme.typography.headlineMedium, color = if (enabled) OwnTVTheme.colors.onSurface else OwnTVTheme.colors.outline)
    }
}

@Composable
private fun DialogScaffold(
    title: String,
    onDismiss: () -> Unit,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val colors = OwnTVTheme.colors
    // A REAL dialog window, not an in-place overlay: it owns the D-pad focus scope, so nothing in the
    // HUD behind it (play button, catch-all focusable, stream-info chips) can compete for or steal
    // focus — which is what intermittently locked the subtitle/audio pickers out of focus on
    // codec-heavy (HDR/DTS) streams. Back is handled by the window itself via onDismissRequest.
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        // Compact glass popup matching the storage picker: smaller font + narrow box.
        tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.72f) {
            Box(
                modifier = Modifier.fillMaxSize().modalScrim(strength = 1.2f).trapAllFocusExit().focusGroup(),
                contentAlignment = Alignment.Center,
            ) {
                // Glass effect panel (same translucent chrome as the volume/timing dialogs) — the
                // inner LazyColumn manages its own scroll, so scroll = false.
                Column(modifier = Modifier.dialogPanel(width = 260.dp, corner = 16.dp, padding = 14.dp, scroll = false)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Spacer(Modifier.height(8.dp))
                    // Cap to the screen (minus dialog chrome) so all rows stay reachable on small screens.
                    val listMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 160.dp).coerceIn(140.dp, 240.dp)
                    LazyColumn(state = state, modifier = Modifier.heightIn(max = listMax), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick, modifier = modifier.fillMaxWidth(), selected = selected, shape = RoundedCornerShape(12.dp),
        selectedContainerColor = colors.primaryContainer, contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.DIALOGS,
    ) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) colors.onPrimaryContainer else if (focused) colors.primary else colors.onSurface)
            if (selected) {
                Spacer(Modifier.weight(1f))
                OwnTVIcon(OwnTVIcon.STAR, tint = colors.onPrimaryContainer, filled = true, modifier = Modifier.size(14.dp))
            }
        }
    }
}
