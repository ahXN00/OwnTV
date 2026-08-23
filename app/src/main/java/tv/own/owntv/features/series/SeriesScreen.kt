package tv.own.owntv.features.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
// Aliased: the grid and list versions share a name, and both are used in this file.
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.features.customize.MoveToCategoryDialog
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.features.live.LiveKey
import tv.own.owntv.features.live.displayLabel
import tv.own.owntv.core.settings.PanelSection
import tv.own.owntv.features.settings.data.BrowseColumnGap
import tv.own.owntv.features.settings.data.BrowseColumnDividerSpace
import tv.own.owntv.features.settings.data.BrowseContainerPadding
import tv.own.owntv.features.settings.data.browsePanelGapTotal
import tv.own.owntv.features.settings.data.computePanelWidths
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.settings.rememberPanelShares
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.ui.components.InAppToast
import tv.own.owntv.ui.components.rememberInAppToast
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.ui.components.MenuAction
import tv.own.owntv.ui.components.arranged
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.PosterCard
import tv.own.owntv.ui.components.ProviderChip
import tv.own.owntv.ui.components.ProgressRing
import tv.own.owntv.ui.components.ResumeDialog
import tv.own.owntv.ui.components.formatTimestamp
import tv.own.owntv.ui.components.SetTmdbNameDialog
import tv.own.owntv.ui.components.TrailerPlayerScreen
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.longPressMenuGuard
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.components.SortChip
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.PreviewPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.gridFocusTarget
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.format.localizedInteger

@Composable
fun SeriesScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: SeriesViewModel = koinViewModel()
    val openedSeries by vm.openedSeries.collectAsStateWithLifecycle()

    // Track leaving a show so the grid can put focus back on the poster you came from (the episode
    // view that held focus is unmounted on Back — focus would otherwise die and land on the sidebar).
    var returnFromShow by remember { mutableStateOf(false) }
    LaunchedEffect(openedSeries) { if (openedSeries != null) returnFromShow = true }

    if (openedSeries != null) {
        EpisodeView(
            series = openedSeries!!,
            vm = vm,
            onFullscreen = onFullscreen,
            onChildFocused = onChildFocused,
            restoreFocus = restoreFocus,
            onRestored = onRestored,
            modifier = modifier,
        )
    } else {
        // Not in a show → nothing episode-specific to restore; clear the flag so it doesn't linger.
        if (restoreFocus) onRestored()
        SeriesGrid(
            vm = vm,
            onChildFocused = onChildFocused,
            restoreSelected = returnFromShow,
            onRestoredSelected = { returnFromShow = false },
            modifier = modifier,
        )
    }
}

