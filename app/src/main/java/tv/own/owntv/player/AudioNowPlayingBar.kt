package tv.own.owntv.player

import tv.own.owntv.ui.theme.stageTone
import tv.own.owntv.ui.theme.LocalStagePalette
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.tv.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.ContentScale
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.drawBoxShadow
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.stage.stageFocusLook
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.R
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.animationsOn

/**
 * The wide "now-playing" bar shown in the top bar (left of the weather chip) while [PlayerMode.AUDIO]
 * is active. Video is stopped; only audio plays.
 *
 * **Two-stage focus:**
 *  1. **Collapsed** — no D-pad focus: equaliser cover + title + a static play/pause glyph.
 *  2. **Stage 1 — pill focused:** focus lands on the WHOLE bar as one target (highlighted, expanded to
 *     show the full row). The inner buttons are NOT individually navigable yet.
 *  3. **Stage 2 — activated:** press OK on the focused pill → focus moves inside and is **trapped**
 *     there: D-pad left/right only step between the buttons (never escaping the bar), OK runs the
 *     focused button, and **Back** is the only way out — it returns to Stage 1.
 *
 * The trap is enforced manually with [onPreviewKeyEvent] because Compose's default 2D focus search
 * would let left/right leak out to the neighbouring top-bar chips.
 *
 * The equaliser animates only while playing and freezes flat when paused, on every state. prev/next are
 * context-wired by the shell (channel zap / episode queue / disabled for a standalone movie).
 */
