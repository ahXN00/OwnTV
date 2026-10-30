package tv.own.owntv.features.shell.components

import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.settings.SettingsRepository.NavLength
import tv.own.owntv.core.settings.SettingsRepository.NavSize
import tv.own.owntv.ui.components.BrandMark
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.Wordmark
import tv.own.owntv.ui.components.rememberAppliedIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.drawBoxShadow
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.ownTvTween
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.text.NumberFormat

/** What the Stage rail shows right now: the Navigation setting plus where focus is (see OwnTVShell). */
enum class RailState {
    /** A 7 px accent glow at the screen edge; the items stay focusable so ◀ can still reach them. */
    HIDDEN,
    /** The 84 px icon capsule: Floating at rest, or Size = Compact. */
    CAPSULE,
    /** The chosen Size over a scrim: the floating rail while it holds focus. */
    OPEN,
    /** The chosen Size without the scrim: Navigation = Docked. */
    PINNED,
}

/**
 * What the rail's "Now Playing" item shows: the docked channel's logo, or the equalizer while Audio
 * Mode is running (there is no picture to stand for then).
 */
data class NowPlayingRail(val logoUrl: String?, val audioMode: Boolean, val playing: Boolean)

/** The rail order: Search first, Sports then the Guide right after Live TV, More last. */
private val StageRailOrder = listOf(
    MainSection.SEARCH, MainSection.HOME, MainSection.LIVE_TV, MainSection.SPORTS, MainSection.EPG,
    MainSection.MOVIES, MainSection.SERIES, MainSection.DOWNLOADS,
)

/** The rail's width with names: Normal 280 · Wide 340 · Extra wide 400; Compact is the 84 capsule. */
private val NavSize.openWidth get() = when (this) {
    NavSize.COMPACT -> 84
    NavSize.NORMAL -> 280
    NavSize.WIDE -> 340
    NavSize.EXTRA_WIDE -> 400
}

/** The space a docked rail takes from the content, from the screen edge: 28 + 84 capsule, 24 + width with names. */
fun railDockedReserve(size: NavSize) =
    if (size == NavSize.COMPACT) (28 + 84 + 16).mpx else (24 + size.openWidth + 16).mpx

/**
 * Where content starts below the top-right cluster (34 + its 68 height + a gap) on screens that have not
 * been redrawn for Stage yet. Each screen's own phase takes this away as it gets the full canvas.
 */
val StageContentTop = 110.mpx

/**
 * The Stage navigation rail: a floating glass capsule, the rail at its
 * [size] over a scrim or docked, or only the accent glow at the edge. [count] is the number beside an
 * item (Wide and up) and [detail] the line under its name (Extra wide), null for none. [length] Full
 * runs the rail the height of the screen. Entering the rail from the content always lands on the
 * selected section, and ▶ out of it returns to exactly where the content was ([contentFocusRequester]).
 */