@Composable
private fun SeriesContextMenu(
    title: String,
    isFavorite: Boolean,
    canMove: Boolean,
    isHistory: Boolean,
    hasTmdbDetails: Boolean,
    trailerKey: String?,
    canRefetchTmdb: Boolean,
    onShowDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMove: () -> Unit,
    // "Move to category…" (issue #87): send this series into a user's combined category.
    onMoveToCategory: () -> Unit,
    onHide: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onDownload: () -> Unit,
    onRefetch: () -> Unit,
    onSetTmdbName: () -> Unit,
    onPlayTrailer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim()
            .trapAllFocusExit().focusGroup()
            .longPressMenuGuard(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            // The menu as data: same actions, same gating, same order as the buttons that used to be
            // written out here one by one. Close is not in the list — it stays pinned last.
            val actions = buildList {
                add(MenuAction("favourite", if (isFavorite) stringResource(R.string.content_remove_favourite) else stringResource(R.string.content_add_favourite), OwnTVIcon.FAVORITE, onClick = onToggleFavorite))
                if (canMove) add(MenuAction("move", stringResource(R.string.content_move), onClick = onMove))
                if (canMove) add(MenuAction("move_to_category", stringResource(R.string.content_move_to_category), onClick = onMoveToCategory))
                if (isHistory) add(MenuAction("remove_history", stringResource(R.string.content_remove_history), onClick = onRemoveFromHistory))
                add(MenuAction("hide", stringResource(R.string.common_hide), onClick = onHide))
                add(MenuAction("download", stringResource(R.string.content_download_all_episodes), OwnTVIcon.DOWNLOADS, onClick = onDownload))
                if (hasTmdbDetails) add(MenuAction("tmdb_details", stringResource(R.string.content_tmdb_details), OwnTVIcon.MENU, onClick = onShowDetails))
                // Play Trailer (§7.3 U4) — only when TMDB actually has a trailer for this show (§11.1 gating).
                trailerKey?.let { key -> add(MenuAction("play_trailer", stringResource(R.string.content_play_trailer)) { onPlayTrailer(key) }) }
                // Refetch TMDB details (§11.2 U5a) — clear a wrong/stale match (or a 7-day "no match" cache) and re-search.
                if (canRefetchTmdb) {
                    add(MenuAction("refetch_tmdb", stringResource(R.string.content_refetch_tmdb), onClick = onRefetch))
                    // Set TMDB name (§11.2 U5b) — hand-type the exact TMDB title when the auto-match is wrong.
                    add(MenuAction("set_tmdb_name", stringResource(R.string.content_set_tmdb_name), onClick = onSetTmdbName))
                }
            }
            arranged(ContentMenu.SERIES, actions).forEachIndexed { index, action ->
                OwnTVButton(
                    action.label,
                    onClick = action.onClick,
                    style = OwnTVButtonStyle.SECONDARY,
                    icon = action.icon,
                    modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(focus) else Modifier),
                )
            }
            Spacer(Modifier.height(4.dp))
            OwnTVButton(stringResource(R.string.content_close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SeriesGrid(
    vm: SeriesViewModel,
    onChildFocused: () -> Unit,
    restoreSelected: Boolean = false,
    onRestoredSelected: () -> Unit = {},
    modifier: Modifier,
) {
    val alreadyDownloadedMessage = stringResource(R.string.content_already_downloaded)
    val refetchingTmdbMessage = stringResource(R.string.content_refetching_tmdb)
    val researchingTmdbMessage = stringResource(R.string.content_researching_tmdb)
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val providerNames by vm.providerNames.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val selectedSeries by vm.selectedSeries.collectAsStateWithLifecycle()
    val selectedSeriesMeta by vm.selectedSeriesMeta.collectAsStateWithLifecycle()
    val selectedSeriesDownloads by vm.selectedSeriesDownloads.collectAsStateWithLifecycle()
    val metadataMode by vm.metadataMode.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val toast = rememberInAppToast()
    val series = vm.series.collectAsLazyPagingItems()
    val moveState by vm.moveState.collectAsStateWithLifecycle()
    var contextSeries by remember { mutableStateOf<tv.own.owntv.core.database.entity.SeriesEntity?>(null) }
    // The series the "Move to category…" flow is moving (issue #87), with the origin captured at
    // menu-open time (the rail can't change under the modal, but capturing is still safer).
    var moveItem by remember { mutableStateOf<tv.own.owntv.core.database.entity.SeriesEntity?>(null) }
    var moveOriginKey by remember { mutableStateOf<String?>(null) }
    var moveOriginName by remember { mutableStateOf<String?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    // "Set TMDB name" dialog target (§11.2 U5b); null = closed.
    var setTmdbNameSeries by remember { mutableStateOf<tv.own.owntv.core.database.entity.SeriesEntity?>(null) }
    // In-app trailer playback (§7.3 U4); non-null = fullscreen player open with this YouTube key.
    var trailerVideoKey by remember { mutableStateOf<String?>(null) }
    // Fullscreen TMDB details window (§11.1); null = closed.
    var detailsSeries by remember { mutableStateOf<tv.own.owntv.core.database.entity.SeriesEntity?>(null) }
    // Id + list position of the series the context menu was opened on. The id re-focuses the same item
    // when it survives (Favourite/Download/Cancel); when the item is REMOVED (Remove from history, or
    // un-Favourite while on the Favorites category), it's gone from the paged list, so we re-focus the
    // nearest surviving neighbour by position instead of escaping to the CategoryRail.
    var contextSeriesId by remember { mutableStateOf<Long?>(null) }
    var contextSeriesIndex by remember { mutableStateOf(-1) }
    val contextFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)
    val selectedLabel = selectedItem?.displayLabel(R.string.content_category_all_series) ?: stringResource(R.string.content_category_all_series)
    val gridSelFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val firstItemFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // CH+- key paging (grid + category rail). gridPaneFocused/railPaneFocused gate which pane acts.
    val scope = rememberCoroutineScope()
    val settingsVm: tv.own.owntv.features.settings.SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val rememberSeries by settingsVm.rememberLastSeries.collectAsStateWithLifecycle()

    // "Remember last item per category": ON → each category keeps its own scroll position (per-category
    // grid + list states). OFF → reset the shared grid/list states to the top on category change
    // (fixes the cross-category scroll-leak bug).
    val perCategoryGrid = remember { mutableStateMapOf<LiveKey, androidx.compose.foundation.lazy.grid.LazyGridState>() }
    val perCategoryList = remember { mutableStateMapOf<LiveKey, androidx.compose.foundation.lazy.LazyListState>() }
    // NOTE: plain constructors, not remember*State() — these are created lazily inside getOrPut, so a
    // @Composable/rememberSaveable call here would register slots conditionally and corrupt the slot table.
    val effectiveGridState = if (rememberSeries) perCategoryGrid.getOrPut(selectedKey) { androidx.compose.foundation.lazy.grid.LazyGridState() } else gridState
    val effectiveListState = if (rememberSeries) perCategoryList.getOrPut(selectedKey) { androidx.compose.foundation.lazy.LazyListState() } else listState
    LaunchedEffect(selectedKey, rememberSeries) {
        if (!rememberSeries) { runCatching { gridState.scrollToItem(0) }; runCatching { listState.scrollToItem(0) } }
    }
    val catListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var gridPaneFocused by remember { mutableStateOf(false) }
    var railPaneFocused by remember { mutableStateOf(false) }

    // Back from a show's episodes: scroll the grid to the poster you opened, then focus it. It may be
    // far down and not composed, so without scrolling the focus request fails and focus falls to the
    // sidebar (the same scroll-then-focus fix Movies uses).
    LaunchedEffect(restoreSelected, series.itemCount) {
        if (restoreSelected && series.itemCount > 0) {
            val sel = selectedSeries
            val idx = if (sel != null) series.itemSnapshotList.items.indexOfFirst { it.id == sel.id } else -1
            if (idx >= 0) {
                // Scroll the layout that is actually on screen. Scrolling only the grid state left the
                // LIST view unscrolled, so a show further down was never composed, the focus request
                // failed, and focus fell out to the CategoryRail instead of the show you came back from.
                if (viewMode == SettingsRepository.VodViewMode.GRID) {
                    runCatching { effectiveGridState.scrollToItem(idx) }
                } else {
                    runCatching { effectiveListState.scrollToItem(idx) }
                }
                kotlinx.coroutines.delay(60)
                // One retry: on a cold paged list 60 ms is occasionally short of composition, and a
                // silent miss is exactly the failure being fixed here.
                if (runCatching { gridSelFocus.requestFocus() }.isFailure) {
                    withFrameNanos { }
                    if (runCatching { gridSelFocus.requestFocus() }.isFailure) {
                        runCatching { firstItemFocus.requestFocus() }
                    }
                }
            } else {
                runCatching { firstItemFocus.requestFocus() }
            }
            onRestoredSelected()
        }
    }
    // Closing the long-press context menu must return focus inside this pane, never the CategoryRail.
    //   - Item still present (Favourite toggle / Download / Cancel): re-focus the same item by id.
    //   - Item removed (Remove from history, or un-Favourite on the Favorites category): the paged
    //     list no longer contains it, so focus the NEAREST surviving neighbour by position (the item
    //     that slid into the removed slot, else the new last item, else first item). Only if the whole
    //     category is now empty do we let focus leave (there's nothing here to land on).
    var reorderWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(contextSeries, moveItem, creatingCategory, moveState) {
        if (contextSeries != null) return@LaunchedEffect
        // Opening the TMDB Details window closes the menu; let the window keep focus (it traps focus and
        // refocuses the series on close), don't yank it back to the grid here.
        if (detailsSeries != null) return@LaunchedEffect
        // Same for the "Set TMDB name" dialog — it refocuses the series itself when it closes.
        if (setTmdbNameSeries != null) return@LaunchedEffect
        // Same for the trailer player.
        if (trailerVideoKey != null) return@LaunchedEffect
        // The context menu closes before MoveToCategoryDialog (and its nested name prompt) opens.
        // Do not focus the grid behind either modal; re-run this effect when the whole flow closes.
        if (moveItem != null || creatingCategory) return@LaunchedEffect

        // Move mode (reorder) restoration: when the overlay is dismissed (Commit/Cancel).
        if (moveState != null) { reorderWasOpen = true; return@LaunchedEffect }
        if (reorderWasOpen) {
            reorderWasOpen = false
            // Fall through to restoreToContextRow logic below.
        }

        val targetId = contextSeriesId
        if (targetId == null) { contextSeriesIndex = -1; return@LaunchedEffect }
        val items = series.itemSnapshotList.items
        val idx = items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            runCatching {
                if (viewMode == SettingsRepository.VodViewMode.LIST) effectiveListState.scrollToItem(idx)
                else effectiveGridState.scrollToItem(idx)
            }
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        } else {
            withFrameNanos { }
            val settled = series.itemSnapshotList.items.filterNotNull()
            if (settled.isEmpty()) {
                runCatching { firstItemFocus.requestFocus() }
            } else {
                val neighbor = settled.getOrNull(contextSeriesIndex.coerceAtLeast(0)) ?: settled.last()
                val neighborIdx = items.indexOfFirst { it.id == neighbor.id }.coerceAtLeast(0)
                runCatching {
                    if (viewMode == SettingsRepository.VodViewMode.LIST) effectiveListState.scrollToItem(neighborIdx)
                    else effectiveGridState.scrollToItem(neighborIdx)
                }
                contextSeriesId = neighbor.id
                withFrameNanos { }
                runCatching { contextFocus.requestFocus() }
            }
        }
        contextSeriesIndex = -1
    }

    // Manual panel widths (Settings → Panel Width Adjustment). The saved percentages now resolve
    // against the inside of one shared content container; no stored value is rewritten.
    val panelShares = rememberPanelShares(PanelSection.SERIES, settingsVm)
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
                    it.displayLabel(R.string.content_category_all_series),
                    it.icon,
                    showGenreDot = it.key is LiveKey.Folder,
                    providerName = it.providerName,
                )
            },
            selectedIndex = selectedIndex,
            onSelect = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
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

        Column(
            modifier = Modifier
                .then(if (panels != null) Modifier.width(panels.list) else Modifier.weight(1.8f))
                .fillMaxSize()
                .onFocusChanged { gridPaneFocused = it.hasFocus }
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { gridPaneFocused },
                    // On the "All" list (every series) a long-press jump to the very last item is
                    // pointless and janks, so disable long-press there — short-press skipping stays.
                    longPressEnabled = { selectedKey != LiveKey.All },
                    lastIndex = { series.itemCount - 1 },
                    currentTargetIndex = {
                        val sel = selectedSeries
                        if (sel != null) {
                            val idx = series.itemSnapshotList.items.indexOfFirst { it.id == sel.id }
                            if (idx >= 0) idx
                            else if (viewMode == SettingsRepository.VodViewMode.GRID) effectiveGridState.firstVisibleItemIndex
                            else effectiveListState.firstVisibleItemIndex
                        } else {
                            if (viewMode == SettingsRepository.VodViewMode.GRID) effectiveGridState.firstVisibleItemIndex
                            else effectiveListState.firstVisibleItemIndex
                        }
                    },
                    onJumpToIndex = { idx ->
                        scope.launch {
                            val item = series.itemSnapshotList.items.getOrNull(idx)
                            if (viewMode == SettingsRepository.VodViewMode.GRID) {
                                runCatching { effectiveGridState.scrollToItem(idx) }
                            } else {
                                runCatching { effectiveListState.scrollToItem(idx) }
                            }
                            withFrameNanos { }
                            if (item != null) {
                                vm.onSeriesFocused(item)
                                runCatching { gridSelFocus.requestFocus() }
                            } else {
                                runCatching { firstItemFocus.requestFocus() }
                            }
                        }
                    },
                )
                // Entering this pane must land on a poster, never the search bar: prefer the
                // last-focused series, else the first one. onEnter fires only for directional entry
                // from outside (internal moves don't re-trigger it).
                .focusProperties {
                    onEnter = {
                        if (runCatching { gridSelFocus.requestFocus() }.isFailure) {
                            runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                }
                // Held Up/Down can outrun the lazy grid's composition and escape this pane
                // (landing on the top bar) — trap vertical exits; Left/Right/Back leave normally.
                .trapVerticalFocusExit()
                .focusGroup()
        ) {
            Text(stringResource(R.string.content_section_category, stringResource(R.string.common_nav_series), selectedLabel), style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(pluralStringResource(R.plurals.content_count_series, count, selectedLabel, count), style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(query = searchQuery, onQueryChange = vm::setSearchQuery, placeholder = stringResource(R.string.content_search_series, selectedLabel), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                SortChip(mode = sortMode, onToggle = vm::toggleSort, playlistLabel = stringResource(R.string.content_provider))
                Spacer(Modifier.width(10.dp))
                tv.own.owntv.ui.components.OwnTVButton(
                    label = stringResource(if (viewMode == SettingsRepository.VodViewMode.GRID) R.string.settings_view_grid else R.string.settings_view_list),
                    onClick = vm::toggleViewMode,
                    icon = if (viewMode == SettingsRepository.VodViewMode.GRID) OwnTVIcon.MENU else OwnTVIcon.SERIES,
                    style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY,
                )
            }
            Spacer(Modifier.height(14.dp))

            if (series.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) stringResource(R.string.content_no_series_found, searchQuery.trim()) else stringResource(R.string.content_no_series_here),
                        style = MaterialTheme.typography.bodyLarge, color = OwnTVTheme.colors.onSurfaceVariant,
                    )
                }
            } else if (viewMode == SettingsRepository.VodViewMode.LIST) {
                LazyColumn(
                    state = effectiveListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = series.itemCount,
                        key = series.itemKey { it.id },
                        contentType = series.itemContentType { "series" },
                    ) { index ->
                        val s = series[index]
                        if (s != null) {
                            SeriesListRow(
                                series = s,
                                isFavorite = favoriteIds.contains(s.id),
                                providerName = providerNames[s.sourceId],
                                modifier = Modifier.gridFocusTarget(
                                    itemId = s.id, index = index,
                                    contextId = contextSeriesId, contextFocus = contextFocus,
                                    selectedId = selectedSeries?.id, selectedFocus = gridSelFocus,
                                    firstItemFocus = firstItemFocus,
                                ),
                                onFocus = { vm.onSeriesFocused(s) },
                                onClick = { vm.openSeries(s) },
                                onLongClick = { contextSeries = s; contextSeriesId = s.id; contextSeriesIndex = index },
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = effectiveGridState,
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        count = series.itemCount,
                        key = series.itemKey { it.id },
                        contentType = series.itemContentType { "series" },
                    ) { index ->
                        val s = series[index]
                        if (s != null) {
                            PosterCard(
                                posterUrl = s.posterUrl,
                                title = s.name,
                                rating = s.rating,
                                isFavorite = favoriteIds.contains(s.id),
                                providerName = providerNames[s.sourceId],
                                modifier = Modifier.gridFocusTarget(
                                    itemId = s.id, index = index,
                                    contextId = contextSeriesId, contextFocus = contextFocus,
                                    selectedId = selectedSeries?.id, selectedFocus = gridSelFocus,
                                    firstItemFocus = firstItemFocus,
                                ),
                                onFocus = { vm.onSeriesFocused(s) },
                                onClick = { vm.openSeries(s) },
                                onLongClick = { contextSeries = s; contextSeriesId = s.id; contextSeriesIndex = index },
                            )
                        }
                    }
                }
            }
        }

        if (previewVisible) {
            Spacer(Modifier.width(BrowseColumnGap))
            Box(
                modifier = Modifier
                    .then(if (panels != null) Modifier.width(panels.preview) else Modifier.weight(1f))
                    .fillMaxSize()
                    .roundedPanel(fillColor = PreviewPanelFill)
                    .padding(BrowseContainerPadding),
            ) {
                val s = selectedSeries
                if (s == null) {
                    PreviewPane(hint = stringResource(R.string.content_focus_series))
                } else {
                // Gap-fill merge (§7.1/§4.1): provider wins unless the mode is TMDB-only.
                val meta = selectedSeriesMeta?.takeIf { it.seriesId == s.id }?.cache
                val tmdbWins = metadataMode.tmdbWins
                val providerPoster = s.posterUrl?.takeIf { it.isNotBlank() }
                val tmdbPoster = tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
                val art = (if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster)
                    ?: s.backdropUrl?.takeIf { it.isNotBlank() }
                    ?: tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath)
                val plot = if (tmdbWins) meta?.overview ?: s.plot?.takeIf { it.isNotBlank() }
                    else s.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
                val year = if (tmdbWins) meta?.year ?: s.year else s.year ?: meta?.year
                val rating = if (tmdbWins) meta?.rating?.takeIf { it > 0 } ?: s.rating?.takeIf { it > 0 }
                    else s.rating?.takeIf { it > 0 } ?: meta?.rating?.takeIf { it > 0 }
                val genres = jsonStringList(meta?.genresJson)
                val cast = tv.own.owntv.core.metadata.MetadataCast.names(meta?.castJson)
                // Outer details Box carries the rounded panel (glass-aware); no clip/background here,
                // mirroring MovieDetailsPane so the PreviewPanelFill glass shows through.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Dimens.GapLarge),
                ) {
                    // Non-focusable status strip — only present while this series' episodes are downloading.
                    tv.own.owntv.ui.components.downloadStripFor(selectedSeriesDownloads)?.let {
                        tv.own.owntv.ui.components.DownloadStatusStrip(it)
                        Spacer(Modifier.height(12.dp))
                    }
                    // Tall portrait poster (like the list / a phone screen), centred in the pane.
                    Box(modifier = Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(OwnTVTheme.colors.surfaceContainerLowest),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!art.isNullOrBlank()) {
                                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                OwnTVIcon(OwnTVIcon.SERIES, tint = OwnTVTheme.colors.onSurfaceVariant, modifier = Modifier.height(48.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(s.name, style = MaterialTheme.typography.titleLarge, color = OwnTVTheme.colors.onSurface)
                    val metaBits = listOfNotNull(year?.let { localizedInteger(it, grouping = false) }, rating?.let { stringResource(R.string.content_rating, it) })
                    if (metaBits.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(metaBits.joinToString(stringResource(R.string.content_metadata_separator)), style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant)
                    }
                    if (genres.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(genres.joinToString(stringResource(R.string.content_genres_separator)), style = MaterialTheme.typography.labelMedium, color = OwnTVTheme.colors.primary)
                    }
                    if (!plot.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(plot, style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    }
                    if (cast.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.content_media_cast), style = MaterialTheme.typography.labelMedium, color = OwnTVTheme.colors.onSurface)
                        Spacer(Modifier.height(2.dp))
                        Text(cast.take(6).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = OwnTVTheme.colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.content_press_ok_episodes), style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.primary)
                }
                }
            }
        }
    }
    }

    // Long-press a series → context menu.
    contextSeries?.let { s ->
        val cacheForS = selectedSeriesMeta?.takeIf { it.seriesId == s.id }?.cache
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { contextSeries = null }) { SeriesContextMenu(
            title = s.name,
            isFavorite = favoriteIds.contains(s.id),
            canMove = selectedKey is LiveKey.Folder || selectedKey is LiveKey.Custom || selectedKey == LiveKey.Favorites,
            isHistory = selectedKey == LiveKey.History,
            hasTmdbDetails = metadataMode.enrich && cacheForS != null,
            trailerKey = if (metadataMode.enrich) cacheForS?.trailerKey else null,
            canRefetchTmdb = metadataMode.enrich,
            onShowDetails = { contextSeries = null; detailsSeries = s },
            onToggleFavorite = { vm.toggleFavorite(s); contextSeries = null },
            onMove = { contextSeries = null; vm.enterMoveMode(s, selectedKey) },
            onMoveToCategory = {
                moveOriginKey = when (val k = selectedKey) {
                    is LiveKey.Folder -> vm.folderKey(k.id)
                    is LiveKey.Custom -> k.id
                    LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                    else -> null
                }
                moveOriginName = railItems.firstOrNull { it.key == selectedKey }?.title
                moveItem = s
                contextSeries = null
            },
            onHide = { vm.hideSeries(s); contextSeries = null },
            onRemoveFromHistory = { vm.removeFromHistory(s.id); contextSeries = null },
            onDownload = { vm.downloadSeries(s); contextSeries = null },
            onRefetch = {
                contextSeries = null
                toast.show(refetchingTmdbMessage)
                vm.refetchSeriesMeta(s)
            },
            onSetTmdbName = { contextSeries = null; setTmdbNameSeries = s },
            onPlayTrailer = { key -> contextSeries = null; trailerVideoKey = key },
            onDismiss = { contextSeries = null },
        ) }
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
        moveItem?.let { s ->
            val originKey = moveOriginKey
            if (originKey != null) {
                MoveToCategoryDialog(
                    moveTargets = moveTargets.filterNot { it.id == originKey },
                    originName = moveOriginName ?: stringResource(R.string.settings_customize_this_category),
                    onNewCategory = { creatingCategory = true },
                    onMove = { targetId, keepInOrigin ->
                        vm.moveToCategory(CustomizeKeys.series(s), s.id, originKey, targetId, keepInOrigin)
                        moveItem = null
                    },
                    onDismiss = { moveItem = null },
                )
            }
        }
    }

    // Fullscreen TMDB details window (§11.1) — read-only, Back exits; refocus the series on close.
    LaunchedEffect(detailsSeries) {
        if (detailsSeries == null && contextSeriesId != null) {
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        }
    }
    detailsSeries?.let { s ->
        val cache = selectedSeriesMeta?.takeIf { it.seriesId == s.id }?.cache
        tv.own.owntv.features.shell.components.MediaDetailsScreen(
            details = buildSeriesDetails(s, cache, metadataMode.tmdbWins),
            onExit = { detailsSeries = null },
        )
    }

    // "Set TMDB name" override dialog (§11.2 U5b). Prefill once per target (saved override, else cleaned title).
    LaunchedEffect(setTmdbNameSeries) {
        if (setTmdbNameSeries == null && contextSeriesId != null) {
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        }
    }
    setTmdbNameSeries?.let { s ->
        var prefill by remember(s.id) { mutableStateOf<SeriesViewModel.TmdbNamePrefill?>(null) }
        LaunchedEffect(s.id) { prefill = vm.seriesTmdbNamePrefill(s) }
        prefill?.let { p ->
            SetTmdbNameDialog(
                initialTitle = p.title,
                initialYear = p.year,
                hasOverride = p.hasOverride,
                onSave = { title, year ->
                    setTmdbNameSeries = null
                    vm.setSeriesTmdbName(s, title, year)
                    toast.show(researchingTmdbMessage)
                },
                onClear = {
                    setTmdbNameSeries = null
                    vm.clearSeriesTmdbName(s)
                    toast.show(researchingTmdbMessage)
                },
                onDismiss = { setTmdbNameSeries = null },
            )
        }
    }

    // In-app trailer player (§7.3 U4) — fullscreen over everything; Back/Exit closes and refocuses the series.
    LaunchedEffect(trailerVideoKey) {
        if (trailerVideoKey == null && contextSeriesId != null) {
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        }
    }
    trailerVideoKey?.let { key ->
        TrailerPlayerScreen(videoKey = key, onExit = { trailerVideoKey = null })
    }

    // Move mode overlay.
    moveState?.let { ms ->
        MoveOrderOverlay(
            title = stringResource(R.string.content_reorder_series),
            itemNames = ms.items.map { it.name },
            activeIndex = ms.activeIndex,
            onMoveUp = vm::moveUp,
            onMoveDown = vm::moveDown,
            onCommit = vm::commitMove,
            onCancel = vm::cancelMove,
        )
    }

    InAppToast(toast)
}

