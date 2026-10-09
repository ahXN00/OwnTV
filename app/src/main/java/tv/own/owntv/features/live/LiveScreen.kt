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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.focus.FocusRequester as RowFocus
import tv.own.owntv.ui.theme.mpx
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.tv.material3.Text
import tv.own.owntv.R
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import tv.own.owntv.features.customize.categoryRestoreSummary
import tv.own.owntv.features.customize.CategoryRestorePlan
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
import tv.own.owntv.features.shell.components.CategoryContextMenu
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.jumpLazyListTo
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.components.ChannelGenre
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.ui.components.MenuAction
import tv.own.owntv.ui.components.arranged
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.ProviderChip
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.PreviewPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.gridFocusTarget
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.LocalPopupFontFamily
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.EpgNowNext

/** Live TV (Stage P4): the channel list, the stage beside it and the categories as a sheet or a column. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LiveScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean = true,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    onContentScrolled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * Pins the list to one folder and takes the category rail away — how More → Favourites and
     * More → History show channels without a second copy of this list existing.
     */
    lockedKey: LiveKey? = null,
    /** Guide view (Live TV tool row): opens the TV Guide until the in-place guide arrives (P5). */
    onOpenGuide: () -> Unit = {},
) {
    val vm: LiveViewModel = koinViewModel()
    // Locking and unlocking are one pair: the pin belongs to this screen's lifetime, not to the view
    // model's. On the television that view model is a single instance shared with the browse section,
    // so a pin left behind froze its category rail. `DisposableEffect` (not `LaunchedEffect`) also
    // means the pin is in place before the first frame, so the list never flashes the wrong folder.
    if (lockedKey != null) {
        val pinned = lockedKey
        DisposableEffect(pinned) {
            vm.lock(pinned)
            onDispose { vm.unlock() }
        }
    }
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val providerNames by vm.providerNames.collectAsStateWithLifecycle()
    val railFocus = remember { FocusRequester() }
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
    val categoryMoveState by vm.categoryMoveState.collectAsStateWithLifecycle()

    // Current programme title for each loaded channel (id → title), against the stored guide. Drives
    // the small "now playing" subtitle on each channel row. Channels with no guide are absent from the
    // map → their row shows no second line.
    //
    // An appended page asks only about the channels it added; the view model keeps the rest. This
    // used to re-query every loaded channel on every append and again every 60 seconds, which deep
    // in a large category was a dozen chunked queries a minute to learn nothing new.
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val loadedChannels = channels.itemSnapshotList.items.filterNotNull()
    LaunchedEffect(loadedChannels.size, loadedChannels.firstOrNull()?.id, loadedChannels.lastOrNull()?.id) {
        vm.ensureNowPlaying(loadedChannels)
    }
    // Turnover happens on the minute, so wait for the next one rather than 60s from mount — otherwise
    // rows change late and at different instants from each other.
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000 - (now % 60_000))
            vm.refreshNowPlaying(channels.itemSnapshotList.items.filterNotNull())
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
    // Right from the rail on an empty list: the list's search box, so a search with no results can be cleared.
    val listSearchFocus = remember { FocusRequester() }

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
    val perCategoryChannelIds = remember { mutableStateMapOf<LiveKey, Long>() }
    val effectiveListState =
        if (rememberLive) perCategoryStates.getOrPut(selectedKey) { androidx.compose.foundation.lazy.LazyListState() }
        else listState
    LaunchedEffect(selectedKey, rememberLive) {
        if (!rememberLive) runCatching { listState.scrollToItem(0) }
    }
    val catListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val categoryQuery by vm.categoryQuery.collectAsStateWithLifecycle()
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
    // The channel the "Schedule recording…" dialog is open for (#2).
    var schedulingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    // Programme picked in the catch-up dialog, awaiting the "Watch from start / Watch channel" choice.
    // The Live picker used to start the archive straight from the pick, so the same programme opened
    // from the Guide (which asks) and from here behaved differently — this makes the two match.
    var catchupDetail by remember {
        mutableStateOf<Pair<ChannelEntity, tv.own.owntv.core.database.entity.EpgProgrammeEntity>?>(null)
    }
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) } // long-press quick menu
    // Multiview: whether the menu offers it at all, how many tiles the grid has, and the confirmation
    // after a channel is kept (null = nothing to say).
    val liveSettings = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val multiviewEnabled by liveSettings.multiviewEnabled.collectAsStateWithLifecycle(false)
    val multiviewTiles by liveSettings.multiviewTiles.collectAsStateWithLifecycle(
        tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES,
    )
    // Also says where a scheduled recording landed ("Will record Sat 21:00").
    val multiviewToast = tv.own.owntv.ui.components.rememberInAppToast()
    val recordingChannelIds by vm.recordingChannelIds.collectAsStateWithLifecycle()
    // Resolved through resources rather than stringResource: the count is only known inside the click.
    val multiviewRes = androidx.compose.ui.platform.LocalContext.current.resources
    var contextCategory by remember { mutableStateOf<LiveRailItem?>(null) }
    // The rail row a category menu was opened from, kept after the menu closes so the cursor can go
    // back to that exact row. Held as a key, not an index: a Move changes the row's position.
    var contextCategoryKey by remember { mutableStateOf<LiveKey?>(null) }
    var railFocusRow by remember { mutableStateOf<Int?>(null) }
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
    // Bring row [idx] on screen only when it is not already fully there: returning focus to a row
    // that is visible must not yank the list so that row jumps to the top.
    suspend fun revealRow(idx: Int) {
        val info = effectiveListState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.index == idx }
        val fullyVisible = row != null && row.offset >= info.viewportStartOffset && row.offset + row.size <= info.viewportEndOffset
        if (!fullyVisible) runCatching { effectiveListState.scrollToItem(idx) }
    }
    // One requester per row position, for ▲/▼ stepping. Only composed rows are attached.
    val rowRequesters = remember { HashMap<Int, RowFocus>() }
    fun rowFocus(i: Int): RowFocus = rowRequesters.getOrPut(i) { RowFocus() }
    // Move to row [i]: scroll just enough to show it with one row of room (the edge-scroll rule), wait for
    // it to be laid out, then focus it. Works for a held key: nothing depends on focus search.
    // The row a burst of presses is heading for: every press counts from there, not from the row that
    // still has focus, and the newest press cancels the step still in flight.
    var stepTarget by remember { mutableStateOf<Int?>(null) }
    var stepJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun stepTo(i: Int) {
        stepTarget = i
        stepJob?.cancel()
        stepJob = scope.launch {
            val info = effectiveListState.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.index == i }
            val step = ((info.visibleItemsInfo.firstOrNull()?.size ?: 0) + info.mainAxisItemSpacing).toFloat()
            val delta = when {
                row == null -> if (i > (info.visibleItemsInfo.lastOrNull()?.index ?: 0)) step else -step
                row.offset - step < info.viewportStartOffset -> row.offset - step - info.viewportStartOffset
                row.offset + row.size + step > info.viewportEndOffset -> row.offset + row.size + step - info.viewportEndOffset
                else -> 0f
            }
            if (delta != 0f) runCatching { effectiveListState.scrollBy(delta) }
            if (effectiveListState.layoutInfo.visibleItemsInfo.none { it.index == i }) runCatching { effectiveListState.scrollToItem(i) }
            withFrameNanos { }
            runCatching { rowFocus(i).requestFocus() }
            stepTarget = null
        }
    }
    // Land focus back on the long-pressed channel's row (or a sensible fallback if it's gone).
    suspend fun restoreToContextRow() {
        val targetId = contextChannelId
        if (targetId == null) { runCatching { selFocus.requestFocus() }; return }

        val idx = channels.itemSnapshotList.items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            revealRow(idx)
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
        if (renaming != null || matchingEpg != null || offsettingEpg != null || catchupChannel != null || schedulingChannel != null || enteringMoveMode ||
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
    // Rename restoration: re-assert row focus after dialog closes.
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
    // Catch-up restoration.
    var catchupWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(catchupChannel, catchupDetail) {
        if (catchupChannel != null || catchupDetail != null) { catchupWasOpen = true; return@LaunchedEffect }
        if (!catchupWasOpen) return@LaunchedEffect
        catchupWasOpen = false
        restoreToContextRow()
    }
    // Schedule-recording restoration.
    var schedulingWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(schedulingChannel) {
        if (schedulingChannel != null) { schedulingWasOpen = true; return@LaunchedEffect }
        if (!schedulingWasOpen) return@LaunchedEffect
        schedulingWasOpen = false
        restoreToContextRow()
    }
    // Channel reorder restoration.
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
    // Category rail focus restoration.
    var contextCategoryWasOpen by remember { mutableStateOf(false) }
    var categoryMoveWasOpen by remember { mutableStateOf(false) }
    // Land on the row the menu was opened from; fall back to the column if that row is gone (Hide).
    fun restoreToContextCategory() {
        val row = contextCategoryKey?.let { k -> railItems.indexOfFirst { it.key == k } }?.takeIf { it >= 0 }
        contextCategoryKey = null
        if (row != null) railFocusRow = row else runCatching { railFocus.requestFocus() }
    }
    LaunchedEffect(contextCategory, categoryMoveState) {
        if (contextCategory != null) contextCategoryWasOpen = true
        if (categoryMoveState != null) categoryMoveWasOpen = true

        if (contextCategory == null && contextCategoryWasOpen && categoryMoveState == null) {
            contextCategoryWasOpen = false
            if (!categoryMoveWasOpen) {
                kotlinx.coroutines.delay(60)
                restoreToContextCategory()
            }
        }
        if (categoryMoveState == null && categoryMoveWasOpen) {
            categoryMoveWasOpen = false
            kotlinx.coroutines.delay(60)
            restoreToContextCategory()
        }
    }
    // Returning from fullscreen: scroll to and focus the channel you were watching (waits for the list to load).
    // Also used by "Startup → Live · Favorites": there's no remembered channel yet, so land on the first row
    // (not the nav panel).
    LaunchedEffect(restoreFocus, channels.itemCount) {
        if (!restoreFocus || channels.itemCount == 0) return@LaunchedEffect
        val ch = previewChannel
        val idx = if (ch != null) channels.itemSnapshotList.items.indexOfFirst { it.id == ch.id } else -1
        if (idx >= 0) {
            revealRow(idx)
            delay(60)
            runCatching { selFocus.requestFocus() }
        } else {
            delay(60)
            runCatching { firstItemFocus.requestFocus() }
        }
        onRestored()
    }
    // Search's "Go to channel": scroll to where the channel sits so that part of the list loads, find
    // its row by id and focus it. The list can still be the old category's for a moment, so focus is
    // re-asserted for a short while; nothing found after ~5 s lands on the first row.
    val reveal by vm.reveal.collectAsStateWithLifecycle()
    LaunchedEffect(reveal, selectedKey) {
        val r = reveal ?: return@LaunchedEffect
        if (selectedKey != r.key) return@LaunchedEffect
        if (rememberLive) perCategoryChannelIds[selectedKey] = r.id
        var found = false
        for (attempt in 0 until 50) {
            if (channels.itemCount > 0) {
                val idx = channels.itemSnapshotList.indexOfFirst { it?.id == r.id }
                if (idx >= 0) {
                    revealRow(idx)
                    withFrameNanos { }
                    if (runCatching { selFocus.requestFocus() }.getOrDefault(false)) found = true
                    if (found && attempt >= 8) break
                } else {
                    runCatching { effectiveListState.scrollToItem(r.position.coerceAtMost(channels.itemCount - 1)) }
                }
            }
            delay(100)
        }
        if (!found) runCatching { firstItemFocus.requestFocus() }
        vm.revealDone()
    }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)
    val selectedLabel = selectedItem?.displayLabel() ?: stringResource(R.string.content_category_all_channels)

    val liveLayout by vm.liveLayout.collectAsStateWithLifecycle()
    val nowProgrammes by vm.nowProgrammes.collectAsStateWithLifecycle()
    val playlistMarks by vm.playlistMarks.collectAsStateWithLifecycle()
    // Separate panels keeps the categories on screen; the Stage layout opens them as a sheet on Left.
    val separate = liveLayout == tv.own.owntv.core.settings.SettingsRepository.LiveLayout.SEPARATE && lockedKey == null
    var categoriesOpen by remember { mutableStateOf(false) }
    var sheetHadFocus by remember { mutableStateOf(false) }
    // The current channel's row. After a long-press that row carries contextFocus instead of selFocus
    // (gridFocusTarget prefers it), so both are tried before falling back to the first row.
    fun focusCurrentRow(): Boolean =
        runCatching { selFocus.requestFocus() }.getOrDefault(false) ||
            runCatching { contextFocus.requestFocus() }.getOrDefault(false) ||
            runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)
    val categoriesVisible = lockedKey == null && (separate || categoriesOpen)
    val railCounts by (if (categoriesVisible) vm.railCounts else remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<LiveKey, Int>()) })
        .collectAsStateWithLifecycle()
    val scheduleFocus = remember { FocusRequester() }
    var openProgramme by remember { mutableStateOf<Pair<ChannelEntity, tv.own.owntv.core.parser.XtEpgEntry>?>(null) }
    // Back closes the sheet: one level out.
    androidx.activity.compose.BackHandler(enabled = categoriesOpen) { categoriesOpen = false; focusCurrentRow() }
    LaunchedEffect(categoriesOpen) {
        if (categoriesOpen) { withFrameNanos { }; runCatching { railFocus.requestFocus() } }
        // However the sheet closed (Back, a pick, ◀ to the rail), the next one starts fresh.
        else sheetHadFocus = false
    }

    // Focus from the categories to the channel list: the remembered channel, else the first row.
    fun focusChannelList() {
        val targetId = if (rememberLive) perCategoryChannelIds[selectedKey] ?: previewChannel?.id else previewChannel?.id
        scope.launch {
            if (channels.itemCount > 0) {
                val targetIdx = if (targetId != null) {
                    channels.itemSnapshotList.items.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } ?: 0
                } else 0
                revealRow(targetIdx)
                withFrameNanos { }
                repeat(3) {
                    if (targetId != null && focusCurrentRow()) return@launch
                    if (runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)) return@launch
                    withFrameNanos { }
                }
            } else {
                runCatching { listSearchFocus.requestFocus() }
            }
        }
    }

    val (categoryEntries, groupsHeading) = liveCategoryEntries(railItems, railCounts)
    val headerLabel = if (selectedItem?.key is LiveKey.Folder || selectedItem?.key is LiveKey.Custom) ProviderTags.parse(selectedLabel).name else selectedLabel

    // CH± in the categories moves the highlight only; OK picks (owner, 2026-10-01).
    var catFocusIndex by remember { mutableStateOf<Int?>(null) }
    val categoriesModifier = Modifier
        .onFocusChanged {
            railPaneFocused = it.hasFocus
            if (it.hasFocus && previewEnabled) vm.stopPreview()
            // The sheet closes when focus leaves it (◀ to the rail) — not while its own menus are open,
            // and not on the "unfocused" report every node gets when it first attaches.
            if (it.hasFocus) sheetHadFocus = true
            else if (sheetHadFocus && categoriesOpen && contextCategory == null && categoryMoveState == null) {
                sheetHadFocus = false
                categoriesOpen = false
            }
        }
        .chNavPaging(
            enabled = chNavEnabled,
            upSkip = chNavUpSkip,
            downSkip = chNavDownSkip,
            isFocused = { railPaneFocused },
            lastIndex = { railItems.size - 1 },
            currentTargetIndex = { catFocusIndex ?: selectedIndex },
            onJumpToIndex = { idx -> catFocusIndex = idx; railFocusRow = idx },
        )
    val onCategorySelect: (Int) -> Unit = { idx ->
        railItems.getOrNull(idx)?.let { vm.select(it.key) }
        if (categoriesOpen) { categoriesOpen = false; focusChannelList() }
    }
    val onCategoryLongSelect: (Int) -> Unit = { idx ->
        railItems.getOrNull(idx)?.let { item ->
            if (item.key is LiveKey.Folder || item.key is LiveKey.Custom) {
                contextCategory = item
                contextCategoryKey = item.key
            }
        }
    }

    // Manual panel widths (Settings → Panel Width Adjustment), mapped onto Stage: Category = the sheet
    // (Stage) or the column (Separate), List and Preview split the rest; Preview 0% = full-width list.
    val panelShares = rememberPanelShares(PanelSection.LIVE, settingsVm)
    // The Stage layout has its own set: the sheet on its own scale, list + preview = 100.
    val panelOn by settingsVm.panelWidthEnabled.getValue(PanelSection.LIVE).collectAsStateWithLifecycle()
    val stageSaved by settingsVm.liveStageWidths.collectAsStateWithLifecycle()
    val stageWidths = stageSaved.takeIf { panelOn && !separate }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onChildFocused() },
    ) {
        // Horizontal geometry is a fraction of the mockup's 1920 width, so every zoom reflows.
        val screenW = maxWidth
        fun fx(px: Int) = screenW * (px / 1920f)
        val margin = fx(64)
        val row = screenW - margin * 2
        val previewShown = if (separate) panelShares?.preview != 0 else stageWidths?.preview != 0
        val liveHints = listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
            "◀" to stringResource(R.string.content_category_browser_title),
            "▶" to stringResource(R.string.content_key_schedule),
            stringResource(R.string.content_key_hold_ok) to stringResource(R.string.content_key_options),
        )
        val gapCat = fx(26)
        val gapStage = if (separate) fx(30) else fx(34)
        val colW: Dp
        val listW: Dp
        val stageW: Dp
        val sheetW: Dp
        if (separate && panelShares != null) {
            val p = computePanelWidths(panelShares, row, gapCat + if (previewShown) gapStage else 0.dp)
            colW = p.category; listW = p.list; stageW = p.preview; sheetW = 0.dp
        } else if (!separate && stageWidths != null) {
            // The sheet is a share of the screen; the row between the margins is list + preview.
            val rest = (row - if (previewShown) gapStage else 0.dp).coerceAtLeast(1.dp)
            colW = 0.dp
            sheetW = screenW * (stageWidths.sheet / 100f)
            listW = rest * (stageWidths.list / 100f)
            stageW = rest - listW
        } else {
            // The mockup's geometry.
            colW = fx(350); sheetW = fx(450)
            listW = if (separate) fx(620) else fx(846)
            stageW = if (separate) fx(766) else fx(912)
        }
        val listX = if (separate) margin + colW + gapCat else margin
        val listWidth = if (previewShown) listW else row - (listX - margin)

        if (separate) {
            LiveCategories(
                entries = categoryEntries,
                selectedIndex = selectedIndex,
                groupsHeading = groupsHeading,
                sheet = false,
                listState = catListState,
                onSelect = onCategorySelect,
                onLongSelect = onCategoryLongSelect,
                onNavigateRight = { focusChannelList() },
                focusRequester = railFocus,
                focusRowIndex = railFocusRow,
                onRowFocused = { railFocusRow = null },
                onRowFocus = { catFocusIndex = it },
                searchQuery = categoryQuery,
                onSearchQueryChange = vm::setCategoryQuery,
                modifier = categoriesModifier
                    .padding(start = margin, top = 128.mpx, bottom = 24.mpx)
                    .width(colW)
                    .fillMaxHeight(),
            )
        }

        val targetChannelIdState = remember {
            androidx.compose.runtime.derivedStateOf {
                if (rememberLive) perCategoryChannelIds[selectedKey] ?: previewChannel?.id else previewChannel?.id
            }
        }
        val targetChannelId by targetChannelIdState

        // The list side, dimmed to 40% while the sheet is over it (P3-02).
        Box(
            Modifier
                .fillMaxSize()
                .then(if (categoriesOpen) Modifier.graphicsLayer { alpha = 0.4f } else Modifier),
        ) {
            LiveHeader(
                category = headerLabel,
                count = count,
                showChevron = !separate && lockedKey == null,
                modifier = Modifier.padding(start = fx(84), top = 52.mpx).width(listX - fx(84) + listWidth),
            )
            // Header tools + channel list: one focus group.
            Column(
                modifier = Modifier
                    .padding(start = listX, top = 118.mpx)
                    .width(listWidth)
                    .fillMaxHeight()
                    .onFocusChanged { channelPaneFocused = it.hasFocus }
                    .chNavPaging(
                        enabled = chNavEnabled,
                        upSkip = chNavUpSkip,
                        downSkip = chNavDownSkip,
                        isFocused = { channelPaneFocused },
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
                            val target = channels.itemSnapshotList.items.getOrNull(idx)?.id
                            scope.launch {
                                runCatching { effectiveListState.scrollToItem(idx) }
                                withFrameNanos { }
                                if (target != null) {
                                    val item = channels.itemSnapshotList.items.firstOrNull { it.id == target }
                                    if (item != null) {
                                        // The remembered channel is the focus target, so it moves with the jump;
                                        // focus by row number, which holds whatever the row's other requesters are.
                                        vm.onChannelFocused(item)
                                        if (rememberLive) perCategoryChannelIds[selectedKey] = item.id
                                        withFrameNanos { }
                                        runCatching { rowFocus(idx).requestFocus() }
                                    }
                                } else {
                                    runCatching { firstItemFocus.requestFocus() }
                                }
                            }
                        },
                    )
                    // Entering this pane lands on a channel row, never the search field.
                    .focusProperties {
                        onEnter = {
                            if (targetChannelId == null || !focusCurrentRow()) runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                    // Held Up/Down never escape the list; pinned (More) keeps Up for its tabs.
                    .focusProperties {
                        onExit = {
                            when (requestedFocusDirection) {
                                // The rows handle ◀ and ▶ themselves; from the tool row neither leaves the pane.
                                FocusDirection.Left, FocusDirection.Right -> cancelFocusChange()
                                FocusDirection.Up, FocusDirection.Down -> if (lockedKey == null) cancelFocusChange()
                                else -> Unit
                            }
                        }
                    }
                    .focusGroup(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.mpx),
                    modifier = Modifier.padding(start = 4.mpx).focusGroup(),
                ) {
                    tv.own.owntv.ui.stage.StageSearchField(
                        query = searchQuery,
                        onQueryChange = vm::setSearchQuery,
                        placeholder = if (separate) stringResource(R.string.common_search) else stringResource(R.string.content_search_in, headerLabel),
                        // The mockup's share of the list width, but never more than the two tools leave:
                        // a narrow custom list shrinks the field, not "Guide view".
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .width(if (separate) listWidth * (230f / 620f) else listWidth * (456f / 846f))
                            .focusRequester(listSearchFocus)
                            .onFocusChanged { if (it.hasFocus && previewEnabled) vm.stopPreview() }
                            // ◀ Categories from the search field too, as from the rows (not while typing).
                            .onPreviewKeyEvent { e ->
                                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                                    e.key == androidx.compose.ui.input.key.Key.DirectionLeft &&
                                    searchQuery.isEmpty() && lockedKey == null
                                ) {
                                    if (separate) runCatching { railFocus.requestFocus() } else categoriesOpen = true
                                    true
                                } else false
                            },
                    )
                    tv.own.owntv.ui.stage.StageTool(
                        text = null,
                        icon = OwnTVIcon.SORT,
                        value = if (sortMode == tv.own.owntv.core.settings.SettingsRepository.SortMode.ALPHA) stringResource(R.string.settings_sort_alpha) else stringResource(R.string.content_epg_sort_provider),
                        onClick = vm::toggleSort,
                    )
                    tv.own.owntv.ui.stage.StageTool(
                        text = stringResource(R.string.content_live_guide_view),
                        icon = OwnTVIcon.GRID,
                        onClick = onOpenGuide,
                    )
                }
                Spacer(Modifier.height(20.mpx))
                if (channels.itemCount == 0) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isNotBlank()) stringResource(R.string.content_no_channels_found, searchQuery.trim()) else stringResource(R.string.content_no_channels_here),
                            style = tv.own.owntv.ui.theme.stageText(20, 500),
                            color = tv.own.owntv.ui.theme.StageColors.Muted,
                        )
                    }
                } else {
                    // The list clips whatever is drawn outside it, which cut the focused row's 44 px glow.
                    // It is laid out [glowRoom] wider at each side and padded back by the same amount,
                    // so the rows sit exactly where they did and the glow has room to show.
                    val glowRoom = 24.mpx
                    // Scroll only as far as the focused row needs to be on screen, as Home does: the TV
                    // default pins focus a third of the way down, so each ▼ moved the highlight a row
                    // and then slid the whole list back under it — a double step.
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides edgeScrollSpec) {
                    LazyColumn(
                        state = effectiveListState,
                        verticalArrangement = Arrangement.spacedBy(8.mpx),
                        modifier = Modifier.fillMaxWidth().weight(1f).layout { measurable, constraints ->
                            val extra = glowRoom.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = constraints.minWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2,
                                ),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-extra, 0) }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = glowRoom, end = glowRoom, bottom = 16.mpx),
                    ) {
                        items(
                            count = channels.itemCount,
                            key = channels.itemKey { it.id },
                            contentType = channels.itemContentType { "channel" },
                        ) { index ->
                            val channel = channels[index]
                            if (channel != null) {
                                val isPreviewed by remember(channel.id) {
                                    androidx.compose.runtime.derivedStateOf { previewChannel?.id == channel.id }
                                }
                                val isTarget by remember(channel.id) {
                                    androidx.compose.runtime.derivedStateOf { targetChannelIdState.value == channel.id }
                                }
                                val parsed = remember(channel.name) { ProviderTags.parse(channel.name) }
                                LiveStageRow(
                                    channel = channel,
                                    name = parsed,
                                    // The channel under the cursor answers from the preview itself, so the row
                                    // and the stage beside it can never disagree.
                                    now = if (isPreviewed) nowNext?.now ?: nowProgrammes[channel.id] else nowProgrammes[channel.id],
                                    nowTitle = nowPlaying[channel.id],
                                    isFavorite = favoriteIds.contains(channel.id),
                                    showNumber = showChannelNumbers,
                                    mark = playlistMarks[channel.sourceId],
                                    modifier = Modifier.gridFocusTarget(
                                        itemId = channel.id, index = index,
                                        contextId = contextChannelId, contextFocus = contextFocus,
                                        selectedId = if (isTarget) channel.id else null, selectedFocus = selFocus,
                                        firstItemFocus = firstItemFocus,
                                    ).focusRequester(rowFocus(index)).onPreviewKeyEvent { e ->
                                        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (e.key) {
                                            // D3: the remote's Menu key opens the same menu as holding OK.
                                            androidx.compose.ui.input.key.Key.Menu -> { contextChannel = channel; contextChannelId = channel.id; true }
                                            // ◀ Categories: the sheet (Stage) or the column (Separate); pinned lists have none.
                                            androidx.compose.ui.input.key.Key.DirectionLeft -> when {
                                                lockedKey != null -> false
                                                separate -> { runCatching { railFocus.requestFocus() }; true }
                                                else -> { categoriesOpen = true; true }
                                            }
                                            // ▶ Schedule: next / later under the stage; nothing else to the right.
                                            androidx.compose.ui.input.key.Key.DirectionRight -> { runCatching { scheduleFocus.requestFocus() }; true }
                                            // ▲/▼ step by row number, not by focus search: a held key outran the
                                            // lazy list at the edge or a page boundary and stopped (owner).
                                            androidx.compose.ui.input.key.Key.DirectionDown -> (stepTarget ?: index).let { from -> from + 1 < channels.itemCount && run { stepTo(from + 1); true } }
                                            androidx.compose.ui.input.key.Key.DirectionUp -> (stepTarget ?: index).let { from -> from > 0 && run { stepTo(from - 1); true } }
                                            else -> false
                                        }
                                    },
                                    onFocus = {
                                        vm.onChannelFocused(channel)
                                        if (rememberLive) perCategoryChannelIds[selectedKey] = channel.id
                                    },
                                    onClick = {
                                        vm.watchFullscreen(channel, channels.itemSnapshotList.items.filterNotNull())
                                        if (!externalPlayerOn) onFullscreen()
                                    },
                                    onLongClick = { contextChannel = channel; contextChannelId = channel.id },
                                )
                            }
                        }
                    }
                    }
                }
                // With the preview off the hints stay under the list, where there is nothing else to hold them.
                if (lockedKey == null && !previewShown) {
                    tv.own.owntv.ui.stage.StageKeyHints(
                        liveHints,
                        // A clear gap under the list, and 40 from the bottom rather than the mockup's 26:
                        // TV panels overscan the very edge away (owner, 2026-09-30).
                        modifier = Modifier.padding(start = 16.mpx, top = 22.mpx, bottom = 40.mpx),
                    )
                }
            }

            // Under the preview, bottom right (owner, 2026-10-01), so the list runs down to the bottom edge.
            if (lockedKey == null && previewShown) {
                tv.own.owntv.ui.stage.StageKeyHints(
                    liveHints,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = margin, bottom = 40.mpx),
                )
            }

            if (previewShown) {
                LiveStagePane(
                    channel = previewChannel,
                    channelName = previewChannel?.let { ProviderTags.parse(it.name).name },
                    nowNext = nowNext,
                    previewEngine = vm.previewEngine,
                    showVideo = effectivePreview,
                    singleSessionBlocked = previewBlockedSingleSession,
                    scheduleFocus = scheduleFocus,
                    onOpenProgramme = { p -> previewChannel?.let { openProgramme = it to p } },
                    onBackToList = { focusCurrentRow() },
                    modifier = Modifier
                        .padding(start = screenW - margin - stageW, top = 120.mpx)
                        .width(stageW),
                )
            }
        }

        if (categoriesOpen) {
            LiveCategories(
                entries = categoryEntries,
                selectedIndex = selectedIndex,
                groupsHeading = groupsHeading,
                sheet = true,
                listState = catListState,
                onSelect = onCategorySelect,
                onLongSelect = onCategoryLongSelect,
                onNavigateRight = { categoriesOpen = false; focusChannelList() },
                focusRequester = railFocus,
                focusRowIndex = railFocusRow,
                onRowFocused = { railFocusRow = null },
                onRowFocus = { catFocusIndex = it },
                searchQuery = categoryQuery,
                onSearchQueryChange = vm::setCategoryQuery,
                modifier = categoriesModifier
                    .padding(start = fx(24), top = 24.mpx, bottom = 24.mpx)
                    .width(sheetW)
                    .fillMaxHeight(),
            )
        }
    }

    // A next / later programme opened from the schedule under the stage (▶ Schedule): the TV Guide's
    // programme menu (P4-02), acting through the guide's view model so both screens do the same thing.
    openProgramme?.let { (ch, xt) ->
        val epgVm: tv.own.owntv.features.epg.EpgViewModel = koinViewModel()
        val leadMinutes by epgVm.reminderLeadMinutes.collectAsStateWithLifecycle()
        val p = remember(ch.id, xt.startMs) {
            tv.own.owntv.core.database.entity.EpgProgrammeEntity(
                sourceId = ch.sourceId, epgChannelId = ch.epgChannelId.orEmpty(),
                startMs = xt.startMs, stopMs = xt.stopMs, title = xt.title, description = xt.description,
            )
        }
        val formatTime = rememberSystemTimeFormatter()
        tv.own.owntv.features.epg.GuideProgrammeMenu(
            title = p.title,
            subtitle = listOf(
                stringResource(R.string.content_live_time_range_plain, formatTime(p.startMs), formatTime(p.stopMs)),
                listOfNotNull(ch.number?.toString(), ProviderTags.parse(ch.name).name).joinToString(" "),
            ).joinToString(" · "),
            actions = tv.own.owntv.features.epg.guideProgrammeActions(
                epgVm, ch, p, System.currentTimeMillis(),
                onWatch = { vm.watchFullscreen(ch, emptyList()); if (!externalPlayerOn) onFullscreen() },
                onPlayFromStart = { vm.playCatchupProgramme(ch, p); if (!externalPlayerOn) onFullscreen() },
                onPickEpg = { contextChannelId = ch.id; matchingEpg = ch },
                onEpgOffset = { contextChannelId = ch.id; offsettingEpg = ch },
                autoMatch = false,
            ),
            leadMinutes = leadMinutes,
            onDismiss = { openProgramme = null; runCatching { scheduleFocus.requestFocus() } },
        )
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
            channel = ch,
            title = ch.name,
            subtitle = listOfNotNull(headerLabel, vm.sourceNameOf(ch.sourceId)).joinToString(" · "),
            isFavorite = favoriteIds.contains(ch.id),
            epgMatchManual = vm.currentEpgMatch(ch) != null,
            epgShiftMinutes = vm.currentEpgShift(ch),
            canMove = selectedKey is LiveKey.Folder || selectedKey is LiveKey.Custom || selectedKey == LiveKey.Favorites,
            isHistory = selectedKey == LiveKey.History,
            onToggleFavorite = { vm.toggleFavorite(ch); contextChannel = null },
            onRename = { renaming = ch; contextChannel = null },
            onHide = { vm.hideChannel(ch); contextChannel = null },
            onMatchEpg = { matchingEpg = ch; contextChannel = null },
            onEpgOffset = { offsettingEpg = ch; contextChannel = null },
            onCatchup = { catchupChannel = ch; contextChannel = null },
            isRecording = ch.id in recordingChannelIds,
            onRecord = { if (ch.id in recordingChannelIds) vm.stopRecordingOn(ch) else vm.recordNow(ch); contextChannel = null },
            onScheduleRecord = { schedulingChannel = ch; contextChannel = null },
            onPlayExternal = { vm.playExternal(ch); contextChannel = null },
            // Only offered once Multiview is switched on. Adding is silent apart from the toast: the
            // grid opens when the user plays a channel, which is the gesture that says "now".
            onAddToMultiview = if (multiviewEnabled) {
                {
                    vm.addToMultiview(ch, multiviewTiles)
                    multiviewToast.show(
                        multiviewRes.getString(
                            R.string.multiview_added,
                            vm.multiviewSelection.value.size,
                            multiviewTiles,
                        ),
                    )
                    contextChannel = null
                }
            } else {
                null
            },
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
            onRemoveFromCategory = (selectedKey as? LiveKey.Custom)?.let { k -> { vm.removeFromCustomCategory(ch, k); contextChannel = null } },
            onDismiss = { contextChannel = null },
        )
    }

    val scheduledDay = tv.own.owntv.ui.format.rememberBestDateFormatter("EEEMMMd")
    val scheduledTime = tv.own.owntv.ui.format.rememberSystemTimeFormatter()
    schedulingChannel?.let { ch ->
        ScheduleRecordingDialog(
            channelName = ch.name,
            clashesFor = { start, stop -> vm.recordingClashes(ch, start, stop) },
            onSchedule = { start, stop ->
                schedulingChannel = null
                vm.scheduleRecording(ch, start, stop) { at ->
                    multiviewToast.show(multiviewRes.getString(R.string.recording_scheduled_for, scheduledDay(at) + " " + scheduledTime(at)))
                }
            },
            onDismiss = { schedulingChannel = null },
        )
    }

    tv.own.owntv.ui.components.InAppToast(multiviewToast)

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

    // Category Move mode overlay — intercepts D-pad Up/Down/OK/Back while reordering.
    categoryMoveState?.let { ms ->
        MoveOrderOverlay(
            title = stringResource(R.string.content_move),
            itemNames = ms.items,
            activeIndex = ms.activeIndex,
            onMoveUp = vm::moveCategoryUp,
            onMoveDown = vm::moveCategoryDown,
            onCommit = vm::commitCategoryMove,
            onCancel = vm::cancelCategoryMove,
        )
    }

    contextCategory?.let { item ->
        val restorePlan by produceState<CategoryRestorePlan?>(null, item.key) { value = vm.categoryRestorePlan(item.key) }
        CategoryContextMenu(
            categoryName = item.displayLabel(),
            canHide = item.key is LiveKey.Folder || item.key is LiveKey.Custom,
            canMove = item.key is LiveKey.Folder || item.key is LiveKey.Custom,
            onHide = { vm.hideCategory(item.key); contextCategory = null },
            onMove = { vm.enterCategoryMoveMode(item.key); contextCategory = null },
            // Always offered: the plan is read in the background and only fills in the confirmation.
            resetLabel = stringResource(if (item.key is LiveKey.Custom) R.string.content_category_restore_custom else R.string.content_category_restore_playlist),
            resetLines = restorePlan?.let { categoryRestoreSummary(it) },
            onReset = { vm.restoreCategory(item.key); contextCategory = null },
            onDismiss = { contextCategory = null }
        )
    }
}

