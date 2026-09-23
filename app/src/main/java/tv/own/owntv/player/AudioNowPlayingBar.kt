package tv.own.owntv.player

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
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.animationsOn
import tv.own.owntv.ui.theme.LocalPopupFontFamily

/**
 * The wide "now-playing" bar shown in the top bar (left of the weather chip) while [PlayerMode.AUDIO]
 * is active — Audio Mode plan §6. Video is stopped; only audio plays.
 *
 * **Two-stage focus (owner spec):**
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
    /** Focus target for "enter the audio session" from the sidebar's Now Playing item. Lands on stage 1. */
    entryFocusRequester: FocusRequester? = null,
    /** The favourite state of whatever is playing — the same channel/movie/series the fullscreen HUD toggles. */
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    /** Reported so the top strip can grow with the capsule and push the content panel down. */
    onExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
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
    LaunchedEffect(expanded) { onExpandedChange(expanded) }

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
        // --- Content (drawn in all states; buttons focusable only in stage 2) ---
        // Focus never fills or outlines the card in stage 1 — the catcher's slight scale-up is the only
        // cue. Stage 2 (active) shows the accent outline; the focused button carries its accent ring/icon.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                // AMOLED-dark card (owner spec) in dark; the same "extreme" in light = a crisp pure-white
                // card. Never a glassy tint in either theme.
                .background(if (colors.isDark) Color(0xFF080D0C) else Color(0xFFFFFFFF))
                .border(
                    width = if (active) 2.dp else 1.dp,
                    color = if (active) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(18.dp),
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // At rest the bare equalizer, exactly as before. Grown, it moves inside an artwork tile
                // that shows the station logo when there is one — on IPTV there usually is not, so the
                // tile falls back to an accent wash with the equalizer over it. It is never empty.
                if (expanded) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                            .background(colors.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val logo = meta.logoUrl
                        if (!logo.isNullOrBlank()) {
                            AsyncImage(model = logo, contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                        Equalizer(playing = isPlaying, color = colors.primary, modifier = Modifier.size(width = 22.dp, height = 16.dp))
                    }
                } else {
                    Equalizer(playing = isPlaying, color = colors.primary, modifier = Modifier.size(width = 26.dp, height = 20.dp))
                }

                Column(Modifier.widthIn(max = if (expanded) 260.dp else 150.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        meta.title ?: "",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().then(
                            if (hasFocus) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                        ),
                    )
                    // The station line: what is playing under the title, then whether it is live or how
                    // far through it you are. The dot between them is drawn, not typed, so the line needs
                    // no separator string in 24 languages.
                    if (expanded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val station = meta.localizedSubtitle()
                            if (station != null) {
                                Text(
                                    station,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 130.dp),
                                )
                                StationDot(colors.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                            if (isLive) {
                                LiveRow(colors.favorite)
                            } else if (hasTime) {
                                TimeLabel({ positionState.value }, duration, colors.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Breathing room between the text block and the transport buttons (mock: buttons pushed right).
                if (expanded) Spacer(Modifier.width(14.dp))

                if (expanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AudioBtn(0, if (isLive) OwnTVIcon.SKIP_PREVIOUS else OwnTVIcon.SEEK_BACK, active && enabled[0], enabled[0], requesters, { focusedSlot = it }) {
                            if (isLive) onPrev() else player.seekBy(-seekStep)
                        }
                        // Play/pause leads the row visually: one step bigger than the rest (mock proportions).
                        AudioBtn(1, if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, active, true, requesters, { focusedSlot = it }, sizeDp = 36, iconDp = 17, glow = true) { player.togglePlayPause() }
                        AudioBtn(2, if (isLive) OwnTVIcon.SKIP_NEXT else OwnTVIcon.SEEK_FORWARD, active && enabled[2], enabled[2], requesters, { focusedSlot = it }) {
                            if (isLive) onNext() else player.seekBy(seekStep)
                        }
                        // The heart keeps its coral wherever it appears in the app, so one colour still
                        // means "favourite" here as it does on a poster.
                        AudioBtn(3, OwnTVIcon.FAVORITE, active && enabled[3], enabled[3], requesters, { focusedSlot = it }, activeTint = if (favorite) colors.favorite else null) {
                            onToggleFavorite?.invoke()
                        }
                        AudioBtn(4, if (volume <= 0) OwnTVIcon.VOLUME_MUTE else OwnTVIcon.VOLUME_HIGH, active, true, requesters, { focusedSlot = it }) { player.toggleMute() }
                        AudioBtn(5, OwnTVIcon.EXPAND, active, true, requesters, { focusedSlot = it }, onClick = onExpand)
                        // Close stops Audio Mode altogether. Back only leaves the focus trap — the two
                        // used to be the same key, which is why there was no way to end a session here.
                        AudioBtn(6, OwnTVIcon.CLOSE, active, true, requesters, { focusedSlot = it }, onClick = onClose)
                    }
                }
            }

            // The progress hairline, drawn over the card's bottom edge in every state rather than laid
            // out below the row — that way the strip at rest is exactly the height it always was.
            if (hasTime) {
                // A normal fillMaxWidth child makes this wrap-content card request the complete
                // top-bar width. Match the card after it has been measured so the hairline overlays
                // its bottom edge without stretching the capsule or pushing other chips off-screen.
                val lineColor = colors.primary
                Box(
                    Modifier.matchParentSize().drawBehind {
                        val frac = (positionState.value.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        val h = 1.5.dp.toPx()
                        val w = size.width * frac
                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                        drawRect(lineColor, topLeft = Offset(x, size.height - h), size = Size(w, h))
                    },
                )
            }
        }

        // --- Stage 1 focus catcher: the whole pill as one target. Focusable only until activated; once
        // activated it steps aside so the trapped buttons own focus. OK enters stage 2. ---
        FocusableSurface(
            onClick = { active = true },
            modifier = Modifier
                .matchParentSize()
                .focusRequester(pillFocus)
                .then(entryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusProperties { canFocus = focusable && !active },
            shape = RoundedCornerShape(18.dp),
            focusedScale = 1.03f,
            glowElevation = 0,
            // Fully invisible catcher: stage-1 focus is drawn by the card itself (top/bottom accent
            // lines above) — no fill, no glow shadow, no built-in border ("glass" look fix).
            showFocusBorder = false,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            selectedContainerColor = Color.Transparent,
        ) { _ -> }
    }
}

/** The elapsed/total label, its own scope so only it recomposes as the position ticks. */
@Composable
private fun TimeLabel(position: () -> Long, duration: Long, color: Color) {
    Text(
        stringResource(R.string.player_time_progress, fmtTime(position()), fmtTime(duration)),
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalPopupFontFamily.current),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The separator in the station line, drawn rather than typed so it needs no string in 24 languages. */
@Composable
private fun StationDot(color: Color) {
    Box(Modifier.size(3.dp).clip(CircleShape).background(color))
}

@Composable
private fun LiveRow(dotColor: Color) {
    val colors = OwnTVTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        // Animations Off: a steady dot. The pulse is not run at all (never a 0 ms infinite transition).
        if (animationsOn) PulsingDot(dotColor) else Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(stringResource(R.string.player_live), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PulsingDot(dotColor: Color) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val a by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "liveDotAlpha",
    )
    Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor).alpha(a))
}

/**
 * Bars that dance while [playing] and freeze flat when paused (Audio Mode plan §3/§6). Also stands in
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

@Composable
private fun AudioBtn(
    slot: Int,
    icon: OwnTVIcon,
    focusable: Boolean,
    enabled: Boolean,
    requesters: List<FocusRequester>,
    onFocused: (Int) -> Unit,
    sizeDp: Int = 28,
    iconDp: Int = 13,
    /** Overrides the resting tint — the favourite heart keeps its coral wherever it appears. */
    activeTint: Color? = null,
    /** Draws focus as light (rim + wash + bloom) instead of a bare ring — the play circle. */
    glow: Boolean = false,
    onClick: () -> Unit,
) {
    // White-on-dark circles; focus = accent ring (built-in focus border) + accent icon, background
    // stays dark — outline-only focus per owner spec (mini-player mock in audio-hud-options.html).
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .size(sizeDp.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .focusRequester(requesters[slot])
            .onFocusChanged { if (it.isFocused) onFocused(slot) }
            .focusProperties { canFocus = focusable && enabled },
        shape = CircleShape,
        focusedScale = 1.02f,
        glowElevation = 0,
        focusLight = if (glow) colors.primary else null,
        // Bare icons — no circle fill or glow behind them; focus shows only the accent ring + tint.
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        OwnTVIcon(
            icon,
            tint = activeTint ?: if (focused) colors.primary else if (colors.isDark) Color.White else colors.onSurface,
            filled = true,
            modifier = Modifier.size(iconDp.dp),
        )
    }
}

@Composable
private fun fmtTime(ms: Long): String = tv.own.owntv.ui.components.formatTimestamp(ms)
