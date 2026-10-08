@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package tv.own.owntv.features.series

import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import tv.own.owntv.features.live.LiveCategories
import tv.own.owntv.features.live.ProviderTags
import tv.own.owntv.features.live.edgeScrollSpec
import tv.own.owntv.features.shell.components.VodCinematicBackdrop
import tv.own.owntv.features.shell.components.VodDetailsCard
import tv.own.owntv.features.shell.components.VodGroupDetails
import tv.own.owntv.features.shell.components.VodGroupLibrary
import tv.own.owntv.features.shell.components.VodGroupOrganise
import tv.own.owntv.features.shell.components.VodGroupWatch
import tv.own.owntv.features.shell.components.VodHeader
import tv.own.owntv.features.shell.components.VodHero
import tv.own.owntv.features.shell.components.VodListRow
import tv.own.owntv.features.shell.components.VodOptionsMenu
import tv.own.owntv.features.shell.components.VodPosterArt
import tv.own.owntv.features.shell.components.VodSortMenu
import tv.own.owntv.features.shell.components.VodSortTool
import tv.own.owntv.features.shell.components.VodStepper
import tv.own.owntv.features.shell.components.VodTitleInfo
import tv.own.owntv.features.shell.components.cinematicQualityBadges
import tv.own.owntv.features.shell.components.vodCategoryEntries
import tv.own.owntv.features.shell.components.vodCount
import tv.own.owntv.features.shell.components.vodLine
import tv.own.owntv.features.shell.components.vodRating
import tv.own.owntv.features.shell.components.vodSortLabel
import tv.own.owntv.features.shell.components.VodMeta
import tv.own.owntv.features.shell.components.vodRuntime
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageEpisode
import tv.own.owntv.ui.stage.StageGroupLabel
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageMenuChoice
import tv.own.owntv.ui.stage.StageMenuHeader
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.drawBoxShadow
import tv.own.owntv.ui.theme.dissolveEdges
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpxSp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.aspectRatio
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.stage.StageSegmented
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.features.live.displayLabel
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.customize.categoryRestoreSummary
import tv.own.owntv.features.customize.CategoryRestorePlan
import androidx.compose.runtime.produceState
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.features.customize.MoveToCategoryDialog
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.features.live.displayLabel
import tv.own.owntv.core.settings.PanelSection
import tv.own.owntv.features.settings.data.computePanelWidths
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.settings.rememberPanelShares
import tv.own.owntv.features.shell.components.CategoryContextMenu
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.ui.components.InAppToast
import tv.own.owntv.ui.components.rememberInAppToast
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.ui.components.MenuAction
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.ProgressRing
import tv.own.owntv.ui.components.ResumeDialog
import tv.own.owntv.ui.components.SetTmdbNameDialog
import tv.own.owntv.ui.components.TrailerPlayerScreen
import tv.own.owntv.ui.components.chNavPaging
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.components.gridFocusTarget
import tv.own.owntv.ui.format.localizedInteger
import tv.own.owntv.ui.format.rememberAirDateFormatter
import tv.own.owntv.core.live.LiveKey

@Composable
fun SeriesScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * Pins the grid to one folder and takes the category rail away — how More → Favourites and
     * More → History show shows without a second copy of this grid existing.
     */
    lockedKey: LiveKey? = null,
    /** Hands the shell this screen's way in from the rail (Separate: the open category; Cinematic: the title). */
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
) {
    val vm: SeriesViewModel = koinViewModel()
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
    val openedSeries by vm.openedSeries.collectAsStateWithLifecycle()

    // Track leaving a show so the grid can put focus back on the poster you came from (the episode
    // view that held focus is unmounted on Back — focus would otherwise die and land on the sidebar).
    // The show itself, not "the selected one": the grid's way in can focus the first poster before
    // the opened one is laid out, and that makes the first poster the selected show.
    var returnToShowId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(openedSeries) { openedSeries?.let { returnToShowId = it.id } }
    // Where that show sat in the titles, when it was opened from them: the list reloads from its
    // first page on the way back, so a show further down is found only after scrolling there again.
    var returnToIndex by remember { mutableIntStateOf(-1) }

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
            restoreShowId = returnToShowId,
            restoreShowIndex = returnToIndex,
            onOpenAt = { returnToIndex = it },
            onRestoredSelected = { returnToShowId = null; returnToIndex = -1 },
            lockedKey = lockedKey,
            onEntryHook = onEntryHook,
            modifier = modifier,
        )
    }
}

