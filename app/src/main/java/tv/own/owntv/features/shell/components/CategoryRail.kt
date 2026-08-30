package tv.own.owntv.features.shell.components

import androidx.compose.runtime.Immutable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.draw.rotate
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.components.ChannelGenre
import tv.own.owntv.ui.components.NavAccentBar
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.ProviderChip
import tv.own.owntv.ui.components.rememberNavLadderColors
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.components.RailPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.glass

/**
 * A category as shown in the rail: just its full name, optionally prefixed with an [icon] (the
 * Favorites / History special rails). Category folders render the name alone — no abbreviation
 * badge (#75).
 */
@Immutable
data class RailCategory(
    /** Stable provider/category key. Synthetic rows keep their English key here for filtering and state. */
    val fullName: String,
    val icon: OwnTVIcon? = null,
    @param:androidx.annotation.StringRes val labelRes: Int? = null,
    // Whether to show the genre hint dot. False for synthetic aggregates ("All Channels/Movies/Series")
    // that combine every provider category — those aren't a real provider genre, so no dot.
    val showGenreDot: Boolean = true,
    val providerName: String? = null,
)

/**
 * Layer 2 — the vertical folder rail. Collapsed (focus elsewhere) it shows compact abbreviation
 * pills (FAV, HIS, UK, …); when it holds focus it expands to show full names.
 *
 * Performance notes (providers can have hundreds of categories):
 *  - The pills live in a [LazyColumn], so only the visible ones are composed.
 *  - The rail's slot in the screen layout stays a fixed [Dimens.RailWidth]; the expanded rail is
 *    drawn as an overlay (zIndex) on top of the content pane instead of pushing it, so the channel
 *    grid is never re-laid-out during the expand animation.
 */