@Composable
fun AudioNowPlayingBar(
    player: PlaybackEngine,
    isLive: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    focusable: Boolean,
    modifier: Modifier = Modifier,
    /** Focus target for "enter the audio session" from the sidebar's Now Playing item. Lands on stage 1. */
    entryFocusRequester: FocusRequester? = null,
    /** The favourite state of whatever is playing — the same channel/movie/series the fullscreen HUD toggles. */
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val accent = stageAccent
    val layoutDirection = LocalLayoutDirection.current
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val volume by player.volume.collectAsStateWithLifecycle()
    // Held as State and read only by the time label and the hairline's draw lambda, so the
    // once-a-second tick does not recompose the whole bar.
    val positionState = player.position.collectAsStateWithLifecycle()
    val duration by player.duration.collectAsStateWithLifecycle()
    val seekStep by player.seekStepMs.collectAsStateWithLifecycle()

    var hasFocus by remember { mutableStateOf(false) } // any part of the bar holds focus (stage 1 or 2)
    var active by remember { mutableStateOf(false) }    // stage 2 — inner buttons live and trapped
    var pendingReturn by remember { mutableStateOf(false) } // Back pressed → hand focus back to the pill

    val pillFocus = remember { FocusRequester() }

    // Button slots, fixed order: 0=back 1=play 2=forward 3=favourite 4=volume 5=fullscreen 6=close.
    // Slots 0 and 2 are prev/next channel on a live stream and seek on a recording — same place, same
    // shape, different verb, because seeking a live stream means nothing. Both are only focusable when
    // there is somewhere to go; favourite only when the shell knows what to favourite.
    val slotCount = 7
    val requesters = remember { List(slotCount) { FocusRequester() } }
    val seekable = !isLive && duration > 0L
    val canBack = if (isLive) canPrev else seekable
    val canForward = if (isLive) canNext else seekable
    val enabled = booleanArrayOf(canBack, true, canForward, onToggleFavorite != null, true, true, true)
    val navSlots = (0 until slotCount).filter { enabled[it] }
    var focusedSlot by remember { mutableIntStateOf(1) } // play by default

    val expanded = (hasFocus || active) && focusable
    val hasTime = !isLive && duration > 0L

    fun moveFocus(dir: Int) {
        val pos = navSlots.indexOf(focusedSlot)
        val next = pos + dir
        if (pos >= 0 && next in navSlots.indices) {
            val slot = navSlots[next]
            focusedSlot = slot
            runCatching { requesters[slot].requestFocus() }
        }
        // out of range → do nothing: focus stays inside (trapped).
    }

    // Enter/exit stage 2: drop focus onto the play button (trapped), or hand it back to the whole pill.
    LaunchedEffect(active) {
        if (active) {
            focusedSlot = if (1 in navSlots) 1 else navSlots.first()
            runCatching { requesters[focusedSlot].requestFocus() }
        } else if (pendingReturn) {
            pendingReturn = false
            runCatching { pillFocus.requestFocus() }
        }
    }
    // Losing focusability (bar dismissed / mode left) collapses back to a single target.
    LaunchedEffect(focusable) { if (!focusable) { active = false; pendingReturn = false } }
    // Back while activated → return to the whole-pill focus (stage 1). This is the ONLY exit from stage 2.
    if (active) BackHandler { pendingReturn = true; active = false }

    Box(
        modifier = modifier
            // Just track focus. We never reset `active` here: while activated the trap below keeps focus
            // inside, so the only "focus lost" events are the transient drops during the stage-1↔2
            // hand-off — resetting on those is exactly what used to collapse the bar on OK. Real exits go
            // through Back (→ stage 1) or losing [focusable] (→ collapsed), handled explicitly below.
            .onFocusChanged { hasFocus = it.hasFocus }
            // Trap the D-pad while activated: left/right only step between buttons, up/down are swallowed,
            // and OK falls through so the focused button's own click handler runs it.
            .onPreviewKeyEvent { ev ->
                if (!active || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key.horizontalDirection(layoutDirection)) {
                    HorizontalDirection.START -> { moveFocus(-1); true }
                    HorizontalDirection.END -> { moveFocus(1); true }
                    null -> when (ev.key) {
                        Key.DirectionUp, Key.DirectionDown -> true // swallow: never escape vertically
                        else -> false
                    }
                }
            }
            .focusGroup(),
    ) {
        if (!expanded) {
            // Rest: a glass pill in the Continue pill's place — equaliser, "Now playing", title.
            Row(
                Modifier.height(50.mpx).widthIn(max = 470.mpx).stageGlass(StageRadii.Pill).padding(horizontal = 18.mpx),
                horizontalArrangement = Arrangement.spacedBy(10.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.mpx), contentAlignment = Alignment.Center) {
                    Equalizer(playing = isPlaying, color = accent.accent, modifier = Modifier.size(18.mpx, 15.mpx))
                }
                Text(stringResource(R.string.shell_now_playing), style = stageText(15, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta.title ?: "", style = stageText(18, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            // Focused: the whole card is one target, drawn FX. OK: the accent rim,
            // and focus moves into the buttons.
            val r = 22.mpx
            Row(
                Modifier
                    .height(76.mpx)
                    .then(
                        if (active) {
                            Modifier.stageGlass(r, overContent = true).drawBehind { drawInnerRing(accent.accent, 2.mpx.toPx(), r.toPx()) }
                        } else {
                            // The FX sweep is translucent; the glass under it keeps the text readable over a wallpaper.
                            Modifier.stageGlass(r, overContent = true).stageFocusLook(StageFocus.FX, r)
                        },
                    )
                    .padding(start = 12.mpx, end = 18.mpx),
                horizontalArrangement = Arrangement.spacedBy(16.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The art tile: the station logo on a white plate when there is one, else the equaliser
                // on an accent wash. It is never empty.
                val logo = meta.logoUrl
                Box(
                    Modifier.size(52.mpx).clip(RoundedCornerShape(12.mpx))
                        .background(if (logo.isNullOrBlank()) accent.accent.copy(alpha = 0.16f) else Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!logo.isNullOrBlank()) {
                        AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.mpx))
                    } else {
                        Equalizer(playing = isPlaying, color = accent.accent, modifier = Modifier.size(22.mpx, 18.mpx))
                    }
                }
                Column(Modifier.width(260.mpx)) {
                    Text(
                        meta.title ?: "",
                        style = stageText(19, 700),
                        color = StageColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().then(
                            if (hasFocus) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                        ),
                    )
                    Spacer(Modifier.height(3.mpx))
                    // The station line: what is playing, then LIVE; or how far through a recording you are.
                    // The dot between them is drawn, not typed, so it needs no separator string.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                        if (isLive) {
                            meta.localizedSubtitle()?.let { station ->
                                Text(station, style = stageText(15, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                StationDot(StageColors.Muted.copy(alpha = 0.5f))
                            }
                            LiveRow()
                        } else if (hasTime) {
                            TimeLabel({ positionState.value }, duration)
                        }
                    }
                }
                Row(
                    Modifier.padding(start = 10.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.mpx),
                ) {
                    AudioBtn(0, if (isLive) OwnTVIcon.SKIP_PREVIOUS else OwnTVIcon.SEEK_BACK, active && enabled[0], enabled[0], requesters, { focusedSlot = it }) {
                        if (isLive) onPrev() else player.seekBy(-seekStep)
                    }
                    // Play/pause leads the row: one step bigger, on its own white-10% disc.
                    AudioBtn(1, if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, active, true, requesters, { focusedSlot = it }, big = true) { player.togglePlayPause() }
                    AudioBtn(2, if (isLive) OwnTVIcon.SKIP_NEXT else OwnTVIcon.SEEK_FORWARD, active && enabled[2], enabled[2], requesters, { focusedSlot = it }) {
                        if (isLive) onNext() else player.seekBy(seekStep)
                    }
                    // The heart keeps its coral wherever it appears in the app, so one colour still
                    // means "favourite" here as it does on a poster.
                    AudioBtn(3, OwnTVIcon.FAVORITE, active && enabled[3], enabled[3], requesters, { focusedSlot = it }, favorite = favorite) {
                        onToggleFavorite?.invoke()
                    }
                    AudioBtn(4, if (volume <= 0) OwnTVIcon.VOLUME_MUTE else OwnTVIcon.VOLUME_HIGH, active, true, requesters, { focusedSlot = it }) { player.toggleMute() }
                    AudioBtn(5, OwnTVIcon.EXPAND, active, true, requesters, { focusedSlot = it }, onClick = onExpand)
                    // Close stops Audio Mode altogether. Back only leaves the focus trap — the two
                    // used to be the same key, which is why there was no way to end a session here.
                    AudioBtn(6, OwnTVIcon.CLOSE, active, true, requesters, { focusedSlot = it }, onClick = onClose)
                }
            }
            // The progress hairline along the card's bottom edge (recordings only), inset 22.
            if (hasTime) {
                val lineColor = accent.accent
                val track = StageColors.Ink.copy(alpha = 0.12f)
                Box(
                    Modifier.matchParentSize().drawBehind {
                        val inset = 22.mpx.toPx()
                        val h = 3.mpx.toPx()
                        val w = size.width - 2 * inset
                        val frac = (positionState.value.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        val top = size.height - h
                        val corner = CornerRadius(2.mpx.toPx())
                        drawRoundRect(track, Offset(inset, top), Size(w, h), corner)
                        val x = if (layoutDirection == LayoutDirection.Rtl) inset + w * (1 - frac) else inset
                        drawRoundRect(lineColor, Offset(x, top), Size(w * frac, h), corner)
                    },
                )
            }
        }

        // --- Stage 1 focus catcher: the whole bar as one target. Focusable only until activated; once
        // activated it steps aside so the trapped buttons own focus. OK enters stage 2. ---
        Box(
            Modifier
                .matchParentSize()
                .focusRequester(pillFocus)
                .then(entryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusProperties { canFocus = focusable && !active }
                .clickable(remember { MutableInteractionSource() }, null) { active = true },
        )
    }
}

/** The elapsed/total label, its own scope so only it recomposes as the position ticks. */
@Composable
private fun TimeLabel(position: () -> Long, duration: Long) {
    Text(
        stringResource(R.string.player_time_progress, fmtTime(position()), fmtTime(duration)),
        style = stageText(15, 600).copy(fontFeatureSettings = "tnum"),
        color = StageColors.Muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The separator in the station line, drawn rather than typed so it needs no string in 24 languages. */
@Composable
private fun StationDot(color: Color) {
    Box(Modifier.size(4.mpx).clip(CircleShape).background(color))
}

/** "● LIVE": a red 7 px dot with an 8 px glow, then LIVE in 800. */
@Composable
private fun LiveRow() {
    val red = Color(0xFFFF5A4F)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
        // Animations Off: a steady dot. The pulse is not run at all (never a 0 ms infinite transition).
        val alpha = if (animationsOn) pulse() else 1f
        Box(
            Modifier.size(7.mpx).alpha(alpha).drawBehind {
                drawBoxShadow(red, 8.mpx.toPx(), size.minDimension / 2f)
                drawCircle(red)
            },
        )
        Text(stringResource(R.string.player_live), style = stageText(15, 800), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun pulse(): Float {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val a by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "liveDotAlpha",
    )
    return a
}

/**
 * Bars that dance while [playing] and freeze flat when paused. Also stands in
 * for the missing picture on the sidebar's Now Playing item while Audio Mode is running.
 */
@Composable
internal fun Equalizer(playing: Boolean, color: Color, modifier: Modifier) {
    // Paused, or Animations Off: flat bars and no transition running at all (never a 0 ms infinite one).
    if (playing && animationsOn) DancingEqualizer(color, modifier) else EqualizerBars(color, modifier) { 0.18f }
}

@Composable
private fun DancingEqualizer(color: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = (0 until EQ_BARS).map { i ->
        transition.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420 + i * 90, easing = LinearEasing), RepeatMode.Reverse),
            label = "eqBar$i",
        )
    }
    EqualizerBars(color, modifier) { heights[it].value }
}

private const val EQ_BARS = 5

@Composable
private fun EqualizerBars(color: Color, modifier: Modifier, heightOf: (Int) -> Float) {
    val bars = EQ_BARS
    Canvas(modifier) {
        val gap = size.width * 0.12f
        val barW = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = heightOf(i)
            val bh = size.height * h
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), size.height - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

/**
 * `.audc .ab`: a 44 px round button (52 on a white-10% disc for play/pause), icon 21 (24). Focused =
 * FILLED. Shown in the focused state but only focusable once the bar is activated.
 */
@Composable
private fun AudioBtn(
    slot: Int,
    icon: OwnTVIcon,
    focusable: Boolean,
    enabled: Boolean,
    requesters: List<FocusRequester>,
    onFocused: (Int) -> Unit,
    big: Boolean = false,
    /** The favourite heart: coral and solid while the item is a favourite. */
    favorite: Boolean = false,
    onClick: () -> Unit,
) {
    val a = stageAccent
    val colors = OwnTVTheme.colors
    val d = if (big) 52.mpx else 44.mpx
    StageSurface(
        onClick = { if (enabled) onClick() },
        radius = d / 2,
        modifier = Modifier
            .size(d)
            .alpha(if (enabled) 1f else 0.35f)
            .focusRequester(requesters[slot])
            .onFocusChanged { if (it.isFocused) onFocused(slot) }
            .focusProperties { canFocus = focusable && enabled },
        // `.audc .ab.big`: ink 10% (8% in light).
        idle = if (big) Modifier.background(StageColors.Ink.copy(alpha = if (LocalStagePalette.current.isLight) 0.08f else 0.10f), CircleShape) else Modifier,
        contentAlignment = Alignment.Center,
    ) { focused ->
        OwnTVIcon(
            icon,
            tint = when {
                focused -> a.onAccent
                favorite -> colors.favorite
                else -> stageTone(Color(0xFFE6EEEA), StageColors.ItemText)
            },
            filled = favorite || icon == OwnTVIcon.PLAY,
            modifier = Modifier.size(if (big) 24.mpx else 21.mpx),
        )
    }
}

@Composable
private fun fmtTime(ms: Long): String = tv.own.owntv.ui.components.formatTimestamp(ms)
