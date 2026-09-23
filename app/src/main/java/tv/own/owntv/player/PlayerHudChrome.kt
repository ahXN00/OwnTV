package tv.own.owntv.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.player.PlayerControl
import tv.own.owntv.core.player.ControlCluster
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.animationsOn

/**
 * The HUD's chrome: the top strip, the channel/direct-tune OSD cards, the centre transport and the
 * bottom bar. Split out of [PlayerHud] — behaviour unchanged; these are `internal` rather than
 * file-private only because [PlayerHud] now lives in a sibling file.
 */

// ---------------- Top bar ----------------

@Composable
internal fun TopBar(
    player: PlaybackEngine, isLive: Boolean, chips: List<String>, duration: Long,
    onBack: () -> Unit, modifier: Modifier = Modifier,
    // Live only: the Now/Next guide, rendered at the far end of the same strip.
    trailing: (@Composable () -> Unit)? = null,
    // The clock. Rendered between two equal-weight halves so it lands on the true centre of the screen
    // whatever the channel name and guide card happen to be doing on either side of it.
    centre: (@Composable () -> Unit)? = null,
) {
    // Reactive meta so the title row updates instantly on a channel zap (the plain vars aren't observed).
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val displayTitle = meta.title?.takeIf { it.isNotBlank() }
        ?: meta.episodeNumber?.let { stringResource(R.string.player_episode_number, it) }
        ?: ""
    val localizedSubtitle = meta.localizedSubtitle()
    val vodSubtitle = if (isLive) {
        meta.subtitle
    } else {
        buildList {
            localizedSubtitle?.takeIf { it.isNotBlank() }?.let(::add)
            meta.seasonNumber?.let { add(stringResource(R.string.player_season_number, it)) }
        }.joinToString(stringResource(R.string.content_metadata_separator)).ifBlank { null }
    }
    Row(modifier = modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
      // Left half. Equal weight to the right half, so [centre] sits on the real midpoint of the screen
      // rather than the midpoint of whatever space the title happened to leave over.
      Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(OwnTVIcon.BACK, size = 40, onClick = onBack)
        Spacer(Modifier.width(14.dp))
        // Live: the channel logo sits with the channel NAME (identity), not with the programme — so the
        // whole "which channel am I on" group reads as one unit however wide the TV is.
        if (isLive) {
            ChannelLogo(meta.logoUrl, displayTitle, size = 46)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            val chipRow: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val durMin = (duration / 60000)
                    val parts = buildList {
                        meta.year?.takeIf { it.isNotBlank() }?.let { add(it) }
                        if (!isLive && durMin > 0) add(stringResource(R.string.player_duration_minutes, durMin))
                        addAll(chips) // aspect · resolution · fps · audio
                    }
                    parts.forEachIndexed { i, label ->
                        if (i > 0) Box(Modifier.size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
                    }
                    // The LIVE badge has left the top bar: live status belongs beside the timeline that
                    // describes it, so it is now the stateful badge at the right end of the dock's band A.
                }
            }
            // Live stacks the technical chips ABOVE the channel name; VOD keeps title-then-chips.
            if (isLive) {
                chipRow()
                Spacer(Modifier.height(2.dp))
                // Channel number ahead of the name — this is where you look to learn the number of a
                // channel you arrived at by zapping. meta.subtitle carries it ("#123") only while the
                // "Channel numbers" setting is on, so an off setting leaves the name alone.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    meta.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.45f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(displayTitle, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                vodSubtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(displayTitle, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                chipRow()
            }
        }
      } // end left half
      if (centre != null) {
          Spacer(Modifier.width(20.dp))
          centre()
          Spacer(Modifier.width(20.dp))
      }
      // Right half, pushed to the far edge. Same weight as the left, hence the true centring above.
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Top) {
          if (trailing != null) trailing()
      }
    }
}

