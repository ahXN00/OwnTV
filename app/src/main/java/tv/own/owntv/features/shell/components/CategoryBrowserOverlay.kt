package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.features.live.LiveCategories
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.features.live.liveCategoryEntries
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx

/**
 * Category sheet that slides in over the playing video when the user presses Left a second time
 * inside the in-player channel list: Live TV's own category sheet ([LiveCategories]), 20 from the
 * screen edge — search, Favorites / Recently watched / Catch-up / All channels with their counts, then
 * the provider groups with their tags (customizations applied: hidden categories excluded, renames
 * shown, manual order respected). OK loads that category's channels into the channel-list overlay;
 * Back or Left returns to the channel list without changing anything. The playing list's category is
 * the selected one and is focused first. The remote's paging keys page through it, as in the channel list.
 */
@Composable
fun CategoryBrowserOverlay(
    railItems: List<LiveRailItem>,
    railCounts: Map<LiveKey, Int>,
    currentKey: LiveKey?,
    onSelect: (LiveKey) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val (entries, groupsHeading) = liveCategoryEntries(railItems, railCounts)
    val selectedIndex = remember(entries, currentKey) { entries.indexOfFirst { it.item.key == currentKey }.coerceAtLeast(0) }
    val listState = rememberLazyListState()
    val listFocus = remember { FocusRequester() }
    // Paging moves focus by setting the row to land on; the sheet scrolls to it and reports back.
    var focusRow by remember { mutableStateOf<Int?>(null) }
    var focusedIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
    var hasFocus by remember { mutableStateOf(false) }
    // While the search is being typed in, ◀/▶ move the text cursor; OK or Back ends typing first.
    var typing by remember { mutableStateOf(false) }
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()

    // Entering the sheet's list lands on the selected category (its focusProperties.onEnter). Focusing
    // it scrolls it to the top edge, hiding the search and the fixed entries above it; when the
    // selected row fits on the first screen, keep the list at its top as the Live TV sheet shows it.
    LaunchedEffect(Unit) {
        runCatching { listFocus.requestFocus() }
        withFrameNanos { }
        if (selectedIndex < FIRST_SCREEN_ROWS) runCatching { listState.scrollToItem(0) }
    }

    BackHandler { onDismiss() }

    val shade = Color(2, 5, 6)
    Box(
        modifier = modifier
            .fillMaxSize()
            .gradientWash(false, 0f to shade.copy(alpha = 0.72f), 0.38f to shade.copy(alpha = 0.45f), 0.62f to Color.Transparent),
    ) {
        LiveCategories(
            entries = entries,
            selectedIndex = selectedIndex,
            groupsHeading = groupsHeading,
            sheet = true,
            listState = listState,
            onSelect = { onSelect(entries[it].item.key) },
            onLongSelect = {},
            onNavigateRight = {},
            focusRequester = listFocus,
            focusRowIndex = focusRow,
            onRowFocused = { focusRow = null },
            onRowFocus = { focusedIndex = it },
            onSearchEditingChange = { typing = it },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 20.mpx, vertical = 22.mpx)
                .width(440.mpx)
                .fillMaxHeight()
                .onFocusChanged { hasFocus = it.hasFocus }
                .onPreviewKeyEvent { e ->
                    // Pushing outward from logical Start returns to the channel list.
                    if (!typing && e.type == KeyEventType.KeyDown && e.key.horizontalDirection(layoutDirection) == HorizontalDirection.START) {
                        onDismiss(); true
                    } else false
                }
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { hasFocus },
                    lastIndex = { entries.lastIndex },
                    currentTargetIndex = { focusedIndex },
                    onJumpToIndex = { focusRow = it },
                ),
        )
    }
}

/** Rows that fit on the sheet's first screen below the search field, the fixed entries included. */
private const val FIRST_SCREEN_ROWS = 10
