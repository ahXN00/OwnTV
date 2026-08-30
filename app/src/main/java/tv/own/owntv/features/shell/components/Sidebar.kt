package tv.own.owntv.features.shell.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.features.shell.MainSection
import tv.own.owntv.R
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.NavAccentBar
import tv.own.owntv.ui.components.rememberNavLadderColors
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.NavDuotoneIcon
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.RailPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.animationsOn
import tv.own.owntv.ui.theme.glass

/**
 * Layer 1 — the MD3 navigation panel. A FIXED icon rail: brand logo at the top (Phase 2), the nav items
 * (browse + Settings) vertically centered in the middle (Phase 3), and the profile avatar pinned at the
 * bottom (Phase 1). The logo is display-only (not focusable); everything else is a focusable nav item.
 */
@Composable
fun Sidebar(
    selected: MainSection,
    onSelect: (MainSection) -> Unit,
    visibleSections: Set<MainSection>,
    avatarId: Int,
    onPickAvatar: () -> Unit,
    profileName: String,
    sourceSummary: String?,
    onSwitchProfile: () -> Unit,
    selectedItemFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    counts: (MainSection) -> Int = { 0 },
    topInset: Dp = Dimens.TopBarHeight,
    /** Non-null while a mini player or an audio session is alive — see [NowPlayingRail]. */
    nowPlaying: NowPlayingRail? = null,
    onNowPlaying: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    var hasFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Phase 2 — the nav is a FIXED icon rail: it never expands or collapses, so the layout never jumps on
    // the D-pad. The profile avatar is pinned at the bottom (Phase 1). Full section labels live in the panes.
    val expanded = false
    // Phase 4 — Search left the rail for the top bar, so when Search is the active section there's no nav
    // item to receive the entry-focus redirect below. Fall back to Home so BACK / left out of Search still
    // lands in the rail instead of stranding focus in the content area.
    // v4.3.0 — the selected section may also be hidden (Nav menu customization); if so, land focus on the
    // first visible browse item (or Settings if every browse item is hidden) so the rail always has a target.
    val focusSection = when {
        selected == MainSection.SEARCH -> MainSection.HOME
        selected == MainSection.SETTINGS -> MainSection.SETTINGS
        selected in visibleSections -> selected
        else -> MainSection.browseOrder.firstOrNull { it in visibleSections } ?: MainSection.SETTINGS
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged {
                // D-pad focus search is spatial — entering the panel from the content area would
                // land on whatever item happens to be horizontally aligned. Redirect every entry to
                // the SELECTED section instead (internal up/down moves don't re-trigger this).
                // Deferred a frame: requesting focus inside onFocusChanged is rejected mid-transaction.
                val entered = it.hasFocus && !hasFocus
                hasFocus = it.hasFocus
                if (it.hasFocus) onFocused()
                if (entered) scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
            }
            .focusGroup()
            .width(Dimens.SidebarWidthCollapsed)
            // The top bar owns the complete top strip. The plate starts below it and shares the main
            // content panel's 6 dp bottom inset; the horizontal inset keeps the existing shell gap.
            .padding(start = 6.dp, top = topInset, end = 6.dp, bottom = 6.dp)
            .roundedPanel(fillColor = RailPanelFill, surface = GlassSurface.SIDEBAR)
            // Keep the plate aligned while lowering the logo slightly inside it.
            .padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Phase 2 — brand mark pinned at the top of the rail. Non-focusable, so D-pad entry into the
        // panel still redirects to the selected nav item below (see onFocusChanged) — it can't trap
        // an "up" press from the first nav item either.
        AppLogo()
        Spacer(Modifier.height(12.dp))

        // The one way back into a docked mini player from every screen (§8 tier 1). Sits directly under
        // the brand mark, above the browse block, and only exists while there is something to return to.
        // It is NOT a MainSection: OK moves focus into the mini window instead of navigating anywhere,
        // so Nav menu customization neither hides it nor counts it.
        if (nowPlaying != null) {
            NowPlayingItem(state = nowPlaying, onClick = onNowPlaying)
            Spacer(Modifier.height(10.dp))
        }

        // Phase 3 — the nav is vertically centered between the logo and the profile, BUT the rail
        // scrolls when UI zoom makes the fixed item list taller than the panel (otherwise the top/bottom
        // items get clipped and become unreachable). A scrollable Box with Center alignment centers the
        // nav when it fits and pans through it when it overflows; Compose brings each item into view as
        // D-pad focus moves to it.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            if (expanded) {
                SectionLabel(stringResource(R.string.common_browse))
                Spacer(Modifier.height(4.dp))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                MainSection.browseOrder.filter { it in visibleSections }.forEach { section ->
                    NavItem(
                        section = section,
                        active = section == selected,
                        expanded = expanded,
                        count = counts(section),
                        onClick = { onSelect(section) },
                        modifier = if (section == focusSection) {
                            Modifier.focusRequester(selectedItemFocusRequester)
                        } else Modifier,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                // Settings closes out the nav block.
                NavItem(
                    section = MainSection.SETTINGS,
                    active = selected == MainSection.SETTINGS,
                    expanded = expanded,
                    count = 0,
                    onClick = { onSelect(MainSection.SETTINGS) },
                    modifier = if (focusSection == MainSection.SETTINGS) {
                        Modifier.focusRequester(selectedItemFocusRequester)
                    } else Modifier,
                )
            }
        }

        // Phase 1 — profile relocated to the bottom of the rail (was top). A divider groups the account
        // avatar apart from the nav items above it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(colors.outlineVariant),
        )
        ProfileCard(
            expanded = expanded,
            avatarId = avatarId,
            profileName = profileName,
            sourceSummary = sourceSummary,
            onPickAvatar = onPickAvatar,
            onSwitchProfile = onSwitchProfile,
        )
    }
}