/** Parse a stored JSON array of strings (genres/cast); empty on null/blank/bad JSON. */
private fun jsonStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())
}

/** Build the fullscreen TMDB-details payload for a series, applying the §7.1/§4.1 merge precedence. */
@Composable
private fun buildSeriesDetails(
    s: SeriesEntity,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
): tv.own.owntv.features.shell.components.MediaDetailsUi {
    val providerPoster = s.posterUrl?.takeIf { it.isNotBlank() }
    val tmdbPoster = tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
    val poster = if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster
    val backdrop = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath)
        ?: s.backdropUrl?.takeIf { it.isNotBlank() }
    val plot = if (tmdbWins) meta?.overview ?: s.plot else s.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
    val year = if (tmdbWins) meta?.year ?: s.year else s.year ?: meta?.year
    val rating = if (tmdbWins) meta?.rating?.takeIf { it > 0 } ?: s.rating?.takeIf { it > 0 }
        else s.rating?.takeIf { it > 0 } ?: meta?.rating?.takeIf { it > 0 }
    val metaLine = listOfNotNull(year?.let { localizedInteger(it, grouping = false) }, rating?.let { stringResource(R.string.content_rating, it) }).joinToString(stringResource(R.string.content_metadata_separator))
    return tv.own.owntv.features.shell.components.MediaDetailsUi(
        title = s.name,
        backdropUrl = backdrop,
        posterUrl = poster,
        metaLine = metaLine,
        genres = jsonStringList(meta?.genresJson),
        plot = plot,
        cast = tv.own.owntv.core.metadata.MetadataCast.parse(meta?.castJson),
    )
}