@Composable
private fun SeriesGrid(
    vm: SeriesViewModel,
    onChildFocused: () -> Unit,
    restoreShowId: Long? = null,
    restoreShowIndex: Int = -1,
    onOpenAt: (Int) -> Unit = {},
    onRestoredSelected: () -> Unit = {},
    /** Non-null while this grid is a More screen's stage — see [SeriesScreen]. */
    lockedKey: LiveKey? = null,
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
    modifier: Modifier,
) {
    val alreadyDownloadedMessage = stringResource(R.string.content_already_downloaded)
    val refetchingTmdbMessage = stringResource(R.string.content_refetching_tmdb)
    val researchingTmdbMessage = stringResource(R.string.content_researching_tmdb)
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val seriesProgress by vm.seriesProgress.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val storedViewMode by vm.viewMode.collectAsStateWithLifecycle()
    val selectedSeries by vm.selectedSeries.collectAsStateWithLifecycle()
    val selectedSeriesMeta by vm.selectedSeriesMeta.collectAsStateWithLifecycle()
    val metadataMode by vm.metadataMode.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val toast = rememberInAppToast()
    val series = vm.series.collectAsLazyPagingItems()
    val moveState by vm.moveState.collectAsStateWithLifecycle()
    val categoryMoveState by vm.categoryMoveState.collectAsStateWithLifecycle()
    var contextCategory by remember { mutableStateOf<LiveRailItem?>(null) }
    // The rail row a category menu was opened from, kept after the menu closes so the cursor can go
    // back to that exact row. Held as a key, not an index: a Move changes the row's position.
    var contextCategoryKey by remember { mutableStateOf<LiveKey?>(null) }
    var railFocusRow by remember { mutableStateOf<Int?>(null) }
    val railFocus = remember { FocusRequester() }
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
    var trailerTitle by remember { mutableStateOf<String?>(null) }
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
    // Right from the rail on an empty list: the list's search box, so a search with no results can be cleared.
    val listSearchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // CH+- key paging (grid + category rail). gridPaneFocused/railPaneFocused gate which pane acts.
    val scope = rememberCoroutineScope()
    val settingsVm: tv.own.owntv.features.settings.SettingsViewModel = koinViewModel()
    val vodLayout by settingsVm.vodLayout.collectAsStateWithLifecycle()
    // Cinematic is the browse section's; More → Favourites / History pin the plain Separate list.
    val cinematic = vodLayout == SettingsRepository.VodLayout.CINEMATIC && lockedKey == null
    val cinematicDetailsPct by settingsVm.cinematicDetailsHeight(PanelSection.SERIES).collectAsStateWithLifecycle()
    // Cinematic is grid-only. The stored choice is deliberately not rewritten, so switching back to
    // Separate restores the user's List.
    val viewMode = if (cinematic) SettingsRepository.VodViewMode.GRID else storedViewMode
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val rememberSeries by settingsVm.rememberLastSeries.collectAsStateWithLifecycle()

    // "Remember last item per category": ON → each category keeps its own scroll position (per-category
    // grid + list states). OFF → reset the shared grid/list states to the top on category change
    // (fixes the cross-category scroll-leak bug).
    val perCategoryGrid = remember { mutableStateMapOf<LiveKey, androidx.compose.foundation.lazy.grid.LazyGridState>() }
    val perCategoryList = remember { mutableStateMapOf<LiveKey, androidx.compose.foundation.lazy.LazyListState>() }
    val perCategorySeriesIds = remember { mutableStateMapOf<LiveKey, Long>() }
    // NOTE: plain constructors, not remember*State() — these are created lazily inside getOrPut, so a
    // @Composable/rememberSaveable call here would register slots conditionally and corrupt the slot table.
    val effectiveGridState = if (rememberSeries) perCategoryGrid.getOrPut(selectedKey) { androidx.compose.foundation.lazy.grid.LazyGridState() } else gridState
    val effectiveListState = if (rememberSeries) perCategoryList.getOrPut(selectedKey) { androidx.compose.foundation.lazy.LazyListState() } else listState
    LaunchedEffect(selectedKey, rememberSeries) {
        if (!rememberSeries) { runCatching { gridState.scrollToItem(0) }; runCatching { listState.scrollToItem(0) } }
    }
    // The List view scrolls a LazyListState, the grids (Cinematic and Separate) a LazyGridState.
    val usesList = viewMode == SettingsRepository.VodViewMode.LIST
    suspend fun scrollToIndex(i: Int) {
        runCatching { if (usesList) effectiveListState.scrollToItem(i) else effectiveGridState.scrollToItem(i) }
    }
    val stepper = remember { VodStepper(scope) { effectiveListState } }
    val playlistMarks by vm.playlistMarks.collectAsStateWithLifecycle()
    // Separate keeps the categories on screen; Cinematic opens them as a sheet (◀, or the Categories tool).
    var categoriesOpen by remember { mutableStateOf(false) }
    var sheetHadFocus by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val sortFocus = remember { FocusRequester() }
    val categoriesVisible = lockedKey == null && (!cinematic || categoriesOpen)
    val railCounts by (if (categoriesVisible) vm.railCounts else remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<LiveKey, Int>()) })
        .collectAsStateWithLifecycle()
    val catListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var gridPaneFocused by remember { mutableStateOf(false) }
    var railPaneFocused by remember { mutableStateOf(false) }
    // Tiles the provider gave no artwork for: ask for the TMDB poster already cached from an earlier
    // focus, so the placeholder is only shown when nothing at all is known (mirrors Movies).
    val cachedPosters by vm.cachedPosters.collectAsStateWithLifecycle()
    LaunchedEffect(effectiveGridState, effectiveListState, viewMode, series) {
        val grid = !usesList
        snapshotFlow {
            val indices = if (grid) effectiveGridState.layoutInfo.visibleItemsInfo.map { it.index }
            else effectiveListState.layoutInfo.visibleItemsInfo.map { it.index }
            indices.filter { it < series.itemCount }
                .mapNotNull { series.peek(it) }
                .filter { it.posterUrl.isNullOrBlank() }
        }.distinctUntilChanged().collect { vm.onPosterlessVisible(it) }
    }

    // Back from a show's episodes: scroll the grid to the poster you opened, then focus it. It may be
    // far down and not composed, so without scrolling the focus request fails and focus falls to the
    // sidebar (the same scroll-then-focus fix Movies uses).
    LaunchedEffect(restoreShowId, series.itemCount) {
        val showId = restoreShowId ?: return@LaunchedEffect
        if (series.itemCount == 0) return@LaunchedEffect
        // By position, placeholders included: `items` drops pages not loaded yet and shifts the index.
        var idx = series.itemSnapshotList.indexOfFirst { it?.id == showId }
        if (idx < 0 && restoreShowIndex in 0 until series.itemCount) {
            // Its page is not loaded yet: scrolling to where it sat loads it; wait up to about a second.
            scrollToIndex(restoreShowIndex)
            repeat(60) {
                if (idx < 0) {
                    withFrameNanos { }
                    idx = series.itemSnapshotList.indexOfFirst { it?.id == showId }
                }
            }
        }
        val show = series.itemSnapshotList.getOrNull(idx)
        if (show != null) {
            // Scroll the layout that is actually on screen. Scrolling only the grid state left the
            // LIST view unscrolled, so a show further down was never composed, the focus request
            // failed, and focus fell out to the CategoryRail instead of the show you came back from.
            scrollToIndex(idx)
            // The shell is switching the canvas back from the episodes page at the same time, and its
            // way in may already have put focus on the first poster and made that the selected show.
            // Make this show the selected one again (gridSelFocus follows it), then keep taking focus
            // back to it for about 0.7 s, the first poster standing in only while it is not laid out.
            var landed = false
            repeat(40) {
                if (!landed) {
                    if (selectedSeries?.id != show.id) vm.onSeriesFocused(show)
                    if (rememberSeries) perCategorySeriesIds[selectedKey] = show.id
                    // A stand-in brings itself into view, scrolling this show out of the layout again.
                    if (it > 0) scrollToIndex(idx)
                }
                withFrameNanos { }
                if (!landed || !gridPaneFocused) {
                    // Landed only on this show: a stand-in focused meanwhile has made itself the selected
                    // show, and gridSelFocus then points at it — the next frame selects this one again.
                    landed = runCatching { gridSelFocus.requestFocus() }.getOrDefault(false) && selectedSeries?.id == show.id
                    if (!landed && !gridPaneFocused) runCatching { firstItemFocus.requestFocus() }
                }
            }
        } else {
            runCatching { firstItemFocus.requestFocus() }
        }
        onRestoredSelected()
    }
    // Closing the long-press context menu must return focus inside this pane, never the CategoryRail.
    //   - Item still present (Favourite toggle / Download / Cancel): re-focus the same item by id.
    //   - Item removed (Remove from history, or un-Favourite on the Favorites category): the paged
    //     list no longer contains it, so focus the NEAREST surviving neighbour by position (the item
    //     that slid into the removed slot, else the new last item, else first item). Only if the whole
    //     category is now empty do we let focus leave (there's nothing here to land on).
    LaunchedEffect(contextSeries, moveItem, creatingCategory, moveState) {
        if (contextSeries != null) return@LaunchedEffect
        // Opening the TMDB Details window closes the menu; let the window keep focus (it traps focus and
        // refocuses the series on close), don't yank it back to the grid here.
        if (detailsSeries != null) return@LaunchedEffect
        // Same for the "Set TMDB name" dialog — it refocuses the series itself when it closes.
        if (setTmdbNameSeries != null) return@LaunchedEffect
        // Same for the trailer player.
        if (trailerVideoKey != null) return@LaunchedEffect
        // The context menu closes before MoveToCategoryDialog (and its nested name prompt) opens, and
        // the reorder overlay owns focus while it is up. Do not focus the grid behind any of them;
        // this effect re-runs when the whole flow closes and restores the row below.
        if (moveItem != null || creatingCategory || moveState != null) return@LaunchedEffect

        val targetId = contextSeriesId
        if (targetId == null) { contextSeriesIndex = -1; return@LaunchedEffect }
        val items = series.itemSnapshotList.items
        val idx = items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            scrollToIndex(idx)
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
                scrollToIndex(neighborIdx)
                contextSeriesId = neighbor.id
                withFrameNanos { }
                runCatching { contextFocus.requestFocus() }
            }
        }
        contextSeriesIndex = -1
    }

    // The show the cursor was on: after a long-press it carries contextFocus instead of gridSelFocus
    // (gridFocusTarget prefers it), so both are tried before the first show (fix playbook 6).
    fun focusCurrentTitle(): Boolean =
        runCatching { gridSelFocus.requestFocus() }.getOrDefault(false) ||
            runCatching { contextFocus.requestFocus() }.getOrDefault(false) ||
            runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)
    // From the categories to the shows: the remembered one, else the first; the search field when empty.
    fun focusTitles() {
        val targetId = if (rememberSeries) perCategorySeriesIds[selectedKey] ?: selectedSeries?.id else selectedSeries?.id
        scope.launch {
            if (series.itemCount > 0) {
                val targetIdx = targetId?.let { id -> series.itemSnapshotList.items.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
                scrollToIndex(targetIdx)
                withFrameNanos { }
                // A category just picked is still loading: the first attempt can land on a title of the
                // old list, which then vanishes and drops focus to the rail. So keep watching for about
                // 0.7 s and take focus back whenever the titles lost it.
                repeat(40) {
                    if (!gridPaneFocused) {
                        if (!(targetId != null && focusCurrentTitle())) runCatching { firstItemFocus.requestFocus() }
                    }
                    withFrameNanos { }
                }
            } else {
                runCatching { listSearchFocus.requestFocus() }
            }
        }
    }

    // The way in from the rail: Separate lands on the open category in the column (as Live TV's
    // Separate panels), Cinematic on the title the hero shows. The shell's focus restorer would
    // otherwise return to whatever had focus last, such as the search field.
    DisposableEffect(onEntryHook, cinematic) {
        onEntryHook?.invoke {
            // The column's own row path (scrolls to it if needed), not the list's first child: the search field.
            if (!cinematic && lockedKey == null) { railFocusRow = selectedIndex; true } else focusCurrentTitle()
        }
        onDispose { onEntryHook?.invoke(null) }
    }

    // Manual panel widths (Settings → Panel Width Adjustment), mapped onto Stage: Separate = the column,
    // the posters and the details card (Poster panel 0% = no card); Cinematic = the sheet and the hero height.
    val panelShares = rememberPanelShares(PanelSection.SERIES, settingsVm)
    val cinematicSheetPct by settingsVm.cinematicSheetWidth(PanelSection.SERIES).collectAsStateWithLifecycle()
    val selectedMeta = selectedSeriesMeta?.takeIf { it.seriesId == selectedSeries?.id }?.cache
    val titleInfo = selectedSeries?.let { seriesTitleInfo(it, selectedMeta, metadataMode.tmdbWins) }
    val (categoryEntries, groupsHeading) = vodCategoryEntries(railItems, railCounts, playlistMarks, series = true)
    val headerLabel = if (selectedItem?.key is LiveKey.Folder || selectedItem?.key is LiveKey.Custom) ProviderTags.parse(selectedLabel).name else selectedLabel

    // Back closes the sheet: one level out.
    androidx.activity.compose.BackHandler(enabled = categoriesOpen) { categoriesOpen = false; focusCurrentTitle() }
    LaunchedEffect(categoriesOpen) {
        if (categoriesOpen) { withFrameNanos { }; runCatching { railFocus.requestFocus() } }
        // However the sheet closed (Back, a pick, ◀ to the rail), the next one starts fresh.
        else sheetHadFocus = false
    }
    // CH± in the categories moves the highlight only; OK picks (owner, 2026-10-01).
    var catFocusIndex by remember { mutableStateOf<Int?>(null) }
    val categoriesModifier = Modifier
        .onFocusChanged {
            railPaneFocused = it.hasFocus
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
        if (categoriesOpen) { categoriesOpen = false; focusTitles() }
    }
    val onCategoryLongSelect: (Int) -> Unit = { idx ->
        railItems.getOrNull(idx)?.let { item ->
            if (item.key is LiveKey.Folder || item.key is LiveKey.Custom) {
                contextCategory = item
                contextCategoryKey = item.key
            }
        }
    }
    val targetSeriesId = if (rememberSeries) perCategorySeriesIds[selectedKey] ?: selectedSeries?.id else selectedSeries?.id
    val openMenu: (tv.own.owntv.core.database.entity.SeriesEntity, Int) -> Unit = { show, index -> contextSeries = show; contextSeriesId = show.id; contextSeriesIndex = index }
    val onShowFocus: (tv.own.owntv.core.database.entity.SeriesEntity) -> Unit = { show ->
        vm.onSeriesFocused(show)
        if (rememberSeries) perCategorySeriesIds[selectedKey] = show.id
    }
    val posterOf: (tv.own.owntv.core.database.entity.SeriesEntity) -> String? = { it.posterUrl?.takeIf { u -> u.isNotBlank() } ?: cachedPosters[it.id] }
    val playHint = stringResource(R.string.common_ok) to stringResource(R.string.content_episodes)
    val optionsHint = stringResource(R.string.content_key_hold_ok) to stringResource(R.string.content_key_options)

    // The toolbar and the titles: one focus group, entered on a title, never the search field.
    val paneModifier = Modifier
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
                val idx = if (sel != null) series.itemSnapshotList.items.indexOfFirst { it.id == sel.id } else -1
                if (idx >= 0) idx
                else if (usesList) effectiveListState.firstVisibleItemIndex
                else effectiveGridState.firstVisibleItemIndex
            },
            onJumpToIndex = { idx ->
                // Scroll the target into view, then set it as the selected show so selFocus binds to it
                // (gridFocusTarget keys on selectedSeries.id), and request focus after one frame.
                scope.launch {
                    val item = series.itemSnapshotList.items.getOrNull(idx)
                    scrollToIndex(idx)
                    withFrameNanos { }
                    if (item != null) {
                        // The remembered show is the focus target, so it must move with the jump.
                        onShowFocus(item)
                        withFrameNanos { }
                        runCatching { gridSelFocus.requestFocus() }
                    } else {
                        runCatching { firstItemFocus.requestFocus() }
                    }
                }
            },
        )
        .focusProperties {
            onEnter = {
                val landed = (targetSeriesId != null && focusCurrentTitle()) || runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)
                // Landed on a title: stop the default entry, which would go on to the search field.
                if (landed) cancelFocusChange()
            }
        }
        .focusProperties {
            onExit = {
                when (requestedFocusDirection) {
                    // Cinematic's first column opens the sheet itself; in Separate, ◀ out of the first
                    // column reaches the category column. Nothing lies to the right.
                    androidx.compose.ui.focus.FocusDirection.Left -> if (cinematic) cancelFocusChange()
                    androidx.compose.ui.focus.FocusDirection.Right -> cancelFocusChange()
                    // Pinned (More) keeps Up for its tabs.
                    androidx.compose.ui.focus.FocusDirection.Up, androidx.compose.ui.focus.FocusDirection.Down ->
                        if (lockedKey == null) cancelFocusChange()
                    else -> Unit
                }
            }
        }
        .focusGroup()
    // ◀ from the empty search field opens the categories, as from the titles.
    val searchLeft = Modifier.onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && searchQuery.isEmpty() && lockedKey == null) {
            if (cinematic) categoriesOpen = true else runCatching { railFocus.requestFocus() }
            true
        } else false
    }
    val emptyText = if (searchQuery.isNotBlank()) stringResource(R.string.content_no_series_found, searchQuery.trim()) else stringResource(R.string.content_no_series_here)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onChildFocused() },
    ) {
        // Horizontal geometry is a fraction of the mockup's 1920 width, so every zoom reflows.
        val screenW = maxWidth
        fun fx(px: Int) = screenW * (px / 1920f)
        if (cinematic) {
            val heroH = if (panelShares != null) maxHeight * (cinematicDetailsPct / 100f) else 464.mpx
            val toolTop = 122.mpx + heroH
            Box(Modifier.fillMaxSize().then(if (categoriesOpen) Modifier.graphicsLayer { alpha = 0.36f } else Modifier)) {
                VodCinematicBackdrop(titleInfo?.backdropUrl, series = true, Modifier.align(Alignment.TopEnd))
                VodHeader(
                    section = stringResource(R.string.common_nav_series), category = headerLabel, count = vodCount(true, count),
                    showChevron = true, modifier = Modifier.padding(start = fx(84), top = 52.mpx).width(fx(900)),
                )
                titleInfo?.let { VodHero(it, Modifier.padding(start = fx(84), top = 122.mpx).width(fx(1000)).height(heroH)) }
                Column(Modifier.padding(top = toolTop).fillMaxSize().then(paneModifier)) {
                    Row(
                        Modifier.padding(start = fx(68), end = fx(64)).fillMaxWidth()
                            // ▼ from the toolbar returns to the title the hero shows, not the poster below the tool.
                            .onPreviewKeyEvent { e -> e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && focusCurrentTitle() }
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(10.mpx),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StageSearchField(
                            query = searchQuery, onQueryChange = vm::setSearchQuery,
                            placeholder = stringResource(R.string.content_search_in, headerLabel),
                            modifier = Modifier.width(440.mpx).focusRequester(listSearchFocus).then(searchLeft),
                        )
                        VodSortTool(sortMode, Modifier.focusRequester(sortFocus)) { sortOpen = true }
                        StageTool(text = stringResource(R.string.content_category_browser_title), icon = OwnTVIcon.LIST, onClick = { categoriesOpen = true })
                        Spacer(Modifier.weight(1f))
                        StageKeyHints(listOf(playHint, optionsHint, "◀" to stringResource(R.string.content_category_browser_title)))
                    }
                    if (series.itemCount == 0) {
                        Text(emptyText, style = stageText(20, 500), color = StageColors.Muted, modifier = Modifier.padding(start = fx(84), top = 60.mpx))
                    } else {
                        // The 196 × 294 posters, 26 apart, as many across as fit; the grid grows downwards
                        // (owner, 2026-10-01: the mockup's single row became a grid). Glow room on every side.
                        val glowRoom = 24.mpx
                        val rowW = screenW - fx(84) - fx(64)
                        val columns = ((rowW + 26.mpx) / 222.mpx).toInt().coerceAtLeast(1)
                        // A fixed gap under the toolbar that scrolling posters never enter; the grid's own
                        // top padding is only the room the focused poster's lift and ring need.
                        Spacer(Modifier.height(14.mpx))
                        CompositionLocalProvider(LocalBringIntoViewSpec provides edgeScrollSpec) {
                            LazyVerticalGrid(
                                state = effectiveGridState,
                                columns = GridCells.Fixed(columns),
                                horizontalArrangement = Arrangement.spacedBy(26.mpx),
                                verticalArrangement = Arrangement.spacedBy(30.mpx),
                                contentPadding = PaddingValues(start = fx(84), end = fx(64), top = 14.mpx, bottom = glowRoom),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            ) {
                                items(count = series.itemCount, key = series.itemKey { it.id }, contentType = series.itemContentType { "series" }) { index ->
                                    val show = series[index] ?: return@items
                                    StagePoster(
                                        title = show.name,
                                        rating = show.rating?.takeIf { it > 0 }?.let(::vodRating),
                                        width = 196.mpx, height = 294.mpx,
                                        progress = seriesProgress[show.id],
                                        onClick = { onOpenAt(index); vm.openSeries(show) },
                                        onLongClick = { openMenu(show, index) },
                                        modifier = Modifier
                                            .gridFocusTarget(
                                                itemId = show.id, index = index,
                                                contextId = contextSeriesId, contextFocus = contextFocus,
                                                selectedId = targetSeriesId, selectedFocus = gridSelFocus,
                                                firstItemFocus = firstItemFocus,
                                            )
                                            .onFocusChanged { if (it.isFocused) onShowFocus(show) }
                                            .onPreviewKeyEvent { e ->
                                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                when (e.key) {
                                                    Key.Menu -> { openMenu(show, index); true }
                                                    // ◀ from the first column opens the categories.
                                                    Key.DirectionLeft -> index % columns == 0 && run { categoriesOpen = true; true }
                                                    else -> false
                                                }
                                            },
                                    ) { VodPosterArt(posterOf(show), OwnTVIcon.SERIES) }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Separate panels (P5-05 … P5-07): the category column, the titles, the details card.
            val locked = lockedKey != null
            val margin = if (locked) 0.dp else fx(64)
            val rightEdge = if (locked) screenW else fx(1880)
            val gapCol = if (locked) 0.dp else fx(26)
            val gapCard = fx(30)
            val cardShown = panelShares?.preview != 0
            val p = panelShares?.let { computePanelWidths(it, rightEdge - margin, gapCol + if (cardShown) gapCard else 0.dp) }
            val colW = if (locked) 0.dp else p?.category ?: fx(350)
            val cardW = if (!cardShown) 0.dp else p?.preview ?: fx(550)
            val listX = margin + colW + gapCol
            val listW = if (cardShown) (rightEdge - cardW - gapCard - listX) else rightEdge - listX
            val top = if (locked) 0.mpx else 128.mpx
            if (!locked) {
                VodHeader(
                    section = stringResource(R.string.common_nav_series), category = headerLabel, count = vodCount(true, count),
                    showChevron = false, modifier = Modifier.padding(start = fx(84), top = 52.mpx).width(fx(860)),
                )
                LiveCategories(
                    searchQuery = vm.categoryQuery.collectAsStateWithLifecycle().value,
                    onSearchQueryChange = vm::setCategoryQuery,
                    entries = categoryEntries,
                    selectedIndex = selectedIndex,
                    groupsHeading = groupsHeading,
                    sheet = false,
                    listState = catListState,
                    onSelect = onCategorySelect,
                    onLongSelect = onCategoryLongSelect,
                    onNavigateRight = { focusTitles() },
                    focusRequester = railFocus,
                    focusRowIndex = railFocusRow,
                    onRowFocused = { railFocusRow = null },
                    onRowFocus = { catFocusIndex = it },
                    modifier = categoriesModifier
                        .padding(start = margin, top = top, bottom = 24.mpx)
                        .width(colW)
                        .fillMaxHeight(),
                )
            }
            Column(Modifier.padding(start = listX, top = top).width(listW).fillMaxHeight().then(paneModifier)) {
                Row(
                    Modifier.fillMaxWidth().focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StageSearchField(
                        query = searchQuery, onQueryChange = vm::setSearchQuery,
                        placeholder = stringResource(R.string.content_search_in, headerLabel),
                        modifier = Modifier.weight(1f).focusRequester(listSearchFocus).then(searchLeft),
                    )
                    StageTool(text = null, icon = OwnTVIcon.SORT, value = vodSortLabel(sortMode), trailingIcon = OwnTVIcon.CHEVRON_DOWN, onClick = { sortOpen = true }, modifier = Modifier.focusRequester(sortFocus))
                    StageSegmented(
                        options = listOf(stringResource(R.string.settings_view_grid), stringResource(R.string.settings_view_list)),
                        selected = if (viewMode == SettingsRepository.VodViewMode.LIST) 1 else 0,
                        icons = listOf(OwnTVIcon.GRID, OwnTVIcon.LIST),
                        onSelect = { i -> if ((i == 1) != (viewMode == SettingsRepository.VodViewMode.LIST)) vm.toggleViewMode() },
                    )
                }
                // The lists clip what is drawn outside them, which cut the focused card's glow: they are
                // laid out [glowRoom] wider at each side and padded back by the same amount.
                val glowRoom = 24.mpx
                val glowWide = Modifier.layout { measurable, constraints ->
                    val extra = glowRoom.roundToPx()
                    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-extra, 0) }
                }
                if (series.itemCount == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(emptyText, style = stageText(20, 500), color = StageColors.Muted)
                    }
                } else if (viewMode == SettingsRepository.VodViewMode.LIST) {
                    Spacer(Modifier.height(20.mpx))
                    CompositionLocalProvider(LocalBringIntoViewSpec provides edgeScrollSpec) {
                        LazyColumn(
                            state = effectiveListState,
                            verticalArrangement = Arrangement.spacedBy(6.mpx),
                            contentPadding = PaddingValues(start = glowRoom, end = glowRoom, bottom = 16.mpx),
                            modifier = Modifier.fillMaxWidth().weight(1f).then(glowWide),
                        ) {
                            items(count = series.itemCount, key = series.itemKey { it.id }, contentType = series.itemContentType { "series" }) { index ->
                                val show = series[index] ?: return@items
                                VodListRow(
                                    title = show.name,
                                    line = vodLine(show.year, show.rating, null),
                                    posterUrl = posterOf(show),
                                    placeholder = OwnTVIcon.SERIES,
                                    progress = seriesProgress[show.id],
                                    mark = playlistMarks[show.sourceId],
                                    onClick = { onOpenAt(index); vm.openSeries(show) },
                                    onLongClick = { openMenu(show, index) },
                                    modifier = Modifier
                                        .gridFocusTarget(
                                            itemId = show.id, index = index,
                                            contextId = contextSeriesId, contextFocus = contextFocus,
                                            selectedId = targetSeriesId, selectedFocus = gridSelFocus,
                                            firstItemFocus = firstItemFocus,
                                        )
                                        .focusRequester(stepper.focus(index))
                                        .onFocusChanged { if (it.isFocused) onShowFocus(show) }
                                        .onPreviewKeyEvent { e ->
                                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                            when (e.key) {
                                                Key.Menu -> { openMenu(show, index); true }
                                                Key.DirectionLeft -> !locked && run { runCatching { railFocus.requestFocus() }; true }
                                                // Step by row number: a held key outran the paged list and stopped (playbook 2).
                                                Key.DirectionDown -> (stepper.target ?: index).let { from -> from + 1 < series.itemCount && run { stepper.stepTo(from + 1); true } }
                                                Key.DirectionUp -> (stepper.target ?: index).let { from -> from > 0 && run { stepper.stepTo(from - 1); true } }
                                                else -> false
                                            }
                                        },
                                )
                            }
                        }
                    }
                } else {
                    // 156 × 234 posters 20 apart (5 across in the mockup's 860); a wider panel gains columns.
                    val columns = ((listW + 20.mpx) / 176.mpx).toInt().coerceAtLeast(1)
                    val posterW = (listW - 20.mpx * (columns - 1)) / columns
                    // A fixed gap under the toolbar that scrolled posters never enter (as Cinematic).
                    Spacer(Modifier.height(20.mpx))
                    CompositionLocalProvider(LocalBringIntoViewSpec provides edgeScrollSpec) {
                        LazyVerticalGrid(
                            state = effectiveGridState,
                            columns = GridCells.Fixed(columns),
                            horizontalArrangement = Arrangement.spacedBy(20.mpx),
                            verticalArrangement = Arrangement.spacedBy(30.mpx),
                            contentPadding = PaddingValues(start = glowRoom, end = glowRoom, top = 12.mpx, bottom = 24.mpx),
                            modifier = Modifier.fillMaxWidth().weight(1f).then(glowWide),
                        ) {
                            items(count = series.itemCount, key = series.itemKey { it.id }, contentType = series.itemContentType { "series" }) { index ->
                                val show = series[index] ?: return@items
                                StagePoster(
                                    title = show.name,
                                    rating = show.rating?.takeIf { it > 0 }?.let(::vodRating),
                                    width = posterW, height = posterW * 1.5f,
                                    compact = true,
                                    progress = seriesProgress[show.id],
                                    onClick = { onOpenAt(index); vm.openSeries(show) },
                                    onLongClick = { openMenu(show, index) },
                                    modifier = Modifier
                                        .gridFocusTarget(
                                            itemId = show.id, index = index,
                                            contextId = contextSeriesId, contextFocus = contextFocus,
                                            selectedId = targetSeriesId, selectedFocus = gridSelFocus,
                                            firstItemFocus = firstItemFocus,
                                        )
                                        .onFocusChanged { if (it.isFocused) onShowFocus(show) }
                                        .onPreviewKeyEvent { e ->
                                            if (e.type == KeyEventType.KeyDown && e.key == Key.Menu) { openMenu(show, index); true } else false
                                        },
                                ) { VodPosterArt(posterOf(show), OwnTVIcon.SERIES) }
                            }
                        }
                    }
                }
            }
            if (cardShown) {
                VodDetailsCard(
                    info = titleInfo,
                    hints = listOf(playHint, optionsHint),
                    series = true,
                    modifier = Modifier
                        .padding(start = rightEdge - cardW, top = if (locked) 0.mpx else 118.mpx, bottom = if (locked) 0.mpx else 32.mpx)
                        .width(cardW)
                        .fillMaxHeight(),
                )
            }
        }

        if (categoriesOpen) {
            LiveCategories(
                searchQuery = vm.categoryQuery.collectAsStateWithLifecycle().value,
                onSearchQueryChange = vm::setCategoryQuery,
                entries = categoryEntries,
                selectedIndex = selectedIndex,
                groupsHeading = groupsHeading,
                sheet = true,
                listState = catListState,
                onSelect = onCategorySelect,
                onLongSelect = onCategoryLongSelect,
                onNavigateRight = { categoriesOpen = false; focusTitles() },
                focusRequester = railFocus,
                focusRowIndex = railFocusRow,
                onRowFocused = { railFocusRow = null },
                onRowFocus = { catFocusIndex = it },
                sheetHint = stringResource(R.string.common_nav_series),
                modifier = categoriesModifier
                    .padding(start = fx(24), top = 24.mpx, bottom = 24.mpx)
                    .width(
                        // Panel widths › Categories sheet (Cinematic): its own % of the screen, as Live TV's Stage sheet.
                        if (panelShares != null && cinematic) screenW * (cinematicSheetPct / 100f) else fx(450),
                    )
                    .fillMaxHeight(),
            )
        }
    }

    if (sortOpen) {
        VodSortMenu(
            series = true, current = sortMode,
            x = if (cinematic) 470f else 880f, top = if (cinematic) 250.mpx else 186.mpx,
            onPick = vm::setSort,
            onDismiss = { sortOpen = false; runCatching { sortFocus.requestFocus() } },
        )
    }

    // Long-press (or the remote's Menu key) on a series → ☰ Title options (P5-12).
    contextSeries?.let { s ->
        val cacheForS = selectedSeriesMeta?.takeIf { it.seriesId == s.id }?.cache
        val trailerKey = if (metadataMode.enrich) cacheForS?.trailerKey else null
        val inHistory by androidx.compose.runtime.produceState(selectedKey == LiveKey.History, s.id) { value = vm.isInHistory(s) }
        val canMove = selectedKey is LiveKey.Folder || selectedKey is LiveKey.Custom || selectedKey == LiveKey.Favorites
        val actions = buildList {
            add(MenuAction("play_trailer", stringResource(R.string.content_play_trailer), OwnTVIcon.PLAY_CIRCLE, group = VodGroupWatch) {
                trailerKey?.let { contextSeries = null; trailerTitle = s.name; trailerVideoKey = it }
            })
            add(MenuAction("favourite", stringResource(if (favoriteIds.contains(s.id)) R.string.content_remove_favourite else R.string.content_add_favourite), OwnTVIcon.FAVORITE, group = VodGroupLibrary) {
                vm.toggleFavorite(s); contextSeries = null
            })
            add(MenuAction("download", stringResource(R.string.content_download_all_episodes), OwnTVIcon.DOWNLOADS, group = VodGroupLibrary) {
                vm.downloadSeries(s); contextSeries = null
            })
            add(MenuAction("remove_history", stringResource(R.string.content_remove_history), OwnTVIcon.HISTORY, group = VodGroupLibrary) {
                vm.removeFromHistory(s.id); contextSeries = null
            })
            if (canMove) {
                add(MenuAction("move", stringResource(R.string.content_move), OwnTVIcon.MOVE, group = VodGroupOrganise) {
                    contextSeries = null; vm.enterMoveMode(s, selectedKey)
                })
                // "Move to category…" (issue #87): send this series into a user's combined category.
                add(MenuAction("move_to_category", stringResource(R.string.content_move_to_category), OwnTVIcon.FOLDER, group = VodGroupOrganise) {
                    moveOriginKey = when (val k = selectedKey) {
                        is LiveKey.Folder -> vm.folderKey(k.id)
                        is LiveKey.Custom -> k.id
                        LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                        else -> null
                    }
                    moveOriginName = railItems.firstOrNull { it.key == selectedKey }?.title
                    moveItem = s
                    contextSeries = null
                })
            }
            add(MenuAction("hide", stringResource(R.string.common_hide), OwnTVIcon.EYE_OFF, group = VodGroupOrganise) { vm.hideSeries(s); contextSeries = null })
            add(MenuAction("tmdb_details", stringResource(R.string.content_series_details), OwnTVIcon.INFO, group = VodGroupDetails) {
                if (cacheForS != null) { contextSeries = null; detailsSeries = s }
            })
            // Update details (§11.2 U5a) and Set TMDB name (U5b): only while enrichment is on.
            if (metadataMode.enrich) {
                add(MenuAction("refetch_tmdb", stringResource(R.string.content_refetch_tmdb), OwnTVIcon.REFRESH, group = VodGroupDetails) {
                    contextSeries = null
                    toast.show(refetchingTmdbMessage)
                    vm.refetchSeriesMeta(s)
                })
                add(MenuAction("set_tmdb_name", stringResource(R.string.content_set_tmdb_name), OwnTVIcon.PENCIL, group = VodGroupDetails) {
                    contextSeries = null; setTmdbNameSeries = s
                })
            }
        }
        VodOptionsMenu(
            title = s.name,
            subtitle = listOfNotNull(
                s.year?.toString(),
                s.categoryId?.let { id -> categoryEntries.firstOrNull { it.item.key == LiveKey.Folder(id) }?.label },
            ).joinToString(" · ").ifBlank { null },
            posterUrl = posterOf(s),
            x = if (cinematic) 1300f else 1320f,
            menu = ContentMenu.SERIES,
            actions = actions,
            disabled = setOfNotNull(
                "play_trailer".takeIf { trailerKey == null },
                "remove_history".takeIf { !inHistory },
                "tmdb_details".takeIf { !(metadataMode.enrich && cacheForS != null) },
            ),
            onDismiss = { contextSeries = null },
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
        TrailerPlayerScreen(videoKey = key, title = trailerTitle, onExit = { trailerVideoKey = null })
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

    // Category Move mode overlay.
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
            categoryName = item.displayLabel(R.string.content_category_all_series),
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

    // Restore focus to the rail when the context menu or category move mode closes.
    // Land on the row the menu was opened from; fall back to the column if that row is gone (Hide).
    fun restoreToContextCategory() {
        val row = contextCategoryKey?.let { k -> railItems.indexOfFirst { it.key == k } }?.takeIf { it >= 0 }
        contextCategoryKey = null
        if (row != null) railFocusRow = row else runCatching { railFocus.requestFocus() }
    }
    var catMenuWasOpen by remember { mutableStateOf(false) }
    var categoryMoveWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(contextCategory, categoryMoveState) {
        if (contextCategory != null) catMenuWasOpen = true
        if (categoryMoveState != null) categoryMoveWasOpen = true

        if (contextCategory == null && catMenuWasOpen && categoryMoveState == null) {
            catMenuWasOpen = false
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
        logoUrl = tv.own.owntv.core.metadata.MetadataImages.logo(meta?.logoPath),
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
    val metaLine = listOfNotNull(
        rememberAirDateLabel(ep, meta) ?: meta?.year?.let { localizedInteger(it, grouping = false) },
        meta?.rating?.takeIf { it > 0 }?.let { stringResource(R.string.content_rating, it) },
    ).joinToString(stringResource(R.string.content_metadata_separator))
    return tv.own.owntv.features.shell.components.MediaDetailsUi(
        title = title,
        subtitle = stringResource(R.string.content_season_episode, ep.seasonNumber, ep.episodeNumber),
        backdropUrl = still,
        posterUrl = null,
        metaLine = metaLine,
        plot = plot,
    )
}

/**
 * The series page (P6-01 … P6-04): the show's backdrop at the top right, the crumb "‹ Series ·
 * **Crunchyroll**", the title 92/800, the meta line, a two-line synopsis, the Resume / Favourite /
 * Download season / ⋯ buttons, the season tabs with the Grid | List switch and the episode order tool,
 * then the episodes as 16:9 tiles or as list rows. The header stays put; only the episodes scroll.
 */
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
    val researchingTmdbMessage = stringResource(R.string.content_researching_tmdb)
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val loading by vm.episodesLoading.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val downloads by vm.episodeDownloads.collectAsStateWithLifecycle()
    val selectedSeason by vm.selectedSeason.collectAsStateWithLifecycle()
    val lastPlayedId by vm.lastPlayedEpisodeId.collectAsStateWithLifecycle()
    val selectedEpisode by vm.selectedEpisode.collectAsStateWithLifecycle()
    val selectedEpisodeMeta by vm.selectedEpisodeMeta.collectAsStateWithLifecycle()
    val selectedSeriesMeta by vm.selectedSeriesMeta.collectAsStateWithLifecycle()
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val metadataMode by vm.metadataMode.collectAsStateWithLifecycle()
    val episodeProgress by vm.episodeProgress.collectAsStateWithLifecycle()
    val completedIds by vm.completedEpisodeIds.collectAsStateWithLifecycle()
    val hideWatched by vm.hideWatched.collectAsStateWithLifecycle()
    val seriesOrder by vm.seriesOrder.collectAsStateWithLifecycle()
    val nextUpId by vm.nextUpEpisodeId.collectAsStateWithLifecycle()
    val episodeViewMode by vm.episodeViewMode.collectAsStateWithLifecycle()
    val isEpisodeGrid = episodeViewMode == SettingsRepository.VodViewMode.GRID
    val epListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val epGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val seasonMeta by vm.seasonEpisodeMeta.collectAsStateWithLifecycle()
    // Long-running shows have more seasons than fit on one line (12+): the tabs scroll.
    val seasonRowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selFocus = remember { FocusRequester() }
    val firstEpFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val moreFocus = remember { FocusRequester() }
    var initialFocused by remember { mutableStateOf(false) }
    var contextEpisode by remember { mutableStateOf<EpisodeEntity?>(null) }
    var detailsEpisode by remember { mutableStateOf<EpisodeEntity?>(null) }
    // Downloaded subtitles for the episode whose options menu is open (subtitle plan §11).
    var contextEpisodeSubs by remember { mutableStateOf<List<tv.own.owntv.core.database.dao.LinkedSubtitle>>(emptyList()) }
    var showEpisodeDeleteSubs by remember { mutableStateOf(false) }
    var showSorting by remember { mutableStateOf(false) }
    // The ⋯ button's series options and the windows they open.
    var seriesMenuOpen by remember { mutableStateOf(false) }
    var seriesFlowOpen by remember { mutableStateOf(false) }
    var trailerVideoKey by remember { mutableStateOf<String?>(null) }
    var showSeriesDetails by remember { mutableStateOf(false) }
    var setTmdbName by remember { mutableStateOf(false) }
    // Options target's id + its tile's FocusRequester: refocus the episode when the menu (or a window it
    // opened) closes — otherwise focus dies with the menu and falls to the rail.
    var contextEpisodeId by remember { mutableStateOf<Long?>(null) }
    val epContextFocus = remember { FocusRequester() }
    val toast = rememberInAppToast()

    BackHandler { vm.closeSeries() }
    // The show's own details (backdrop, genres, synopsis) are resolved for the focused show; opening one
    // from Home or a Continue pill never focused it in the grid.
    LaunchedEffect(series.id) { vm.onSeriesFocused(series) }
    val seriesMeta = selectedSeriesMeta?.takeIf { it.seriesId == series.id }?.cache
    val info = seriesTitleInfo(series, seriesMeta, metadataMode.tmdbWins)
    val categoryName = series.categoryId
        ?.let { id -> railItems.firstOrNull { it.key == LiveKey.Folder(id) } }
        ?.let { ProviderTags.parse(it.displayLabel(R.string.content_category_all_series)).name }

    /** Scrolls whichever episode layout is live, so focus handling stays layout-agnostic. */
    suspend fun scrollEpisodes(index: Int) {
        if (isEpisodeGrid) epGridState.scrollToItem(index) else epListState.scrollToItem(index)
    }

    // Seasons and episodes are ordered independently (the Episode order menu); both sort explicitly.
    // Specials (season 0, #228) always come after the numbered seasons, in either order.
    val seasons = episodes.map { it.seasonNumber }.distinct()
        .let { if (seriesOrder.seasonsDescending) it.sortedDescending() else it.sorted() }
        .sortedBy { it == 0 }
    val activeSeason = if (seasons.contains(selectedSeason)) selectedSeason else seasons.firstOrNull() ?: 1
    val seasonEpisodes = episodes.filter { it.seasonNumber == activeSeason }
        .let { list ->
            if (seriesOrder.episodesDescending) list.sortedByDescending { ep -> ep.episodeNumber }
            else list.sortedBy { ep -> ep.episodeNumber }
        }
    // "Hide watched" drops episodes watched to ≥95%. Focus-index math below uses this list so a
    // filtered-out last-watched episode falls back to the first visible one instead of losing focus.
    val visibleEpisodes = remember(seasonEpisodes, hideWatched, completedIds) {
        if (hideWatched) seasonEpisodes.filterNot { it.id in completedIds } else seasonEpisodes
    }

    // Opening a show: focus the LAST-WATCHED episode if there is one (#22), else the first. Waits for
    // !loading so the seeded last-watched id/season from the VM is settled. When entering via
    // player-return, mark done WITHOUT focusing — the restore below owns focus.
    LaunchedEffect(loading, seasonEpisodes.isNotEmpty(), restoreFocus) {
        if (initialFocused) return@LaunchedEffect
        if (restoreFocus) { initialFocused = true; return@LaunchedEffect }
        if (!loading && visibleEpisodes.isNotEmpty()) {
            initialFocused = true
            val idx = lastPlayedId?.let { id -> visibleEpisodes.indexOfFirst { it.id == id } } ?: -1
            delay(80)
            if (idx >= 0) {
                runCatching { scrollEpisodes(idx) }
                delay(40)
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
    // CH+- key paging for the episodes.
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
            delay(60)
            runCatching { selFocus.requestFocus() }
        }
        onRestored()
    }

    // A new order or season starts at its top; the first run is the opening, which focuses its own episode.
    var orderSeen by remember { mutableStateOf(false) }
    LaunchedEffect(seriesOrder, activeSeason) {
        if (orderSeen) runCatching { scrollEpisodes(0) } else orderSeen = true
    }
    // Keep the active season's tab in view (opening on a deep season, or after a switch).
    LaunchedEffect(activeSeason, seasons.size) {
        if (seasons.size > 1) {
            val idx = seasons.indexOf(activeSeason)
            if (idx >= 0) runCatching { seasonRowState.scrollToItem(idx) }
        }
    }

    // What the main button plays: the next-up episode (resumed when started), else the first one.
    val target = nextUpId?.let { id -> episodes.firstOrNull { it.id == id } }
        ?: episodes.minWithOrNull(compareBy({ it.seasonNumber }, { it.episodeNumber }))
    val targetProgress = target?.let { episodeProgress[it.id] }
        ?.takeIf { it.positionMs > 0 && it.durationMs > 0 && target.id !in completedIds }
    // The episode's own title: the provider's without its show/code prefix, TMDB's where that leaves
    // nothing (or first, in TMDB-first mode), else "Episode 3".
    val resources = androidx.compose.ui.platform.LocalResources.current
    val epName: (EpisodeEntity, tv.own.owntv.core.database.entity.MetadataCacheEntity?) -> String = { ep, meta ->
        val tmdb = meta?.title?.takeIf { it.isNotBlank() }
        val own = EpisodeTitles.clean(ep.name, series.name)
        (if (metadataMode.tmdbWins) tmdb ?: own else own ?: tmdb)
            ?: resources.getString(R.string.player_episode_number, ep.episodeNumber)
    }
    val durationSecs: (EpisodeEntity) -> Int? = { ep ->
        ep.durationSecs?.takeIf { it > 0 } ?: episodeProgress[ep.id]?.durationMs?.takeIf { it > 0 }?.let { (it / 1000).toInt() }
    }
    // What is left of a started episode, at least a minute ("12 min left").
    val secondsLeft: (EpisodeEntity) -> Int? = { ep ->
        episodeProgress[ep.id]?.takeIf { it.positionMs > 0 && it.durationMs > 0 && ep.id !in completedIds }
            ?.let { (((it.durationMs - it.positionMs) / 1000).toInt()).coerceAtLeast(60) }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() },
    ) {
        // Horizontal geometry is a fraction of the mockup's 1920 width, so every zoom reflows.
        val screenW = maxWidth
        fun fx(px: Int) = screenW * (px / 1920f)
        EpisodesBackdrop(info.backdropUrl ?: info.posterUrl, Modifier.align(Alignment.TopEnd))
        // The crumb: "‹ Series · Crunchyroll".
        Row(
            Modifier.padding(start = fx(84), top = 50.mpx).width(fx(980)),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(OwnTVIcon.CHEVRON_LEFT, StageColors.Muted, Modifier.size(21.mpx))
            Text(
                stringResource(R.string.common_nav_series) + if (categoryName != null) " ·" else "",
                style = stageText(21, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            categoryName?.let {
                Text(it, style = stageText(21, 700), color = tv.own.owntv.ui.theme.stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(start = fx(84), top = 104.mpx).width(fx(980))) {
            // At most 170 high, the room the films' hero gives its title, so two lines shrink rather than
            // push the episodes down.
            tv.own.owntv.features.shell.components.VodHeroTitle(series.name, Modifier.heightIn(max = 170.mpx))
            VodMeta(
                parts = listOfNotNull(
                    info.year?.toString(),
                    info.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    // Specials are not counted as a season, as on Home.
                    seasons.count { it > 0 }.takeIf { it > 0 }?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.home_trending_seasons, it, it) },
                ),
                rating = info.rating, tags = info.tags, size = 20,
                modifier = Modifier.padding(top = 18.mpx, bottom = 12.mpx),
            )
            info.plot?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, style = stageText(19.5f, 400).copy(lineHeight = (19.5f * 1.55f).mpxSp), color = Color(0xFFC9D3CF),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 860.mpx),
                )
            }
            Row(
                Modifier.padding(top = 26.mpx).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(14.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (target != null) {
                    val code = stringResource(R.string.content_season_episode, target.seasonNumber, target.episodeNumber)
                    StageButton(
                        text = stringResource(if (targetProgress != null) R.string.content_resume_episode else R.string.content_play_episode, code),
                        trailing = secondsLeft(target)?.let { "· " + stringResource(R.string.home_time_left, vodRuntime(it)) },
                        onClick = { startEpisode(target) },
                        icon = OwnTVIcon.PLAY, iconFilled = true, tinted = true, height = 60.mpx, textSize = 20,
                    )
                }
                val favourite = favoriteIds.contains(series.id)
                StageButton(
                    text = stringResource(if (favourite) R.string.content_favorited else R.string.content_favorite),
                    onClick = { vm.toggleFavorite(series) },
                    icon = OwnTVIcon.FAVORITE, iconFilled = favourite, height = 60.mpx, textSize = 20,
                )
                if (visibleEpisodes.isNotEmpty() || seasonEpisodes.isNotEmpty()) {
                    StageButton(
                        text = stringResource(R.string.content_download_season),
                        onClick = { vm.downloadSeason(series, activeSeason) },
                        icon = OwnTVIcon.DOWNLOADS, height = 60.mpx, textSize = 20,
                    )
                }
                StageButton(
                    text = null, onClick = { seriesMenuOpen = true }, icon = OwnTVIcon.MORE, round = true,
                    height = 60.mpx, textSize = 20, modifier = Modifier.focusRequester(moreFocus),
                )
            }
        }

        // Season tabs, Grid | List and the order tool, bottom-aligned; the tab underline ends at 564.
        Row(
            Modifier.padding(top = 504.mpx).fillMaxWidth().height(60.mpx).focusGroup(),
            verticalAlignment = Alignment.Bottom,
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                state = seasonRowState,
                horizontalArrangement = Arrangement.spacedBy(20.mpx),
                contentPadding = PaddingValues(start = fx(84) - 12.mpx),
                modifier = Modifier.weight(1f),
            ) {
                items(seasons, key = { it }) { season ->
                    tv.own.owntv.ui.stage.StageTab(
                        label = if (season == 0) stringResource(R.string.content_season_specials) else stringResource(R.string.content_season, season),
                        count = localizedInteger(episodes.count { it.seasonNumber == season }, grouping = false),
                        selected = season == activeSeason,
                        onClick = { vm.selectSeason(season) },
                    )
                }
            }
            Row(
                Modifier.padding(start = 24.mpx, end = fx(64), bottom = 6.mpx),
                horizontalArrangement = Arrangement.spacedBy(10.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageSegmented(
                    options = listOf(stringResource(R.string.settings_view_grid), stringResource(R.string.settings_view_list)),
                    selected = if (isEpisodeGrid) 0 else 1,
                    icons = listOf(OwnTVIcon.GRID, OwnTVIcon.LIST),
                    onSelect = { i ->
                        vm.setEpisodeViewMode(if (i == 0) SettingsRepository.VodViewMode.GRID else SettingsRepository.VodViewMode.LIST)
                    },
                )
                StageTool(
                    text = null, icon = OwnTVIcon.SORT,
                    value = stringResource(if (seriesOrder.episodesDescending) R.string.content_newest_first else R.string.content_oldest_first),
                    trailingIcon = OwnTVIcon.CHEVRON_DOWN, onClick = { showSorting = true },
                    modifier = Modifier.focusRequester(sortFocus),
                )
            }
        }

        // The episodes: only this part scrolls. Its top padding is the focused tile's lift and ring room.
        val epModifierFor: (Int, EpisodeEntity) -> Modifier = { index, ep ->
            Modifier
                .then(if (ep.id == lastPlayedId) Modifier.focusRequester(selFocus) else Modifier)
                .then(if (index == 0) Modifier.focusRequester(firstEpFocus) else Modifier)
                .then(if (ep.id == contextEpisodeId) Modifier.focusRequester(epContextFocus) else Modifier)
                .onFocusChanged { if (it.isFocused) vm.onEpisodeFocused(ep) }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Menu) { contextEpisode = ep; contextEpisodeId = ep.id; true } else false
                }
        }
        val epArea = Modifier
            .padding(top = 586.mpx)
            .fillMaxSize()
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
                    // The target becomes the context anchor so epContextFocus binds to it, then scroll + focus.
                    val jump = visibleEpisodes.getOrNull(idx) ?: return@chNavPaging
                    contextEpisodeId = jump.id
                    vm.onEpisodeFocused(jump)
                    scope.launch {
                        runCatching { scrollEpisodes(idx) }
                        withFrameNanos { }
                        runCatching { epContextFocus.requestFocus() }
                    }
                },
            )
        val openMenu: (EpisodeEntity) -> Unit = { ep -> contextEpisode = ep; contextEpisodeId = ep.id }
        when {
            loading && episodes.isEmpty() -> Box(epArea, contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 48) }
            episodes.isEmpty() -> Box(epArea, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.content_no_episodes), style = stageText(20, 500), color = StageColors.Muted)
            }
            isEpisodeGrid -> {
                // 336 × 189 stills 26 apart, 5 across at 1920; a wider screen gains columns.
                val rowW = screenW - fx(84) - fx(52)
                val columns = ((rowW + 26.mpx) / 362.mpx).toInt().coerceAtLeast(1)
                val tileW = (rowW - 26.mpx * (columns - 1)) / columns
                CompositionLocalProvider(LocalBringIntoViewSpec provides edgeScrollSpec) {
                    LazyVerticalGrid(
                        state = epGridState,
                        columns = GridCells.Fixed(columns),
                        horizontalArrangement = Arrangement.spacedBy(26.mpx),
                        verticalArrangement = Arrangement.spacedBy(33.mpx),
                        contentPadding = PaddingValues(start = fx(84), end = fx(52), top = 24.mpx, bottom = 40.mpx),
                        modifier = epArea,
                    ) {
                        gridItemsIndexed(visibleEpisodes, key = { _, ep -> ep.id }) { index, ep ->
                            val meta = seasonMeta[ep.id]
                            val completed = ep.id in completedIds
                            val prog = episodeProgress[ep.id]
                            val title = epName(ep, meta)
                            StageEpisode(
                                title = title,
                                line = episodeLine(rememberAirDateLabel(ep, meta), durationSecs(ep), secondsLeft(ep), watched = false),
                                number = localizedInteger(ep.episodeNumber, grouping = false),
                                watched = completed,
                                progress = prog?.takeIf { it.durationMs > 0 }?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) },
                                onClick = { startEpisode(ep) },
                                onLongClick = { openMenu(ep) },
                                width = tileW, height = tileW * (189f / 336f),
                                modifier = epModifierFor(index, ep),
                            ) { filter -> EpisodeStill(episodeStillUrl(meta, series, "w780"), filter) }
                        }
                    }
                }
            }
            else -> {
                // `.dl` rows 1790 × 138, 12 apart, the still 224 × 126.
                CompositionLocalProvider(LocalBringIntoViewSpec provides edgeScrollSpec) {
                    LazyColumn(
                        state = epListState,
                        verticalArrangement = Arrangement.spacedBy(12.mpx),
                        contentPadding = PaddingValues(start = fx(68), end = fx(62), top = 20.mpx, bottom = 40.mpx),
                        modifier = epArea,
                    ) {
                        itemsIndexed(visibleEpisodes, key = { _, ep -> ep.id }) { index, ep ->
                            val meta = seasonMeta[ep.id]
                            val completed = ep.id in completedIds
                            val prog = episodeProgress[ep.id]
                            val title = epName(ep, meta)
                            val plot = if (metadataMode.tmdbWins) meta?.overview ?: ep.plot else ep.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
                            StageRow(
                                onClick = { startEpisode(ep) },
                                onLongClick = { openMenu(ep) },
                                height = 138.mpx, horizontalPadding = 22.mpx, gap = 24.mpx, radius = 24.mpx,
                                modifier = epModifierFor(index, ep).fillMaxWidth(),
                            ) { focused ->
                                Box(Modifier.size(224.mpx, 126.mpx).clip(RoundedCornerShape(14.mpx))) {
                                    EpisodeStill(episodeStillUrl(meta, series, "w300"), null)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.content_episode_numbered, localizedInteger(ep.episodeNumber, grouping = false), title),
                                        style = stageText(24, 700), color = if (focused) Color.White else StageColors.Text,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        episodeLine(rememberAirDateLabel(ep, meta), durationSecs(ep), null, watched = completed),
                                        style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.mpx),
                                    )
                                    plot?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it, style = stageText(16, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 6.mpx),
                                        )
                                    }
                                }
                                Box(Modifier.width(300.mpx)) {
                                    val fraction = if (completed) 1f else prog?.takeIf { it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs }
                                    if (fraction != null && fraction > 0f) StageProgress(fraction, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // When the episode options close (action or dismiss), focus the episode they were opened from —
    // unless a window they opened (Episode details) now owns focus; it refocuses on close.
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

    // Load the opened episode's downloaded subtitles so the menu can enable "Delete subtitles" (§11).
    LaunchedEffect(contextEpisode?.id) {
        contextEpisodeSubs = contextEpisode?.let { runCatching { vm.downloadedSubtitles(it) }.getOrDefault(emptyList()) } ?: emptyList()
    }

    // ☰ Episode options (P6-04): WATCH, LIBRARY, DETAILS, in the order of Settings › Long-press menus.
    contextEpisode?.takeIf { !showEpisodeDeleteSubs }?.let { ep ->
        val cacheForEp = selectedEpisodeMeta?.takeIf { it.episodeId == ep.id }?.cache
        val watched = ep.id in completedIds
        val actions = buildList {
            add(MenuAction("play_start", stringResource(R.string.content_play_from_start), OwnTVIcon.PLAY, group = VodGroupWatch) {
                contextEpisode = null; vm.playEpisode(ep, 0); goFullscreen()
            })
            add(MenuAction("play_external", stringResource(R.string.content_play_external_short), OwnTVIcon.EXTERNAL, group = VodGroupWatch) {
                contextEpisode = null; vm.playEpisodeExternal(ep)
            })
            add(MenuAction("download", stringResource(R.string.content_download), OwnTVIcon.DOWNLOADS, group = VodGroupLibrary) {
                contextEpisode = null
                if (downloads[ep.id] != null) toast.show(alreadyDownloadedMessage) else vm.downloadEpisode(ep)
            })
            // Manual override of the ≥95% auto-detected watched state.
            add(MenuAction("mark_watched", stringResource(if (watched) R.string.content_mark_unwatched else R.string.content_mark_watched), OwnTVIcon.CHECK, group = VodGroupLibrary) {
                contextEpisode = null
                if (watched) vm.markEpisodeUnwatched(ep) else vm.markEpisodeWatched(ep)
            })
            add(MenuAction("tmdb_details", stringResource(R.string.content_episode_details), OwnTVIcon.INFO, group = VodGroupDetails) {
                if (metadataMode.enrich && cacheForEp != null) { contextEpisode = null; detailsEpisode = ep }
            })
            // Update details (§11.2 U5a) clears this episode's cache AND its show's match, then re-searches.
            if (metadataMode.enrich) {
                add(MenuAction("refetch_tmdb", stringResource(R.string.content_refetch_tmdb), OwnTVIcon.REFRESH, group = VodGroupDetails) {
                    contextEpisode = null
                    toast.show(refetchingTmdbMessage)
                    vm.refetchEpisodeMeta(series, ep)
                })
            }
            add(MenuAction("delete_subtitles", stringResource(R.string.content_delete_subtitles), OwnTVIcon.SUBTITLE, group = VodGroupDetails) {
                if (contextEpisodeSubs.isNotEmpty()) showEpisodeDeleteSubs = true
            })
        }
        val title = epName(ep, seasonMeta[ep.id])
        VodOptionsMenu(
            title = stringResource(R.string.content_episode_numbered, localizedInteger(ep.episodeNumber, grouping = false), title),
            subtitle = series.name + " · " + if (ep.seasonNumber == 0) stringResource(R.string.content_season_specials) else stringResource(R.string.content_season, ep.seasonNumber),
            posterUrl = episodeStillUrl(seasonMeta[ep.id], series, "w300"),
            x = 1290f,
            menu = ContentMenu.EPISODE,
            actions = actions,
            disabled = setOfNotNull(
                "tmdb_details".takeIf { !(metadataMode.enrich && cacheForEp != null) },
                "delete_subtitles".takeIf { contextEpisodeSubs.isEmpty() },
            ),
            onDismiss = { contextEpisode = null },
            top = 250.mpx, artWidth = 112.mpx, artHeight = 63.mpx, firstFocusKey = "download",
        )
    }

    // Episode order (P6-03): seasons and episodes each oldest or newest first, remembered for this
    // series, and Hide watched episodes. Applies at once; stays open so all three can be set in one visit.
    if (showSorting) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
        val choices = listOf(stringResource(R.string.content_oldest_first), stringResource(R.string.content_newest_first))
        tv.own.owntv.features.epg.GuideMenuHost(
            x = 1380f, top = 330.mpx, width = 480.mpx,
            onDismiss = { showSorting = false; runCatching { sortFocus.requestFocus() } },
        ) {
            StageMenuHeader(stringResource(R.string.content_episode_order), stringResource(R.string.content_episode_order_hint))
            StageGroupLabel(stringResource(R.string.content_seasons).uppercase(locale))
            StageMenuChoice(choices, if (seriesOrder.seasonsDescending) 1 else 0, { vm.setSeriesOrder(it == 1, seriesOrder.episodesDescending) })
            StageGroupLabel(stringResource(R.string.content_episodes).uppercase(locale))
            StageMenuChoice(choices, if (seriesOrder.episodesDescending) 1 else 0, { vm.setSeriesOrder(seriesOrder.seasonsDescending, it == 1) }, focusRequester = focus)
            StageGroupLabel(stringResource(R.string.content_menu_group_show).uppercase(locale))
            StageMenuItem(
                text = stringResource(R.string.content_hide_watched_episodes), icon = OwnTVIcon.EYE_OFF,
                value = stringResource(if (hideWatched) R.string.common_on else R.string.common_off),
                onClick = { vm.setHideWatched(!hideWatched) },
            )
        }
    }

    // ⋯ Series options (P5-12, without ORGANISE: this page has no category to move or hide within).
    if (seriesMenuOpen) {
        val trailerKey = if (metadataMode.enrich) seriesMeta?.trailerKey else null
        val inHistory by androidx.compose.runtime.produceState(false, series.id) { value = vm.isInHistory(series) }
        val close: () -> Unit = { seriesMenuOpen = false }
        val openWindow: () -> Unit = { seriesMenuOpen = false }
        val actions = buildList {
            add(MenuAction("play_trailer", stringResource(R.string.content_play_trailer), OwnTVIcon.PLAY_CIRCLE, group = VodGroupWatch) {
                trailerKey?.let { openWindow(); trailerVideoKey = it }
            })
            add(MenuAction("favourite", stringResource(if (favoriteIds.contains(series.id)) R.string.content_remove_favourite else R.string.content_add_favourite), OwnTVIcon.FAVORITE, group = VodGroupLibrary) {
                vm.toggleFavorite(series); close()
            })
            add(MenuAction("download", stringResource(R.string.content_download_all_episodes), OwnTVIcon.DOWNLOADS, group = VodGroupLibrary) {
                vm.downloadSeries(series); close()
            })
            add(MenuAction("remove_history", stringResource(R.string.content_remove_history), OwnTVIcon.HISTORY, group = VodGroupLibrary) {
                vm.removeFromHistory(series.id); close()
            })
            add(MenuAction("tmdb_details", stringResource(R.string.content_series_details), OwnTVIcon.INFO, group = VodGroupDetails) {
                if (seriesMeta != null) { openWindow(); showSeriesDetails = true }
            })
            if (metadataMode.enrich) {
                add(MenuAction("refetch_tmdb", stringResource(R.string.content_refetch_tmdb), OwnTVIcon.REFRESH, group = VodGroupDetails) {
                    close(); toast.show(refetchingTmdbMessage); vm.refetchSeriesMeta(series)
                })
                add(MenuAction("set_tmdb_name", stringResource(R.string.content_set_tmdb_name), OwnTVIcon.PENCIL, group = VodGroupDetails) {
                    openWindow(); setTmdbName = true
                })
            }
        }
        VodOptionsMenu(
            title = series.name,
            subtitle = listOfNotNull(info.year?.toString(), categoryName).joinToString(" · ").ifBlank { null },
            posterUrl = info.posterUrl,
            x = 1300f,
            menu = ContentMenu.SERIES,
            actions = actions,
            disabled = setOfNotNull(
                "play_trailer".takeIf { trailerKey == null },
                "remove_history".takeIf { !inHistory },
                "tmdb_details".takeIf { !(metadataMode.enrich && seriesMeta != null) },
            ),
            onDismiss = close,
        )
    }
    // The menu, and any window it opened, returns focus to ⋯ once all of them are closed.
    LaunchedEffect(seriesMenuOpen, trailerVideoKey, showSeriesDetails, setTmdbName) {
        if (seriesMenuOpen) { seriesFlowOpen = true; return@LaunchedEffect }
        if (trailerVideoKey != null || showSeriesDetails || setTmdbName || !seriesFlowOpen) return@LaunchedEffect
        seriesFlowOpen = false
        withFrameNanos { }
        runCatching { moreFocus.requestFocus() }
    }
    if (showSeriesDetails) {
        tv.own.owntv.features.shell.components.MediaDetailsScreen(
            details = buildSeriesDetails(series, seriesMeta, metadataMode.tmdbWins),
            onExit = { showSeriesDetails = false },
        )
    }
    if (setTmdbName) {
        var prefill by remember(series.id) { mutableStateOf<SeriesViewModel.TmdbNamePrefill?>(null) }
        LaunchedEffect(series.id) { prefill = vm.seriesTmdbNamePrefill(series) }
        prefill?.let { p ->
            SetTmdbNameDialog(
                initialTitle = p.title,
                initialYear = p.year,
                hasOverride = p.hasOverride,
                onSave = { title, year -> setTmdbName = false; vm.setSeriesTmdbName(series, title, year); toast.show(researchingTmdbMessage) },
                onClear = { setTmdbName = false; vm.clearSeriesTmdbName(series); toast.show(researchingTmdbMessage) },
                onDismiss = { setTmdbName = false },
            )
        }
    }
    trailerVideoKey?.let { key -> TrailerPlayerScreen(videoKey = key, title = series.name, onExit = { trailerVideoKey = null }) }

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
                    // Last one deleted → close the popup AND the menu so focus returns to the episode.
                    if (contextEpisodeSubs.isEmpty()) { showEpisodeDeleteSubs = false; contextEpisode = null }
                },
                onDismiss = { showEpisodeDeleteSubs = false; contextEpisode = null },
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

