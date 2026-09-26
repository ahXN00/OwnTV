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
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.ProviderChip
import tv.own.owntv.ui.components.rememberNavLadderColors
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
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
 * Layer 2 — the vertical folder rail.
 *
 * Performance notes (providers can have hundreds of categories):
 *  - The pills live in a [LazyColumn], so only the visible ones are composed.
 *  - The rail's slot in the screen layout stays a fixed width, taking its own column so the
 *    adjacent content pane is never re-laid-out when focus enters or leaves.
 *  - Category search is permanently present at item 0 to prevent list shifts and relayouts.
 *
 * Shared category rail (left vertical navigation column) used by Live TV, Movies, and Series screens.
 * Fixed full-label column with folder search, persistent active-category highlight, and optional
 * genre dot indicators.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    focusRequester: FocusRequester? = null,
    // One-shot request to land the cursor on ONE category row rather than on the column as a whole.
    // [focusRequester] is attached to the container, so requesting it lets Compose pick the first
    // child (the search field / top row) — wrong when returning from a context menu opened further
    // down. Caller passes the row's index and clears it in [onRowFocused].
    focusRowIndex: Int? = null,
    onRowFocused: () -> Unit = {},
    // Column width. Defaults to the stock rail width; Live/Movies/Series override it when the user has
    // turned on manual panel widths for that section (see PanelWidths.kt).
    width: androidx.compose.ui.unit.Dp = Dimens.RailWidthFixed,
    // Browse screens place this column inside one shared content panel. Overlays keep the standalone
    // panel so they remain independently raised above the screen beneath them.
    showPanel: Boolean = true,
    /** Overrides the panel fill. Cinematic passes a translucent one so the backdrop shows through
     *  even for users who have Glass Effect turned off — a solid plate there would hide the art. */
    panelFill: androidx.compose.ui.graphics.Color? = null,
    /** Direct programmatic navigation to the adjacent content pane when pressing D-pad Right,
     *  bypassing Compose's 2D spatial search so focus never lands on the search bar or an arbitrary row. */
    onNavigateRight: (() -> Unit)? = null,
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
    // Return the cursor to a specific row (see [focusRowIndex]). The row is addressed by its original
    // category index, so a rail filtered by the search box still resolves to the right focuser.
    LaunchedEffect(focusRowIndex, visible) {
        val target = focusRowIndex ?: return@LaunchedEffect
        val pos = visible.indexOf(target)
        if (pos >= 0) {
            runCatching { rowFocusers[pos].requestFocus() }
        }
        onRowFocused()
    }
    // Phase 2 — the rail is a FIXED full-label column (no collapse/abbreviation overlay), so it never
    // reflows the layout on the D-pad. Always "expanded" = full category names.
    val expanded = true

    val selectedFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bringIntoViewSpec = androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current

    // Keep the selected category in view when the selection changes — both for the initial load /
    // restored state (rail not yet focused) AND when CH+- paging selects a far-away category while the
    // rail IS focused.
    LaunchedEffect(selectedIndex, categories.size) {
        if (selectedIndex in categories.indices) {
            val targetPos = visible.indexOf(selectedIndex)
            if (targetPos >= 0) {
                val listIndex = targetPos + 1
                val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == listIndex }
                if (!isVisible) {
                    val containerHeight = listState.layoutInfo.viewportSize.height.toFloat()
                    val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index != 0 }?.size?.toFloat()
                        ?: with(density) { 40.dp.toPx() }
                    val offsetDistance = if (containerHeight > 0f) {
                        bringIntoViewSpec.calculateScrollDistance(
                            offset = 0f,
                            size = itemHeight,
                            containerSize = containerHeight,
                        ).toInt()
                    } else 0
                    runCatching { listState.scrollToItem(listIndex, scrollOffset = offsetDistance) }
                }
                if (hasFocus) runCatching { selectedFocus.requestFocus() }
            }
        }
    }

    // Fixed full-label column in the screen's Row — a real grid column (no overlay), so it takes its own
    // space and nothing reflows when focus enters/leaves it.
    val railModifier = modifier.fillMaxHeight().width(width)
    Box(
        modifier = if (showPanel) {
            railModifier.roundedPanel(fillColor = panelFill ?: RailPanelFill, surface = GlassSurface.SIDEBAR)
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
                    hasFocus = it.hasFocus
                    if (it.hasFocus) onFocused() else query = "" // reset the search on leaving
                }
                .focusProperties {
                    // Every entry (from the sidebar OR back from the content) lands on the category
                    // actually open, never a row that was only browsed; the search box when none is.
                    onEnter = {
                        val targetPos = visible.indexOf(selectedIndex)
                        val targetRequester = if (targetPos in rowFocusers.indices) rowFocusers[targetPos] else searchFocus
                        if (!runCatching { targetRequester.requestFocus() }.getOrDefault(false)) {
                            val targetIndex = if (targetPos in rowFocusers.indices) targetPos + 1 else 0
                            scope.launch {
                                runCatching { listState.scrollToItem(targetIndex) }
                                withFrameNanos { }
                                repeat(3) {
                                    if (runCatching { targetRequester.requestFocus() }.getOrDefault(false)) return@launch
                                    withFrameNanos { }
                                }
                            }
                        }
                    }
                }
                // Held Up/Down can outrun the lazy list's composition and escape the rail (landing
                // on the top bar) — trap vertical exits; Left/Right/Back still leave normally.
                .trapVerticalFocusExit()
                .focusGroup(),
            contentPadding = if (showPanel) {
                PaddingValues(start = 0.dp, top = Dimens.GapLarge, end = 10.dp, bottom = Dimens.GapLarge)
            } else {
                PaddingValues(0.dp)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
        ) {
            // Category-search field is permanently rendered at the top of the rail to avoid
            // reflow/jumping when focus enters or leaves. Entering the rail lands on the current
            // category (search is one Up away), and the query filter clears when the rail loses focus.
            item(key = "__rail_search__") {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(tv.own.owntv.R.string.content_search_categories),
                    modifier = Modifier
                        .focusRequester(searchFocus)
                        .fillMaxWidth()
                        .padding(start = 14.dp, bottom = 4.dp)
                        .then(
                            if (onNavigateRight != null) {
                                Modifier.onPreviewKeyEvent { event ->
                                    if (query.isEmpty() &&
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.DirectionRight
                                    ) {
                                        onNavigateRight()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            } else {
                                Modifier
                            }
                        ),
                )
            }
            items(count = visible.size, key = { visible[it] }) { i ->
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
                    onNavigateRight = onNavigateRight,
                    modifier = if (index == selectedIndex) {
                        Modifier.focusRequester(selectedFocus).focusRequester(rowFocusers[i])
                    } else {
                        Modifier.focusRequester(rowFocusers[i])
                    },
                )
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        stringResource(tv.own.owntv.R.string.content_no_categories_match),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(
                            start = 14.dp,
                            end = if (showPanel) 10.dp else 0.dp,
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
                    )
                }
            }
        }
    }
}