/** The channel logo tile, falling back to the first letters of the channel name. */
@Composable
private fun ChannelLogo(logoUrl: String?, title: String?, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF004F46)),
        contentAlignment = Alignment.Center,
    ) {
        if (!logoUrl.isNullOrBlank()) AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Text((title ?: "?").take(3).uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF6FF8E4), fontWeight = FontWeight.Bold)
    }
}

/** The player's channel OSD: channel logo beside its name and number. */
@Composable
internal fun ChannelOsdCard(
    title: String?,
    subtitle: String?,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    /** N3 — now/next under the name on a zap; null (or a channel with no guide) keeps it name-only. */
    guide: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.widthIn(max = if (guide != null) 420.dp else 340.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF004F46)), contentAlignment = Alignment.Center) {
            if (!logoUrl.isNullOrBlank()) AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            else Text((title ?: "?").take(3).uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF6FF8E4), fontWeight = FontWeight.Bold)
        }
        Column {
            Text(title ?: "", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            guide?.invoke()
        }
    }
}

@Composable
internal fun ChannelCard(player: PlaybackEngine, modifier: Modifier = Modifier, guide: (@Composable () -> Unit)? = null) {
    // Collect the reactive meta so the card refreshes the instant a zap changes the channel.
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val displayTitle = meta.title?.takeIf { it.isNotBlank() }
        ?: meta.episodeNumber?.let { stringResource(R.string.player_episode_number, it) }
        ?: ""
    ChannelOsdCard(title = displayTitle, subtitle = meta.localizedSubtitle(), logoUrl = meta.logoUrl, modifier = modifier, guide = guide)
}

/** Direct-tune entry OSD: the number as it's typed, on the same surface (position, radius, scrim) the
 *  channel card uses, so a resolved number simply becomes that card. A blinking caret says "still
 *  accepting digits" and the bar along the bottom drains over the auto-submit window, so the wait is
 *  visible instead of mysterious. [error] turns it into the failure readout for the same number. */
@Composable
internal fun ChannelNumberCard(digits: String, error: String? = null, modifier: Modifier = Modifier) {
    // Animations Off: a steady caret, with no infinite transition started (never a 0 ms one).
    val caretAlpha = if (!animationsOn) 1f else {
        val caret = rememberInfiniteTransition(label = "tuneCaret")
        val blink by caret.animateFloat(
            initialValue = 1f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
            label = "tuneCaretAlpha",
        )
        blink
    }
    val countdown = remember { Animatable(0f) }
    // Captured before the draw lambda: a DrawScope is not a composable, so it can't read the theme.
    val accent = OwnTVTheme.colors.accentOnVideo
    LaunchedEffect(digits, error) {
        if (error != null) { countdown.snapTo(0f); return@LaunchedEffect }
        countdown.snapTo(1f)
        countdown.animateTo(0f, tween(DIRECT_TUNE_TIMEOUT_MS.toInt(), easing = LinearEasing))
    }
    Column(
        modifier.widthIn(min = 148.dp, max = 340.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f))
            // Painted, not laid out: a real bar would fillMaxWidth and stretch the card to its max width.
            .drawWithContent {
                drawContent()
                val barHeight = 3.dp.toPx()
                val top = Offset(0f, size.height - barHeight)
                drawRect(Color.White.copy(alpha = 0.08f), topLeft = top, size = Size(size.width, barHeight))
                drawRect(accent, topLeft = top, size = Size(size.width * countdown.value, barHeight))
            }
            .padding(bottom = 3.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                stringResource(R.string.player_channel_label),
                style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    digits,
                    style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                if (error == null) {
                    Box(
                        Modifier.padding(start = 4.dp, bottom = 4.dp).width(3.dp).height(22.dp)
                            .clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = caretAlpha)),
                    )
                }
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF8A80), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------- Center transport ----------------

