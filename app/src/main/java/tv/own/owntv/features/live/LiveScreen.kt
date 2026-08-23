package tv.own.owntv.features.live

import tv.own.owntv.core.epg.displayLogoUrl
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.features.customize.MoveToCategoryDialog
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.settings.data.BrowseColumnGap
import tv.own.owntv.features.settings.data.BrowseColumnDividerSpace
import tv.own.owntv.features.settings.data.BrowseContainerPadding
import tv.own.owntv.core.settings.PanelSection
import tv.own.owntv.features.settings.data.browsePanelGapTotal
import tv.own.owntv.features.settings.data.computePanelWidths
import tv.own.owntv.features.settings.rememberPanelShares
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.jumpLazyListTo
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.ChannelGenre
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.ui.components.MenuAction
import tv.own.owntv.ui.components.arranged
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.ProviderChip
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.SortChip
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.PreviewPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.gridFocusTarget
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.LocalPopupFontFamily

/** Layer 2–4 for Live TV: real category rail, Paging channel list, and a live preview pane. */
@Composable
fun LiveScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean = true,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    onContentScrolled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: LiveViewModel = koinViewModel()
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val providerNames by vm.providerNames.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val showChannelNumbers by vm.showChannelNumbers.collectAsStateWithLifecycle()
    val externalPlayerOn by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val catchupPlayer by vm.catchupPlayer.collectAsStateWithLifecycle()
    val previewChannel by vm.previewChannel.collectAsStateWithLifecycle()
    val previewCategoryName by vm.previewCategoryName.collectAsStateWithLifecycle()
    val previewArmed by vm.previewArmed.collectAsStateWithLifecycle()
    val previewBlockedSingleSession by vm.previewBlockedSingleSession.collectAsStateWithLifecycle()
    val nowNext by vm.nowNext.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val livePreviewSetting by vm.livePreviewEnabled.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val moveState by vm.moveState.collectAsStateWithLifecycle()

    // Current programme title for each loaded channel (id → title), batched in ONE query against the
    // stored guide. Drives the small "now playing" subtitle on each channel row. Recomputed when the page
    // contents change and every 60s (the programme airing "now" turns over). Channels with no guide are
    // simply absent from the map → their row shows no second line.
    val channelIdsKey = remember(channels.itemSnapshotList) {
        channels.itemSnapshotList.items.filterNotNull().map { it.id }
    }
    val nowPlaying by produceState<Map<Long, String>>(initialValue = emptyMap(), channelIdsKey) {
        if (channelIdsKey.isEmpty()) { value = emptyMap(); return@produceState }
        val loaded = channels.itemSnapshotList.items.filterNotNull()
        value = runCatching { vm.nowPlayingFor(loaded) }.getOrDefault(emptyMap())
        // Refresh periodically so a programme ending/starting is reflected while the list stays open.
        // This producer is auto-cancelled (and restarted) when channelIdsKey changes.
        while (true) {
            kotlinx.coroutines.delay(60_000)
            value = runCatching { vm.nowPlayingFor(loaded) }.getOrDefault(emptyMap())
        }
    }
    // Preview runs only when the player isn't busy (previewEnabled) AND the user hasn't turned it off.
    val effectivePreview = previewEnabled && livePreviewSetting

    // NOTE: do NOT stop the player when LiveScreen leaves composition — going fullscreen disposes
    // this screen, and stopping here would abort the stream that was just started. Playback is
    // stopped on fullscreen exit (shell BackHandler) instead.

    // In-pane preview: play the focused channel after the focus settles (700ms). Disabled while the
    // fullscreen/mini player owns the surface (previewEnabled=false) to avoid two surfaces fighting.
    LaunchedEffect(previewChannel?.id, effectivePreview, previewArmed) {
        // previewArmed gates the case where the last channel was restored on startup — we don't auto-preview
        // it until the user actually focuses a channel (then it plays normally).
        if (!effectivePreview || !previewArmed) return@LaunchedEffect
        val ch = previewChannel ?: return@LaunchedEffect
        delay(700)
        vm.playPreview(ch)
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }

    // CH+- key paging: shared settings + a hoisted rail state so the same modifier can page both the
    // category rail and this channel list. Channel-list pane focus is tracked separately from rail
    // focus so chNavPaging only consumes the keys for whichever pane is active.
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val rememberLive by settingsVm.rememberLastLive.collectAsStateWithLifecycle()

    // "Remember last item per category": ON → each category keeps its own scroll position via a per-category
    // state map (so A→B→A lands back where you were in A). OFF → reset the shared state to the top whenever
    // the category changes (fixes the cross-category scroll-leak bug).
    val perCategoryStates = remember { mutableStateMapOf<LiveKey, androidx.compose.foundation.lazy.LazyListState>() }
    val effectiveListState =
        if (rememberLive) perCategoryStates.getOrPut(selectedKey) { androidx.compose.foundation.lazy.LazyListState() }
        else listState
    LaunchedEffect(selectedKey, rememberLive) {
        if (!rememberLive) runCatching { listState.scrollToItem(0) }
    }
    val catListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val chromeScrollThresholdPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val contentScrolled by remember(effectiveListState, catListState, chromeScrollThresholdPx) {
        androidx.compose.runtime.derivedStateOf {
            effectiveListState.firstVisibleItemIndex > 0 ||
                effectiveListState.firstVisibleItemScrollOffset > chromeScrollThresholdPx ||
                catListState.firstVisibleItemIndex > 0 ||
                catListState.firstVisibleItemScrollOffset > chromeScrollThresholdPx
        }
    }
    LaunchedEffect(contentScrolled) { onContentScrolled(contentScrolled) }
    val scope = rememberCoroutineScope()
    var channelPaneFocused by remember { mutableStateOf(false) }
    var railPaneFocused by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChannelEntity?>(null) }
    var matchingEpg by remember { mutableStateOf<ChannelEntity?>(null) }
    var offsettingEpg by remember { mutableStateOf<ChannelEntity?>(null) }
    var catchupChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    // Programme picked in the catch-up dialog, awaiting the "Watch from start / Watch channel" choice.
    // The Live picker used to start the archive straight from the pick, so the same programme opened
    // from the Guide (which asks) and from here behaved differently — this makes the two match.
    var catchupDetail by remember {
        mutableStateOf<Pair<ChannelEntity, tv.own.owntv.core.database.entity.EpgProgrammeEntity>?>(null)
    }
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) } // long-press quick menu
    // The channel the "Move to category…" flow is moving (issue #87), with the origin captured at
    // menu-open time (the rail can't change under the modal, but capturing is still safer).
    var moveItem by remember { mutableStateOf<ChannelEntity?>(null) }
    var moveOriginKey by remember { mutableStateOf<String?>(null) }
    var moveOriginName by remember { mutableStateOf<String?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    // When the long-press menu closes (Cancel, Favourite, Hide) WITHOUT opening another dialog, return focus
    // to the channel it was opened from — otherwise focus falls back to the nav panel.
    var contextMenuOpen by remember { mutableStateOf(false) }
    // Id of the channel the context menu was opened on, plus a dedicated requester bound to that row.
    // The previous restore was racy (delay(60) + selFocus bound to the *previewed* channel): when the
    // menu scrim disposed the focused menu button, Compose auto-restored focus and the CategoryRail's
    // entry-redirect pinned it to the rail before selFocus.requestFocus() ran. Tracking the long-press
    // target by id and binding a dedicated requester makes the restore deterministic.
    var contextChannelId by remember { mutableStateOf<Long?>(null) }
    val contextFocus = remember { FocusRequester() }
    var enteringMoveMode by remember { mutableStateOf(false) }
    LaunchedEffect(moveState) { if (moveState != null) enteringMoveMode = false }
    // Land focus back on the long-pressed channel's row (or a sensible fallback if it's gone).
    suspend fun restoreToContextRow() {
        val targetId = contextChannelId
        if (targetId == null) { runCatching { selFocus.requestFocus() }; return }

        val idx = channels.itemSnapshotList.items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            runCatching { effectiveListState.scrollToItem(idx) }
            withFrameNanos { } // wait one frame so the row is laid out and contextFocus is attached
            runCatching { contextFocus.requestFocus() }
        } else {
            // Row is gone (e.g. "Hide channel" removed it) — clear the anchor and land on the first row.
            contextChannelId = null
            runCatching { firstItemFocus.requestFocus() }
        }
    }
    LaunchedEffect(contextChannel) {
        val opened = contextChannel != null
        if (opened) { contextMenuOpen = true; return@LaunchedEffect }
        if (!contextMenuOpen) return@LaunchedEffect
        contextMenuOpen = false
        // A follow-up dialog (rename / match EPG / catch-up / move) grabs focus itself — only restore
        // for plain closes (Cancel, Favourite, Hide, Close). Those dialogs restore on their own close.
        if (renaming != null || matchingEpg != null || offsettingEpg != null || catchupChannel != null || enteringMoveMode ||
            moveItem != null || creatingCategory
        ) return@LaunchedEffect
        restoreToContextRow()
    }
    // The context menu closes before the shared Move-to-category dialog opens. Treat both the move
    // picker and its nested New-category prompt as one focus-owning flow, then restore the original
    // row only after the whole flow closes. Otherwise the menu-close effect can steal focus behind
    // the new dialog.
    var moveCategoryWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(moveItem, creatingCategory) {
        if (moveItem != null || creatingCategory) {
            moveCategoryWasOpen = true
        } else if (moveCategoryWasOpen) {
            moveCategoryWasOpen = false
            restoreToContextRow()
        }
    }
    // Rename restoration: completes via a TextInputDialog. Like Match EPG, renaming can trigger
    // a list rebuild (the name change flow), so re-assert the row target.
    var renameWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(renaming) {
        if (renaming != null) { renameWasOpen = true; return@LaunchedEffect }
        if (!renameWasOpen) return@LaunchedEffect
        renameWasOpen = false
        repeat(5) {
            delay(200)
            restoreToContextRow()
        }
    }
    // Catch-up restoration: returning from the picker or the follow-up detail dialog.
    var catchupWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(catchupChannel, catchupDetail) {
        if (catchupChannel != null || catchupDetail != null) { catchupWasOpen = true; return@LaunchedEffect }
        if (!catchupWasOpen) return@LaunchedEffect
        catchupWasOpen = false
        restoreToContextRow()
    }
    // Move mode (reordering) restoration: when the overlay is dismissed (Commit/Cancel).
    var reorderWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(moveState) {
        if (moveState != null) { reorderWasOpen = true; return@LaunchedEffect }
        if (!reorderWasOpen) return@LaunchedEffect
        reorderWasOpen = false
        restoreToContextRow()
    }
    // The Match EPG dialog grabbed focus while open — when it closes (pick/clear/dismiss), put focus
    // back on the channel it was opened for instead of letting it fall to the nav panel.
    var matchEpgWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(matchingEpg) {
        if (matchingEpg != null) { matchEpgWasOpen = true; return@LaunchedEffect }
        if (!matchEpgWasOpen) return@LaunchedEffect
        matchEpgWasOpen = false
        restoreToContextRow()
        // Picking a match rewrites customizations, which recreates the pager on its own schedule —
        // the rebuilt rows land a moment later and yank focus off the row we just restored, and the
        // exact timing varies with list size. Re-assert the target row briefly instead of racing a
        // single load-state transition.
        repeat(5) {
            delay(200)
            restoreToContextRow()
        }
    }
    // Same for the EPG-offset dialog: it owns focus while open, so hand it back to the channel row.
    var epgOffsetWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(offsettingEpg) {
        if (offsettingEpg != null) { epgOffsetWasOpen = true; return@LaunchedEffect }
        if (!epgOffsetWasOpen) return@LaunchedEffect
        epgOffsetWasOpen = false
        restoreToContextRow()
    }
    // Returning from fullscreen: scroll to and focus the channel you were watching (waits for the list to load).
    // Also used by "Startup → Live · Favorites": there's no remembered channel yet, so land on the first row
    // (not the nav panel).
    LaunchedEffect(restoreFocus, channels.itemCount) {
        if (!restoreFocus || channels.itemCount == 0) return@LaunchedEffect
        val ch = previewChannel
        val idx = if (ch != null) channels.itemSnapshotList.items.indexOfFirst { it.id == ch.id } else -1
        if (idx >= 0) {
            runCatching { effectiveListState.scrollToItem(idx) }
            delay(60)
            runCatching { selFocus.requestFocus() }
        } else {
            delay(60)
            runCatching { firstItemFocus.requestFocus() }
        }
        onRestored()
    }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)
    val selectedLabel = selectedItem?.displayLabel() ?: stringResource(R.string.content_category_all_channels)

    // Manual panel widths (Settings → Panel Width Adjustment). The saved percentages now resolve
    // against the inside of one shared content container; no stored value is rewritten.
    val panelShares = rememberPanelShares(PanelSection.LIVE, settingsVm)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel(fillColor = ContentPanelFill)
            .padding(BrowseContainerPadding)
            .onFocusChanged { if (it.hasFocus) onChildFocused() },
    ) {
    val previewVisible = panelShares?.preview != 0
    val innerGapTotal = browsePanelGapTotal(previewVisible)
    val panels = panelShares?.let { computePanelWidths(it, maxWidth, innerGapTotal) }
    Row(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        CategoryRail(
            width = panels?.category ?: Dimens.RailWidthFixed,
            categories = railItems.map {
                RailCategory(
                    it.displayLabel(),
                    it.icon,
                    showGenreDot = it.key is LiveKey.Folder,
                    providerName = it.providerName,
                )
            },
            selectedIndex = selectedIndex,
            onSelect = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
            // Focusing a folder stops the in-pane preview — but only when a preview is actually running.
            // When the player is docked (live PiP) or fullscreen, previewEnabled is false and stopPreview
            // would kill that stream (e.g. while navigating left to leave Live), so we skip it.
            onFocused = { if (previewEnabled) vm.stopPreview() },
            listState = catListState,
            showPanel = false,
            modifier = Modifier
                .onFocusChanged { railPaneFocused = it.hasFocus }
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { railPaneFocused },
                    lastIndex = { railItems.size - 1 },
                    currentTargetIndex = { selectedIndex },
                    // Selecting a category loads only its first paged page (~50 items), not all channels
                    // at once, so this is fast. The rail's LaunchedEffect scrolls + focuses the pill.
                    onJumpToIndex = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
                ),
        )

        Spacer(Modifier.width(BrowseColumnGap))
        Box(
            Modifier
                .width(BrowseColumnDividerSpace)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .background(OwnTVTheme.colors.outlineVariant.copy(alpha = 0.35f)),
        )

        Spacer(Modifier.width(BrowseColumnGap))

        // Layer 3 — header + channel list (fixed-width column; the preview pane fills the rest)
        Column(
            modifier = Modifier
                .width(panels?.list ?: Dimens.ChannelListWidth)
                .fillMaxHeight()
                // Track whether this pane holds focus so chNavPaging only consumes CH keys when it does.
                .onFocusChanged { channelPaneFocused = it.hasFocus }
                // CH+- key paging for this channel list. Long-press jumps to first/last channel;
                // short press skips N. currentTargetIndex falls back to the visible top when the
                // previewed channel isn't in the loaded window (paged data).
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { channelPaneFocused },
                    // On the "All" list (every channel) a long-press jump to the very last item is
                    // pointless and janks, so disable long-press there — short-press skipping stays.
                    longPressEnabled = { selectedKey != LiveKey.All },
                    lastIndex = { channels.itemCount - 1 },
                    currentTargetIndex = {
                        val pc = previewChannel
                        if (pc != null) {
                            val idx = channels.itemSnapshotList.items.indexOfFirst { it.id == pc.id }
                            if (idx >= 0) idx else effectiveListState.firstVisibleItemIndex
                        } else {
                            effectiveListState.firstVisibleItemIndex
                        }
                    },
                    onJumpToIndex = { idx ->
                        // Scroll the target into view, then focus it. selFocus is bound to the
                        // previewed channel by gridFocusTarget, so we make the target the previewed
                        // one (which also fires the debounced 700ms preview — desired).
                        val target = channels.itemSnapshotList.items.getOrNull(idx)?.id
                        scope.launch {
                            runCatching { effectiveListState.scrollToItem(idx) }
                            withFrameNanos { }
                            if (target != null) {
                                // Set the previewed channel so selFocus binds to the new row, then focus.
                                val item = channels.itemSnapshotList.items.firstOrNull { it.id == target }
                                if (item != null) {
                                    vm.onChannelFocused(item)
                                    runCatching { selFocus.requestFocus() }
                                }
                            } else {
                                runCatching { firstItemFocus.requestFocus() }
                            }
                        }
                    },
                )
                // Entering this pane (from the rail or the preview) must land on a channel row, never
                // the search bar: prefer the last-focused channel, else the first row. onEnter fires
                // only for directional entry from outside (internal moves don't re-trigger it).
                .focusProperties {
                    onEnter = {
                        if (runCatching { selFocus.requestFocus() }.isFailure) {
                            runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                }
                // Held Up/Down can outrun the lazy list's composition and escape this pane
                // (landing on the top bar) — trap vertical exits; Left/Right/Back leave normally.
                .trapVerticalFocusExit()
                .focusGroup()
        ) {
            Text(
                stringResource(R.string.content_section_category, stringResource(R.string.common_nav_live_tv), selectedLabel),
                style = MaterialTheme.typography.headlineMedium,
                color = OwnTVTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.content_count_channels, count, selectedLabel, count),
                style = MaterialTheme.typography.titleMedium,
                color = OwnTVTheme.colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    placeholder = stringResource(R.string.content_search_channels, selectedLabel),
                    modifier = Modifier.weight(1f).onFocusChanged { if (it.hasFocus && previewEnabled) vm.stopPreview() },
                )
                Spacer(Modifier.size(10.dp))
                SortChip(mode = sortMode, onToggle = vm::toggleSort)
            }
            Spacer(Modifier.height(14.dp))

            if (channels.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) stringResource(R.string.content_no_channels_found, searchQuery.trim()) else stringResource(R.string.content_no_channels_here),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OwnTVTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(state = effectiveListState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        count = channels.itemCount,
                        key = channels.itemKey { it.id },
                        contentType = channels.itemContentType { "channel" },
                    ) { index ->
                        val channel = channels[index]
                        if (channel != null) {
                            ChannelRow(
                                channel = channel,
                                isFavorite = favoriteIds.contains(channel.id),
                            nowTitle = nowPlaying[channel.id],
                            showNumber = showChannelNumbers,
                            providerName = providerNames[channel.sourceId],
                                modifier = Modifier.gridFocusTarget(
                                    itemId = channel.id, index = index,
                                    contextId = contextChannelId, contextFocus = contextFocus,
                                    selectedId = previewChannel?.id, selectedFocus = selFocus,
                                    firstItemFocus = firstItemFocus,
                                ),
                                onFocus = { vm.onChannelFocused(channel) },
                                onClick = {
                                    vm.watchFullscreen(channel, channels.itemSnapshotList.items.filterNotNull())
                                    // External player on for Live TV: the channel went to another app, so
                                    // don't mount the fullscreen player (it would spin up an idle engine).
                                    if (!externalPlayerOn) onFullscreen()
                                },
                                onLongClick = { contextChannel = channel; contextChannelId = channel.id },
                            )
                        }
                    }
                }
            }
        }

        // Layer 4 — preview pane (informational only — no focusable actions; management lives in long-press)
        if (previewVisible) {
            Spacer(Modifier.width(BrowseColumnGap))
            Box(
                modifier = Modifier
                    .then(if (panels != null) Modifier.width(panels.preview) else Modifier.weight(1f))
                    .fillMaxSize()
                    .roundedPanel(fillColor = PreviewPanelFill, surface = GlassSurface.PREVIEW)
                    .padding(BrowseContainerPadding),
            ) {
                LivePreviewPane(
                    channel = previewChannel,
                    categoryName = previewCategoryName,
                    nowNext = nowNext,
                    previewEngine = vm.previewEngine,
                    showVideo = effectivePreview,
                    singleSessionBlocked = previewBlockedSingleSession,
                )
            }
        }
    }
    }

    catchupChannel?.let { ch ->
        CatchupDialog(
            channelName = ch.name,
            loadProgrammes = { vm.catchupProgrammes(ch) },
            onPick = { prog -> catchupChannel = null; catchupDetail = ch to prog },
            jumpOffsetsSec = remember(ch.id) { vm.catchupJumpOptions(ch) },
            jumpWindowSec = remember(ch.id) { vm.catchupWindowOf(ch) },
            onJump = { offset ->
                catchupChannel = null
                vm.playCatchupAt(ch, offset)
                if (!externalPlayerOn) onFullscreen()
            },
            onDismiss = { catchupChannel = null },
        )
    }

    // Same dialog the Guide shows for a programme, so both routes offer the identical choice:
    // replay from the start, tune the channel live, favourite it, or back out.
    catchupDetail?.let { (ch, prog) ->
        tv.own.owntv.features.epg.ProgrammeDetailDialog(
            channelName = ch.name,
            programme = prog,
            loadDescription = { vm.programmeDescription(it) },
            canCatchup = true, // only reachable from the catch-up picker, which already gated on this
            isFavorite = favoriteIds.contains(ch.id),
            onToggleFavorite = { vm.toggleFavorite(ch) },
            onWatch = { catchupDetail = null; vm.watchFullscreen(ch, emptyList()); if (!externalPlayerOn) onFullscreen() },
            onPlayCatchup = { catchupDetail = null; vm.playCatchupProgramme(ch, prog); onFullscreen() },
            // External: the archive went to another app, so don't mount the fullscreen player over it.
            onPlayCatchupExternal = { catchupDetail = null; vm.playCatchupExternal(ch, prog) },
            catchupPlayer = catchupPlayer,
            onDismiss = { catchupDetail = null },
            compact = true,
        )
    }

    renaming?.let { ch ->
        TextInputDialog(
            title = stringResource(R.string.content_rename_channel),
            initial = ch.name,
            hint = stringResource(R.string.content_rename_hint),
            onConfirm = { vm.renameChannel(ch, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    matchingEpg?.let { ch ->
        EpgMatchDialog(
            channelName = ch.name,
            currentMatch = vm.currentEpgMatch(ch),
            loadChannels = { q -> vm.availableEpgChannels(ch.name, q) },
            onPick = { epgId -> vm.setEpgMatch(ch, epgId); matchingEpg = null },
            onClear = { vm.setEpgMatch(ch, null); matchingEpg = null },
            onDismiss = { matchingEpg = null },
        )
    }

    offsettingEpg?.let { ch ->
        EpgOffsetDialog(
            channelName = ch.name,
            currentMinutes = vm.currentEpgShift(ch),
            globalMinutes = vm.globalEpgShift(),
            onSet = { vm.setEpgShift(ch, it) },
            onDismiss = { offsettingEpg = null },
        )
    }

    // Long-press a channel → quick actions.
    contextChannel?.let { ch ->
        ChannelContextMenu(
            channelName = ch.name,
            isFavorite = favoriteIds.contains(ch.id),
            hasCatchup = ch.catchup,
            canMove = selectedKey is LiveKey.Folder || selectedKey is LiveKey.Custom || selectedKey == LiveKey.Favorites,
            isHistory = selectedKey == LiveKey.History,
            onToggleFavorite = { vm.toggleFavorite(ch); contextChannel = null },
            onRename = { renaming = ch; contextChannel = null },
            onHide = { vm.hideChannel(ch); contextChannel = null },
            onMatchEpg = { matchingEpg = ch; contextChannel = null },
            onEpgOffset = { offsettingEpg = ch; contextChannel = null },
            onCatchup = { catchupChannel = ch; contextChannel = null },
            onPlayExternal = { vm.playExternal(ch); contextChannel = null },
            onMove = { contextChannel = null; enteringMoveMode = true; vm.enterMoveMode(ch, selectedKey) },
            onMoveToCategory = {
                moveOriginKey = when (val k = selectedKey) {
                    is LiveKey.Folder -> vm.folderKey(k.id)
                    is LiveKey.Custom -> k.id
                    LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                    else -> null
                }
                moveOriginName = railItems.firstOrNull { it.key == selectedKey }?.title
                moveItem = ch
                contextChannel = null
            },
            onRemoveFromHistory = { vm.removeFromHistory(ch.id); contextChannel = null },
            onDismiss = { contextChannel = null },
        )
    }

    // Move to… a combined category (issue #87), incl. the "＋ New category…" name prompt.
    val moveTargets by vm.moveTargets.collectAsStateWithLifecycle()
    if (creatingCategory) {
        TextInputDialog(
            title = stringResource(R.string.settings_customize_new_category_title),
            hint = stringResource(R.string.settings_customize_new_category_description),
            confirmLabel = stringResource(R.string.common_create),
            allowBlank = false,
            onConfirm = { vm.createCustomCategory(it); creatingCategory = false },
            onDismiss = { creatingCategory = false },
        )
    } else {
        moveItem?.let { ch ->
            val originKey = moveOriginKey
            if (originKey != null) {
                MoveToCategoryDialog(
                    moveTargets = moveTargets.filterNot { it.id == originKey },
                    originName = moveOriginName ?: stringResource(R.string.settings_customize_this_category),
                    onNewCategory = { creatingCategory = true },
                    onMove = { targetId, keepInOrigin ->
                        vm.moveToCategory(CustomizeKeys.channel(ch), ch.id, originKey, targetId, keepInOrigin)
                        moveItem = null
                    },
                    onDismiss = { moveItem = null },
                )
            }
        }
    }

    // Move mode overlay — intercepts D-pad Up/Down/OK/Back while reordering.
    moveState?.let { ms ->
        MoveOrderOverlay(
            title = stringResource(R.string.content_reorder_channel),
            itemNames = ms.items.map { it.name },
            activeIndex = ms.activeIndex,
            onMoveUp = vm::moveUp,
            onMoveDown = vm::moveDown,
            onCommit = vm::commitMove,
            onCancel = vm::cancelMove,
        )
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    isFavorite: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    nowTitle: String? = null,
    showNumber: Boolean = true,
    providerName: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.hasFocus) onFocus() },
        shape = RoundedCornerShape(12.dp),
        surface = GlassSurface.CARDS,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.displayLogoUrl.isNullOrBlank()) {
                    AsyncImage(model = channel.displayLogoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
            // Provider channel number, in a fixed-width strip so every name below starts at the same x
            // however many digits the number has. Hidden entirely when the setting is off.
            if (showNumber) {
                tv.own.owntv.ui.components.ChannelNumberColumn(
                    number = channel.number,
                    color = colors.onSurfaceVariant,
                )
            }
            // Name + (optional) current programme. The subtitle is rendered only when guide data exists,
            // so channels without EPG look exactly as before — single line.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focused) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nowTitle != null) {
                    Text(
                        nowTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isFavorite) {
                OwnTVIcon(OwnTVIcon.FAVORITE, tint = colors.favorite, filled = true, modifier = Modifier.size(20.dp))
            }
            providerName?.let { ProviderChip(name = it) }
        }
    }
}

/** Long-press quick actions for a Live channel (favourite / rename / hide / match EPG / EPG offset / catch-up / move / remove history). */
@Composable
private fun ChannelContextMenu(
    channelName: String,
    isFavorite: Boolean,
    hasCatchup: Boolean,
    canMove: Boolean,
    isHistory: Boolean,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onMatchEpg: () -> Unit,
    onEpgOffset: () -> Unit,
    onCatchup: () -> Unit,
    onPlayExternal: () -> Unit,
    onMove: () -> Unit,
    // "Move to category…" (issue #87): send this channel into a user's combined category.
    onMoveToCategory: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim()
            .trapAllFocusExit().focusGroup()
            .longPressMenuGuard(), // the long-press OK is still held — don't let it auto-click a menu item
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(channelName, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            // The menu as data: same actions, same gating, same order as the buttons that used to be
            // written out here one by one. Close is not in the list — it stays pinned last.
            val actions = buildList {
                add(MenuAction("favourite", if (isFavorite) stringResource(R.string.content_remove_favourite) else stringResource(R.string.content_add_favourite), OwnTVIcon.FAVORITE, group = 0, onClick = onToggleFavorite))
                add(MenuAction("rename", stringResource(R.string.content_rename), group = 0, onClick = onRename))
                add(MenuAction("match_epg", stringResource(R.string.content_match_epg), OwnTVIcon.EPG, group = 1, onClick = onMatchEpg))
                add(MenuAction("epg_offset", stringResource(R.string.content_epg_time_offset), OwnTVIcon.EPG, group = 1, onClick = onEpgOffset))
                if (hasCatchup) add(MenuAction("catchup", stringResource(R.string.content_catchup), group = 1, onClick = onCatchup))
                // Always offered, regardless of the Live TV external-player default — this is the per-channel
                // escape hatch for a stream neither in-app engine can open (same as Movies/Series/Downloads).
                add(MenuAction("play_external", stringResource(R.string.content_play_external_short), OwnTVIcon.PLAY, group = 1, onClick = onPlayExternal))
                if (canMove) {
                    add(MenuAction("move", stringResource(R.string.content_move), group = 2, onClick = onMove))
                    add(MenuAction("move_to_category", stringResource(R.string.content_move_to_category), group = 2, onClick = onMoveToCategory))
                }
                add(MenuAction("hide", stringResource(R.string.content_hide_channel), destructive = true, group = 3, onClick = onHide))
                if (isHistory) add(MenuAction("remove_history", stringResource(R.string.content_remove_history), destructive = true, group = 3, onClick = onRemoveFromHistory))
            }
            var previousGroup: Int? = null
            arranged(ContentMenu.LIVE, actions).forEachIndexed { index, action ->
                if (previousGroup != null && action.group != previousGroup) ChannelMenuDivider()
                previousGroup = action.group
                ChannelMenuAction(
                    label = action.label,
                    onClick = action.onClick,
                    icon = action.icon,
                    modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(focus) else Modifier),
                    destructive = action.destructive,
                )
            }

            ChannelMenuDivider()
            ChannelMenuAction(stringResource(R.string.content_close), onDismiss, OwnTVIcon.CLOSE, Modifier.fillMaxWidth())
        }
    }
}