/** A provider may omit an episode title. Keep the fallback in Compose so it follows the active locale. */
@Composable
private fun episodeDisplayTitle(episode: EpisodeEntity): String =
    episode.name.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_episode_number, episode.episodeNumber)

/** Right-hand pane for the focused episode (Option B): 16:9 TMDB still, name, S/E · year · rating, plot. */
@Composable
private fun EpisodeDetailPane(
    episode: EpisodeEntity?,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
    nextUpEpisode: EpisodeEntity?,
    nextUpPositionMs: Long,
    onPlayNextUp: () -> Unit,
    downloadStrip: tv.own.owntv.ui.components.DownloadStripState? = null,
) {
    val colors = OwnTVTheme.colors
    if (episode == null) {
        PreviewPane(hint = stringResource(R.string.content_focus_episode))
        return
    }
    val still = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath ?: meta?.posterPath)
    val title = if (tmdbWins) meta?.title?.takeIf { it.isNotBlank() } ?: episodeDisplayTitle(episode) else episodeDisplayTitle(episode)
    val plot = if (tmdbWins) meta?.overview ?: episode.plot?.takeIf { it.isNotBlank() }
        else episode.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
    val bits = listOfNotNull(
        stringResource(R.string.content_season_episode, episode.seasonNumber, episode.episodeNumber),
        meta?.year?.let { localizedInteger(it, grouping = false) },
        meta?.rating?.takeIf { it > 0 }?.let { stringResource(R.string.content_rating, it) },
    )
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.GapLarge)) {
        // Non-focusable status strip — the focused episode's own download, else the series' aggregate.
        if (downloadStrip != null) {
            tv.own.owntv.ui.components.DownloadStatusStrip(downloadStrip)
            Spacer(Modifier.height(14.dp))
        }
        // "Next up" Play card — the series' resume/continue target. Hidden when there's no next-up (all
        // caught up) or when it's the same episode already focused (OK plays it anyway).
        nextUpEpisode?.takeIf { it.id != episode.id }?.let { nup ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(colors.primaryContainer.copy(alpha = 0.22f)).padding(12.dp),
            ) {
                Text(stringResource(R.string.content_next_up), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.content_season_episode_title, nup.seasonNumber, nup.episodeNumber, episodeDisplayTitle(nup)), style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nextUpPositionMs > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.content_resume_at, formatTimestamp(nextUpPositionMs)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                OwnTVButton(label = stringResource(R.string.content_play), onClick = onPlayNextUp, icon = OwnTVIcon.PLAY, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
        }
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            if (!still.isNullOrBlank()) {
                AsyncImage(model = still, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                OwnTVIcon(OwnTVIcon.SERIES, tint = colors.onSurfaceVariant, modifier = Modifier.height(40.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(bits.joinToString(stringResource(R.string.content_metadata_separator)), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (!plot.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(plot, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.content_ok_play_options), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

/** Minimal long-press menu for an episode: Download (+ toast if already), TMDB Details when matched. */
@Composable
private fun EpisodeContextMenu(
    title: String,
    watched: Boolean,
    hasTmdbDetails: Boolean,
    canRefetchTmdb: Boolean,
    onShowDetails: () -> Unit,
    onDownload: () -> Unit,
    onPlayExternal: () -> Unit,
    onToggleWatched: () -> Unit,
    onRefetch: () -> Unit,
    // Non-null only when this episode has downloaded OpenSubtitles subtitles (subtitle plan §11).
    onDeleteSubtitles: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup().longPressMenuGuard(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            // The menu as data: same actions, same gating, same order as the buttons that used to be
            // written out here one by one. Close is not in the list — it stays pinned last.
            val actions = buildList {
                add(MenuAction("download", stringResource(R.string.content_download), OwnTVIcon.DOWNLOADS, onClick = onDownload))
                // Phase B: one-off external playback, independent of the global "External player" toggle.
                add(MenuAction("play_external", stringResource(R.string.content_play_external), OwnTVIcon.PLAY, onClick = onPlayExternal))
                // Manual override of the ≥95% auto-detected watched state (option 2 design pass).
                add(MenuAction("mark_watched", if (watched) stringResource(R.string.content_mark_unwatched) else stringResource(R.string.content_mark_watched), onClick = onToggleWatched))
                if (hasTmdbDetails) add(MenuAction("tmdb_details", stringResource(R.string.content_tmdb_details), OwnTVIcon.MENU, onClick = onShowDetails))
                // Refetch TMDB details (§11.2 U5a) — clears this episode's cache AND its show's match, then re-searches.
                if (canRefetchTmdb) add(MenuAction("refetch_tmdb", stringResource(R.string.content_refetch_tmdb), onClick = onRefetch))
                // Delete subtitles — only when this episode has downloaded OpenSubtitles subs (§11).
                onDeleteSubtitles?.let { add(MenuAction("delete_subtitles", stringResource(R.string.content_delete_subtitles), OwnTVIcon.SUBTITLE, onClick = it)) }
            }
            arranged(ContentMenu.EPISODE, actions).forEachIndexed { index, action ->
                OwnTVButton(
                    action.label,
                    onClick = action.onClick,
                    style = OwnTVButtonStyle.SECONDARY,
                    icon = action.icon,
                    modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(focus) else Modifier),
                )
            }
            Spacer(Modifier.height(4.dp))
            OwnTVButton(stringResource(R.string.content_close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())        }
    }
}

/** Build the fullscreen TMDB-details payload for an episode (still as the hero; no 2:3 poster). */
@Composable
private fun buildEpisodeDetails(
    ep: EpisodeEntity,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
): tv.own.owntv.features.shell.components.MediaDetailsUi {
    val still = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath ?: meta?.posterPath)
    val title = if (tmdbWins) meta?.title?.takeIf { it.isNotBlank() } ?: episodeDisplayTitle(ep) else episodeDisplayTitle(ep)
    val plot = if (tmdbWins) meta?.overview ?: ep.plot else ep.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
    val metaLine = listOfNotNull(meta?.year?.let { localizedInteger(it, grouping = false) }, meta?.rating?.takeIf { it > 0 }?.let { stringResource(R.string.content_rating, it) }).joinToString(stringResource(R.string.content_metadata_separator))
    return tv.own.owntv.features.shell.components.MediaDetailsUi(
        title = title,
        subtitle = stringResource(R.string.content_season_episode, ep.seasonNumber, ep.episodeNumber),
        backdropUrl = still,
        posterUrl = null,
        metaLine = metaLine,
        plot = plot,
    )
}

@Composable
private fun EpisodeView(
    series: SeriesEntity,
    vm: SeriesViewModel,
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean,
    onRestored: () -> Unit,
    modifier: Modifier,
) {
    val alreadyDownloadedMessage = stringResource(R.string.content_already_downloaded)
    val refetchingTmdbMessage = stringResource(R.string.content_refetching_tmdb)
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val loading by vm.episodesLoading.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val downloads by vm.episodeDownloads.collectAsStateWithLifecycle()
    val selectedSeason by vm.selectedSeason.collectAsStateWithLifecycle()
    val lastPlayedId by vm.lastPlayedEpisodeId.collectAsStateWithLifecycle()
    val selectedEpisode by vm.selectedEpisode.collectAsStateWithLifecycle()
    val selectedEpisodeMeta by vm.selectedEpisodeMeta.collectAsStateWithLifecycle()
    val episodeDownloadStates by vm.episodeDownloadStates.collectAsStateWithLifecycle()
    val openedSeriesDownloads by vm.openedSeriesDownloads.collectAsStateWithLifecycle()
    val metadataMode by vm.metadataMode.collectAsStateWithLifecycle()
    val episodeProgress by vm.episodeProgress.collectAsStateWithLifecycle()
    val completedIds by vm.completedEpisodeIds.collectAsStateWithLifecycle()
    val hideWatched by vm.hideWatched.collectAsStateWithLifecycle()
    val seriesOrder by vm.seriesOrder.collectAsStateWithLifecycle()
    val nextUpId by vm.nextUpEpisodeId.collectAsStateWithLifecycle()
    val epListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val episodeViewMode by vm.episodeViewMode.collectAsStateWithLifecycle()
    val isEpisodeGrid = episodeViewMode == SettingsRepository.VodViewMode.GRID
    // Grid mode has its own scroll state; every scroll/focus path below goes through `scrollEpisodes`
    // so the two layouts share one set of focus rules instead of duplicating them.
    val epGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val seasonMeta by vm.seasonEpisodeMeta.collectAsStateWithLifecycle()
    // Season selector rail state — long-running shows can have more seasons than fit on one line
    // (12+); the selector scrolls chip-by-chip with D-pad focus and keeps the active season in view.
    val seasonRowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val firstEpFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var initialFocused by remember { mutableStateOf(false) }
    var contextEpisode by remember { mutableStateOf<EpisodeEntity?>(null) }
    var detailsEpisode by remember { mutableStateOf<EpisodeEntity?>(null) }
    // Downloaded subtitles for the episode whose context menu is open (subtitle plan §11).
    var contextEpisodeSubs by remember { mutableStateOf<List<tv.own.owntv.core.database.dao.LinkedSubtitle>>(emptyList()) }
    var showEpisodeDeleteSubs by remember { mutableStateOf(false) }
    var showSorting by remember { mutableStateOf(false) }
    // Long-press target's id + its row's FocusRequester: refocus the episode row when the context menu
    // (or a window it opened) closes — otherwise focus dies with the menu and falls to the sidebar.
    var contextEpisodeId by remember { mutableStateOf<Long?>(null) }
    val epContextFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val toast = rememberInAppToast()

    BackHandler { vm.closeSeries() }

    /** Scrolls whichever episode layout is live, so focus handling stays layout-agnostic. */
    suspend fun scrollEpisodes(index: Int) {
        if (isEpisodeGrid) epGridState.scrollToItem(index) else epListState.scrollToItem(index)
    }

    // Season rail and episode list are ordered independently (the "Sorting" popup). Both branches
    // sort explicitly rather than leaning on upstream order, so the two orders are symmetrical.
    val seasons = episodes.map { it.seasonNumber }.distinct()
        .let { if (seriesOrder.seasonsDescending) it.sortedDescending() else it.sorted() }
    val activeSeason = if (seasons.contains(selectedSeason)) selectedSeason else seasons.firstOrNull() ?: 1
    val seasonEpisodes = episodes.filter { it.seasonNumber == activeSeason }
        .let { list ->
            if (seriesOrder.episodesDescending) list.sortedByDescending { ep -> ep.episodeNumber }
            else list.sortedBy { ep -> ep.episodeNumber }
        }
    // "Hide watched" filter — drops episodes watched to ≥95%. Focus-index math below uses this list so a
    // filtered-out last-watched episode falls back to the first visible one instead of losing focus.
    val visibleEpisodes = remember(seasonEpisodes, hideWatched, completedIds) {
        if (hideWatched) seasonEpisodes.filterNot { it.id in completedIds } else seasonEpisodes
    }

    // Opening a show: grab focus on the LAST-WATCHED episode if there is one (#22), else the first
    // episode (the grid that had focus is unmounted, so focus would otherwise die and fall back to the
    // sidebar). Waits for !loading so the seeded last-watched id/season from the VM is settled. When
    // entering via player-return, mark done WITHOUT focusing — the restore below owns focus.
    LaunchedEffect(loading, seasonEpisodes.isNotEmpty(), restoreFocus) {
        if (initialFocused) return@LaunchedEffect
        if (restoreFocus) { initialFocused = true; return@LaunchedEffect }
        if (!loading && visibleEpisodes.isNotEmpty()) {
            initialFocused = true
            val idx = lastPlayedId?.let { id -> visibleEpisodes.indexOfFirst { it.id == id } } ?: -1
            kotlinx.coroutines.delay(80)
            if (idx >= 0) {
                runCatching { scrollEpisodes(idx) }
                kotlinx.coroutines.delay(40)
                runCatching { selFocus.requestFocus() }
            } else {
                runCatching { firstEpFocus.requestFocus() }
            }
        }
    }

    // Resume flow: AUTO continues silently, ASK prompts (≥10s saved), NEVER starts from zero.
    val resumeMode by vm.resumeMode.collectAsStateWithLifecycle()
    // Global external-player toggle: never mount the fullscreen in-app player (it spins up mpv)
    // when playback is handed to an external app.
    val externalPlayerOn by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val goFullscreen: () -> Unit = { if (!externalPlayerOn) onFullscreen() }
    val scope = rememberCoroutineScope()
    // CH+- key paging for the episode list.
    val settingsVm: tv.own.owntv.features.settings.SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    var epPaneFocused by remember { mutableStateOf(false) }
    var resumePrompt by remember { mutableStateOf<Pair<EpisodeEntity, Long>?>(null) }
    val startEpisode: (EpisodeEntity) -> Unit = { ep ->
        scope.launch {
            val pos = vm.savedPositionMs(ep)
            when {
                resumeMode == SettingsRepository.ResumeMode.ASK && pos >= 10_000 -> resumePrompt = ep to pos
                resumeMode == SettingsRepository.ResumeMode.AUTO && pos > 0 -> { vm.playEpisode(ep, pos); goFullscreen() }
                else -> { vm.playEpisode(ep, 0); goFullscreen() }
            }
        }
    }

    // Returning from fullscreen: scroll to and focus the episode you were watching.
    LaunchedEffect(restoreFocus, visibleEpisodes.size) {
        if (!restoreFocus) return@LaunchedEffect
        val idx = lastPlayedId?.let { id -> visibleEpisodes.indexOfFirst { it.id == id } } ?: -1
        if (idx >= 0) {
            runCatching { scrollEpisodes(idx) }
            kotlinx.coroutines.delay(60)
            runCatching { selFocus.requestFocus() }
        }
        onRestored()
    }

    // Keep the active season scrolled into view in the season rail (opening on a deep season, or after
    // the user switches season). Without this a show that opens on, say, season 8 would still show 1–7.
    LaunchedEffect(activeSeason, seasons.size) {
        if (seasons.size > 1) {
            val idx = seasons.indexOf(activeSeason)
            if (idx >= 0) runCatching { seasonRowState.scrollToItem(idx) }
        }
    }

    Column(
        // Same rounded content panel as the series grid — the episode list was the one view drawn
        // without a panel background.
        modifier = modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }
            .roundedPanel(fillColor = ContentPanelFill)
            .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OwnTVButton(label = stringResource(R.string.common_back), onClick = { vm.closeSeries() }, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.CHEVRON)
            Text(series.name, style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.weight(1f))
            OwnTVButton(
                label = if (favoriteIds.contains(series.id)) stringResource(R.string.content_favorited) else stringResource(R.string.content_favorite),
                onClick = { vm.toggleFavorite(series) },
                style = OwnTVButtonStyle.SECONDARY,
                icon = OwnTVIcon.FAVORITE,
            )
            // "Hide watched" toggle (moved up from the season rail). Shown only once the series has at
            // least one watched episode; filters the active season's episode list.
            if (completedIds.isNotEmpty()) {
                OwnTVButton(
                    label = if (hideWatched) stringResource(R.string.content_show_watched) else stringResource(R.string.content_hide_watched),
                    onClick = { vm.setHideWatched(!hideWatched) },
                    style = OwnTVButtonStyle.SECONDARY,
                )
            }
            // List of titles, or a wall of episode stills. Same control and the same two labels as the
            // catalog's own view toggle, so it reads as the same idea in a different place.
            OwnTVButton(
                label = stringResource(
                    if (episodeViewMode == SettingsRepository.VodViewMode.GRID) R.string.settings_view_grid
                    else R.string.settings_view_list,
                ),
                onClick = {
                    vm.setEpisodeViewMode(
                        if (episodeViewMode == SettingsRepository.VodViewMode.GRID) SettingsRepository.VodViewMode.LIST
                        else SettingsRepository.VodViewMode.GRID,
                    )
                },
                style = OwnTVButtonStyle.SECONDARY,
                icon = if (episodeViewMode == SettingsRepository.VodViewMode.GRID) OwnTVIcon.MENU else OwnTVIcon.SERIES,
            )
            // Season/episode order for THIS series (visual only — playback always runs 1,2,3…).
            // Opens the popup; the two orders are set independently and saved per series.
            OwnTVButton(
                label = stringResource(R.string.content_sorting),
                onClick = { showSorting = true },
                style = OwnTVButtonStyle.SECONDARY,
                icon = OwnTVIcon.SORT,
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading && episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OwnTVSpinner(sizeDp = 48)
            }
            episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.content_no_episodes), style = MaterialTheme.typography.bodyLarge, color = OwnTVTheme.colors.onSurfaceVariant)
            }
            else -> {
                // Option B (§11.1): episode list on the left, focused-episode detail pane on the right.
                // In grid mode the pane is gone and the episodes take the full width.
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier
                        .weight(if (isEpisodeGrid) 1f else 1.4f)
                        .fillMaxHeight()
                        .onFocusChanged { epPaneFocused = it.hasFocus }
                        .chNavPaging(
                            enabled = chNavEnabled,
                            upSkip = chNavUpSkip,
                            downSkip = chNavDownSkip,
                            isFocused = { epPaneFocused },
                            lastIndex = { visibleEpisodes.lastIndex },
                            currentTargetIndex = {
                                val sel = selectedEpisode
                                if (sel != null) visibleEpisodes.indexOfFirst { it.id == sel.id }
                                else if (isEpisodeGrid) epGridState.firstVisibleItemIndex else epListState.firstVisibleItemIndex
                            },
                            onJumpToIndex = { idx ->
                                // Set the target as the context anchor so epContextFocus binds to its
                                // row, then scroll + focus it. Mirrors the context-menu restore pattern.
                                val target = visibleEpisodes.getOrNull(idx) ?: return@chNavPaging
                                contextEpisodeId = target.id
                                vm.onEpisodeFocused(target)
                                scope.launch {
                                    runCatching { scrollEpisodes(idx) }
                                    withFrameNanos { }
                                    runCatching { epContextFocus.requestFocus() }
                                }
                            },
                        )) {
                        if (seasons.size > 1) {
                            LazyRow(
                                state = seasonRowState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(seasons, key = { it }) { season ->
                                    val seasonEps = episodes.filter { it.seasonNumber == season }
                                    SeasonChip(
                                        season = season,
                                        selected = season == activeSeason,
                                        completedCount = seasonEps.count { it.id in completedIds },
                                        totalCount = seasonEps.size,
                                        onClick = { vm.selectSeason(season) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                        // Shared by both layouts: the focus anchors are identical, only the item differs.
                        val epModifierFor: (Int, EpisodeEntity) -> Modifier = { index, ep ->
                            Modifier
                                .then(if (ep.id == lastPlayedId) Modifier.focusRequester(selFocus) else Modifier)
                                .then(if (index == 0) Modifier.focusRequester(firstEpFocus) else Modifier)
                                .then(if (ep.id == contextEpisodeId) Modifier.focusRequester(epContextFocus) else Modifier)
                        }
                        if (isEpisodeGrid) {
                            LazyVerticalGrid(
                                state = epGridState,
                                columns = GridCells.Adaptive(minSize = 210.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                gridItemsIndexed(visibleEpisodes, key = { _, ep -> ep.id }) { index, ep ->
                                    val prog = episodeProgress[ep.id]
                                    val completed = ep.id in completedIds
                                    EpisodeTile(
                                        episode = ep,
                                        meta = seasonMeta[ep.id],
                                        series = series,
                                        tmdbWins = metadataMode.tmdbWins,
                                        lastWatched = ep.id == lastPlayedId,
                                        completed = completed,
                                        progressFraction = prog?.takeIf { !completed && it.durationMs > 0 }
                                            ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) },
                                        onClick = { startEpisode(ep) },
                                        onFocus = { vm.onEpisodeFocused(ep) },
                                        onLongClick = { contextEpisode = ep; contextEpisodeId = ep.id },
                                        modifier = epModifierFor(index, ep),
                                    )
                                }
                            }
                        } else {
                            LazyColumn(state = epListState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Keyed by episode id, not index: on a season switch the item at a
                                // given position is a different episode, and index keys would carry
                                // focus/row state across to it.
                                itemsIndexed(visibleEpisodes, key = { _, ep -> ep.id }) { index, ep ->
                                    val prog = episodeProgress[ep.id]
                                    val completed = ep.id in completedIds
                                    EpisodeRow(
                                        episode = ep,
                                        lastWatched = ep.id == lastPlayedId,
                                        completed = completed,
                                        progressFraction = prog?.takeIf { !completed && it.durationMs > 0 }
                                            ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) },
                                        onClick = { startEpisode(ep) },
                                        onFocus = { vm.onEpisodeFocused(ep) },
                                        onLongClick = { contextEpisode = ep; contextEpisodeId = ep.id },
                                        modifier = epModifierFor(index, ep),
                                    )
                                }
                            }
                        }
                    }
                    // Grid mode drops the preview pane on purpose: the tiles already show the still,
                    // which is the whole point of the layout, and a full-width grid fits far more.
                    if (!isEpisodeGrid) Box(modifier = Modifier.weight(1f).fillMaxHeight().roundedPanel(fillColor = PreviewPanelFill)) {
                        val ep = selectedEpisode
                        val meta = selectedEpisodeMeta?.takeIf { it.episodeId == ep?.id }?.cache
                        val nextUpEp = nextUpId?.let { id -> episodes.firstOrNull { it.id == id } }
                        val nextUpPos = nextUpEp?.let { episodeProgress[it.id]?.positionMs } ?: 0L
                        EpisodeDetailPane(
                            episode = ep,
                            meta = meta,
                            tmdbWins = metadataMode.tmdbWins,
                            nextUpEpisode = nextUpEp,
                            nextUpPositionMs = nextUpPos,
                            onPlayNextUp = { nextUpEp?.let { startEpisode(it) } },
                            // The focused episode's own download, else the whole-series aggregate.
                            downloadStrip = (ep?.let { e -> episodeDownloadStates[e.id]?.let { tv.own.owntv.ui.components.downloadStripFor(listOf(it)) } })
                                ?: tv.own.owntv.ui.components.downloadStripFor(openedSeriesDownloads),
                        )
                    }
                }
            }
        }
    }

    // When the episode context menu closes (action or dismiss), put focus back on the episode row it was
    // opened from — unless a window the menu opened (TMDB Details) now owns focus; it refocuses on close.
    LaunchedEffect(contextEpisode) {
        if (contextEpisode != null) return@LaunchedEffect
        if (detailsEpisode != null) return@LaunchedEffect
        if (contextEpisodeId != null) {
            withFrameNanos { }
            runCatching { epContextFocus.requestFocus() }
        }
    }
    LaunchedEffect(detailsEpisode) {
        if (detailsEpisode == null && contextEpisodeId != null) {
            withFrameNanos { }
            runCatching { epContextFocus.requestFocus() }
        }
    }

    // Load the opened episode's downloaded subtitles so the menu can show "Delete subtitles" (§11).
    LaunchedEffect(contextEpisode?.id) {
        contextEpisodeSubs = contextEpisode?.let { runCatching { vm.downloadedSubtitles(it) }.getOrDefault(emptyList()) } ?: emptyList()
    }

    // Long-press an episode → context menu (Download idempotent + toast; TMDB Details when matched).
    contextEpisode?.let { ep ->
        val cacheForEp = selectedEpisodeMeta?.takeIf { it.episodeId == ep.id }?.cache
        val alreadyDownloaded = downloads[ep.id] != null
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { contextEpisode = null }) { EpisodeContextMenu(
            title = stringResource(R.string.content_season_episode_title, ep.seasonNumber, ep.episodeNumber, episodeDisplayTitle(ep)),
            watched = ep.id in completedIds,
            hasTmdbDetails = metadataMode.enrich && cacheForEp != null,
            canRefetchTmdb = metadataMode.enrich,
            onShowDetails = { contextEpisode = null; detailsEpisode = ep },
            onDownload = {
                contextEpisode = null
                if (alreadyDownloaded) {
                    toast.show(alreadyDownloadedMessage)
                } else vm.downloadEpisode(ep)
            },
            onPlayExternal = { contextEpisode = null; vm.playEpisodeExternal(ep) },
            onToggleWatched = {
                contextEpisode = null
                if (ep.id in completedIds) vm.markEpisodeUnwatched(ep) else vm.markEpisodeWatched(ep)
            },
            onRefetch = {
                contextEpisode = null
                toast.show(refetchingTmdbMessage)
                vm.refetchEpisodeMeta(series, ep)
            },
            onDeleteSubtitles = if (contextEpisodeSubs.isNotEmpty()) ({ showEpisodeDeleteSubs = true }) else null,
            onDismiss = { contextEpisode = null },
        ) }
    }

    // Season/episode order popup for this series. Applies immediately; stays open so both rows can
    // be set in one visit.
    if (showSorting) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showSorting = false }) { SeriesSortingDialog(
            order = seriesOrder,
            onChange = { seasonsDesc, episodesDesc -> vm.setSeriesOrder(seasonsDesc, episodesDesc) },
            onDismiss = { showSorting = false },
        ) }
    }

    // Per-episode "Delete subtitles" popup (§11) — individual deletion; closes when none remain.
    if (showEpisodeDeleteSubs) {
        val ep = contextEpisode
        if (ep == null || contextEpisodeSubs.isEmpty()) {
            showEpisodeDeleteSubs = false
        } else {
            tv.own.owntv.features.subtitles.SubtitleDeletePopup(
                contentTitle = stringResource(R.string.content_season_episode_title, ep.seasonNumber, ep.episodeNumber, episodeDisplayTitle(ep)),
                items = contextEpisodeSubs,
                onDelete = { sub ->
                    vm.deleteSubtitle(sub.cacheId)
                    contextEpisodeSubs = contextEpisodeSubs.filterNot { it.cacheId == sub.cacheId }
                    // Last one deleted → close the popup AND the context menu so focus returns to the
                    // episode row (the menu's Delete action is gone anyway).
                    if (contextEpisodeSubs.isEmpty()) { showEpisodeDeleteSubs = false; contextEpisode = null }
                },
                onDismiss = { showEpisodeDeleteSubs = false },
            )
        }
    }

    // Fullscreen TMDB details window for the episode (§11.1) — read-only, Back exits.
    detailsEpisode?.let { ep ->
        val cache = selectedEpisodeMeta?.takeIf { it.episodeId == ep.id }?.cache
        tv.own.owntv.features.shell.components.MediaDetailsScreen(
            details = buildEpisodeDetails(ep, cache, metadataMode.tmdbWins),
            onExit = { detailsEpisode = null },
        )
    }

    resumePrompt?.let { (ep, pos) ->
        ResumeDialog(
            positionMs = pos,
            onResume = { resumePrompt = null; vm.playEpisode(ep, pos); goFullscreen() },
            onStartOver = { resumePrompt = null; vm.playEpisode(ep, 0); goFullscreen() },
            onDismiss = { resumePrompt = null },
        )
    }

    InAppToast(toast)
}