@Composable
fun StageRail(
    state: RailState,
    size: NavSize,
    length: NavLength,
    selected: MainSection,
    visibleSections: Set<MainSection>,
    onSelect: (MainSection) -> Unit,
    count: (MainSection) -> Int?,
    detail: @Composable (MainSection) -> String?,
    profileName: String,
    profileLine: String,
    onSwitchProfile: () -> Unit,
    onPickAvatar: () -> Unit,
    /** The profile's drawn avatar (-1 = none) and its own picture (blank = none). */
    avatarId: Int = -1,
    avatarPath: String = "",
    selectedItemFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    /** A screen's own way in, when it has one (Home returns to the exact control left from). */
    enterContent: (() -> Boolean)? = null,
    onFocused: () -> Unit,
    nowPlaying: NowPlayingRail?,
    onNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = (state == RailState.OPEN || state == RailState.PINNED) && size != NavSize.COMPACT
    val counts = size >= NavSize.WIDE
    val details = size == NavSize.EXTRA_WIDE
    val full = length == NavLength.FULL
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    // Search, Settings and More all land on a rail item: Search has its own, Settings lives behind More.
    val focusSection = when {
        selected == MainSection.SETTINGS || selected == MainSection.MORE -> MainSection.MORE
        selected == MainSection.SEARCH || selected in visibleSections -> selected
        else -> StageRailOrder.firstOrNull { it in visibleSections } ?: MainSection.MORE
    }
    val items = StageRailOrder.filter { it == MainSection.SEARCH || it in visibleSections } + MainSection.MORE

    // Slide in from the edge (320 ms, or a snap with animations off) when the rail leaves HIDDEN. A rail
    // that opens from the capsule already on screen widens in place instead: sliding it in from off-screen
    // made the capsule vanish and come back, a visible double take.
    val slide = remember { Animatable(0f) }
    val slideSpec: androidx.compose.animation.core.AnimationSpec<Float> = ownTvTween(320)
    LaunchedEffect(open, state == RailState.HIDDEN) {
        slide.animateTo(if (state == RailState.HIDDEN) -1f else 0f, slideSpec)
    }
    val railWidth by animateDpAsState(
        targetValue = if (open) size.openWidth.mpx else 84.mpx,
        animationSpec = ownTvTween(320),
    )
    // Names stay laid out until the width has closed back to the capsule, so collapsing mirrors opening
    // instead of re-centring the icons in a rail that is still wide.
    val shown = open || railWidth > 84.mpx
    // Names fade in over the second half of the widening (and out over the first half of the narrowing),
    // so a rail too narrow for them never shows them squeezed down to an ellipsis.
    val namesAlpha = ((railWidth - 84.mpx) / (size.openWidth - 84).coerceAtLeast(1).mpx * 2f - 1f).coerceIn(0f, 1f)

    Box(modifier.fillMaxSize()) {
        if (state == RailState.OPEN && open) {
            Box(Modifier.fillMaxSize().background(StageColors.Scrim))
        }
        if (state == RailState.HIDDEN) RailEdgeGlow(Modifier.align(Alignment.CenterStart))

        val width = railWidth
        val edge = if (shown) 24.mpx else 28.mpx
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = edge)
                .then(if (full) Modifier.padding(vertical = 24.mpx).fillMaxHeight() else Modifier)
                .width(width)
                .graphicsLayer {
                    val travel = (width + edge).toPx()
                    translationX = slide.value * travel * (if (rtl) -1f else 1f)
                }
                // Fades as it slides out, so its shadow never lingers at the edge once hidden.
                .alpha(if (state == RailState.HIDDEN) (1f + slide.value).coerceIn(0f, 1f) else 1f)
                .onFocusChanged {
                    // Every entry from the content lands on the selected section, not on whatever item
                    // happens to line up; moves inside the rail do not re-trigger this.
                    val entered = it.hasFocus && !hasFocus
                    hasFocus = it.hasFocus
                    if (it.hasFocus) onFocused()
                    if (entered) scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
                }
                .focusProperties {
                    onExit = {
                        val out = if (rtl) FocusDirection.Left else FocusDirection.Right
                        if (requestedFocusDirection == out && enterContent != null) {
                            // Focus cannot be moved from inside a focus change: stop this one, and let
                            // the screen place focus itself on the next frame.
                            cancelFocusChange()
                            scope.launch {
                                withFrameNanos { }
                                if (!enterContent()) runCatching { contentFocusRequester.requestFocus() }
                            }
                        } else if (requestedFocusDirection == out && runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)) {
                            cancelFocusChange()
                        }
                    }
                }
                // ▶ always leaves for the content. The open rail can cover the content's left
                // column (More's sheet), and then the D-pad search finds nothing to its right and focus
                // simply stayed in the rail.
                .onKeyEvent { e ->
                    val out = if (rtl) Key.DirectionLeft else Key.DirectionRight
                    if (e.key != out) return@onKeyEvent false
                    if (e.type == KeyEventType.KeyDown) scope.launch {
                        if (enterContent?.invoke() != true) runCatching { contentFocusRequester.requestFocus() }
                    }
                    true
                }
                .focusGroup()
                .stageGlass(if (shown) 34.mpx else 42.mpx, overContent = true)
                .padding(
                    horizontal = if (shown) 16.mpx else 0.mpx,
                    vertical = if (shown) 22.mpx else 16.mpx,
                ),
            horizontalAlignment = if (shown) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.mpx),
        ) {
            RailBrand(shown, namesAlpha)
            if (nowPlaying != null) {
                RailItem(
                    icon = if (nowPlaying.audioMode) OwnTVIcon.EQ else OwnTVIcon.PLAY,
                    label = stringResource(R.string.shell_now_playing),
                    trailing = if (nowPlaying.audioMode && counts) stringResource(R.string.shell_now_playing_audio) else null,
                    detail = null,
                    open = shown,
                    namesAlpha = namesAlpha,
                    active = false,
                    accentIcon = true,
                    onClick = onNowPlaying,
                )
                RailSeparator(shown)
            }
            items.forEach { section ->
                RailItem(
                    icon = section.stageIcon,
                    label = stringResource(section.labelRes),
                    trailing = if (counts) count(section)?.let { NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(it) } else null,
                    detail = if (details) detail(section) else null,
                    open = shown,
                    namesAlpha = namesAlpha,
                    active = section == selected ||
                        (section == MainSection.MORE && selected == MainSection.SETTINGS),
                    onClick = { onSelect(section) },
                    modifier = if (section == focusSection) Modifier.focusRequester(selectedItemFocusRequester) else Modifier,
                )
            }
            // Full height: the profile sits at the bottom, after the last separator.
            if (full) Spacer(Modifier.weight(1f))
            RailSeparator(shown)
            RailProfile(shown, namesAlpha, profileName, profileLine.takeIf { counts }, avatarId, avatarPath, onSwitchProfile, onPickAvatar)
        }
    }
}