/** Quiet menu row: calm/transparent at rest, luminous material only on the focused action. */
@Composable
private fun ChannelMenuAction(
    label: String,
    onClick: () -> Unit,
    icon: OwnTVIcon? = null,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val colors = OwnTVTheme.colors
    val danger = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.012f,
        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        focusedContainerColor = if (destructive) androidx.compose.ui.graphics.Color(0xFF6E2B2B) else colors.primaryContainer,
        selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        surface = GlassSurface.DIALOGS,
        glassFrostScale = 0.86f,
        glassIdleRimAlpha = 0f,
    ) { focused ->
        val foreground = when {
            destructive && !focused -> danger
            destructive -> androidx.compose.ui.graphics.Color.White
            focused -> colors.onPrimaryContainer
            else -> colors.onSurface
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) OwnTVIcon(icon, foreground, Modifier.size(19.dp), filled = true)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChannelMenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(OwnTVTheme.colors.outlineVariant.copy(alpha = 0.45f)),
    )
}

@Composable
private fun LivePreviewPane(
    channel: ChannelEntity?,
    categoryName: String?,
    nowNext: EpgNowNext?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    singleSessionBlocked: Boolean = false,
) {
    val colors = OwnTVTheme.colors
    val previewState by previewEngine.state.collectAsStateWithLifecycle()
    val previewHeight by previewEngine.videoHeight.collectAsStateWithLifecycle()
    val streamChips by previewEngine.streamChips.collectAsStateWithLifecycle()
    // Show the ExoPlayer surface once it's playing/buffering; on ERROR fall back to the channel logo.
    val previewPlaying = showVideo && previewState != tv.own.owntv.player.LivePreviewEngine.State.ERROR &&
        previewState != tv.own.owntv.player.LivePreviewEngine.State.IDLE
    val previewLoading = showVideo && previewState == tv.own.owntv.player.LivePreviewEngine.State.LOADING
    val videoRes = previewHeight?.let { "${it}p" }
    if (channel == null) {
        PreviewPane(hint = stringResource(R.string.content_focus_channel))
        return
    }
    Column(
        // Scrollable so the EPG (Now/Next/Later) never gets clipped when it makes the pane taller
        // than the screen. The pane is informational only — there are NO focusable elements here,
        // so D-pad right never enters it (management actions live in the long-press menu).
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(Dimens.GapLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.displayLogoUrl.isNullOrBlank()) {
                AsyncImage(model = channel.displayLogoUrl, contentDescription = null, modifier = Modifier.size(120.dp))
            } else {
                OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
            }
            if (previewPlaying) {
                tv.own.owntv.player.ExoPreviewSurface(engine = previewEngine, modifier = Modifier.fillMaxSize())
            }
            if (previewLoading) {
                OwnTVSpinner(sizeDp = 28)
            }
            // One-stream provider with the stream already in use: explain the dead pane rather than
            // leaving the user to read it as a broken channel (F31).
            if (singleSessionBlocked && !previewPlaying) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        stringResource(R.string.content_preview_single_stream),
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            // Real stream spec — aspect · resolution · fps · audio. The channel NAME often lies ("…4K"),
            // so this shows what you'll actually get before you commit to watching. Falls back to just the
            // resolution until the full format is known.
            val chips = streamChips.takeIf { it.isNotEmpty() } ?: videoRes?.let { listOf(it) }.orEmpty()
            chips.takeIf { previewPlaying && it.isNotEmpty() }?.let { list ->
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    list.forEach { label ->
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(channel.name, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)

        // Metadata row — category · inferred genre (with colour dot) · catch-up status · EPG status.
        // All informational, never focusable.
        ChannelMetaRow(channel = channel, categoryName = categoryName, nowNext = nowNext)

        EpgSection(nowNext)

        // No action buttons — all management (Favorite / Rename / Hide / Match EPG / Catch-up) is in
        // the long-press menu. Just a hint so the watch affordance + where-to-find-options stay obvious.
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.content_press_ok_fullscreen),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * Informational metadata row under the channel name: the channel's category, its inferred genre
 * (with a colour dot), catch-up availability, and a short EPG-coverage hint. Purely visual —
 * nothing here is selectable/focusable, so D-pad navigation never enters the preview pane.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelMetaRow(
    channel: ChannelEntity,
    categoryName: String?,
    nowNext: EpgNowNext?,
) {
    val colors = OwnTVTheme.colors
    // Genre is inferred from the channel's real category name. Unmatched categories fall back to OTHER
    // (grey dot) so every channel still gets a genre marker — the genre is never inferred from the
    // channel NAME (a station brand like "CNN" / "Hindi MTV Plus" would be misleading).
    val genre = remember(categoryName) { ChannelGenre.fromCategory(categoryName) }

    // EPG status — "EPG · Nd" when we know the stored coverage span (bulk-guide channels), plain "EPG"
    // when only now/next is available (short-EPG API channels), "No EPG" when nothing was resolved.
    val epgStatus = when {
        nowNext == null || (nowNext.now == null && nowNext.next == null) -> stringResource(R.string.content_no_epg)
        nowNext.coverageDays != null && nowNext.coverageDays > 0 -> stringResource(R.string.content_epg_days, nowNext.coverageDays)
        else -> stringResource(R.string.content_epg)
    }

    // Catch-up status — only meaningful when the channel actually supports it.
    val catchupLabel = if (channel.catchup) {
        channel.catchupDays.takeIf { it > 0 }?.let { stringResource(R.string.content_catchup_days, it) } ?: stringResource(R.string.content_catchup)
    } else null

    val chips = buildList {
        // Genre chip (always shown, with its colour dot — including the grey "Other" fallback so every
        // channel has a genre marker), then the raw category name when it differs from the genre label.
        add(MetaChip(stringResource(genre.displayLabelRes), dot = genre.dot, primary = genre != ChannelGenre.OTHER))
        if (!categoryName.isNullOrBlank() && categoryName != genre.canonicalLabel) add(MetaChip(categoryName))
        if (catchupLabel != null) add(MetaChip(catchupLabel, accent = true))
        add(MetaChip(epgStatus))
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { chip -> MetaChipBadge(chip) }
    }
}

/** A single metadata chip — small text, optional colour dot, on a hairline-rounded surface. */
private data class MetaChip(
    val text: String,
    val dot: Color? = null,
    val primary: Boolean = false,
    val accent: Boolean = false,
)

@Composable
private fun MetaChipBadge(chip: MetaChip) {
    val colors = OwnTVTheme.colors
    val fg = when {
        chip.primary -> colors.primary
        chip.accent -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .height(26.dp)                  // uniform chip height — long category names can't wrap to 2 lines and make one chip taller than the others
            .clip(RoundedCornerShape(7.dp))
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        chip.dot?.let {
            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(it))
        }
        Text(
            chip.text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontFamily = LocalPopupFontFamily.current,
            fontWeight = FontWeight.Medium,
            maxLines = 1,                   // never wrap — keeps every chip the same height
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Now-playing (with progress) + up-next, from the channel's short EPG. Hidden when no guide exists. */
@Composable
private fun EpgSection(nowNext: EpgNowNext?) {
    val colors = OwnTVTheme.colors
    val formatTime = rememberSystemTimeFormatter()
    val now = nowNext?.now
    val next = nowNext?.next
    if (now == null && next == null) return

    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (now != null) {
            Text(stringResource(R.string.content_live_now_label), style = MaterialTheme.typography.labelSmall, color = colors.primary, fontWeight = FontWeight.Bold)
            Text(
                now.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val span = (now.stopMs - now.startMs).coerceAtLeast(1)
            val progress = ((System.currentTimeMillis() - now.startMs).toFloat() / span).coerceIn(0f, 1f)
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceContainerLowest),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.primary))
            }
            Text(
                stringResource(R.string.content_live_time_range_plain, formatTime(now.startMs), formatTime(now.stopMs)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (next != null) {
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.content_live_next_label, formatTime(next.startMs)), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(
                next.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Upcoming programmes after "next" — see what's on later without opening the Guide (#11).
        val later = nowNext.upcoming
        if (later.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.content_live_later_label), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
            later.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(formatTime(p.startMs), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    Text(p.title, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun formatCatchupTime(
    startMs: Long,
    stopMs: Long,
    formatTime: (Long) -> String,
): String {
    val formatDay = rememberBestDateFormatter("EEE")
    val day = formatDay(startMs)
    return stringResource(R.string.content_live_day_time_range, day, formatTime(startMs), formatTime(stopMs))
}

/** Live TV catch-up: pick a recent (already-aired) programme on a catch-up channel to replay from start. */
@Composable
private fun CatchupDialog(
    channelName: String,
    loadProgrammes: suspend () -> List<tv.own.owntv.core.database.entity.EpgProgrammeEntity>,
    onPick: (tv.own.owntv.core.database.entity.EpgProgrammeEntity) -> Unit,
    // Fallback for a channel with an archive but no guide: there are no programmes to name, but the
    // archive is still there, so offer times instead of the old dead-end "go match your EPG" message.
    jumpOffsetsSec: List<Int>,
    jumpWindowSec: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val formatTime = rememberSystemTimeFormatter()
    val list by androidx.compose.runtime.produceState<List<tv.own.owntv.core.database.entity.EpgProgrammeEntity>?>(initialValue = null) {
        value = runCatching { loadProgrammes() }.getOrDefault(emptyList())
    }
    androidx.activity.compose.BackHandler { onDismiss() }
    // "Choose exact time…" opens on top of this dialog, same as the player's route into it.
    var manualTime by remember { mutableStateOf(false) }
    if (manualTime) {
        CatchupManualTimeDialog(
            windowSec = jumpWindowSec,
            onPick = { manualTime = false; onJump(it) },
            onDismiss = { manualTime = false },
        )
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(list) {
        // Still loading, or nothing focusable at all (no programmes AND no archive to jump into).
        if (list == null || (list!!.isEmpty() && jumpOffsetsSec.isEmpty())) return@LaunchedEffect
        kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() }
    }
    // Popup(focusable = true) is a hard focus boundary: a stray D-pad press or the Live screen's own
    // LaunchedEffect focus requests can no longer drop focus onto the channel grid behind the scrim
    // (same fix as EpgMatchDialog / ChannelContextMenu). trapAllFocusExit() additionally blocks
    // directional exits through the scrim. PopupFontTheme swaps in the selected popup family + scales fonts to
    // match the other popup menus (0.75f), and the box is shrunk to that same denser size.
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
    tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.75f) {
        Box(
            Modifier.fillMaxSize()
                .modalScrim()
                .trapAllFocusExit()
                .focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
        // Inner list is height-capped to the screen (minus the dialog chrome) so the Close button
        // stays reachable on small/low-res screens; the outer column can't verticalScroll (LazyColumn).
        val listHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 220.dp).coerceIn(140.dp, 300.dp)
        Column(Modifier.dialogPanel(width = 460.dp, corner = 16.dp, padding = 18.dp, scroll = false)) {
            Text(stringResource(R.string.content_catchup_title, channelName), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            val noGuide = list?.isEmpty() == true && jumpOffsetsSec.isNotEmpty()
            Text(
                stringResource(if (noGuide) R.string.content_catchup_jump_prompt else R.string.content_catchup_prompt),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            when (val progs = list) {
                null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                else -> if (progs.isEmpty()) {
                    // No guide for this channel. The archive still exists, so offer times to jump to;
                    // only fall back to the "match your EPG" note when there is no archive window either.
                    if (jumpOffsetsSec.isNotEmpty()) {
                        CatchupJumpRows(
                            offsetsSec = jumpOffsetsSec,
                            firstFocus = firstFocus,
                            onPick = onJump,
                            modifier = Modifier.fillMaxWidth().height(listHeight),
                            onChooseExact = { manualTime = true },
                        )
                    } else {
                        Text(
                            stringResource(R.string.content_catchup_empty),
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(listHeight), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(progs, key = { it.id }) { p ->
                            FocusableSurface(
                                onClick = { onPick(p) },
                                modifier = if (p == progs.first()) Modifier.fillMaxWidth().focusRequester(firstFocus) else Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                contentAlignment = Alignment.CenterStart,
                                surface = GlassSurface.DIALOGS,
                            ) { _ ->
                                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(p.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatCatchupTime(p.startMs, p.stopMs, formatTime), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OwnTVButton(stringResource(R.string.content_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
        }
        }
    } // PopupFontTheme
    }
}

/** Manual EPG matching: pick which guide channel this channel uses (search across all EPG feeds).
 *  Shared with the Guide screen (long-press a channel → Match EPG). */
@Composable
internal fun EpgMatchDialog(
    channelName: String,
    currentMatch: String?,
    loadChannels: suspend (String) -> List<tv.own.owntv.core.database.entity.EpgChannelEntity>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var query by remember { mutableStateOf("") }
    val results by androidx.compose.runtime.produceState<List<tv.own.owntv.core.database.entity.EpgChannelEntity>?>(initialValue = null, query) {
        kotlinx.coroutines.delay(250)
        value = runCatching { loadChannels(query) }.getOrDefault(emptyList())
    }
    androidx.activity.compose.BackHandler { onDismiss() }

    // Pull focus into the dialog once the list first arrives (first result, else the search bar).
    // One-shot, so later search-driven reloads don't steal focus from the field while typing.
    val firstItemFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var didInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(results) {
        if (didInitialFocus || results == null) return@LaunchedEffect
        didInitialFocus = true
        kotlinx.coroutines.delay(60)
        if (results!!.isNotEmpty()) runCatching { firstItemFocus.requestFocus() }
        else runCatching { searchFocus.requestFocus() }
    }

    // Popup(focusable=true) is a hard focus boundary: a stray D-pad right/left with no target inside
    // can no longer drop focus onto the screen behind the scrim (same fix as EpgMatchReviewDialog).
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
    tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.75f) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().modalScrim().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        // Same small-screen cap as CatchupDialog: search bar + buttons must stay reachable.
        val listHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 260.dp).coerceIn(140.dp, 240.dp)
        Column(Modifier.dialogPanel(width = 384.dp, corner = 16.dp, padding = 14.dp)) {
            Text(stringResource(R.string.content_match_epg), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                if (currentMatch != null) {
                    stringResource(R.string.content_epg_match_prompt_current, channelName, currentMatch)
                } else {
                    stringResource(R.string.content_epg_match_prompt, channelName)
                },
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // Actions live in a right-hand column so a D-pad right from the search bar or ANY list row
            // reaches Close/Clear directly — no scrolling to the bottom of a long list.
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    SearchBar(query = query, onQueryChange = { query = it }, placeholder = stringResource(R.string.content_search_guide_channels), modifier = Modifier.fillMaxWidth().focusRequester(searchFocus), surface = GlassSurface.DIALOGS)
                    Spacer(Modifier.height(12.dp))
                    val list = results
                    when {
                        list == null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                        list.isEmpty() -> Text(
                            if (query.isBlank()) stringResource(R.string.content_no_epg_data) else stringResource(R.string.content_no_guide_channels, query),
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                        )
                        else -> LazyColumn(Modifier.fillMaxWidth().height(listHeight), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(list, key = { it.id }) { epg ->
                                FocusableSurface(
                                    onClick = { onPick(epg.epgChannelId) },
                                    modifier = if (epg == list.first()) Modifier.fillMaxWidth().focusRequester(firstItemFocus) else Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    contentAlignment = Alignment.CenterStart,
                                    surface = GlassSurface.DIALOGS,
                                ) { _ ->
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                                        Text(epg.displayName ?: epg.epgChannelId, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(epg.epgChannelId, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVButton(stringResource(R.string.content_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                    if (currentMatch != null) OwnTVButton(stringResource(R.string.content_clear_match), onClick = onClear, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    } // PopupFontTheme
    } // Popup
}

/**
 * Per-channel EPG time offset. Providers often hang both the East and the West stream of a network
 * off ONE guide, so one of them runs hours out; this moves that channel's guide only. Shared with the
 * Guide screen (long-press a channel → EPG offset). The change is written on "Done", so stepping
 * through a few hours is a single edit.
 */
@Composable
internal fun EpgOffsetDialog(
    channelName: String,
    currentMinutes: Int?,
    globalMinutes: Int,
    onSet: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var minutes by remember { mutableStateOf(currentMinutes ?: globalMinutes) }
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { doneFocus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
    tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.75f) {
    androidx.compose.foundation.layout.Box(
        // trapAllFocusExit, like every other dialog here: a D-pad press at the edge of a row must not
        // walk out of the popup onto the screen behind the scrim.
        Modifier.fillMaxSize().modalScrim()
            .trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.dialogPanel(width = 420.dp, corner = 16.dp, padding = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.content_epg_time_offset), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.content_epg_offset_channel_description, channelName),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(
                    stringResource(R.string.content_epg_shift_minutes, "−", "30"),
                    onClick = { minutes = (minutes - 30).coerceAtLeast(-12 * 60) },
                    style = OwnTVButtonStyle.SECONDARY, compact = true,
                )
                Text(
                    liveEpgShiftLabel(minutes),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.primary,
                    modifier = Modifier.width(120.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                OwnTVButton(
                    stringResource(R.string.content_epg_shift_minutes, "+", "30"),
                    onClick = { minutes = (minutes + 30).coerceAtMost(14 * 60) },
                    style = OwnTVButtonStyle.SECONDARY, compact = true,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    if (currentMinutes == null) R.string.content_epg_offset_following_global else R.string.content_epg_offset_channel_only,
                    liveEpgShiftLabel(globalMinutes),
                ),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnTVButton(
                    stringResource(R.string.common_done),
                    onClick = { onSet(minutes); onDismiss() },
                    modifier = Modifier.weight(1f).focusRequester(doneFocus),
                )
                if (currentMinutes != null) {
                    OwnTVButton(
                        stringResource(R.string.content_epg_offset_use_global),
                        onClick = { onSet(null); onDismiss() },
                        style = OwnTVButtonStyle.SECONDARY,
                        modifier = Modifier.weight(1f),
                    )
                }
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.weight(1f))
            }
        }
    }
    } // PopupFontTheme
    } // Popup
}

@Composable
private fun liveEpgShiftLabel(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.common_off)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.US
    val number = java.text.NumberFormat.getIntegerInstance(locale)
    val sign = if (minutes < 0) "−" else "+"
    val absolute = kotlin.math.abs(minutes)
    val hours = absolute / 60
    val remainder = absolute % 60
    return when {
        hours == 0 -> stringResource(R.string.content_epg_shift_minutes, sign, number.format(remainder))
        remainder == 0 -> stringResource(R.string.content_epg_shift_hours, sign, number.format(hours))
        else -> stringResource(
            R.string.content_epg_shift_hours_minutes,
            sign,
            number.format(hours),
            number.format(remainder),
        )
    }
}
