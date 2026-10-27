package tv.own.owntv.player

import tv.own.owntv.ui.theme.hudShade
import tv.own.owntv.ui.theme.hudInk
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.R
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.owntv.core.settings.RemoteShortcutPress
import tv.own.owntv.ui.components.LocalRemoteShortcuts
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.displayText // PlayerFailureReason.displayText, for the error overlay
import tv.own.owntv.ui.theme.gradientWash

/**
 * The full-screen player HUD. This file owns the HUD's STATE — visibility, direct tune, the dialog the
 * user has open, the zap/engine flashes — and composes the pieces that draw it. Those pieces live in
 * sibling files: [PlayerHudChrome] (top bar, OSD cards, transport, bottom bar), [PlayerHudControls]
 * (buttons, seek bars, shared formatters) and [PlayerHudDialogs] (every dialog the HUD opens).
 */

@Composable
internal fun MediaSpec.displayText(): String {
    val decoderText = decoder?.let {
        when (it) {
            is DecoderSpec.Hardware -> buildList {
                add(stringResource(R.string.player_decoder_hardware))
                if (it.direct) add(stringResource(R.string.player_decoder_direct))
            }.joinToString(stringResource(R.string.player_metadata_separator))
            is DecoderSpec.Software -> buildList {
                add(stringResource(R.string.player_decoder_software))
                if (it.gpu) add(stringResource(R.string.player_decoder_gpu))
            }.joinToString(stringResource(R.string.player_metadata_separator))
            is DecoderSpec.Named -> buildList {
                add(
                    when (it.value.lowercase()) {
                        "exoplayer" -> stringResource(R.string.settings_player_exoplayer)
                        "mpv" -> stringResource(R.string.settings_player_mpv)
                        else -> it.value
                    },
                )
                if (it.hardware) add(stringResource(R.string.player_decoder_hardware))
                if (it.direct) add(stringResource(R.string.player_decoder_direct))
            }.joinToString(stringResource(R.string.player_metadata_separator))
        }
    }
    return listOfNotNull(codec, resolution, decoderText)
        .joinToString(stringResource(R.string.player_metadata_separator))
}

@Composable
private fun PlaybackFailure.displayText(): String {
    // Resolved through the same [describe] mapping the toast renderer uses. LocalConfiguration is read
    // so a language change still recomposes this, exactly as stringResource would.
    androidx.compose.ui.platform.LocalConfiguration.current
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    return describe { id, args -> resources.getString(id, *args.toTypedArray()) }
}

internal const val DIRECT_TUNE_TIMEOUT_MS = 2_000L
private const val DIRECT_TUNE_FEEDBACK_MS = 1_500L
private const val DIRECT_TUNE_PLAYBACK_WAIT_MS = 8_000L
private const val MAX_DIRECT_TUNE_DIGITS = 5
private const val PLAYER_SHORTCUT_LONG_PRESS_MS = 600L

/** Re-poll for late-arriving audio/subtitle tracks while the track menu is open and still empty.
 *  20 × 300 ms = 6 s, comfortably past the slowest observed HDR/DTS track report, then it stops. */
private const val TRACK_POLL_MS = 300L
private const val TRACK_POLL_TRIES = 20

/** A stall shorter than this is an ordinary re-buffer and gets the plain spinner. Past it, a line under the
 *  spinner says the stream is reconnecting by itself, so a long outage doesn't look like a hung app.
 *  Counted from the stall, which itself only starts once the buffer (~10s) has run dry. */
private const val RECONNECT_NOTICE_AFTER_MS = 10_000L

internal enum class HudDialog { NONE, AUDIO, SUBS, SPEED, ZOOM, QUALITY, VOLUME, JUMP_BACK, SLEEP_TIMER }

/** What the top-left channel OSD shows for direct tune: the digits being typed, the channel a number
 *  resolved to, or a failure message. All three render as the same card as the channel OSD. */
private sealed interface TuneOsd {
    data class Entry(val digits: String) : TuneOsd
    data class Tuned(val info: DirectTuneChannelInfo) : TuneOsd
    data class Message(val digits: String, val text: String) : TuneOsd
}