@Composable
private fun SeasonChip(season: Int, selected: Boolean, completedCount: Int, totalCount: Int, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    val label = if (totalCount > 0) {
        stringResource(R.string.content_season_progress, season, completedCount, totalCount)
    } else {
        stringResource(R.string.content_season, season)
    }
    FocusableSurface(
        onClick = onClick,
        selected = selected,
        shape = CircleShape,
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.primaryContainer,
        contentAlignment = Alignment.Center,
        surface = GlassSurface.CARDS,
    ) { _ ->
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/**
 * One episode tile in grid mode: a 16:9 still with the episode number, title and watched state.
 *
 * The image ladder matters more here than in the list. TMDB's still is the point of the grid, but an
 * episode it has never heard of has NO picture of its own — the provider stores none — so it falls back
 * to the show's own art. The show's *backdrop* comes first because it is 16:9 like the still; the
 * portrait poster only after that, since it has to be cropped to fit.
 *
 * When the tile is showing fallback art every tile in the season looks identical, so the episode number
 * becomes the only thing distinguishing them and is drawn large. With a real still it stays a small
 * corner badge and lets the picture do the work.
 */
@Composable
private fun EpisodeTile(
    episode: EpisodeEntity,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    series: SeriesEntity,
    tmdbWins: Boolean,
    lastWatched: Boolean,
    completed: Boolean,
    progressFraction: Float?,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    // w300, not the w780 the detail pane uses: a tile is a fraction of the screen, and a season of
    // w780 stills is several times the pixels for no visible gain on a TV.
    val still = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath, size = "w300")
    val fallback = series.backdropUrl?.takeIf { it.isNotBlank() } ?: series.posterUrl?.takeIf { it.isNotBlank() }
    // The still ALWAYS wins when there is one — unlike titles or plots, this is not a provider-vs-TMDB
    // merge (§7.1). The provider has no episode image at all, so the show's own art is a stand-in for a
    // missing picture, never a competing one. Letting it win would put the same image on every tile and
    // defeat the whole layout. Provider-only mode never resolves metadata, so `still` is null there and
    // the show art is used for all episodes, which is what that mode should look like.
    val art = still ?: fallback
    val isFallback = still == null
    val title = if (tmdbWins) meta?.title?.takeIf { it.isNotBlank() } ?: episodeDisplayTitle(episode)
    else episodeDisplayTitle(episode)

    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.TopStart,
        surface = GlassSurface.CARDS,
    ) { focused ->
        LaunchedEffect(focused) { if (focused) onFocus() }
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerLowest),
            ) {
                if (!art.isNullOrBlank()) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Big centred number whenever the picture cannot identify the episode by itself.
                if (isFallback) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            localizedInteger(episode.episodeNumber, grouping = false),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            localizedInteger(episode.episodeNumber, grouping = false),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onPrimaryContainer,
                        )
                    }
                }
                if (completed) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text("✓", style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer)
                    }
                }
                if (lastWatched) {
                    Text(
                        stringResource(R.string.content_last_watched),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                            .clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                // Part-watched bar hugging the bottom edge, same language as the list row.
                if (progressFraction != null) {
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth(progressFraction)
                            .height(3.dp).background(colors.primary),
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (completed && !focused) colors.onSurfaceVariant else colors.onSurface,
                fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeEntity,
    lastWatched: Boolean,
    completed: Boolean,
    progressFraction: Float?,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val displayTitle = episodeDisplayTitle(episode)
    // Row is clean text (number + name + last-watched). Play = single-press; Download / TMDB Details
    // moved to long-press (§11.1). The focused episode drives the right detail pane. Watched state:
    // ✓ + dimmed name when completed (≥95%); a thin progress bar hugging the bottom edge when part-watched.
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.CARDS,
    ) { focused ->
        LaunchedEffect(focused) { if (focused) onFocus() }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(if (focused || completed) colors.primaryContainer else colors.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (completed) {
                        Text("✓", style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer)
                    } else {
                        Text(localizedInteger(episode.episodeNumber, grouping = false), style = MaterialTheme.typography.labelLarge, color = if (focused) colors.onPrimaryContainer else colors.onSurfaceVariant)
                    }
                }
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        focused -> colors.onSurface
                        completed -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    },
                    fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Mark the episode you last watched so it's findable even when it isn't focused (#22).
                if (lastWatched) {
                    Text(
                        stringResource(R.string.content_last_watched),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            // Part-watched: a thin progress bar hugging the row's bottom edge (track + fill).
            if (progressFraction != null) {
                Box(modifier = Modifier.fillMaxWidth().height(2.dp), contentAlignment = Alignment.CenterStart) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.onSurface.copy(alpha = 0.18f)))
                    Box(modifier = Modifier.fillMaxWidth(progressFraction).height(2.dp).background(colors.primary))
                }
            }
        }
    }
}