@Composable
internal fun CenterControls(
    player: PlaybackEngine, nav: NavState, isPlaying: Boolean, isLive: Boolean,
    onRewindLive: (() -> Unit)?, onForwardLive: (() -> Unit)?, timeshiftOffset: () -> Int?,
    playFocus: FocusRequester, modifier: Modifier = Modifier,
) {
    val seekStep by player.seekStepMs.collectAsStateWithLifecycle() // Settings -> Seek step
    val rewindMode = onRewindLive != null // this is a catch-up-capable Live channel
    // Read here, not in the HUD root: it ticks every second while rewound (T14).
    val timeshiftOffsetSec = timeshiftOffset()
    val timeshifting = timeshiftOffsetSec != null
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (timeshifting) {
            // Counts down as the archive catches up to the live edge; grows if you pause.
            Text(
                if (timeshiftOffsetSec <= 1) stringResource(R.string.player_at_live_edge) else stringResource(R.string.player_behind_live, mmss(timeshiftOffsetSec)),
                style = MaterialTheme.typography.labelLarge,
                color = OwnTVTheme.colors.accentOnVideo,
            )
            Spacer(Modifier.height(12.dp))
        }
        TransportCapsule {
            if (nav.hasPrev) CircleButton(OwnTVIcon.SKIP_PREVIOUS, size = 44) { player.previous() }
            when {
                rewindMode -> CircleButton(OwnTVIcon.REWIND, size = 44) { onRewindLive() } // step back into the archive
                !isLive -> CircleButton(OwnTVIcon.REWIND, size = 44) { player.seekBy(-seekStep) }
            }
            CircleButton(if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, size = 64, primary = true, modifier = Modifier.focusRequester(playFocus)) { player.togglePlayPause() }
            when {
                rewindMode && timeshifting -> CircleButton(OwnTVIcon.FORWARD, size = 44) { onForwardLive?.invoke() } // toward live
                !isLive && !rewindMode -> CircleButton(OwnTVIcon.FORWARD, size = 44) { player.seekBy(seekStep) }
            }
            // "Go to live" is no longer a transport button: it is the Go Live pill at the head of the
            // dock's left cluster, beside the timeline it acts on.
            if (nav.hasNext) CircleButton(OwnTVIcon.SKIP_NEXT, size = 44) { player.next() }
        }
    }
}

// ---------------- Bottom bar ----------------