@Composable
fun PlayerHud(
    player: PlaybackEngine,
    onBack: () -> Unit,
    onPip: (() -> Unit)? = null,
    onAudioMode: (() -> Unit)? = null,
    onMultiview: (() -> Unit)? = null,
    onRecordThis: (() -> Unit)? = null,
    recordingThis: Boolean = false,
    inert: Boolean = false,
    onChannelUp: (() -> Unit)? = null,
    onChannelDown: (() -> Unit)? = null,
    onOpenChannelList: (() -> Unit)? = null,
    onOpenHistoryList: (() -> Unit)? = null,
    onRewindLive: (() -> Unit)? = null,
    onForwardLive: (() -> Unit)? = null,
    onGoToLive: (() -> Unit)? = null,
    onScrubLive: ((Int) -> Unit)? = null,
    liveProgrammes: List<LiveProgramme> = emptyList(),
    liveGaps: () -> List<LongRange> = { emptyList() },
    jumpBackOptions: (() -> List<Int>)? = null,
    onJumpBack: ((Int) -> Unit)? = null,
    onPreviousChannel: (() -> Unit)? = null,
    jumpBackWindowSec: (() -> Int)? = null,
    timeshiftOffsetSec: (() -> Int?)? = null,
    onTuneToNumber: (suspend (Int) -> DirectTuneResult)? = null,
    directTuneContextKey: Long = 0L,
    compatMode: Boolean? = null,
    onToggleCompatMode: (() -> Unit)? = null,
    vodOnExo: Boolean? = null,
    onToggleVodEngine: (() -> Unit)? = null,
    onSearchSubtitles: (() -> Unit)? = null,
    onSelectLocalSubtitle: (() -> Unit)? = null,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    liveEpgCard: (@Composable () -> Unit)? = null,
    zapGuide: (@Composable () -> Unit)? = null,
    watchingWallMs: (() -> Long?)? = null,
    sleepProgrammeEndMs: (() -> Long?)? = null,
    liveLeftRightRewinds: Boolean = false,
    matchLine: (@Composable () -> Unit)? = null,
    onOtherChannels: (() -> Unit)? = null,
    otherChannelsCount: Int = 0,
    onToastOk: (() -> Unit)? = null,
    // Nuevos parámetros opcionales para las variantes de canales
    variants: List<ChannelVariant> = emptyList(),
    currentChannelId: Long = -1L,
    onVariantSelected: ((ChannelVariant) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val timeshiftOffset: () -> Int? = { timeshiftOffsetSec?.invoke() }
    val layoutDirection = LocalLayoutDirection.current
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val position = player.position.collectAsStateWithLifecycle()
    val duration by player.duration.collectAsStateWithLifecycle()
    val buffering by player.buffering.collectAsStateWithLifecycle()
    val error by player.error.collectAsStateWithLifecycle()
    val errorInfo by player.errorInfo.collectAsStateWithLifecycle()
    val providerBackOff by player.providerBackOff.collectAsStateWithLifecycle()
    val stalledSinceMs by player.stalledSinceMs.collectAsStateWithLifecycle()
    val nav by player.nav.collectAsStateWithLifecycle()
    val volume by player.volume.collectAsStateWithLifecycle()
    val videoRes by player.videoRes.collectAsStateWithLifecycle()
    val streamChips by player.streamChips.collectAsStateWithLifecycle()
    val engineChip by player.engineChip.collectAsStateWithLifecycle()
    val audioCount by player.audioCount.collectAsStateWithLifecycle()
    val audioDelayMs by player.audioDelayMs.collectAsStateWithLifecycle()
    val audioDelayRemembered by player.audioDelayRemembered.collectAsStateWithLifecycle()
    val subCount by player.subCount.collectAsStateWithLifecycle()
    val zoomMode by player.zoomMode.collectAsStateWithLifecycle()
    val speed by player.speed.collectAsStateWithLifecycle()
    val isLive = player.isLiveContent
    val switchedToExo = stringResource(R.string.player_switch_exo)
    val switchedToMpv = stringResource(R.string.player_switch_mpv)
    val tuneNotFound = stringResource(R.string.player_channel_not_found)
    val multipleChannels = stringResource(R.string.player_multiple_channels)
    val tuneFailed = stringResource(R.string.player_tune_failed)

    val nextUpTitle by player.nextUpTitle.collectAsStateWithLifecycle()
    val stopsAtItemEnd by player.stopsAtItemEnd.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf(HudDialog.NONE) }
    // Estado local para mostrar u ocultar el diálogo de variantes de calidad
    var showVariantsDialog by remember { mutableStateOf(false) }

    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val catchFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    var focusSeekOnShow by remember { mutableStateOf(false) }

    var autoNextDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(nextUpTitle, nav.hasNext) { autoNextDismissed = false }
    val msToAdvance: () -> Long = { if (!isLive && duration > 0L) (duration - 8_000L) - position.value else Long.MAX_VALUE }
    val nextCardDue by remember(isLive, duration) { derivedStateOf { msToAdvance() in 0L..30_000L } }
    val showNextCard = !isLive && error == null && nav.hasNext && nextUpTitle != null && !stopsAtItemEnd &&
        nextCardDue && !autoNextDismissed

    var controlsVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    val reportContext = androidx.compose.ui.platform.LocalContext.current
    var wakeTick by remember { mutableIntStateOf(0) }
    val forceShow = error != null || dialog != HudDialog.NONE || showVariantsDialog
    BackHandler(enabled = controlsVisible && !forceShow) { controlsVisible = false }
    val canZap = onChannelUp != null && onChannelDown != null
    var channelFlash by remember { mutableIntStateOf(0) }
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(channelFlash) { if (channelFlash > 0) { showFlash = true; delay(3000); showFlash = false } }

    var engineMsg by remember { mutableStateOf<String?>(null) }
    var engineFlash by remember { mutableIntStateOf(0) }
    LaunchedEffect(engineFlash) { if (engineFlash > 0) { delay(1800); engineMsg = null } }
    val smoothnessHint by player.smoothnessHint.collectAsStateWithLifecycle()
    val smoothnessHintText = stringResource(R.string.player_smoothness_hint)
    LaunchedEffect(smoothnessHint) {
        if (smoothnessHint) {
            engineMsg = smoothnessHintText
            delay(6_000)
            if (engineMsg == smoothnessHintText) engineMsg = null
        }
    }
    val toggleCompat: (() -> Unit)? = onToggleCompatMode?.let { cb -> {
        engineMsg = if (compatMode == true) switchedToExo else switchedToMpv; engineFlash++; cb()
    } }
    val toggleVod: (() -> Unit)? = onToggleVodEngine?.let { cb -> {
        engineMsg = if (vodOnExo == true) switchedToMpv else switchedToExo; engineFlash++; cb()
    } }

    var digitBuffer by remember { mutableStateOf("") }
    var submissionRequest by remember { mutableStateOf<Int?>(null) }
    var submissionTick by remember { mutableIntStateOf(0) }

    var lookupInFlight by remember { mutableStateOf(false) }

    var tuneOsd by remember { mutableStateOf<TuneOsd?>(null) }
    var tuneOsdTick by remember { mutableIntStateOf(0) }

    val digitsActive = digitBuffer.isNotEmpty()
    val heldDigitKeys = remember { mutableSetOf<Key>() }

    val cancelDirectTune: () -> Unit = {
        digitBuffer = ""
        submissionRequest = null
        tuneOsd = null
        heldDigitKeys.clear()
        submissionTick++
        tuneOsdTick++
    }

    val zap: (Int) -> Unit = { d ->
        cancelDirectTune()
        (if (d < 0) onChannelUp else onChannelDown)?.invoke(); channelFlash++
    }

    LaunchedEffect(digitBuffer) {
        if (digitBuffer.isEmpty()) return@LaunchedEffect
        delay(DIRECT_TUNE_TIMEOUT_MS)
        val num = digitBuffer.toIntOrNull()
        digitBuffer = ""
        if (num != null) { submissionRequest = num; submissionTick++ }
        else tuneOsd = null
    }
    LaunchedEffect(submissionTick) {
        val num = submissionRequest ?: return@LaunchedEffect
        submissionRequest = null
        lookupInFlight = true
        val result = try {
            onTuneToNumber?.invoke(num)
        } finally {
            lookupInFlight = false
            heldDigitKeys.clear()
        }
        tuneOsd = when (result) {
            is DirectTuneResult.Found -> TuneOsd.Tuned(result.channel)
            is DirectTuneResult.NotFound -> TuneOsd.Message(num.toString(), tuneNotFound)
            is DirectTuneResult.Ambiguous -> TuneOsd.Message(num.toString(), multipleChannels)
            is DirectTuneResult.Failed -> TuneOsd.Message(num.toString(), tuneFailed)
            is DirectTuneResult.Cancelled -> null
            null -> null
        }
        if (tuneOsd != null) tuneOsdTick++
    }
    LaunchedEffect(tuneOsdTick) {
        when (val osd = tuneOsd) {
            is TuneOsd.Tuned -> {
                if (osd.info.restarted) {
                    withTimeoutOrNull(DIRECT_TUNE_PLAYBACK_WAIT_MS) {
                        snapshotFlow { isPlaying && !buffering && error == null }.first { !it }
                        snapshotFlow { (isPlaying && !buffering) || error != null }.first { it }
                    }
                }
                delay(DIRECT_TUNE_FEEDBACK_MS)
                tuneOsd = null
            }
            is TuneOsd.Message -> { delay(DIRECT_TUNE_FEEDBACK_MS); tuneOsd = null }
            is TuneOsd.Entry, null -> Unit
        }
    }
    LaunchedEffect(inert) { if (inert) cancelDirectTune() }
    LaunchedEffect(dialog) { if (dialog != HudDialog.NONE) cancelDirectTune() }
    LaunchedEffect(directTuneContextKey) {
        if (digitBuffer.isNotEmpty() || submissionRequest != null) {
            digitBuffer = ""
            submissionRequest = null
            heldDigitKeys.clear()
            submissionTick++
            if (tuneOsd is TuneOsd.Entry) tuneOsd = null
        }
    }
    BackHandler(enabled = digitsActive) { digitBuffer = ""; tuneOsd = null }

    val hudScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(forceShow) { if (forceShow) controlsVisible = true }
    LaunchedEffect(controlsVisible, player) { if (controlsVisible) player.refreshStreamChips() }
    DisposableEffect(showInfo, player) {
        if (showInfo) player.refreshStreamChips()
        player.setBitrateTrackingEnabled(showInfo)
        onDispose { player.setBitrateTrackingEnabled(false) }
    }
    LaunchedEffect(controlsVisible, wakeTick, forceShow, inert) {
        if (controlsVisible && !forceShow && !inert && !showVariantsDialog) { delay(4500); controlsVisible = false }
    }
    LaunchedEffect(controlsVisible, error, dialog, inert, showNextCard, showVariantsDialog) {
        if (dialog != HudDialog.NONE || inert || showVariantsDialog) return@LaunchedEffect
        if (showNextCard) { runCatching { nextFocus.requestFocus() }; return@LaunchedEffect }
        if (controlsVisible) {
            val toSeek = focusSeekOnShow
            focusSeekOnShow = false
            when {
                error != null -> runCatching { retryFocus.requestFocus() }
                toSeek -> runCatching { seekFocus.requestFocus() }.onFailure { runCatching { playFocus.requestFocus() } }
                else -> runCatching { playFocus.requestFocus() }
            }
        } else runCatching { catchFocus.requestFocus() }
    }

    val remoteShortcuts = LocalRemoteShortcuts.current
    var shortcutKeyCode by remember { mutableIntStateOf(android.view.KeyEvent.KEYCODE_UNKNOWN) }
    var shortcutPressedAt by remember { mutableStateOf(0L) }
    val dispatchPlayerShortcut: (RemoteShortcutAction) -> Unit = { action ->
        when (action) {
            RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS -> { controlsVisible = true; dialog = HudDialog.SUBS }
            RemoteShortcutAction.OPEN_AUDIO_CONTROLS -> { controlsVisible = true; dialog = HudDialog.AUDIO }
            RemoteShortcutAction.OPEN_ASPECT_CONTROLS -> { controlsVisible = true; dialog = HudDialog.ZOOM }
            RemoteShortcutAction.TOGGLE_PLAYBACK_INFO -> showInfo = !showInfo
            else -> remoteShortcuts.dispatch(action)
        }
    }
    Box(
        modifier = modifier.fillMaxSize()
            .onPreviewKeyEvent { e ->
            if (onTuneToNumber != null && !inert && dialog == HudDialog.NONE && !showVariantsDialog) {
                val digit = keyToDigit(e.key)
                if (digit != null) {
                    if (e.type == KeyEventType.KeyUp) {
                        heldDigitKeys.remove(e.key)
                        return@onPreviewKeyEvent true
                    }
                    if (e.type == KeyEventType.KeyDown) {
                        if (lookupInFlight || !heldDigitKeys.add(e.key)) {
                            return@onPreviewKeyEvent true
                        }
                        val enteredDigits = digitBuffer + digit
                        tuneOsd = TuneOsd.Entry(enteredDigits)
                        tuneOsdTick++
                        if (enteredDigits.length == MAX_DIRECT_TUNE_DIGITS) {
                            digitBuffer = ""
                            submissionRequest = enteredDigits.toIntOrNull()
                            submissionTick++
                        } else {
                            digitBuffer = enteredDigits
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                if (e.type == KeyEventType.KeyDown && !lookupInFlight && digitsActive &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)
                ) {
                    val num = digitBuffer.toIntOrNull()
                    digitBuffer = ""
                    if (num != null) { submissionRequest = num; submissionTick++ }
                    return@onPreviewKeyEvent true
                }
            }
            if (canZap && (e.key == Key.ChannelUp || e.key == Key.ChannelDown)) {
                if (e.type == KeyEventType.KeyDown) {
                    zap(if (e.key == Key.ChannelUp) 1 else -1)
                }
                return@onPreviewKeyEvent true
            }
            if (remoteShortcuts.enabled && !inert && dialog == HudDialog.NONE && !showVariantsDialog && !digitsActive) {
                val keyCode = e.nativeKeyEvent.keyCode
                val keyBindings = remoteShortcuts.bindings.filter {
                    it.keyCode == keyCode &&
                        it.action != RemoteShortcutAction.PAGE_TOWARD_FIRST &&
                        it.action != RemoteShortcutAction.PAGE_TOWARD_LAST &&
                        it.action != RemoteShortcutAction.JUMP_TO_FIRST &&
                        it.action != RemoteShortcutAction.JUMP_TO_LAST
                }
                if (keyBindings.isNotEmpty()) {
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            if (shortcutKeyCode != keyCode) {
                                shortcutKeyCode = keyCode
                                shortcutPressedAt = e.nativeKeyEvent.eventTime
                            }
                            return@onPreviewKeyEvent true
                        }
                        KeyEventType.KeyUp -> if (shortcutKeyCode == keyCode) {
                            val heldLong = e.nativeKeyEvent.eventTime - shortcutPressedAt >= PLAYER_SHORTCUT_LONG_PRESS_MS
                            val binding = if (heldLong) {
                                keyBindings.firstOrNull { it.press == RemoteShortcutPress.LONG }
                                    ?: keyBindings.firstOrNull { it.press == RemoteShortcutPress.SHORT }
                            } else {
                                keyBindings.firstOrNull { it.press == RemoteShortcutPress.SHORT }
                            }
                            shortcutKeyCode = android.view.KeyEvent.KEYCODE_UNKNOWN
                            binding?.let { dispatchPlayerShortcut(it.action) }
                            return@onPreviewKeyEvent true
                        }
                    }
                }
            }
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                canZap && e.key == Key.MediaNext -> { zap(1); true }
                canZap && e.key == Key.MediaPrevious -> { zap(-1); true }
                canZap && isLive && !controlsVisible && e.key == Key.DirectionUp -> { zap(1); true }
                canZap && isLive && !controlsVisible && e.key == Key.DirectionDown -> { zap(-1); true }
                !controlsVisible && error == null && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) &&
                    (if (onScrubLive != null) liveLeftRightRewinds else !isLive && duration > 0L) -> {
                    val back = e.key == Key.DirectionLeft
                    if (onScrubLive != null) {
                        onScrubLive(if (back) LIVE_SCRUB_STEP_SEC else -LIVE_SCRUB_STEP_SEC)
                    } else {
                        val step = player.seekStepMs.value
                        player.seekBy(if (back) -step else step)
                    }
                    focusSeekOnShow = true
                    controlsVisible = true
                    true
                }
                onOpenChannelList != null && !controlsVisible &&
                    e.key.horizontalDirection(layoutDirection) == HorizontalDirection.START -> { onOpenChannelList(); true }
                onOpenHistoryList != null && !controlsVisible &&
                    e.key.horizontalDirection(layoutDirection) == HorizontalDirection.END -> { onOpenHistoryList(); true }
                onToastOk != null && !controlsVisible &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter) -> { onToastOk(); true }
                controlsVisible -> { wakeTick++; false }
                else -> false
            }
        },
    ) {
        if (!controlsVisible && !showNextCard) {
            Box(
                Modifier.fillMaxSize().focusRequester(catchFocus).focusable()
                    .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key != Key.Back) { controlsVisible = true; true } else false },
            )
        }

        if (showInfo) {
            StreamInfoOverlay(player, modifier = Modifier.align(Alignment.TopEnd).padding(top = 112.dp, end = 20.dp))
        }

        val tuned = (tuneOsd as? TuneOsd.Tuned)?.info
        Column(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = 28.dp, top = if (controlsVisible) 92.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLive && (tuned != null || (showFlash && !controlsVisible))) {
                if (tuned != null) {
                    ChannelOsdCard(title = tuned.name, subtitle = tuned.number?.let { stringResource(R.string.player_channel_number, it) }, logoUrl = tuned.logoUrl, guide = zapGuide)
                } else {
                    ChannelCard(player, guide = zapGuide)
                }
            }
            when (val osd = tuneOsd) {
                is TuneOsd.Entry -> ChannelNumberCard(osd.digits)
                is TuneOsd.Message -> ChannelNumberCard(osd.digits, error = osd.text)
                is TuneOsd.Tuned, null -> Unit
            }
        }

        RecordingBadge(modifier = Modifier.align(Alignment.TopEnd))

        if (controlsVisible) {
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(210.dp)
                .gradientWash(
                    vertical = true,
                    0.0f to hudShade(0.72f),
                    0.5f to hudShade(0.68f),
                    1.0f to Color.Transparent,
                ))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(260.dp)
                .gradientWash(
                    vertical = true,
                    0.0f to Color.Transparent,
                    0.45f to hudShade(0.68f),
                    1.0f to hudShade(0.78f),
                ))

            TopBar(
                player, isLive, listOfNotNull(engineChip) + streamChips.ifEmpty { listOfNotNull(videoRes) }, duration, onBack,
                modifier = Modifier.align(Alignment.TopStart),
                below = matchLine,
                trailing = if (error == null) liveEpgCard else null,
                centre = if (error == null) {
                    { PlayerClock(watchingMs = watchingWallMs?.invoke()) }
                } else null,
            )

            if (error == null) {
                CenterControls(player, nav, isPlaying, isLive, onRewindLive, onForwardLive, timeshiftOffset, playFocus, modifier = Modifier.align(Alignment.Center))

                val reportDuration = duration.takeIf { it > 0 }?.let { formatTime(it) }
                val reportSavedMessage = stringResource(R.string.player_report_saved)

                BottomBar(
                    seekFocus = seekFocus,
                    player = player, isLive = isLive, position = { position.value }, duration = duration,
                    volume = volume, audioCount = audioCount, subCount = subCount, zoomMode = zoomMode,
                    speedLabel = formatSpeed(speed),
                    onScrubLive = onScrubLive, timeshiftOffset = timeshiftOffset, onGoToLive = onGoToLive,
                    liveProgrammes = liveProgrammes,
                    liveGaps = liveGaps,
                    onOpenJumpBack = if (onJumpBack != null) { { dialog = HudDialog.JUMP_BACK } } else null,
                    onPreviousChannel = onPreviousChannel,
                    onOtherChannels = onOtherChannels, otherChannelsCount = otherChannelsCount,
                    compatMode = compatMode, onToggleCompatMode = toggleCompat,
                    vodOnExo = vodOnExo, onToggleVodEngine = toggleVod,
                    onInfo = { showInfo = !showInfo }, infoOn = showInfo,
                    onReport = {
                        val meta = player.currentMeta.value
                        val reportPosition = tv.own.owntv.ui.components.formatTimestamp(reportContext.resources, position.value)
                        hudScope.launch {
                            val snapshot = buildString {
                                appendLine(player.streamInfo().joinToString("\n") { (k, v) -> "  $k: $v" })
                                appendLine("  position: $reportPosition${reportDuration?.let { " / $it" }.orEmpty()}")
                            }
                            PlaybackErrorLog.report(
                                context = reportContext,
                                engine = engineChip ?: "?",
                                live = isLive,
                                title = meta.title,
                                snapshot = snapshot,
                            )
                        }
                        engineMsg = reportSavedMessage
                        engineFlash++
                    },
                    favorite = favorite, onToggleFavorite = onToggleFavorite,
                    onOpenDialog = { dialog = it }, onPip = onPip, onAudioMode = onAudioMode,
                    onMultiview = onMultiview, onRecordThis = onRecordThis, recordingThis = recordingThis,
                    variantsCount = variants.size, onOpenVariants = { showVariantsDialog = true },
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }

        if (showNextCard) {
            NextEpisodeCard(
                seconds = { ((msToAdvance() + 999L) / 1000L).toInt().coerceIn(0, 30) },
                title = nextUpTitle ?: "",
                playFocus = nextFocus,
                onPlayNow = { autoNextDismissed = true; player.next() },
                onCancel = { autoNextDismissed = true; player.cancelAutoNext() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 120.dp),
            )
        }

        engineMsg?.let { msg ->
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    msg,
                    style = tv.own.owntv.ui.theme.stageText(25, 600),
                    color = hudInk,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(hudShade(0.55f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        when {
            error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_playback_error), style = tv.own.owntv.ui.theme.stageText(40, 600), color = Color.White)
                Spacer(Modifier.height(8.dp))
                error?.let {
                    Text(it.displayText(), style = tv.own.owntv.ui.theme.stageText(25, 400), color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
                errorInfo?.let { info ->
                    info.reason?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it.displayText(), style = tv.own.owntv.ui.theme.stageText(25, 400), color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                    info.spec?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it.displayText(), style = tv.own.owntv.ui.theme.stageText(22, 700), color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
                    }
                    info.raw?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.player_raw_error, it), style = tv.own.owntv.ui.theme.stageText(22, 400), color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                }
                val recordingConflict = rememberRecordingConflict(error)
                Spacer(Modifier.height(18.dp))
                if (recordingConflict != null) {
                    Text(
                        stringResource(R.string.player_recording_conflict),
                        style = tv.own.owntv.ui.theme.stageText(25, 400),
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        tv.own.owntv.ui.stage.StageButton(
                            stringResource(R.string.player_keep_watching_stop_recording),
                            onClick = { recordingConflict.stopAndRetry() },
                            modifier = Modifier.focusRequester(retryFocus),
                            icon = OwnTVIcon.PLAY,
                            tinted = true,
                        )
                        tv.own.owntv.ui.stage.StageButton(
                            stringResource(R.string.settings_close),
                            onClick = onBack,
                        )
                    }
                } else {
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_retry), onClick = { player.retry() }, modifier = Modifier.focusRequester(retryFocus), icon = OwnTVIcon.PLAY, tinted = true)
                }
            }
            buffering -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                OwnTVSpinner(sizeDp = 56)
                val stalledSince = stalledSinceMs
                if (providerBackOff == null && stalledSince != null) {
                    val showNotice by produceState(false, stalledSince) {
                        delay(RECONNECT_NOTICE_AFTER_MS - (android.os.SystemClock.elapsedRealtime() - stalledSince))
                        value = true
                    }
                    if (showNotice) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.player_reconnecting),
                            style = tv.own.owntv.ui.theme.stageText(25, 400),
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.7f),
                        )
                    }
                }
                providerBackOff?.let { wait ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(
                            R.string.player_provider_retry_after,
                            wait.httpCode,
                            wait.message ?: stringResource(R.string.player_provider_busy),
                            wait.secondsLeft,
                        ),
                        style = tv.own.owntv.ui.theme.stageText(25, 400),
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                }
            }
        }
    }

    // --- Diálogo flotante para desplegar las variantes de calidad ---
    if (showVariantsDialog) {
        ChannelVariantsDialog(
            variants = variants,
            currentChannelId = currentChannelId,
            onVariantSelected = { selectedVariant ->
                onVariantSelected?.invoke(selectedVariant)
                showVariantsDialog = false
            },
            onDismiss = { showVariantsDialog = false }
        )
    }

    when (dialog) {
        HudDialog.AUDIO -> {
            var audioTracks by remember { mutableStateOf(player.audioTracks()) }
            LaunchedEffect(Unit) {
                repeat(TRACK_POLL_TRIES) {
                    if (audioTracks.isNotEmpty()) return@LaunchedEffect
                    delay(TRACK_POLL_MS)
                    audioTracks = player.audioTracks()
                }
            }
            TrackDialog(
                stringResource(R.string.player_audio_track), audioTracks,
                onSelect = { player.selectAudio(it.mpvId); dialog = HudDialog.NONE }, onOff = null,
                onDismiss = { dialog = HudDialog.NONE },
                audioDelayMs = if (player.audioDelayAvailable()) audioDelayMs else null,
                onAdjustAudioDelay = if (player.audioDelayAvailable()) ({ d -> player.adjustAudioDelay(d) }) else null,
                audioDelayRemembered = audioDelayRemembered,
                onToggleRememberAudioDelay = if (player.audioDelayAvailable()) ({ player.toggleRememberAudioDelay() }) else null,
            )
        }
        HudDialog.SUBS -> {
            var subTracks by remember { mutableStateOf(player.textTracks()) }
            LaunchedEffect(Unit) {
                repeat(TRACK_POLL_TRIES) {
                    if (subTracks.isNotEmpty()) return@LaunchedEffect
                    delay(TRACK_POLL_MS)
                    subTracks = player.textTracks()
                }
            }
            val subDelay by player.subDelayMs.collectAsStateWithLifecycle()
            TrackDialog(
                stringResource(R.string.player_subtitles), subTracks,
                onSelect = { player.selectSubtitle(it.mpvId); dialog = HudDialog.NONE },
                onOff = { player.disableSubtitles(); dialog = HudDialog.NONE },
                onDismiss = { dialog = HudDialog.NONE },
                onSearchSubtitles = onSearchSubtitles?.let { open -> { dialog = HudDialog.NONE; open() } },
                onSelectLocalSubtitle = onSelectLocalSubtitle?.let { open -> { dialog = HudDialog.NONE; open() } },
                subDelayMs = subDelay,
                onAdjustSubDelay = if (player.subtitleTimingAvailable()) player::adjustSubtitleDelay else null,
                onResetSubDelay = player::resetSubtitleDelay,
            )
        }
        HudDialog.SPEED -> SpeedDialog(current = speed, onSelect = { player.setSpeed(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.ZOOM -> ZoomDialog(current = zoomMode, onSelect = { player.setZoomModeByUser(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.QUALITY -> {
            val heights = remember { player.videoQualities.value }
            val pick by player.videoQualityPick.collectAsStateWithLifecycle()
            QualityDialog(heights, pick, onSelect = { player.selectVideoQuality(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        }
        HudDialog.VOLUME -> VolumeDialog(player, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.SLEEP_TIMER -> SleepTimerDialog(
            timer = org.koin.compose.koinInject(),
            programmeEndMs = remember { sleepProgrammeEndMs?.invoke() },
            onDismiss = { dialog = HudDialog.NONE },
        )
        HudDialog.JUMP_BACK -> {
            val options = remember { jumpBackOptions?.invoke().orEmpty() }
            val windowSec = remember { jumpBackWindowSec?.invoke() ?: 0 }
            if (options.isEmpty()) {
                dialog = HudDialog.NONE
            } else {
                tv.own.owntv.features.live.CatchupJumpDialog(
                    title = stringResource(R.string.content_catchup_jump),
                    offsetsSec = options,
                    windowSec = windowSec,
                    onPick = { dialog = HudDialog.NONE; onJumpBack?.invoke(it) },
                    onDismiss = { dialog = HudDialog.NONE },
                )
            }
        }
        HudDialog.NONE -> Unit
    }
}