/**
 * ☰ Channel options (P3-04): the Stage options menu, 540 wide over the stage, above the scrim. Header
 * = the logo plate, "256 · Sky Cinema Premieren" and "category · playlist"; the actions in the groups
 * WATCH · CHANNEL · GUIDE DATA · ORGANISE with their values (Catch-up "7 days", Match EPG "Auto",
 * EPG time offset "Global"). The order and visibility are the user's (Settings › Long-press menus ›
 * Live TV); a group label is drawn wherever the group changes.
 */
@Composable
private fun ChannelContextMenu(
    channel: ChannelEntity,
    title: String,
    subtitle: String?,
    isFavorite: Boolean,
    canMove: Boolean,
    isHistory: Boolean,
    epgMatchManual: Boolean,
    epgShiftMinutes: Int?,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onMatchEpg: () -> Unit,
    onEpgOffset: () -> Unit,
    onCatchup: () -> Unit,
    // A recording of this channel is running: Record becomes Stop recording.
    isRecording: Boolean,
    onRecord: () -> Unit,
    onScheduleRecord: () -> Unit,
    onPlayExternal: () -> Unit,
    // Null unless Multiview is switched on: keep this channel for the grid (Plan D, B5).
    onAddToMultiview: (() -> Unit)?,
    onMove: () -> Unit,
    // "Move to category…" (issue #87): send this channel into a user's combined category.
    onMoveToCategory: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    // Only inside a custom category: take the channel out of that category alone.
    onRemoveFromCategory: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    val catchupValue = channel.catchupDays.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.settings_epg_guide_days_value, it, it) }
    val matchValue = if (epgMatchManual) stringResource(R.string.settings_manual) else stringResource(R.string.settings_auto)
    val offsetValue = epgShiftMinutes?.let { liveEpgShiftLabel(it) } ?: stringResource(R.string.content_epg_offset_global_short)
    // Group ids = the mockup's four groups, in its order.
    val watch = 0; val chan = 1; val guide = 2; val organise = 3
    val actions = buildList {
        if (channel.catchup) add(MenuAction("catchup", stringResource(R.string.content_catchup), OwnTVIcon.REWIND, group = watch, onClick = onCatchup))
        // Record this channel from now. The guide's Record needs a programme, so a channel
        // the provider publishes no guide for can only be recorded from here. While it records, the
        // same row stops it — pressing Record again would start a second copy on another connection.
        add(MenuAction("record", stringResource(if (isRecording) R.string.recording_stop else R.string.recording_record), OwnTVIcon.REC, group = watch, onClick = onRecord))
        add(MenuAction("schedule_record", stringResource(R.string.recording_schedule), OwnTVIcon.CLOCK, group = watch, onClick = onScheduleRecord))
        if (onAddToMultiview != null) {
            add(MenuAction("add_to_multiview", stringResource(R.string.multiview_add_to), OwnTVIcon.MULTIVIEW, group = watch, onClick = onAddToMultiview))
        }
        // Always offered, regardless of the Live TV external-player default — this is the per-channel
        // escape hatch for a stream neither in-app engine can open (same as Movies/Series/Downloads).
        add(MenuAction("play_external", stringResource(R.string.content_play_external_short), OwnTVIcon.EXTERNAL, group = watch, onClick = onPlayExternal))
        add(MenuAction("favourite", if (isFavorite) stringResource(R.string.content_remove_favourite) else stringResource(R.string.content_add_favourite), OwnTVIcon.FAVORITE, group = chan, onClick = onToggleFavorite))
        add(MenuAction("rename", stringResource(R.string.content_rename), OwnTVIcon.PENCIL, group = chan, onClick = onRename))
        add(MenuAction("hide", stringResource(R.string.content_hide_channel), OwnTVIcon.EYE_OFF, group = chan, onClick = onHide))
        if (isHistory) add(MenuAction("remove_history", stringResource(R.string.content_remove_history), OwnTVIcon.HISTORY, group = chan, onClick = onRemoveFromHistory))
        add(MenuAction("match_epg", stringResource(R.string.content_match_epg), OwnTVIcon.EPG, group = guide, onClick = onMatchEpg))
        add(MenuAction("epg_offset", stringResource(R.string.content_epg_time_offset), OwnTVIcon.CLOCK, group = guide, onClick = onEpgOffset))
        if (canMove) {
            add(MenuAction("move", stringResource(R.string.content_move), OwnTVIcon.MOVE, group = organise, onClick = onMove))
            add(MenuAction("move_to_category", stringResource(R.string.content_move_to_category), OwnTVIcon.FOLDER, group = organise, onClick = onMoveToCategory))
        }
        if (onRemoveFromCategory != null) {
            add(MenuAction("remove_from_category", stringResource(R.string.content_remove_from_category), OwnTVIcon.CLOSE, group = organise, onClick = onRemoveFromCategory))
        }
    }
    val values = mapOf("catchup" to catchupValue, "match_epg" to matchValue, "epg_offset" to offsetValue)
    val groupLabels = listOf(
        stringResource(R.string.content_menu_group_watch),
        stringResource(R.string.content_menu_group_channel),
        stringResource(R.string.content_menu_group_guide_data),
        stringResource(R.string.content_menu_group_organise),
    ).map { it.uppercase(androidx.compose.ui.text.intl.Locale.current.platformLocale) }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color(2, 5, 6).copy(alpha = 0.55f)).longPressMenuGuard(),
        ) {
            val w = maxWidth
            tv.own.owntv.ui.stage.StageMenu(
                Modifier
                    .padding(start = w * (930f / 1920f), top = 118.mpx, bottom = 24.mpx)
                    .width(540.mpx)
                    .verticalScroll(rememberScrollState())
                    .trapAllFocusExit()
                    .focusGroup(),
            ) {
                tv.own.owntv.ui.stage.StageMenuHeader(
                    title = listOfNotNull(channel.number?.toString(), ProviderTags.parse(channel.name).name).joinToString(" · "),
                    subtitle = subtitle,
                    leading = {
                        LivePlate(channel.displayLogoUrl, 70.mpx, 50.mpx)
                    },
                )
                var previousGroup: Int? = null
                arranged(ContentMenu.LIVE, actions).forEachIndexed { index, action ->
                    if (action.group != previousGroup) tv.own.owntv.ui.stage.StageGroupLabel(groupLabels[action.group])
                    previousGroup = action.group
                    tv.own.owntv.ui.stage.StageMenuItem(
                        text = action.label,
                        icon = action.icon,
                        iconFilled = action.icon == OwnTVIcon.REC,
                        value = values[action.key],
                        onClick = action.onClick,
                        modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                    )
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
    val formatTime = rememberSystemTimeFormatter()
    val list by androidx.compose.runtime.produceState<List<tv.own.owntv.core.database.entity.EpgProgrammeEntity>?>(initialValue = null) {
        value = runCatching { loadProgrammes() }.getOrDefault(emptyList())
    }
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
    val noGuide = list?.isEmpty() == true && jumpOffsetsSec.isNotEmpty()
    // A Stage popup: its own window, so nothing behind can take focus (as EpgMatchDialog / the menus).
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_catchup_title, channelName),
        body = stringResource(if (noGuide) R.string.content_catchup_jump_prompt else R.string.content_catchup_prompt),
        eyebrow = null,
        scroll = false,
        buttons = { tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_close), onClick = onDismiss, height = 56.mpx, textSize = 19) },
    ) {
        when (val progs = list) {
            null -> Box(Modifier.fillMaxWidth().height(100.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
            else -> if (progs.isEmpty()) {
                // No guide for this channel. The archive still exists, so offer times to jump to; only
                // fall back to the "match your EPG" note when there is no archive window either.
                if (jumpOffsetsSec.isNotEmpty()) {
                    CatchupJumpRows(
                        offsetsSec = jumpOffsetsSec,
                        firstFocus = firstFocus,
                        onPick = onJump,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        onChooseExact = { manualTime = true },
                    )
                } else {
                    Text(stringResource(R.string.content_catchup_empty), style = tv.own.owntv.ui.theme.stageText(18, 500), color = tv.own.owntv.ui.theme.StageColors.Muted)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.mpx)) {
                    items(progs, key = { it.id }) { p ->
                        tv.own.owntv.ui.stage.StagePopupOption(
                            title = p.title, subtitle = formatCatchupTime(p.startMs, p.stopMs, formatTime), onClick = { onPick(p) },
                            modifier = if (p == progs.first()) Modifier.focusRequester(firstFocus) else Modifier,
                            leading = { tv.own.owntv.ui.stage.StagePopupIcon(tv.own.owntv.ui.components.OwnTVIcon.CATCHUP) },
                        )
                    }
                }
            }
        }
    }
}