/**
 * What the rail's "Now Playing" item shows: the docked channel's logo, or the equalizer while Audio
 * Mode is running (there is no picture to stand for then).
 */
data class NowPlayingRail(val logoUrl: String?, val audioMode: Boolean, val playing: Boolean)

/**
 * The Now Playing rail item — a logo tile inside a pulsing accent ring, with the label below it.
 *
 * The ring pulses so a docked window that is otherwise off in a corner announces itself; the pulse is
 * dropped entirely when the user has turned animations off, leaving a static ring at the same weight.
 */
@Composable
private fun NowPlayingItem(
    state: NowPlayingRail,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(13.dp)
    val pulse = if (animationsOn) {
        val transition = rememberInfiniteTransition(label = "nowPlayingRing")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
            label = "nowPlayingRingAlpha",
        ).value
    } else {
        0.7f
    }
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        surface = GlassSurface.SIDEBAR,
        showFocusBorder = false,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(39.dp)
                    .clip(shape)
                    .glass(surface = GlassSurface.SIDEBAR, baseFill = Color.Transparent, shape = shape)
                    .background(
                        if (focused) colors.primary else colors.primary.copy(alpha = 0.18f),
                        shape,
                    )
                    .border(
                        width = if (focused) 2.dp else 1.5.dp,
                        color = if (focused) colors.onPrimary else colors.primary.copy(alpha = pulse),
                        shape = shape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val content = if (focused) colors.onPrimary else colors.primary
                when {
                    state.audioMode -> tv.own.owntv.player.Equalizer(
                        playing = state.playing,
                        color = content,
                        modifier = Modifier.width(22.dp).height(16.dp),
                    )
                    !state.logoUrl.isNullOrBlank() -> AsyncImage(
                        model = state.logoUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.size(26.dp),
                    )
                    else -> OwnTVIcon(OwnTVIcon.PLAY, tint = content, filled = true, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                stringResource(R.string.shell_now_playing),
                style = MaterialTheme.typography.labelSmall,
                color = if (focused) colors.onSurface else colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

/**
 * Brand mark at the top of the rail — the cyan play-triangle inside a rounded-square outline, the same
 * geometry as [ic_launcher_foreground] (kept consistent with the planned branded splash). Drawn from
 * [OwnTVIcon.PLAY] (filled) inside an outlined [Box] so it matches the visual weight of the 56dp avatar
 * below. Decorative only: a plain Box is not focusable, so it neither captures D-pad focus nor traps an
 * "up" press. Tints with [OwnTVTheme.colors].primary so it follows the user's accent like the nav icons.
 */
@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(width = 2.dp, color = colors.primary, shape = RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        OwnTVIcon(icon = OwnTVIcon.PLAY, tint = colors.primary, modifier = Modifier.size(26.dp), filled = true)
    }
}

@Composable
private fun ProfileCard(
    expanded: Boolean,
    avatarId: Int,
    profileName: String,
    sourceSummary: String?,
    onPickAvatar: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val sourceLabel = sourceSummary ?: stringResource(R.string.shell_no_source)

    if (!expanded) {
        // Fixed nav: just the avatar — click opens the profile switcher ("who's watching"), long-press
        // changes the avatar picture. Pinned top-left, always in the same spot.
        AvatarButton(avatarId = avatarId, sizeDp = 56, onClick = onSwitchProfile, onLongClick = onPickAvatar)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(colors.primaryContainer.copy(alpha = 0.45f)),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarButton(avatarId = avatarId, sizeDp = 64, onClick = onPickAvatar)
            Spacer(Modifier.height(10.dp))
            Text(
                profileName.ifBlank { stringResource(R.string.common_own_tv_user) },
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                sourceLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            // Switch profile without quitting the app (routes back to the "Who's watching?" gate).
            FocusableSurface(
                onClick = onSwitchProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainer,
                contentAlignment = Alignment.Center,
                surface = GlassSurface.SIDEBAR,
            ) { focused ->
                val c = if (focused) colors.onSurface else colors.onSurfaceVariant
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OwnTVIcon(icon = OwnTVIcon.PERSON, tint = c, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.common_switch_profile),
                        style = MaterialTheme.typography.labelLarge,
                        color = c,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).then(
                            if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarButton(avatarId: Int, sizeDp: Int, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.size(sizeDp.dp),
        shape = CircleShape,
        focusedScale = 1.02f,
        focusedContainerColor = OwnTVTheme.colors.surfaceContainerHighest,
        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentAlignment = Alignment.Center,
        surface = GlassSurface.SIDEBAR,
    ) { _ ->
        OwnTVAvatar(avatarId = avatarId, modifier = Modifier.size((sizeDp - 4).dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OwnTVTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
    )
}

@Composable
private fun NavItem(
    section: MainSection,
    active: Boolean,
    expanded: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    // Approved compact beacon: the focus owner still spans the rail for reliable D-pad targeting,
    // while the visible selection is a centered 48 dp tile with its own marker and soft halo.
    val shape = RoundedCornerShape(13.dp)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = active,
        shape = shape,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        // The visible fill is rendered by the inner nav ladder, but the outer focus owner still
        // needs to know this is glass so it does not create a scale/shadow layer while scrolling.
        surface = GlassSurface.SIDEBAR,
        showFocusBorder = false,
        renderSelectionContainer = false,
        contentAlignment = Alignment.Center,
    ) { focused ->
        val ladder = rememberNavLadderColors(
            selected = active,
            focused = focused,
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // A separate 3 dp marker stays readable after focus moves away, in solid and glass modes.
            NavAccentBar(visible = ladder.showAccentBar, height = 22.dp)
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(39.dp)
                    .then(
                        if (active) Modifier.shadow(
                            elevation = 6.dp,
                            shape = shape,
                            ambientColor = colors.primary.copy(alpha = 0.30f),
                            spotColor = colors.primary.copy(alpha = 0.30f),
                            clip = false,
                        ) else Modifier
                    )
                    .clip(shape)
                    // Material follows the active mode; compact geometry stays identical in both.
                    .glass(surface = GlassSurface.SIDEBAR, baseFill = ladder.container, shape = shape)
                    .then(
                        if (active) Modifier.background(
                            Brush.linearGradient(
                                listOf(
                                    colors.primary.copy(alpha = 0.64f),
                                    colors.primaryContainer.copy(alpha = 0.76f),
                                ),
                            ),
                            shape,
                        ) else Modifier
                    )
                    .then(
                        when {
                            focused && active -> Modifier.border(
                                1.dp,
                                colors.primary.copy(alpha = 0.95f),
                                shape,
                            )
                            focused -> Modifier.border(
                                tv.own.owntv.ui.theme.LocalFocusBorderWidth.current,
                                ladder.focusBorder ?: colors.focusBorder,
                                shape,
                            )
                            active -> Modifier.border(
                                1.dp,
                                colors.primary.copy(alpha = 0.28f),
                                shape,
                            )
                            else -> Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Monochrome duotone nav icon — tints via the shared ladder (muted idle, white cursor,
                // accent when active). No per-frame animation on the always-visible nav.
                NavDuotoneIcon(
                    section = section,
                    color = if (active) colors.onPrimaryContainer else ladder.icon,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private val MainSection.navIcon: OwnTVIcon
    get() = when (this) {
        MainSection.SEARCH -> OwnTVIcon.SEARCH
        MainSection.HOME -> OwnTVIcon.HOME
        MainSection.LIVE_TV -> OwnTVIcon.LIVE_TV
        MainSection.MOVIES -> OwnTVIcon.MOVIES
        MainSection.SERIES -> OwnTVIcon.SERIES
        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS
        MainSection.EPG -> OwnTVIcon.EPG
        MainSection.SETTINGS -> OwnTVIcon.SETTINGS
    }