/**
 * The day an episode first aired, ready to render, or null when nothing knows it. Asked for by a
 * user whose series run to thousands of episodes, where the titles are near-identical and the
 * number stops being a landmark long before episode nine hundred.
 */
@Composable
private fun rememberAirDateLabel(
    episode: EpisodeEntity,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
): String? {
    val format = rememberAirDateFormatter()
    val ms = tv.own.owntv.core.content.AirDate.of(episode.airDateMs, meta?.airDate)
    return ms?.let(format)
}

/**
 * What the hero and the details card show for [series], with the §7.1 / §4.1 precedence: provider first,
 * TMDB filling the gaps — flipped when the source mode is TMDB-only. A show has no runtime of its own.
 */
internal fun seriesTitleInfo(
    series: tv.own.owntv.core.database.entity.SeriesEntity,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
): VodTitleInfo {
    val providerPlot = series.plot?.takeIf { it.isNotBlank() }
    val providerPoster = series.posterUrl?.takeIf { it.isNotBlank() }
    val tmdbPoster = tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
    return VodTitleInfo(
        title = series.name,
        logoUrl = tv.own.owntv.core.metadata.MetadataImages.logo(meta?.logoPath),
        backdropUrl = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath, size = "w1280")
            ?: series.backdropUrl?.takeIf { it.isNotBlank() },
        posterUrl = if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster,
        year = if (tmdbWins) meta?.year ?: series.year else series.year ?: meta?.year,
        genres = jsonStringList(meta?.genresJson),
        runtimeSecs = null,
        rating = if (tmdbWins) meta?.rating?.takeIf { it > 0 } ?: series.rating?.takeIf { it > 0 }
            else series.rating?.takeIf { it > 0 } ?: meta?.rating?.takeIf { it > 0 },
        tags = cinematicQualityBadges(series.qualityRank, series.advertisedCapabilities),
        plot = if (tmdbWins) meta?.overview ?: providerPlot else providerPlot ?: meta?.overview,
        cast = tv.own.owntv.core.metadata.MetadataCast.parse(meta?.castJson),
    )
}


