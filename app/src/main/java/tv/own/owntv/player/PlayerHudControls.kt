package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.animationsOn
import tv.own.owntv.ui.theme.ownTvTween
import tv.own.owntv.ui.format.localizedDecimal
import tv.own.owntv.ui.format.rememberSystemTimeFormatter

/**
 * Shared HUD primitives: the two time formatters, the button vocabulary and the two
 * scrub bars. Split out of [PlayerHud] — behaviour unchanged; the declarations the other HUD files reach
 * for are `internal` rather than file-private for that reason alone.
 */

@Composable
internal fun formatTime(ms: Long): String = tv.own.owntv.ui.components.formatTimestamp(ms)

/** mm:ss for a seconds offset (e.g. 150 → "2:30"). */
@Composable
internal fun mmss(sec: Int): String = tv.own.owntv.ui.components.formatTimestamp(sec * 1000L)

// ---------------- Buttons ----------------

@Composable
internal fun CircleButton(icon: OwnTVIcon, size: Int, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Focus fills the button with the accent and rings it in a white hairline. The glyph takes
    // onAccentOnVideo rather than a fixed dark, so a deep custom accent still gets a readable icon —
    // that role is already derived from the seed's luminance, which is the check this needs.
    val accent = OwnTVTheme.colors.accentOnVideo
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(size.dp),
        shape = CircleShape,
        focusedScale = 1.1f,
        focusedContainerColor = accent,
        unfocusedContainerColor = if (primary) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.10f),
        selectedContainerColor = Color.White.copy(alpha = 0.10f),
        contentAlignment = Alignment.Center,
    ) { focused ->
        if (focused) {
            Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape))
        }
        OwnTVIcon(
            icon,
            tint = when {
                focused -> OwnTVTheme.colors.onAccentOnVideo
                primary -> Color(0xFF0E1513)
                else -> Color.White
            },
            filled = true,
            modifier = Modifier.size((size * 0.42f).dp),
        )
    }
}