private val MainSection.stageIcon: OwnTVIcon
    get() = when (this) {
        MainSection.SEARCH -> OwnTVIcon.SEARCH
        MainSection.HOME -> OwnTVIcon.HOME
        MainSection.LIVE_TV -> OwnTVIcon.LIVE_TV
        MainSection.EPG -> OwnTVIcon.EPG
        MainSection.SPORTS -> OwnTVIcon.SPORTS
        MainSection.MOVIES -> OwnTVIcon.MOVIES
        MainSection.SERIES -> OwnTVIcon.SERIES
        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS
        MainSection.MORE, MainSection.SETTINGS -> OwnTVIcon.TILES
    }

/** `.edge`: 7 × 150 at the screen edge, accent, glowing 22 px at 80% and 60 px at 35%, 90% opaque. */
@Composable
private fun RailEdgeGlow(modifier: Modifier) {
    val accent = stageAccent.accent
    Box(
        modifier
            .size(7.mpx, 150.mpx)
            .alpha(0.9f)
            .drawBehind {
                val r = 7.mpx.toPx()
                drawBoxShadow(accent.copy(alpha = 0.35f), 60.mpx.toPx(), r)
                drawBoxShadow(accent.copy(alpha = 0.8f), 22.mpx.toPx(), r)
                // border-radius: 0 7 7 0 — square against the screen edge, round on the inside.
                drawRoundRect(accent, cornerRadius = CornerRadius(r))
                drawRect(accent, size = size.copy(width = size.width / 2f))
            },
    )
}

/** The mark (50), with the wordmark beside it when the rail is open (`.brandrow`, 0 10 18 padding). */
@Composable
private fun ColumnScope.RailBrand(open: Boolean, namesAlpha: Float) {
    val icon = rememberAppliedIcon()
    if (!open) {
        BrandMark(icon, 50.mpx, Modifier.padding(bottom = 12.mpx))
        return
    }
    Row(
        Modifier.padding(start = 10.mpx, end = 10.mpx, bottom = 18.mpx),
        horizontalArrangement = Arrangement.spacedBy(14.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(icon, 50.mpx)
        // The wordmark, 130 wide (`.brandrow`, `wordmarkC()`).
        Box(Modifier.alpha(namesAlpha)) { Wordmark(130.mpx) }
    }
}

@Composable
private fun RailSeparator(open: Boolean) {
    Box(
        Modifier
            .then(if (open) Modifier.fillMaxWidth().padding(horizontal = 14.mpx, vertical = 10.mpx) else Modifier.padding(vertical = 8.mpx).width(34.mpx))
            .height(1.mpx)
            .background(StageColors.Ink.copy(alpha = 0.12f)),
    )
}

/**
 * `.frail .it`: a 58 × 58 icon tile in the capsule, a 60 high row with its label, [detail] line under it
 * and count when open.
 * Active = accent icon on accent 14%, plus the glowing dot outside the capsule; focused = FILLED.
 */
@Composable
private fun RailItem(
    icon: OwnTVIcon,
    label: String,
    trailing: String?,
    detail: String?,
    open: Boolean,
    namesAlpha: Float,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentIcon: Boolean = false,
) {
    val a = stageAccent
    val r = 20.mpx
    StageSurface(
        onClick = onClick,
        radius = r,
        // At least 60, not exactly: with a large font size the name and its detail line need more, and a
        // fixed height cut the detail line off (#7.2).
        modifier = modifier.then(if (open) Modifier.fillMaxWidth().heightIn(min = 60.mpx) else Modifier.size(58.mpx)),
        idle = if (active) Modifier.background(a.accent.copy(alpha = 0.14f), RoundedCornerShape(r)) else Modifier,
        contentAlignment = if (open) Alignment.CenterStart else Alignment.Center,
    ) { focused ->
        val iconTint = when {
            focused -> a.onAccent
            active || accentIcon -> a.accent
            else -> StageColors.NavText
        }
        if (!open) {
            OwnTVIcon(icon, iconTint, Modifier.size(27.mpx))
            if (active && !focused) RailActiveDot(Modifier.align(Alignment.CenterEnd).offset(x = 9.mpx))
            return@StageSurface
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.mpx, vertical = 6.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(icon, iconTint, Modifier.size(26.mpx))
            Column(Modifier.weight(1f).alpha(namesAlpha)) {
                Text(
                    label,
                    style = stageText(21, 600),
                    color = if (focused) a.onAccent else if (active) a.accent else StageColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    // `.dt`: 14/500 dim, 2 px under the name; on the focused (filled) row on-accent at 80%.
                    Text(
                        detail,
                        style = stageText(14, 500),
                        color = if (focused) a.onAccent.copy(alpha = 0.8f) else StageColors.Dim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.mpx),
                    )
                }
            }
            if (trailing != null) {
                Text(trailing, style = stageText(16, 600), color = if (focused) a.onAccent else StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(namesAlpha))
            }
        }
    }
}