/** Single vertical category pill in the rail. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RailPill(
    category: RailCategory,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Box-style corners (8.dp), close to the live-TV channel list item, not an over-rounded pill.
    val shape = if (expanded) RoundedCornerShape(8.dp) else CircleShape
    // Glass effect: when the PANELS surface is glassy, the focused/active highlight renders as a
    // frosted glass slice (via Modifier.glass) with a bright white rim, matching the sidebar.
    val panelsGlassy = LocalGlass.current.isGlassy(GlassSurface.PANELS)
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
            .fillMaxWidth()
            .then(
                if (onNavigateRight != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionRight
                        ) {
                            onNavigateRight()
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
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
        contentAlignment = Alignment.CenterStart,
    ) {
        // Persistent left accent bar marking the active category (only in the expanded full-label rail —
        // a vertical bar on a compact circle pill would look wrong). Positioned outside the category pill's
        // border at the start of the item slot, matching the 9 dp spacing and visual treatment of Sidebar.kt.
        // Hidden in glass mode: the frosted highlight already marks the active pill and the accent bar clashes (matches the sidebar).
        NavAccentBar(visible = ladder.showAccentBar && expanded && !panelsGlassy, height = 22.dp)

        Box(
            modifier = Modifier
                .padding(start = 14.dp)
                .then(if (expanded) Modifier.fillMaxWidth().heightIn(min = 40.dp) else Modifier.size(Dimens.RailPillSize))
                .clip(shape)
                // Frosted glass fill when the panel is glassy (idle pills have a transparent ladder fill,
                // which glass() skips); plain tonal fill otherwise.
                .glass(surface = GlassSurface.PANELS, baseFill = ladder.container, shape = shape)
                .then(
                    when {
                        // Focused pill renders the user's configured focus highlight ring. Selected-idle pills
                        // use a muted 1.dp hairline to mark the active category without competing with
                        // the live remote cursor on the content pane.
                        focused -> Modifier.border(
                            tv.own.owntv.ui.theme.LocalFocusBorderWidth.current,
                            OwnTVTheme.colors.focusBorder,
                            shape,
                        )
                        selected -> Modifier.border(
                            1.dp,
                            OwnTVTheme.colors.focusBorder.copy(alpha = 0.28f),
                            shape,
                        )
                        else -> Modifier
                    }
                ),
        ) {
            Row(
                modifier = Modifier
                    .then(if (expanded) Modifier.fillMaxWidth().heightIn(min = 40.dp) else Modifier.size(Dimens.RailPillSize))
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
                        fontWeight = FontWeight.SemiBold,
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
    OwnTVPopup(onDismissRequest = onDismiss) {
        val colors = OwnTVTheme.colors
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        androidx.activity.compose.BackHandler { onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .modalScrim()
                .trapAllFocusExit()
                .focusGroup()
                .longPressMenuGuard(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .dialogPanel(width = 280.dp, scroll = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                        label = stringResource(tv.own.owntv.R.string.common_hide),
                        onClick = onHide,
                        modifier = if (!canMove) Modifier.fillMaxWidth().focusRequester(focus) else Modifier.fillMaxWidth(),
                        destructive = true,
                    )
                }

                RailMenuDivider()
                RailMenuAction(
                    label = stringResource(tv.own.owntv.R.string.common_cancel),
                    onClick = onDismiss,
                    icon = OwnTVIcon.CLOSE,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    val errorColor = MaterialTheme.colorScheme.error
    val errorContainerColor = MaterialTheme.colorScheme.errorContainer
    val onErrorContainerColor = MaterialTheme.colorScheme.onErrorContainer
    tv.own.owntv.ui.components.FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.012f,
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = if (destructive) errorContainerColor else colors.primaryContainer,
        selectedContainerColor = Color.Transparent,
        surface = GlassSurface.DIALOGS,
        glassFrostScale = 0.86f,
        glassIdleRimAlpha = 0f,
    ) { focused ->
        val foreground = when {
            destructive && !focused -> errorColor
            destructive && focused -> onErrorContainerColor
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