/** The transport buttons live in one frosted capsule instead of floating loose over the video. */
@Composable
internal fun TransportCapsule(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(44.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(44.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * The dock: one container holding band A (the instrument) over band B (the tools). Same fill and rim
 * as [TransportCapsule] at the dock's own 22 dp radius, so the two read as one material.
 */
@Composable
internal fun Dock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        content = content,
    )
}

/** An end cap of band A: the elapsed or remaining time, in a fixed column so the bar never shifts. */
@Composable
internal fun TimeCap(text: String, alignment: Alignment.Horizontal) {
    Column(Modifier.width(64.dp), horizontalAlignment = alignment) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The live state, as one object with two states: red with a pulsing dot at the edge, amber and static
 * with the offset while behind. Replaces the LIVE badge that used to sit in the top bar — live status
 * belongs beside the timeline that describes it.
 */
@Composable
internal fun LiveStateBadge(offsetSec: Int?) {
    val behind = offsetSec != null && offsetSec > 1
    val tint = if (behind) Color(0xFFE8A33D) else Color(0xFFDC3232)
    // Only the live edge pulses; "behind" is a static statement of fact.
    //
    // NEVER hand ownTvTween() to infiniteRepeatable. With Animations = Off it is a 0 ms tween, and
    // Compose's VectorizedInfiniteRepeatableSpec divides the play time by the iteration duration to
    // find the current repeat — 0 ms means a divide-by-zero on the main thread on the very next frame.
    // That shipped in 4.2.3 and crashed full-screen Live TV for every user with animations turned off.
    // Reduce animations is honoured by skipping the transition entirely instead.
    val pulse = if (behind || !animationsOn) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "liveDot")
        transition.animateFloat(
            initialValue = 1f, targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "liveDotAlpha",
        ).value
    }
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).background(tint.copy(alpha = 0.85f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        OwnTVIcon(
            OwnTVIcon.LIVE_DOT,
            tint = Color.White.copy(alpha = if (behind) 1f else pulse),
            filled = true,
            modifier = Modifier.size(10.dp),
        )
        Text(
            if (behind) stringResource(R.string.player_live_offset, mmss(offsetSec)) else stringResource(R.string.player_live),
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Go live" — the first item of the dock's left cluster, present only while there is a live edge to
 *  return to. At the live edge it is not drawn at all and the tools sit flush with the left of the
 *  dock; falling behind inserts it and pushes them right. */
@Composable
internal fun GoLivePill(enabled: Boolean, onClick: () -> Unit) {
    if (!enabled) return
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.width(GO_LIVE_SLOT_DP.dp).heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        focusedContainerColor = OwnTVTheme.colors.accentOnVideo,
        unfocusedContainerColor = Color(0xFFDC3232).copy(alpha = 0.85f),
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val tint = if (focused) OwnTVTheme.colors.onAccentOnVideo else Color.White
            OwnTVIcon(OwnTVIcon.LIVE_DOT, tint = tint, filled = true, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.player_go_live), style = MaterialTheme.typography.labelLarge, color = tint, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Wide enough for the longest translation of "Go live" at the 14-character ceiling, so the pill is
 *  the same size in every language rather than sized by whichever word it happens to hold. */
private const val GO_LIVE_SLOT_DP = 108

/** The label a focused tool grows to show, and the value that follows it (a track count, the speed,
 *  the engine name). Growth is horizontal and in place: because each cluster is anchored to its own
 *  screen edge, the button can only push its unvisited neighbours toward the centre gap, never the
 *  ones the user has already walked past. Capped and ellipsised as a last-resort backstop — every
 *  translation is written to the 14-character ceiling, so this should never actually bite. */
@Composable
private fun ExpandingLabel(visible: Boolean, label: String, value: String?, tint: Color) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(ownTvTween(140)) + fadeIn(ownTvTween(140)),
        exit = shrinkHorizontally(ownTvTween(140)) + fadeOut(ownTvTween(140)),
    ) {
        Row(
            Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = TOOL_LABEL_MAX_DP.dp),
            )
            // The count that overlapped the icon's corner while collapsed reads as plain text here.
            if (value != null) {
                Text(value, style = MaterialTheme.typography.labelLarge, color = tint.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Backstop width for a label written to the 14-character ceiling. */
private const val TOOL_LABEL_MAX_DP = 132

@Composable
internal fun SpeedButton(label: String, active: Boolean, toolLabel: String, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        // Focus as light: the accent rim, a 22% wash of it and a soft bloom, all from the accent that
        // reads over video. Reduce animations keeps the rim and drops the bloom.
        focusLight = OwnTVTheme.colors.accentOnVideo,
        focusedContainerColor = Color.White.copy(alpha = 0.16f),
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        // The rate itself is the icon — the extra ">>" glyph read as a seek control next to the real
        // rewind/forward buttons, and "1.0x" already says everything the button does. Focused, the
        // word "Speed" joins it so the rate is not the only thing naming the button.
        val tint = if (active) OwnTVTheme.colors.accentOnVideo else if (focused) Color.White else Color.White.copy(alpha = 0.78f)
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint, fontWeight = FontWeight.SemiBold)
            ExpandingLabel(focused, toolLabel, value = null, tint = tint)
        }
    }
}

@Composable
internal fun formatSpeed(speed: Double): String = if (speed == 1.0) {
    stringResource(R.string.player_speed_normal_short)
} else {
    stringResource(R.string.player_speed, localizedDecimal(speed))
}

/** The MPV/EXO engine toggle: a one-line pill showing the active engine, flipped on click. Accent-coloured
 *  while on the non-default engine (ExoPlayer for VOD; mpv "compatibility" pin for Live). Mirrors [SpeedButton]. */
@Composable
internal fun EngineToggle(label: String, active: Boolean, toolLabel: String, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        // Focus as light: the accent rim, a 22% wash of it and a soft bloom, all from the accent that
        // reads over video. Reduce animations keeps the rim and drops the bloom.
        focusLight = OwnTVTheme.colors.accentOnVideo,
        focusedContainerColor = Color.White.copy(alpha = 0.16f),
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val tint = if (active) OwnTVTheme.colors.accentOnVideo else if (focused) Color.White else Color.White.copy(alpha = 0.78f)
            OwnTVIcon(OwnTVIcon.SWAP, tint = tint, filled = true, modifier = Modifier.size(16.dp))
            // The engine name stays the value; focus adds the word for the thing it switches, so the
            // pill reads "EXO Player" rather than leaving MPV/EXO to be guessed at.
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint, fontWeight = FontWeight.SemiBold)
            ExpandingLabel(focused, toolLabel, value = null, tint = tint)
        }
    }
}