@Composable
fun CategoryRail(
    categories: List<RailCategory>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onLongSelect: ((Int) -> Unit)? = null,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    // Caller-supplied list state. Defaulted so existing callers are unchanged, but Live/Movies/Series
    // pass their own so CH+- key paging can drive the rail's scroll position from the screen.
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    // Optional focus requester to target the rail's focusable list.
    focusRequester: FocusRequester? = null,
    // Column width. Defaults to the stock rail width; Live/Movies/Series override it when the user has
    // turned on manual panel widths for that section (see PanelWidths.kt).
    width: androidx.compose.ui.unit.Dp = Dimens.RailWidthFixed,
    // Browse screens place this column inside one shared content panel. Overlays keep the standalone
    // panel so they remain independently raised above the screen beneath them.
    showPanel: Boolean = true,
) {
    val colors = OwnTVTheme.colors
    var hasFocus by remember { mutableStateOf(false) }
    // Folder search (for big libraries). Filters the rail by name but keeps each folder's ORIGINAL
    // index, so selection highlighting and onSelect still map correctly. Reset when the rail loses
    // focus, so it's fresh every time you open it.
    var query by remember { mutableStateOf("") }
    val visible = remember(categories, query) {
        val q = query.trim()
        if (q.isEmpty()) categories.indices.toList()
        else categories.indices.filter { categories[it].fullName.contains(q, ignoreCase = true) }
    }
    val rowFocusers = remember(visible.size) { List(visible.size) { FocusRequester() } }
    // Phase 2 — the rail is a FIXED full-label column (no collapse/abbreviation overlay), so it never
    // reflows the layout on the D-pad. Always "expanded" = full category names.
    val expanded = true

    val selectedFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // Keep the selected category in view when the selection changes — both for the initial load /
    // restored state (rail not yet focused) AND when CH+- paging selects a far-away category while the
    // rail IS focused. While the user D-pads inside, focus handles scrolling for adjacent moves; this
    // covers the case where a CH key changes selectedIndex by a large jump.
    LaunchedEffect(selectedIndex, categories.size) {
        if (selectedIndex in categories.indices) {
            runCatching { listState.scrollToItem(selectedIndex) }
            if (hasFocus) runCatching { selectedFocus.requestFocus() }
        }
    }

    // Fixed full-label column in the screen's Row — a real grid column (no overlay), so it takes its own
    // space and nothing reflows when focus enters/leaves it.
    val railModifier = modifier.fillMaxHeight().width(width)
    Box(
        modifier = if (showPanel) {
            railModifier.roundedPanel(fillColor = RailPanelFill, surface = GlassSurface.SIDEBAR)
        } else {
            railModifier
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                // LazyColumn fill is now transparent — the outer Box's roundedPanel surfaceContainerLowest
                // shows through, keeping panel 1 the same colour as panels 2/3/4 (Phase 6).
                .onFocusChanged {
                    // Spatial D-pad entry would land on whatever pill is horizontally aligned —
                    // redirect every entry (from the sidebar OR back from the content list) to the
                    // SELECTED category, so you return to the folder you're actually in (e.g. pressing
                    // Left from a channel lands back on that channel's category, not the top of the rail).
                    // Internal moves between pills don't re-trigger this. The redirect must be deferred a
                    // frame: requesting focus inside onFocusChanged is rejected (the focus transaction is
                    // still in progress).
                    val entered = it.hasFocus && !hasFocus
                    hasFocus = it.hasFocus
                    if (it.hasFocus) onFocused() else query = "" // reset the search on leaving
                    if (entered) scope.launch {
                        if (selectedIndex in categories.indices) {
                            // Land on the current category; the search box (top) is one Up away.
                            runCatching { listState.scrollToItem(selectedIndex) }
                            runCatching { selectedFocus.requestFocus() }
                        } else {
                            // No selection (e.g. an empty/special rail) — fall back to the search box.
                            runCatching { listState.scrollToItem(0) }
                            runCatching { searchFocus.requestFocus() }
                        }
                    }
                }
                // Held Up/Down can outrun the lazy list's composition and escape the rail (landing
                // on the top bar) — trap vertical exits; Left/Right/Back still leave normally.
                .trapVerticalFocusExit()
                .focusGroup(),
            contentPadding = if (showPanel) {
                PaddingValues(vertical = Dimens.GapLarge, horizontal = 10.dp)
            } else {
                PaddingValues(0.dp)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
        ) {
            // Category-search field, only while the rail is expanded (focused). Entering the rail lands
            // here; Down drops into the list, and the filter clears when the rail loses focus.
            if (hasFocus) {
                item(key = "__rail_search__") {
                    SearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(tv.own.owntv.R.string.content_search_categories),
                        modifier = Modifier
                            .focusRequester(searchFocus)
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    )
                }
            }
            items(count = visible.size, key = { visible[it].let { idx -> categories.getOrNull(idx)?.fullName ?: idx } }) { i ->
                val index = visible[i]
                RailPill(
                    category = categories[index],
                    // RailPill only lights the green "active" fill when this pill is BOTH the current
                    // category AND focused — so the highlight always follows focus and nothing is auto-lit.
                    selected = index == selectedIndex,
                    expanded = expanded,
                    onClick = { onSelect(index) },
                    onLongClick = onLongSelect?.let { 
                        { 
                            rowFocusers.getOrNull(i)?.requestFocus()
                            it(index) 
                        } 
                    },
                    modifier = if (index == selectedIndex) {
                        Modifier.focusRequester(selectedFocus).focusRequester(rowFocusers[i])
                    } else {
                        Modifier.focusRequester(rowFocusers[i])
                    },
                )
            }
            if (hasFocus && visible.isEmpty()) {
                item {
                    Text(
                        stringResource(tv.own.owntv.R.string.content_no_categories_match),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RailPill(
    category: RailCategory,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Box-style corners (8.dp), close to the live-TV channel list item, not an over-rounded pill.
    val shape = if (expanded) RoundedCornerShape(8.dp) else CircleShape
    // Shared 4-state nav ladder (see NavLadder.kt) — identical treatment to the sidebar nav items so
    // both panels read the same (#47): active+focused (full fill) → focused cursor (outline) →
    // selected-idle (tonal fill + left accent bar) → idle. Focus fills snap in both material modes
    // so an old category cannot leave a dark plate behind while LazyColumn moves the next one into view.
    val ladder = rememberNavLadderColors(
        selected = selected,
        focused = focused,
    )
    val activeSelected = selected && focused

    Box(
        modifier = modifier
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.size(Dimens.RailPillSize))
            .clip(shape)
            // Frosted glass fill when the panel is glassy (idle pills have a transparent ladder fill,
            // which glass() skips); plain tonal fill otherwise.
            .glass(surface = GlassSurface.PANELS, baseFill = ladder.container, shape = shape)
            .then(
                when {
                    focused -> Modifier.border(
                        tv.own.owntv.ui.theme.LocalFocusBorderWidth.current,
                        OwnTVTheme.colors.focusBorder,
                        shape,
                    )
                    selected -> Modifier.border(
                        1.dp,
                        OwnTVTheme.colors.primary.copy(alpha = 0.28f),
                        shape,
                    )
                    else -> Modifier
                }
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.selectable(
                        selected = selected,
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                }
            ),
    ) {
        // Persistent left accent bar marking the active category.
        NavAccentBar(visible = ladder.showAccentBar && expanded)

        Row(
            modifier = Modifier
                .then(if (expanded) Modifier.fillMaxWidth() else Modifier.size(Dimens.RailPillSize))
                .then(if (expanded) Modifier.padding(horizontal = 10.dp, vertical = 8.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            // Favorites / History carry an [icon] inline before the name; category folders show the
            // name alone with no abbreviation badge (#75).
            if (category.icon != null) {
                OwnTVIcon(icon = category.icon, tint = ladder.icon, filled = activeSelected, modifier = Modifier.size(if (expanded) 20.dp else Dimens.RailPillSize / 2))
                if (expanded) Spacer(Modifier.width(8.dp))
            } else if (expanded && category.showGenreDot) {
                // Genre hint dot (Sport/News/Movies/Action/…); unknown categories show the grey
                // "Other" dot rather than an empty slot, so every row has a consistent marker.
                val genreDot = ChannelGenre.fromCategory(category.fullName).dot
                Box(Modifier.size(8.dp).clip(CircleShape).background(genreDot))
                Spacer(Modifier.width(10.dp))
            }
            if (expanded) {
                Text(
                    text = category.labelRes?.let { stringResource(it) } ?: category.fullName,
                    color = ladder.content,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                category.providerName?.let {
                    Spacer(Modifier.width(6.dp))
                    ProviderChip(name = it, maxWidth = 78.dp, compact = true)
                }
            }
        }
    }
}

/** Long-press quick actions for a category (hide / move). */
@Composable
fun CategoryContextMenu(
    categoryName: String,
    canHide: Boolean,
    canMove: Boolean = true,
    onHide: () -> Unit,
    onMove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .focusGroup()
            .longPressMenuGuard(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.roundedPanel(fillColor = colors.surfaceContainerLow).padding(14.dp).width(280.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(categoryName, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))

            if (canMove) {
                RailMenuAction(
                    label = stringResource(tv.own.owntv.R.string.content_move),
                    onClick = onMove,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }

            if (canHide) {
                RailMenuDivider()
                RailMenuAction(
                    label = stringResource(tv.own.owntv.R.string.content_hide_category),
                    onClick = onHide,
                    modifier = if (!canMove) Modifier.fillMaxWidth().focusRequester(focus) else Modifier.fillMaxWidth(),
                    destructive = true,
                )
            }

            RailMenuDivider()
            RailMenuAction(stringResource(tv.own.owntv.R.string.common_cancel), onDismiss, OwnTVIcon.CLOSE, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RailMenuAction(
    label: String,
    onClick: () -> Unit,
    icon: OwnTVIcon? = null,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val colors = OwnTVTheme.colors
    val danger = Color(0xFFFFB4AB)
    tv.own.owntv.ui.components.FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.012f,
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = if (destructive) Color(0xFF6E2B2B) else colors.primaryContainer,
        selectedContainerColor = Color.Transparent,
        surface = GlassSurface.DIALOGS,
        glassFrostScale = 0.86f,
        glassIdleRimAlpha = 0f,
    ) { focused ->
        val foreground = when {
            destructive && !focused -> danger
            destructive -> Color.White
            focused -> colors.onPrimaryContainer
            else -> colors.onSurface
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) OwnTVIcon(icon, foreground, Modifier.size(19.dp).then(iconModifier), filled = true)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RailMenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(OwnTVTheme.colors.outlineVariant.copy(alpha = 0.45f)),
    )
}