@Composable
internal fun BottomBar(
    player: PlaybackEngine, isLive: Boolean, position: () -> Long, duration: Long,
    volume: Int, audioCount: Int, subCount: Int, zoomMode: ZoomMode, speedLabel: String,
    onScrubLive: ((Int) -> Unit)?, timeshiftOffset: () -> Int?, onGoToLive: (() -> Unit)?, onOpenJumpBack: (() -> Unit)?,
    liveProgrammes: List<LiveProgramme> = emptyList(),
    compatMode: Boolean?, onToggleCompatMode: (() -> Unit)?,
    vodOnExo: Boolean?, onToggleVodEngine: (() -> Unit)?,
    onInfo: (() -> Unit)? = null, infoOn: Boolean = false, onReport: (() -> Unit)? = null,
    favorite: Boolean = false, onToggleFavorite: (() -> Unit)? = null,
    onPreviousChannel: (() -> Unit)? = null,
    onOpenDialog: (HudDialog) -> Unit, onPip: (() -> Unit)?, onAudioMode: (() -> Unit)?,
    onMultiview: (() -> Unit)? = null, onRecordThis: (() -> Unit)? = null, recordingThis: Boolean = false,
    onBack: () -> Unit, modifier: Modifier = Modifier,
) {
    // Changes only while rewound into the archive. The film position is read lower still, by
    // [VodTimeline], so the tool buttons do not redraw every second (T14).
    val timeshiftOffsetSec = timeshiftOffset()
    Dock(modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)) {
        // Band A — the instrument. The times are the bar's own end caps now; the separate time row is
        // gone, and on live the right cap is the state badge instead of a clock.
        when {
            // Catch-up live channel → a scrubbable live timeline (last LIVE_WINDOW up to the live edge).
            onScrubLive != null -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        // The live edge is now; the offset says how far behind it the picture is.
                        LiveTimelineBar(
                            offsetSec = timeshiftOffsetSec ?: 0,
                            programmes = liveProgrammes,
                            liveEdgeMs = System.currentTimeMillis(),
                            onScrub = onScrubLive,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    LiveStateBadge(timeshiftOffsetSec)
                }
                Spacer(Modifier.height(10.dp))
            }
            !isLive && duration > 0 -> {
                VodTimeline(player, position, duration)
                Spacer(Modifier.height(10.dp))
            }
            // A live channel with no archive has no timeline to scrub, but it still has a state to
            // report — and the badge no longer lives in the top bar.
            isLive -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LiveStateBadge(timeshiftOffsetSec) }
                Spacer(Modifier.height(10.dp))
            }
        }
        // Band B — the tools. Each cluster hugs its own screen edge and the gap between them is the
        // slack a focused button expands into, so growth is always toward the centre: the left cluster
        // pushes only the buttons to its right, the right cluster only those to its left. Walk either
        // cluster outward-in and nothing you have already passed ever moves.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().focusGroup()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // H2 — the ORDER comes from core's canonical list, not from the order these lines
                // happen to be written in. Each control keeps its own composable and its own
                // condition; only the sequence is shared, which is what makes this bar and the
                // phone's read alike. The `when` is exhaustive, so a control added to the shared
                // list cannot be silently missing here.
                PlayerControl.clusterFor(tv = true, cluster = ControlCluster.MEDIA).forEach { control ->
                    when (control) {
                        // Go Live leads the media cluster, but only while there is actually a live
                        // edge to go back to: at the edge it is absent and the tools sit flush left,
                        // and falling behind inserts it and pushes them right. onGoToLive alone is
                        // non-null on every tuned live channel, so onScrubLive is what says the
                        // channel has an archive at all.
                        PlayerControl.GO_LIVE -> if (onGoToLive != null && onScrubLive != null) {
                            GoLivePill(enabled = (timeshiftOffsetSec ?: 0) > 1) { onGoToLive() }
                        }
                        PlayerControl.VOLUME ->
                            CtrlButton(volumeIcon(volume), label = stringResource(R.string.player_tool_volume)) { onOpenDialog(HudDialog.VOLUME) }
                        PlayerControl.SPEED ->
                            SpeedButton(label = speedLabel, active = speedLabel != stringResource(R.string.player_speed_normal_short), toolLabel = stringResource(R.string.player_tool_speed)) { onOpenDialog(HudDialog.SPEED) }
                        PlayerControl.SUBTITLES ->
                            CtrlButton(OwnTVIcon.SUBTITLE, badge = subCount.takeIf { it > 0 }, label = stringResource(R.string.player_tool_subtitles)) { onOpenDialog(HudDialog.SUBS) }
                        PlayerControl.AUDIO ->
                            CtrlButton(OwnTVIcon.AUDIO, badge = audioCount.takeIf { it > 1 }, label = stringResource(R.string.player_tool_audio)) { onOpenDialog(HudDialog.AUDIO) }
                        // Favorite the current channel/movie/series without leaving the stream (coral
                        // heart = on, the same colour the marker has on posters and in browse rows).
                        PlayerControl.FAVOURITE -> if (onToggleFavorite != null) {
                            CtrlButton(OwnTVIcon.FAVORITE, active = favorite, activeTint = OwnTVTheme.colors.favorite, label = stringResource(R.string.player_tool_favorite)) { onToggleFavorite() }
                        }
                        // "Go back to…" — jump straight to a time in this channel's archive. Only on
                        // catch-up channels. CATCHUP (a TV with a replay loop): REWIND is already the
                        // transport button beside it, and a plain clock would not say which of the
                        // two time controls this is.
                        PlayerControl.CATCH_UP -> if (onOpenJumpBack != null) {
                            CtrlButton(OwnTVIcon.CATCHUP, label = stringResource(R.string.player_tool_catchup)) { onOpenJumpBack() }
                        }
                        // N2 — back to the channel watched before; present only once there is one.
                        PlayerControl.PREVIOUS_CHANNEL -> if (onPreviousChannel != null) {
                            CtrlButton(OwnTVIcon.HISTORY, label = stringResource(R.string.player_previous_channel)) { onPreviousChannel() }
                        }
                        // Belongs to the tools cluster; `clusterFor` never hands them to this loop.
                        PlayerControl.BRIGHTNESS, PlayerControl.CHANNEL_LIST, PlayerControl.ENGINE,
                        PlayerControl.ASPECT, PlayerControl.MINI_PLAYER, PlayerControl.AUDIO_ONLY,
                        PlayerControl.MULTIVIEW, PlayerControl.RECORD, PlayerControl.INFO,
                        PlayerControl.REPORT,
                        -> Unit
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // H2 — the ORDER is core's, exactly as in the media cluster above. Each control
                // keeps its own composable and condition; only the sequence is shared.
                PlayerControl.clusterFor(tv = true, cluster = ControlCluster.TOOLS).forEach { control ->
                    when (control) {
                        PlayerControl.ENGINE -> {
                            // Live "compatibility mode" (Live TV + channels opened from the Guide):
                            // pin this channel to mpv. The pill shows the active engine and flips on
                            // click (teal while pinned to mpv).
                            if (onToggleCompatMode != null) {
                                EngineToggle(label = stringResource(if (compatMode == true) R.string.player_engine_mpv else R.string.player_engine_exo), active = compatMode == true, toolLabel = stringResource(R.string.player_tool_engine)) { onToggleCompatMode() }
                            }
                            // VOD engine toggle (Movies/Series): flip THIS movie/episode between mpv
                            // and ExoPlayer. The pill shows the active engine (teal while ExoPlayer
                            // owns playback).
                            if (onToggleVodEngine != null) {
                                EngineToggle(label = stringResource(if (vodOnExo == true) R.string.player_engine_exo else R.string.player_engine_mpv), active = vodOnExo == true, toolLabel = stringResource(R.string.player_tool_engine)) { onToggleVodEngine() }
                            }
                        }
                        // Aspect/zoom works in every mode now — direct mode resizes the surface view
                        // itself (see MpvVideoSurface), GL mode scales internally.
                        PlayerControl.ASPECT ->
                            CtrlButton(OwnTVIcon.ASPECT, active = zoomMode != ZoomMode.FIT, label = stringResource(R.string.player_tool_aspect)) { onOpenDialog(HudDialog.ZOOM) }
                        PlayerControl.MINI_PLAYER -> if (onPip != null) {
                            CtrlButton(OwnTVIcon.PIP, label = stringResource(R.string.player_tool_mini)) { onPip() }
                        }
                        PlayerControl.AUDIO_ONLY -> if (onAudioMode != null) {
                            CtrlButton(OwnTVIcon.HEADPHONES, label = stringResource(R.string.player_tool_audio_only)) { onAudioMode() }
                        }
                        // Live only, and only once the user has switched Multiview on: four tiles
                        // from the channel already playing. Next to the mini-player button, which is
                        // its nearest relative.
                        PlayerControl.MULTIVIEW -> if (onMultiview != null) {
                            CtrlButton(OwnTVIcon.LIST_GRID, label = stringResource(R.string.multiview_button)) { onMultiview() }
                        }
                        // Record what is already on screen. Present only when the setting is on, so
                        // it never needs to explain itself here — the trade-off was accepted when it
                        // was enabled (D3). Tinted while running, and the label flips, because
                        // "Record" on a button that is already recording is the kind of thing that
                        // loses an hour of somebody's evening.
                        PlayerControl.RECORD -> if (onRecordThis != null) {
                            CtrlButton(
                                OwnTVIcon.LIVE_TV,
                                active = recordingThis,
                                activeTint = androidx.compose.ui.graphics.Color(0xFFEF4444),
                                label = stringResource(
                                    if (recordingThis) R.string.recording_stop else R.string.recording_record,
                                ),
                            ) { onRecordThis() }
                        }
                        // Stream technical info (codec/res/HDR/bitrate/decoder/audio/buffer) —
                        // toggles the overlay. Parked at the far right, where the redundant
                        // exit-fullscreen button used to sit (Back already leaves the player, so
                        // that button never did anything the remote couldn't).
                        PlayerControl.INFO -> if (onInfo != null) {
                            CtrlButton(OwnTVIcon.INFO, active = infoOn, label = stringResource(R.string.player_tool_info)) { onInfo() }
                        }
                        // "Report this stream": copies the readout the user is looking at into the
                        // playback log, so a "this channel judders" complaint carries the
                        // codec/decoder/bitrate that caused it. Only offered while the info overlay
                        // is open — there is nothing to report otherwise, and the bar stays as short
                        // as it was for everyone who never needs this. H1 made that the rule on both
                        // apps.
                        PlayerControl.REPORT -> if (infoOn && onReport != null) {
                            CtrlButton(OwnTVIcon.SHARE, label = stringResource(R.string.player_tool_report)) { onReport() }
                        }
                        // Belongs to the media cluster; `clusterFor` never hands them to this loop.
                        PlayerControl.GO_LIVE, PlayerControl.VOLUME, PlayerControl.BRIGHTNESS,
                        PlayerControl.SPEED, PlayerControl.SUBTITLES, PlayerControl.AUDIO,
                        PlayerControl.FAVOURITE, PlayerControl.CATCH_UP, PlayerControl.PREVIOUS_CHANNEL,
                        PlayerControl.CHANNEL_LIST,
                        -> Unit
                    }
                }
            }
        }
    }
}