@Composable
internal fun CtrlButton(
    icon: OwnTVIcon,
    badge: Int? = null,
    active: Boolean = false,
    // The favourite heart stays coral here instead of taking the accent, so one colour means
    // "favourite" everywhere in the app — browse rows, posters and the player alike.
    activeTint: Color = OwnTVTheme.colors.accentOnVideo,
    label: String,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        // A square 44 dp while collapsed; focus lets it grow sideways to fit its label.
        modifier = Modifier.height(44.dp).widthIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        // Focus as light: the accent rim, a 22% wash of it and a soft bloom, all from the accent that
        // reads over video. Reduce animations keeps the rim and drops the bloom.
        focusLight = OwnTVTheme.colors.accentOnVideo,
        focusedContainerColor = Color.White.copy(alpha = 0.16f),
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        val pad by animateDpAsState(if (focused) 12.dp else 0.dp, ownTvTween(140), label = "toolPadding")
        val tint = if (active) activeTint else if (focused) Color.White else Color.White.copy(alpha = 0.78f)
        Row(Modifier.padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                OwnTVIcon(icon, tint = tint, filled = true, modifier = Modifier.size(22.dp))
                // Collapsed, the count sits on the icon's corner; expanded it moves inline after the
                // label instead, where it no longer covers a third of the glyph.
                if (badge != null && !focused) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(15.dp).clip(CircleShape).background(OwnTVTheme.colors.accentOnVideo),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.common_number_grouped, badge), style = MaterialTheme.typography.labelSmall, color = OwnTVTheme.colors.onAccentOnVideo, fontWeight = FontWeight.Bold)
                    }
                }
            }
            ExpandingLabel(focused, label, badge?.let { stringResource(R.string.common_number_grouped, it) }, tint)
        }
    }
}

// ---------------- Seekbar ----------------

/** The thumb, haloed: a soft accent disc behind the solid one so the watched point stays findable
 *  against a bright picture. Placed by filling [frac] of the bar and sitting at that box's end. */
@Composable
private fun BoxScope.Playhead(frac: Float) {
    Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(OwnTVTheme.colors.accentOnVideo.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(OwnTVTheme.colors.accentOnVideo))
        }
    }
}

@Composable
internal fun SeekBar(positionMs: Long, durationMs: Long, bufferedMs: Long, stepMs: Long, onSeek: (Long) -> Unit, focusRequester: FocusRequester? = null) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // The buffer ghost: how far ahead the engine has data. Never behind the playhead, so a stale or
    // unreported value simply draws nothing rather than a stripe that contradicts the fill.
    val bufferedFrac = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(frac, 1f) else frac
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onKeyEvent { e ->
                // Physical by design: left rewinds and right advances media time in every locale.
                if (e.type == KeyEventType.KeyDown) when (e.key) {
                    Key.DirectionLeft -> { onSeek(-stepMs); true }
                    Key.DirectionRight -> { onSeek(stepMs); true }
                    else -> false
                } else false
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(if (focused) 6.dp else 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (focused) 0.4f else 0.22f))) {
            Box(Modifier.fillMaxWidth(bufferedFrac).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.28f)))
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(50)).background(OwnTVTheme.colors.accentOnVideo))
        }
        if (focused) {
            Playhead(frac)
            // Time-remaining bubble above the thumb (elapsed is shown at the bar's left, total at the right,
            // so the bubble shows what's LEFT: "-12:34"). Uses a negative offset (not bottom padding) so it
            // floats clear above the 24dp-tall bar — padding can't lift it out of the height-constrained parent.
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier.offset(y = (-32).dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        stringResource(R.string.player_time_remaining, formatTime((durationMs - positionMs).coerceAtLeast(0))),
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
                        color = Color.White,
                    )
                }
            }
        }
    }
    }
}