/**
 * The series page's backdrop: 1400 × 700 at the top right, dissolving into the page on its left (34%)
 * and bottom (38%), under a left wash (80% → 30% at 34% → 0 at 60%) and a bottom one (90% → 0 at 40%).
 */
@Composable
private fun EpisodesBackdrop(url: String?, modifier: Modifier = Modifier) {
    val wash = Color(5, 8, 10)
    Box(modifier.fillMaxWidth(1400f / 1920f).aspectRatio(2f).dissolveEdges(start = 0.34f, bottom = 0.38f)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(1f, -0.4f), modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().gradientWash(false, 0f to wash.copy(alpha = 0.8f), 0.34f to wash.copy(alpha = 0.3f), 0.6f to wash.copy(alpha = 0f), 1f to wash.copy(alpha = 0f)))
        Box(Modifier.fillMaxSize().gradientWash(true, 0f to wash.copy(alpha = 0f), 0.6f to wash.copy(alpha = 0f), 0.97f to wash.copy(alpha = 0.9f), 1f to wash.copy(alpha = 0.9f)))
    }
}

/**
 * An episode's picture: TMDB's still, else the show's backdrop (16:9 like the still), else its poster.
 * The provider stores no episode image, so the show's art only stands in for a missing still.
 */
private fun episodeStillUrl(
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    series: SeriesEntity,
    size: String,
): String? = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath, size = size)
    ?: series.backdropUrl?.takeIf { it.isNotBlank() } ?: series.posterUrl?.takeIf { it.isNotBlank() }

@Composable
private fun EpisodeStill(url: String?, filter: androidx.compose.ui.graphics.ColorFilter?) {
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, colorFilter = filter, modifier = Modifier.fillMaxSize())
        } else {
            OwnTVIcon(OwnTVIcon.SERIES, StageColors.Dim, Modifier.size(40.mpx))
        }
    }
}

/** "Jan 21, 2024 · 24 min · 12 min left" (grid) or "… · Watched" (list): only the parts that are known. */
@Composable
private fun episodeLine(aired: String?, durationSecs: Int?, secondsLeft: Int?, watched: Boolean): String = listOfNotNull(
    aired,
    durationSecs?.let { vodRuntime(it) },
    secondsLeft?.let { stringResource(R.string.home_time_left, vodRuntime(it)) },
    if (watched) stringResource(R.string.content_episode_watched) else null,
).joinToString(" · ")
