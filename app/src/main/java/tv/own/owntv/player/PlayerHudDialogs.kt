package tv.own.owntv.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.displayLabel
import tv.own.owntv.ui.format.localizedDecimal

/**
 * Every dialog the HUD opens — tracks, speed, zoom, volume and subtitle timing — plus the scaffold and
 * row they share. Split out of [PlayerHud]; behaviour unchanged.
 */

private val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0)

/** A/V-sync nudge step, kept identical to the Settings stepper so a value found in the player can be
 *  reproduced there exactly. */
private const val AV_SYNC_STEP_MS = 25

/** Subtitle timing: 0.1 s per ◀ ▶, up to a minute either way. */
private const val SUB_DELAY_STEP_MS = 100
private const val SUB_DELAY_MAX_MS = 60_000

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
    // OpenSubtitles search. Absent for Live TV and when no item context exists.
    onSearchSubtitles: (() -> Unit)? = null,
    // Non-null on the Subtitles dialog for a movie/episode → "Select local subtitle file".
    onSelectLocalSubtitle: (() -> Unit)? = null,
    // Non-null on the Subtitles dialog when timing adjustment applies to the active track →
    // an "ADJUST" section with a "Subtitle timing" row: ◀ ▶ nudge it, OK sets it back to zero.
    subDelayMs: Int = 0,
    onAdjustSubDelay: ((Int) -> Unit)? = null,
    onResetSubDelay: () -> Unit = {},
) {
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
            if (runCatching { focus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(50)
        }
    }
    DialogScaffold(title = title, onDismiss = onDismiss, state = listState) {
        if (tracks.isEmpty() && onOff == null) {
            item { Text(stringResource(R.string.player_no_tracks), style = stageText(18, 400), color = StageColors.Muted, modifier = Modifier.padding(vertical = 10.mpx)) }
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
        // ADD SUBTITLES (subtitles dialog, movie/episode only) — OpenSubtitles search + local file.
        if (onSearchSubtitles != null || onSelectLocalSubtitle != null) {
            item { tv.own.owntv.ui.stage.StagePopupLabel(stringResource(R.string.player_add_subtitles), Modifier.padding(top = 12.mpx)) }
            if (onSearchSubtitles != null) {
                item { ActionRow(stringResource(R.string.player_search_subtitles), OwnTVIcon.SEARCH, onSearchSubtitles) }
            }
            if (onSelectLocalSubtitle != null) {
                item { ActionRow(stringResource(R.string.player_select_local_subtitle), OwnTVIcon.FOLDER, onSelectLocalSubtitle) }
            }
        }
        // ADJUST (subtitles dialog): timing panel for the active subtitle.
        if (onAdjustSubDelay != null) {
            item { tv.own.owntv.ui.stage.StagePopupLabel(stringResource(R.string.player_adjust), Modifier.padding(top = 12.mpx)) }
            item {
                StepperRow(
                    title = stringResource(R.string.player_subtitle_timing),
                    value = formatSubDelay(subDelayMs),
                    step = SUB_DELAY_STEP_MS,
                    canStep = { it in -SUB_DELAY_MAX_MS..SUB_DELAY_MAX_MS },
                    current = subDelayMs,
                    onStep = onAdjustSubDelay,
                    onClick = onResetSubDelay,
                )
            }
        }
        // A/V-sync nudge (audio dialog, VOD only) — fixes a badly-muxed file where audio leads/lags the video.
        if (onAdjustAudioDelay != null) {
            item {
                // 25 ms steps, matching Settings: what this corrects is the display's own picture-processing
                // delay, which lands in the tens of milliseconds — 50 ms could bracket it but not hit it.
                StepperRow(
                    title = stringResource(R.string.player_av_sync),
                    value = formatDelay(audioDelayMs ?: 0),
                    step = AV_SYNC_STEP_MS,
                    canStep = { it in -5_000..5_000 },
                    current = audioDelayMs ?: 0,
                    onStep = onAdjustAudioDelay,
                )
            }
            // Lip-sync error belongs to the stream, not to the user: this keeps the offset for THIS
            // film or channel, so it comes back next time without following you onto anything else.
            if (onToggleRememberAudioDelay != null) {
                item {
                    tv.own.owntv.ui.stage.StagePopupOption(
                        title = stringResource(R.string.player_av_sync_remember),
                        onClick = onToggleRememberAudioDelay,
                        leading = { f -> tv.own.owntv.ui.stage.StagePopupCheck(audioDelayRemembered, f) },
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
        if (runCatching { focus.requestFocus() }.getOrDefault(false)) return
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
 * The sleep timer's choices, the phone's sheet in the television's dialog: Off while one is
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
    tv.own.owntv.ui.stage.StagePopupOption(
        title = stringResource(R.string.player_sleep_timer_screen_off),
        leading = { f -> tv.own.owntv.ui.stage.StagePopupCheck(allowed, f) },
        onClick = {
            if (allowed) {
                screenOff.revoke()
                allowed = false
            } else {
                runCatching { tv.own.owntv.features.pip.startAsHandOff(screenOff.requestIntent()) { ask.launch(it) } }.onFailure {
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

/** Auto (Settings → Maximum video quality), then every height this stream offers, highest first. */
@Composable
internal fun QualityDialog(heights: List<Int>, current: Int?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    val options: List<Int?> = listOf<Int?>(null) + heights
    val selectedIndex = options.indexOf(current).coerceAtLeast(0)
    DialogScaffold(title = stringResource(R.string.player_tool_quality), onDismiss = onDismiss) {
        items(options.size) { index ->
            val height = options[index]
            OptionRow(
                label = if (height == null) stringResource(R.string.settings_auto) else stringResource(R.string.settings_video_quality_lines, height),
                selected = height == current,
                modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(height) },
            )
        }
    }
}

@Composable
internal fun VolumeDialog(player: PlaybackEngine, onDismiss: () -> Unit) {
    val volume by player.volume.collectAsStateWithLifecycle()
    // Mute the channel and "–" disables; without the shared guard focus died there (see
    // [tv.own.owntv.ui.components.rememberStepperFocus]).
    val steppers = tv.own.owntv.ui.components.rememberStepperFocus(
        plusEnabled = volume < 150,
        minusEnabled = volume > 0,
    )
    // Real dialog window for the same focus isolation as DialogScaffold (see there).
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss, title = stringResource(R.string.player_volume), eyebrow = null, width = 640.mpx,
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(if (volume == 0) R.string.player_unmute else R.string.player_mute), onClick = { player.toggleMute() }, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = onDismiss, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.mpx, Alignment.CenterHorizontally)) {
            StepButton(stringResource(R.string.common_minus), enabled = volume > 0, modifier = Modifier.focusRequester(steppers.minus)) { player.adjustVolumeByUser(-5) }
            Text(stringResource(R.string.player_percent, volume), style = stageText(44, 800), color = stageAccent.accent, modifier = Modifier.width(160.mpx), textAlign = TextAlign.Center)
            StepButton(stringResource(R.string.common_plus), enabled = volume < 150, modifier = Modifier.focusRequester(steppers.plus)) { player.adjustVolumeByUser(5) }
        }
    }
}

@Composable
private fun formatSubDelay(ms: Int): String = when {
    ms == 0 -> stringResource(R.string.player_subtitle_delay_zero)
    ms > 0 -> stringResource(R.string.player_subtitle_delay_positive, ms / 1000.0)
    else -> stringResource(R.string.player_subtitle_delay_negative, -ms / 1000.0)
}

/**
 * The settings-row stepper in a popup: the row holds focus, ◀ ▶ move [current] by [step] while
 * [canStep] allows the result, and the value sits on the right as "− 0 ms +".
 */
@Composable
private fun StepperRow(title: String, value: String, step: Int, canStep: (Int) -> Boolean, current: Int, onStep: (Int) -> Unit, onClick: () -> Unit = {}) {
    tv.own.owntv.ui.stage.StagePopupOption(
        title = title,
        onClick = onClick,
        modifier = Modifier.onPreviewKeyEvent { e ->
            val delta = when (e.key) {
                Key.DirectionLeft -> -step
                Key.DirectionRight -> step
                else -> return@onPreviewKeyEvent false
            }
            if (e.type == KeyEventType.KeyDown && canStep(current + delta)) onStep(delta)
            true
        },
        trailing = { tv.own.owntv.ui.stage.StageStepper(value) },
    )
}

/** A round − / + on the volume popup: a Stage button, dimmed while it can go no further. */
@Composable
private fun StepButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    tv.own.owntv.ui.stage.StageButton(label, onClick = { if (enabled) onClick() }, round = true, height = 72.mpx, textSize = 30, modifier = modifier.alpha(if (enabled) 1f else 0.45f))
}

/**
 * The player's pick-one popups (tracks, speed, zoom, quality, sleep timer) as Stage popups: the title,
 * then `.opt` rows. A REAL dialog window, not an in-place overlay: it owns the D-pad focus scope, so
 * nothing in the HUD behind it (play button, catch-all focusable, stream-info chips) can compete for or
 * steal focus — which is what intermittently locked the subtitle/audio pickers out of focus on
 * codec-heavy (HDR/DTS) streams. Back is handled by the window itself via onDismissRequest.
 */
@Composable
private fun DialogScaffold(
    title: String,
    onDismiss: () -> Unit,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = title, eyebrow = null, width = 640.mpx, scroll = false) {
        LazyColumn(state = state, modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.mpx), content = content)
    }
}

/** `.opt` with a radio: one of a list, the current one ringed. */
@Composable
private fun OptionRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    tv.own.owntv.ui.stage.StagePopupOption(
        title = label, onClick = onClick, modifier = modifier, chosen = selected,
        leading = { f -> tv.own.owntv.ui.stage.StagePopupRadio(selected, f) },
    )
}

/** `.opt` that opens something (search, a file, the timing panel): its icon instead of a radio. */
@Composable
private fun ActionRow(label: String, icon: OwnTVIcon, onClick: () -> Unit) {
    tv.own.owntv.ui.stage.StagePopupOption(title = label, onClick = onClick, leading = { tv.own.owntv.ui.stage.StagePopupIcon(icon) })
}
