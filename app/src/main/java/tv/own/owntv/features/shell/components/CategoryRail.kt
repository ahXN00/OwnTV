package tv.own.owntv.features.shell.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * A category as shown in the rail: a 2–3 char abbreviation plus its full name. Special rails
 * (Favorites / History) render an [icon] instead of the abbreviation.
 */
data class RailCategory(val abbr: String, val fullName: String, val icon: OwnTVIcon? = null)

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
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
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
    val width by animateDpAsState(
        targetValue = if (hasFocus) Dimens.RailWidthExpanded else Dimens.RailWidth,
        animationSpec = tween(150),
        label = "railWidth",
    )

    val listState = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // Keep the selected category in view when the selection changes while the rail isn't focused
    // (initial load, restored state). While the user D-pads inside, focus handles scrolling.
    LaunchedEffect(selectedIndex, categories.size) {
        if (!hasFocus && selectedIndex in categories.indices) {
            runCatching { listState.scrollToItem(selectedIndex) }
        }
    }

    // Fixed-width slot in the screen's Row; the (possibly wider) rail overflows it to the right,
    // drawn above the content pane.
    Box(modifier = modifier.fillMaxHeight().width(Dimens.RailWidth).zIndex(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .width(width)
                .background(colors.panel)
                .onFocusChanged {
                    // Spatial D-pad entry would land on whatever pill is horizontally aligned —
                    // redirect every entry (from the sidebar OR back from the content list) to the
                    // SELECTED category. Internal moves between pills don't re-trigger this. The
                    // redirect must be deferred a frame: requesting focus inside onFocusChanged is
                    // rejected (the focus transaction is still in progress).
                    val entered = it.hasFocus && !hasFocus
                    hasFocus = it.hasFocus
                    if (it.hasFocus) onFocused() else query = "" // reset the search on leaving
                    // Entering the rail lands on the search box (scrolled to the top) so you can filter
                    // categories straight away; Down drops into the list.
                    if (entered) scope.launch {
                        runCatching { listState.scrollToItem(0) }
                        runCatching { searchFocus.requestFocus() }
                    }
                }
                .focusGroup(),
            contentPadding = PaddingValues(vertical = Dimens.GapLarge, horizontal = 10.dp),
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
                        placeholder = "Search categories…",
                        modifier = Modifier
                            .focusRequester(searchFocus)
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    )
                }
            }
            items(count = visible.size, key = { visible[it] }) { i ->
                val index = visible[i]
                RailPill(
                    category = categories[index],
                    // RailPill only lights the green "active" fill when this pill is BOTH the current
                    // category AND focused — so the highlight always follows focus and nothing is auto-lit.
                    selected = index == selectedIndex,
                    expanded = hasFocus,
                    onClick = { onSelect(index) },
                    modifier = if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
            if (hasFocus && visible.isEmpty()) {
                item {
                    Text(
                        "No categories match",
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
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Only ever highlight the FOCUSED pill. The current category shows the green "selected" fill *when
    // it's the one you're on*; otherwise a focused pill gets the focus outline, and a selected-but-not-
    // focused pill shows nothing — so the highlight always reads as "where the remote is".
    val activeSelected = selected && focused

    // M3 tonal states: the active+focused category uses the primary *container* (soft tonal fill), a
    // plain focused pill uses a surface-container fill with a primary outline.
    val bg by animateColorAsState(
        targetValue = when {
            activeSelected -> colors.primaryContainer
            focused -> colors.card
            else -> Color.Transparent
        },
        label = "railPillBg",
    )
    val fg by animateColorAsState(
        targetValue = when {
            activeSelected -> colors.onPrimaryContainer
            focused -> colors.accent
            else -> colors.textSecondary
        },
        label = "railPillFg",
    )

    val shape = if (expanded) RoundedCornerShape(50) else CircleShape

    Row(
        modifier = modifier
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.size(Dimens.RailPillSize))
            .clip(shape)
            .background(bg)
            .then(
                if (focused && !selected) Modifier.border(Dimens.FocusBorderWidth, colors.focusBorder, shape)
                else Modifier
            )
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .then(if (expanded) Modifier.padding(horizontal = 10.dp, vertical = 8.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        // The compact badge (icon or abbreviation) — the row's anchor in both states.
        Box(
            modifier = Modifier.size(if (expanded) 36.dp else Dimens.RailPillSize),
            contentAlignment = Alignment.Center,
        ) {
            if (category.icon != null) {
                OwnTVIcon(icon = category.icon, tint = fg, filled = activeSelected, modifier = Modifier.size(if (expanded) 20.dp else Dimens.RailPillSize / 2))
            } else {
                Text(
                    text = category.abbr,
                    color = fg,
                    style = if (expanded) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = category.fullName,
                color = fg,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