/** Compact one-line row used by the List view mode — fits many series on screen at once (#10). */
@Composable
private fun SeriesListRow(
    series: SeriesEntity,
    isFavorite: Boolean,
    providerName: String? = null,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.CARDS,
    ) { focused ->
        LaunchedEffect(focused) { if (focused) onFocus() }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!series.posterUrl.isNullOrBlank()) {
                    AsyncImage(model = series.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.SERIES, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    series.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focused) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    series.year?.let { add(localizedInteger(it, grouping = false)) }
                    series.rating?.takeIf { it > 0 }?.let { add(stringResource(R.string.content_rating, it)) }
                }.joinToString(stringResource(R.string.content_metadata_separator))
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (isFavorite) {
                OwnTVIcon(OwnTVIcon.FAVORITE, tint = colors.favorite, filled = true, modifier = Modifier.size(18.dp))
            }
            providerName?.let { ProviderChip(name = it) }
        }
    }
}

/**
 * The series "Sorting" popup: season rail order and episode list order, set independently.
 * Applies immediately on select (no OK button); Back closes.
 *
 * PRESENTATION ONLY — playback order (autoplay next episode) always runs in episode-number order,
 * whatever is chosen here.
 */
@Composable
private fun SeriesSortingDialog(
    order: SeriesViewModel.SeriesOrder,
    onChange: (seasonsDescending: Boolean, episodesDescending: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .modalScrim()
            .trapAllFocusExit()
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 680.dp, padding = 28.dp)) {
            Text(stringResource(R.string.content_sorting), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(20.dp))
            SortingRow(
                label = stringResource(R.string.content_seasons),
                descending = order.seasonsDescending,
                onSelect = { desc -> onChange(desc, order.episodesDescending) },
                // Pre-focus the row the user is most likely to change first.
                focusRequester = focus,
            )
            Spacer(Modifier.height(12.dp))
            SortingRow(
                label = stringResource(R.string.content_episodes),
                descending = order.episodesDescending,
                onSelect = { desc -> onChange(order.seasonsDescending, desc) },
            )
        }
    }
}

/** One "Oldest first / Newest first" pair. Both labels are the same width, so nothing resizes. */
@Composable
private fun SortingRow(
    label: String,
    descending: Boolean,
    onSelect: (Boolean) -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = OwnTVTheme.colors.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 110.dp, max = 180.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        OwnTVButton(
            label = stringResource(R.string.content_oldest_first),
            onClick = { onSelect(false) },
            style = if (!descending) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
            modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
        )
        OwnTVButton(
            label = stringResource(R.string.content_newest_first),
            onClick = { onSelect(true) },
            style = if (descending) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
        )
    }
}