internal const val LIVE_SCRUB_STEP_SEC = 60     // per Left/Right press (hold to scrub fast); buttons stay 30 s

/** Scrubbable live timeline for a catch-up channel: spans the last [LIVE_WINDOW_SEC] up to the live edge.
 *  Left = back in time, Right = toward live; the thumb is the watched point and the gap to the red LIVE dot
 *  on the right is how far behind live you are. Holding a key scrubs freely; the archive loads when you
 *  settle (the VM debounces). Going past the window keeps working via the ⏪ button — the bar just pins left. */
@Composable
internal fun LiveTimelineBar(
    offsetSec: Int,
    programmes: List<LiveProgramme>,
    liveEdgeMs: Long,
    onScrub: (Int) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frac = offsetFrac(offsetSec) // 1 = live edge, 0 = far edge
    // Keyed on the minute, not the raw instant: the live edge advances every second, and no tick on a
    // two-hour bar can move visibly in less than that.
    val ticks = remember(programmes, liveEdgeMs / 60_000L) { liveTicks(programmes, liveEdgeMs) }
    val here = remember(programmes, liveEdgeMs / 60_000L, offsetSec) { programmeAt(programmes, liveEdgeMs, offsetSec) }
    val clock = rememberSystemTimeFormatter()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onKeyEvent { e ->
                // Physical by design: left moves away from live; right moves toward the live edge.
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
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(50)).background(OwnTVTheme.colors.accentOnVideo))
        }
        // Guide boundaries, so the two hours read as programmes rather than as an undivided smear.
        // The start of the one you are actually watching is brighter — that is the edge you rewind to
        // when you want a programme from the beginning. Drawn by filling the fraction of the bar up to
        // the boundary and hanging the tick off that box's end, the same placement [Playhead] uses.
        // Matched on position, not on title: a channel that runs the same show twice inside the window
        // would otherwise light up both of its boundaries.
        val hereStartFrac = here?.let { offsetFrac(((liveEdgeMs - it.startMs) / 1000L).toInt()) }
        ticks.forEach { tick ->
            if (tick.startFrac <= 0f || tick.startFrac >= 1f) return@forEach
            val current = tick.startFrac == hereStartFrac
            Box(Modifier.fillMaxWidth(tick.startFrac), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier.width(if (current) 2.dp else 1.dp).height(if (focused) 12.dp else 10.dp)
                        .background(Color.White.copy(alpha = if (current) 0.85f else 0.35f)),
                )
            }
        }
        // Live-edge marker at the far right — the same lit mark the badge and the Go live pill carry.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            OwnTVIcon(OwnTVIcon.LIVE_DOT, tint = Color(0xFFFF4D4D), filled = true, modifier = Modifier.size(14.dp))
        }
        if (focused) {
            Playhead(frac)
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.padding(bottom = 30.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    // What you are scrubbing INTO, by name — "20:54 · Premier League" says far more than
                    // "−12:04 behind" about whether to stop here. Falls back to the bare offset wherever
                    // the guide has nothing for this channel.
                    val atClock = clock(liveEdgeMs - offsetSec.coerceAtLeast(0) * 1000L)
                    Text(
                        when {
                            here != null -> stringResource(R.string.player_live_scrub_at, atClock, here.title)
                            offsetSec <= 1 -> stringResource(R.string.player_live)
                            else -> stringResource(R.string.player_live_offset, mmss(offsetSec))
                        },
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                }
            }
        }
    }
    }
}