private fun volumeIcon(volume: Int): OwnTVIcon = when {
    volume == 0 -> OwnTVIcon.VOLUME_MUTE
    volume < 50 -> OwnTVIcon.VOLUME_LOW
    else -> OwnTVIcon.VOLUME_HIGH
}

/** A film's seek bar and its two time caps — the only part of the dock that needs the position, so the
 *  only part that recomposes as it ticks (T14). */
@Composable
private fun VodTimeline(player: PlaybackEngine, position: () -> Long, duration: Long) {
    val seekStep by player.seekStepMs.collectAsStateWithLifecycle() // Settings -> Seek step
    val buffered by player.bufferedMs.collectAsStateWithLifecycle()
    val pos = position()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TimeCap(formatTime(pos), Alignment.Start)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            SeekBar(positionMs = pos, durationMs = duration, bufferedMs = buffered, stepMs = seekStep, onSeek = { player.seekBy(it) })
        }
        Spacer(Modifier.width(12.dp))
        TimeCap(stringResource(R.string.player_time_remaining, formatTime((duration - pos).coerceAtLeast(0))), Alignment.End)
    }
}

/** Next-episode countdown card: "Next episode in Ns" + title, with Play now / Cancel. Play now advances
 *  immediately; Cancel suppresses the automatic advance for the current item. [seconds] is read inside,
 *  so the countdown redraws this card rather than the whole HUD. */
@Composable
internal fun NextEpisodeCard(
    seconds: () -> Int,
    title: String,
    playFocus: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            stringResource(R.string.player_next_episode, seconds()),
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentOnVideo,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnTVButton(
                stringResource(R.string.player_play_now),
                onClick = onPlayNow,
                icon = OwnTVIcon.PLAY,
                modifier = Modifier.focusRequester(playFocus),
            )
            OwnTVButton(
                stringResource(R.string.common_cancel),
                onClick = onCancel,
                icon = OwnTVIcon.CLOSE,
                style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY,
            )
        }
    }
}