/** `.it.on::after`: 6 px, 9 px outside the capsule's item, glowing 10 px. */
@Composable
private fun RailActiveDot(modifier: Modifier) {
    val accent = stageAccent.accent
    Box(
        modifier
            .size(6.mpx)
            .drawBehind {
                drawBoxShadow(accent, 10.mpx.toPx(), size.minDimension / 2f)
                drawCircle(accent)
            },
    )
}

/**
 * The profile: the 46 px avatar alone in the capsule, avatar + name when open, with "All playlists ·
 * switch ›" under it from Size Wide up ([line]). OK switches profile; a long press changes the avatar.
 */
@Composable
private fun RailProfile(open: Boolean, namesAlpha: Float, name: String, line: String?, avatarId: Int, avatarPath: String, onSwitchProfile: () -> Unit, onPickAvatar: () -> Unit) {
    val a = stageAccent
    val initial = name.trim().take(1).uppercase().ifEmpty { "?" }
    StageSurface(
        onClick = onSwitchProfile,
        onLongClick = onPickAvatar,
        radius = if (open) 20.mpx else 23.mpx,
        modifier = if (open) Modifier.fillMaxWidth() else Modifier.padding(top = 4.mpx).size(46.mpx),
        // Alone in the capsule the avatar covers a fill, so it takes the ring instead.
        focusStyle = if (open) StageFocus.FILLED else StageFocus.POSTER,
        contentAlignment = if (open) Alignment.CenterStart else Alignment.Center,
    ) { focused ->
        if (!open) {
            RailAvatar(initial, avatarId = avatarId, imagePath = avatarPath)
            return@StageSurface
        }
        Row(
            Modifier.padding(start = 12.mpx, end = 12.mpx, top = 10.mpx, bottom = 6.mpx),
            horizontalArrangement = Arrangement.spacedBy(14.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RailAvatar(initial, avatarId = avatarId, imagePath = avatarPath)
            Column(Modifier.alpha(namesAlpha)) {
                Text(name, style = stageText(19, 700), color = if (focused) a.onAccent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (line != null) {
                    Text("$line ›", style = stageText(15, 400), color = if (focused) a.onAccent else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * `.frail .av`: the profile's avatar or own picture in a circle; with neither, its initial on the orange
 * gradient, 20/800 in #2A0D12. More's profile row draws it 58 / 24.
 */
@Composable
internal fun RailAvatar(initial: String, size: Int = 46, textSize: Int = 20, avatarId: Int = -1, imagePath: String = "") {
    if (avatarId != -1 || imagePath.isNotBlank()) {
        tv.own.owntv.ui.components.OwnTVAvatar(avatarId = avatarId, imagePath = imagePath, modifier = Modifier.size(size.mpx).clip(CircleShape))
        return
    }
    Box(
        Modifier
            .size(size.mpx)
            .background(Brush.linearGradient(listOf(Color(0xFFFFB35C), Color(0xFFFF5F7A))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = stageText(textSize, 800), color = Color(0xFF2A0D12), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
