package tv.own.owntv.features.shell.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.features.shell.MainSection
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.NavAccentBar
import tv.own.owntv.ui.components.rememberNavLadderColors
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.NavDuotoneIcon
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Layer 1 — the MD3 navigation panel. A FIXED icon rail: brand logo at the top (Phase 2), the nav items
 * (browse + Settings) vertically centered in the middle (Phase 3), and the profile avatar pinned at the
 * bottom (Phase 1). The logo is display-only (not focusable); everything else is a focusable nav item.
 */
@Composable
fun Sidebar(
    selected: MainSection,
    onSelect: (MainSection) -> Unit,
    avatarId: Int,
    onPickAvatar: () -> Unit,
    profileName: String,
    sourceSummary: String,
    onSwitchProfile: () -> Unit,
    selectedItemFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    counts: (MainSection) -> Int = { 0 },
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
    val focusSection = if (selected == MainSection.SEARCH) MainSection.HOME else selected

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
            .background(colors.background) // Phase 6 — unified panel surface
            // Side inset (6.dp). Combined with the content area's start=0, this leaves a ~6.dp gap
            // between the nav pills and panel 1. Symmetric, so logo/profile stay centered.
            .padding(horizontal = 6.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Phase 2 — brand mark pinned at the top of the rail. Non-focusable, so D-pad entry into the
        // panel still redirects to the selected nav item below (see onFocusChanged) — it can't trap
        // an "up" press from the first nav item either.
        AppLogo()
        Spacer(Modifier.height(12.dp))

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
                SectionLabel("浏览")
                Spacer(Modifier.height(4.dp))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                MainSection.entries.filter { it.isBrowse }.forEach { section ->
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
    sourceSummary: String,
    onPickAvatar: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = OwnTVTheme.colors

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
                profileName.ifBlank { "OwnTV 用户" },
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                sourceSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
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
            ) { focused ->
                val c = if (focused) colors.onSurface else colors.onSurfaceVariant
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OwnTVIcon(icon = OwnTVIcon.PERSON, tint = c, modifier = Modifier.size(18.dp))
                    Text("切换用户", style = MaterialTheme.typography.labelLarge, color = c, maxLines = 1)
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
        focusedScale = 1.08f,
        focusedContainerColor = OwnTVTheme.colors.surfaceContainerHighest,
        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentAlignment = Alignment.Center,
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
    // Box-style corners (17.dp) so every focus/selection box in the app reads as the same "box", close
    // to the live-TV channel list item, not an over-rounded pill.
    val shape = RoundedCornerShape(17.dp)
    // The nav surface itself is transparent + borderless; the shared 4-state nav ladder (NavLadder.kt)
    // paints the fill, content tint, focus outline and the persistent left accent bar, so the sidebar
    // and the folder CategoryRail read identically (#47). FocusableSurface still provides the focus
    // scale, glow and click.
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = active,
        shape = shape,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        showFocusBorder = false,
        contentAlignment = Alignment.Center,
    ) { focused ->
        val ladder = rememberNavLadderColors(selected = active, focused = focused)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(ladder.container)
                .then(
                    if (ladder.focusBorder != null) Modifier.border(Dimens.FocusBorderWidth, ladder.focusBorder, shape)
                    else Modifier
                ),
        ) {
            // Persistent left accent bar marking the active section, regardless of focus.
            NavAccentBar(visible = ladder.showAccentBar, height = 26.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (expanded) Arrangement.spacedBy(16.dp, Alignment.Start) else Arrangement.Center,
            ) {
                // Monochrome duotone nav icon — tints via the shared ladder (muted idle, white cursor,
                // accent when active). No per-frame animation on the always-visible nav.
                NavDuotoneIcon(
                    section = section,
                    color = ladder.icon,
                    modifier = Modifier.size(28.dp),
                )
                if (expanded) {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = ladder.content,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
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