/** Manual EPG matching: pick which guide channel this channel uses (search across all EPG feeds).
 *  Shared with the Guide screen (long-press a channel → Match EPG). */
@Composable
internal fun EpgMatchDialog(
    channelName: String,
    currentMatch: String?,
    loadChannels: suspend (String) -> List<tv.own.owntv.core.epg.GuideCandidate>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results by androidx.compose.runtime.produceState<List<tv.own.owntv.core.epg.GuideCandidate>?>(initialValue = null, query) {
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

    // The Stage picker: a menu panel over the scrim, focus trapped inside, the list capped so Close stays reachable.
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(Color(2, 5, 6).copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            tv.own.owntv.ui.stage.StageMenu(Modifier.width(640.mpx).trapAllFocusExit().focusGroup()) {
                tv.own.owntv.ui.stage.StageMenuHeader(
                    title = stringResource(R.string.content_match_epg),
                    subtitle = if (currentMatch != null) {
                        stringResource(R.string.content_epg_match_prompt_current, channelName, currentMatch)
                    } else {
                        stringResource(R.string.content_epg_match_prompt, channelName)
                    },
                )
                tv.own.owntv.ui.stage.StageSearchField(
                    query = query, onQueryChange = { query = it },
                    placeholder = stringResource(R.string.content_search_guide_channels).trimEnd('…'),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.mpx).focusRequester(searchFocus),
                )
                Spacer(Modifier.height(12.mpx))
                val list = results
                when {
                    list == null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(120.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 24) }
                    list.isEmpty() -> Text(
                        if (query.isBlank()) stringResource(R.string.content_no_epg_data) else stringResource(R.string.content_no_guide_channels, query),
                        style = tv.own.owntv.ui.theme.stageText(18, 500), color = tv.own.owntv.ui.theme.StageColors.Muted,
                        modifier = Modifier.padding(horizontal = 14.mpx, vertical = 12.mpx),
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth().height(400.mpx)) {
                        items(list, key = { it.epgChannelId }) { epg ->
                            tv.own.owntv.ui.stage.StageMenuItem(
                                text = epg.displayName ?: epg.epgChannelId,
                                value = epg.epgChannelId.takeIf { epg.displayName != null },
                                checked = epg.epgChannelId == currentMatch,
                                onClick = { onPick(epg.epgChannelId) },
                                modifier = if (epg == list.first()) Modifier.focusRequester(firstItemFocus) else Modifier,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.mpx),
                    horizontalArrangement = Arrangement.spacedBy(12.mpx, Alignment.End),
                ) {
                    if (currentMatch != null) tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_clear_match), onClick = onClear, height = 56.mpx, textSize = 19)
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_close), onClick = onDismiss, height = 56.mpx, textSize = 19, tinted = true)
                }
            }
        }
    }
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
    var minutes by remember { mutableStateOf(currentMinutes ?: globalMinutes) }
    val valueFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { valueFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_epg_time_offset),
        body = stringResource(R.string.content_epg_offset_channel_description, channelName),
        eyebrow = null,
        width = 760.mpx,
        buttons = {
            if (currentMinutes != null) tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_epg_offset_use_global), onClick = { onSet(null); onDismiss() }, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = { onSet(minutes); onDismiss() }, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        // ◀ ▶ shift by half an hour, -12 h … +14 h; Done keeps it.
        tv.own.owntv.ui.stage.StageSurface(
            onClick = { onSet(minutes); onDismiss() },
            radius = 18.mpx,
            focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
            modifier = Modifier.fillMaxWidth().height(84.mpx).focusRequester(valueFocus).onPreviewKeyEvent { e ->
                val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                if (d != 0 && e.type == KeyEventType.KeyDown) minutes = (minutes + d * 30).coerceIn(-12 * 60, 14 * 60)
                d != 0
            },
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.mpx), verticalAlignment = Alignment.CenterVertically) {
                tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(26.mpx).graphicsLayer { rotationZ = 180f })
                Text(liveEpgShiftLabel(minutes), style = tv.own.owntv.ui.theme.stageText(36, 800), color = tv.own.owntv.ui.theme.stageAccent.accent, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(26.mpx))
            }
        }
        Text(
            stringResource(if (currentMinutes == null) R.string.content_epg_offset_following_global else R.string.content_epg_offset_channel_only, liveEpgShiftLabel(globalMinutes)),
            style = tv.own.owntv.ui.theme.stageText(15, 500), color = tv.own.owntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(top = 12.mpx),
        )
    }
}

/** Remind me's value: "5 min before", or "At the start" when the reminder comes at the start. */
@Composable
internal fun reminderLeadText(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.settings_reminder_at_start)
    else stringResource(R.string.content_remind_before, stringResource(R.string.player_duration_minutes, minutes))

@Composable
internal fun liveEpgShiftLabel(minutes: Int): String {
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
