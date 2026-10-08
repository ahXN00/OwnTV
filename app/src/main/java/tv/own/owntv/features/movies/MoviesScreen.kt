@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package tv.own.owntv.features.movies

import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.stage.StageSegmented
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.delay
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
import tv.own.owntv.core.database.entity.MovieEntity
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
import tv.own.owntv.features.shell.components.MediaDetailsScreen
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.ui.components.InAppToast
import tv.own.owntv.ui.components.rememberInAppToast
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.ui.components.MenuAction
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.ResumeDialog
import tv.own.owntv.ui.components.SetTmdbNameDialog
import tv.own.owntv.ui.components.TrailerPlayerScreen
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.gridFocusTarget
import androidx.compose.foundation.layout.width
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.format.localizedInteger
import tv.own.owntv.core.live.LiveKey

@Composable
fun MoviesScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    onContentScrolled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * Pins the grid to one folder and takes the category rail away — how More → Favourites and
     * More → History show films without a second copy of this grid existing.
     */
    lockedKey: LiveKey? = null,
    /** Hands the shell this screen's way in from the rail (Separate: the open category; Cinematic: the title). */
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
) {
    val vm: MovieViewModel = koinViewModel()
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
    val alreadyDownloadedMessage = stringResource(R.string.content_already_downloaded)
    val refetchingTmdbMessage = stringResource(R.string.content_refetching_tmdb)
    val researchingTmdbMessage = stringResource(R.string.content_researching_tmdb)
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val storedViewMode by vm.viewMode.collectAsStateWithLifecycle()
    val selectedMovie by vm.selectedMovie.collectAsStateWithLifecycle()
    val selectedMovieMeta by vm.selectedMovieMeta.collectAsStateWithLifecycle()
    val metadataMode by vm.metadataMode.collectAsStateWithLifecycle()
    val moveState by vm.moveState.collectAsStateWithLifecycle()
    val categoryMoveState by vm.categoryMoveState.collectAsStateWithLifecycle()
    var contextMovie by remember { mutableStateOf<MovieEntity?>(null) }
    var contextCategory by remember { mutableStateOf<LiveRailItem?>(null) }
    // The rail row a category menu was opened from, kept after the menu closes so the cursor can go
    // back to that exact row. Held as a key, not an index: a Move changes the row's position.
    var contextCategoryKey by remember { mutableStateOf<LiveKey?>(null) }
    var railFocusRow by remember { mutableStateOf<Int?>(null) }
    val railFocus = remember { FocusRequester() }
    // The movie the "Move to category…" flow is moving (issue #87), with the origin captured at
    // menu-open time (the rail can't change under the modal, but capturing is still safer).
    var moveItem by remember { mutableStateOf<MovieEntity?>(null) }
    var moveOriginKey by remember { mutableStateOf<String?>(null) }
    var moveOriginName by remember { mutableStateOf<String?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    // Fullscreen TMDB details window (§11.1); null = closed.
    var detailsMovie by remember { mutableStateOf<MovieEntity?>(null) }
    // "Set TMDB name" dialog target (§11.2 U5b); null = closed.
    var setTmdbNameMovie by remember { mutableStateOf<MovieEntity?>(null) }
    // In-app trailer playback (§7.3 U4); non-null = fullscreen player open with this YouTube key.
    var trailerVideoKey by remember { mutableStateOf<String?>(null) }
    var trailerTitle by remember { mutableStateOf<String?>(null) }
    // Downloaded subtitles for the movie whose context menu is open (subtitle plan §11); drives the
    // "Delete subtitles" action + its popup. Reloaded on menu open and after each delete.
    var contextMovieSubs by remember { mutableStateOf<List<tv.own.owntv.core.database.dao.LinkedSubtitle>>(emptyList()) }
    var showDeleteSubs by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val toast = rememberInAppToast()
    // Id + list position of the movie the context menu was opened on. The id re-focuses the same item
    // when it survives (Favourite/Download/Cancel); when the item is REMOVED (Remove from history, or
    // un-Favourite while on the Favorites category), it's gone from the paged list, so we re-focus the
    // nearest surviving neighbour by position instead of escaping to the CategoryRail.
    var contextMovieId by remember { mutableStateOf<Long?>(null) }
    var contextMovieIndex by remember { mutableStateOf(-1) }
    val contextFocus = remember { FocusRequester() }
    val selectedProgress by vm.selectedProgress.collectAsStateWithLifecycle()
    val movieProgress by vm.movieProgress.collectAsStateWithLifecycle()
    val downloadStates by vm.downloadStates.collectAsStateWithLifecycle()
    val movies = vm.movies.collectAsLazyPagingItems()
    val resumeMode by vm.resumeMode.collectAsStateWithLifecycle()
    // Global external-player toggle: never mount the fullscreen in-app player (it spins up mpv)
    // when playback is handed to an external app.
    val externalPlayerOn by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val goFullscreen: () -> Unit = { if (!externalPlayerOn) onFullscreen() }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)
    val selectedLabel = selectedItem?.displayLabel(R.string.content_category_all_movies) ?: stringResource(R.string.content_category_all_movies)

    // Resume flow: AUTO continues silently, ASK prompts (≥10s saved), NEVER starts from zero.
    val scope = rememberCoroutineScope()
    var resumePrompt by remember { mutableStateOf<Pair<MovieEntity, Long>?>(null) }
    val startMovie: (MovieEntity) -> Unit = { m ->
        scope.launch {
            val pos = vm.savedPositionMs(m)
            when {
                resumeMode == SettingsRepository.ResumeMode.ASK && pos >= 10_000 -> resumePrompt = m to pos
                resumeMode == SettingsRepository.ResumeMode.AUTO && pos > 0 -> { vm.play(m, pos); goFullscreen() }
                else -> { vm.play(m, 0); goFullscreen() }
            }
        }
    }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val selFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    // Right from the rail on an empty list: the list's search box, so a search with no results can be cleared.
    val listSearchFocus = remember { FocusRequester() }

    // CH+- key paging: shared settings + hoisted rail state. gridPaneFocused/railPaneFocused let
    // chNavPaging consume the keys only for whichever pane is focused.
    val settingsVm: tv.own.owntv.features.settings.SettingsViewModel = koinViewModel()
    val vodLayout by settingsVm.vodLayout.collectAsStateWithLifecycle()
    // Cinematic is the browse section's; More → Favourites / History pin the plain Separate list.
    val cinematic = vodLayout == SettingsRepository.VodLayout.CINEMATIC && lockedKey == null
    val cinematicDetailsPct by settingsVm.cinematicDetailsHeight(PanelSection.MOVIES).collectAsStateWithLifecycle()
    // Cinematic is grid-only: the List rows have nowhere to put a full-bleed backdrop. The stored
    // choice is deliberately not rewritten, so switching back to Separate restores the user's List.
    val viewMode = if (cinematic) SettingsRepository.VodViewMode.GRID else storedViewMode
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val rememberMovies by settingsVm.rememberLastMovies.collectAsStateWithLifecycle()

    // "Remember last item per category": ON → each category keeps its own scroll position (per-category
    // grid + list states, so view-mode toggles also keep their offsets). OFF → reset the shared grid/list
    // states to the top whenever the category changes (fixes the cross-category scroll-leak bug).
    val perCategoryGrid = remember { mutableStateMapOf<LiveKey, LazyGridState>() }
    val perCategoryList = remember { mutableStateMapOf<LiveKey, LazyListState>() }
    val perCategoryMovieIds = remember { mutableStateMapOf<LiveKey, Long>() }
    // NOTE: plain constructors, not remember*State() — these are created lazily inside getOrPut, so a
    // @Composable/rememberSaveable call here would register slots conditionally and corrupt the slot table.
    val effectiveGridState = if (rememberMovies) perCategoryGrid.getOrPut(selectedKey) { LazyGridState() } else gridState
    val effectiveListState = if (rememberMovies) perCategoryList.getOrPut(selectedKey) { LazyListState() } else listState
    LaunchedEffect(selectedKey, rememberMovies) {
        if (!rememberMovies) { runCatching { gridState.scrollToItem(0) }; runCatching { listState.scrollToItem(0) } }
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
    val catListState = rememberLazyListState()
    val chromeScrollThresholdPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val contentScrolled by remember(
        effectiveGridState,
        effectiveListState,
        catListState,
        viewMode,
        chromeScrollThresholdPx,
    ) {
        androidx.compose.runtime.derivedStateOf {
            val contentMoved = if (!usesList) {
                effectiveGridState.firstVisibleItemIndex > 0 ||
                    effectiveGridState.firstVisibleItemScrollOffset > chromeScrollThresholdPx
            } else {
                effectiveListState.firstVisibleItemIndex > 0 ||
                    effectiveListState.firstVisibleItemScrollOffset > chromeScrollThresholdPx
            }
            contentMoved || catListState.firstVisibleItemIndex > 0 ||
                catListState.firstVisibleItemScrollOffset > chromeScrollThresholdPx
        }
    }
    LaunchedEffect(contentScrolled) { onContentScrolled(contentScrolled) }
    var gridPaneFocused by remember { mutableStateOf(false) }
    var railPaneFocused by remember { mutableStateOf(false) }
    // Tiles the provider gave no artwork for: ask for the TMDB poster already cached from an earlier
    // focus, so the placeholder is only shown when nothing at all is known. peek() reads the loaded
    // page without triggering a fetch.
    val cachedPosters by vm.cachedPosters.collectAsStateWithLifecycle()
    LaunchedEffect(effectiveGridState, effectiveListState, viewMode, movies) {
        val grid = !usesList
        snapshotFlow {
            val info = if (grid) effectiveGridState.layoutInfo.visibleItemsInfo.map { it.index }
            else effectiveListState.layoutInfo.visibleItemsInfo.map { it.index }
            info.filter { it < movies.itemCount }
                .mapNotNull { movies.peek(it) }
                .filter { it.posterUrl.isNullOrBlank() }
        }.distinctUntilChanged().collect { vm.onPosterlessVisible(it) }
    }
    // Returning from the player: scroll to and focus the movie you just played (waits for the grid to load).
    LaunchedEffect(restoreFocus, movies.itemCount) {
        if (!restoreFocus || movies.itemCount == 0) return@LaunchedEffect
        val sel = selectedMovie
        val idx = if (sel != null) movies.itemSnapshotList.items.indexOfFirst { it.id == sel.id } else -1
        if (idx >= 0) {
            // Scroll whichever layout is on screen: scrolling only the grid state left LIST view
            // unscrolled, so a movie further down was never composed and focus fell to the CategoryRail
            // instead of the film just played. Same defect as the Series back-from-show restore.
            scrollToIndex(idx)
            delay(60)
            runCatching { selFocus.requestFocus() }
        }
        onRestored()
    }
    // Search's "Go to movie": the same as Live TV's — scroll to where the movie sits, find its title by
    // id and focus it, re-asserting while the old category's titles may still be showing.
    val reveal by vm.reveal.collectAsStateWithLifecycle()
    LaunchedEffect(reveal, selectedKey) {
        val r = reveal ?: return@LaunchedEffect
        if (selectedKey != r.key) return@LaunchedEffect
        if (rememberMovies) perCategoryMovieIds[selectedKey] = r.id
        var found = false
        for (attempt in 0 until 50) {
            if (movies.itemCount > 0) {
                val idx = movies.itemSnapshotList.indexOfFirst { it?.id == r.id }
                if (idx >= 0) {
                    if (!gridPaneFocused) scrollToIndex(idx)
                    withFrameNanos { }
                    if (runCatching { selFocus.requestFocus() }.getOrDefault(false)) found = true
                    if (found && attempt >= 8) break
                } else {
                    scrollToIndex(r.position.coerceAtMost(movies.itemCount - 1))
                }
            }
            delay(100)
        }
        if (!found) runCatching { firstItemFocus.requestFocus() }
        vm.revealDone()
    }
    // Closing the long-press context menu must return focus inside this pane, never the CategoryRail.
    //   - Item still present (Favourite toggle / Download / Cancel): re-focus the same item by id.
    //   - Item removed (Remove from history, or un-Favourite on the Favorites category): the paged
    //     list no longer contains it, so focus the NEAREST surviving neighbour by position (the item
    //     that slid into the removed slot, else the new last item, else first item). Only if the whole
    //     category is now empty do we let focus leave (there's nothing here to land on).
    LaunchedEffect(contextMovie, moveItem, creatingCategory, moveState) {
        if (contextMovie != null) return@LaunchedEffect
        // Opening the TMDB Details window or the Set TMDB name dialog closes the menu; don't yank focus
        // back to the grid — they need it (and trap it). The grid is refocused when they close (see below).
        if (detailsMovie != null) return@LaunchedEffect
        if (setTmdbNameMovie != null) return@LaunchedEffect
        if (trailerVideoKey != null) return@LaunchedEffect
        // The context menu closes before MoveToCategoryDialog (and its nested name prompt) opens, and
        // the reorder overlay owns focus while it is up. Do not focus the grid behind any of them;
        // this effect re-runs when the whole flow closes and restores the row below.
        if (moveItem != null || creatingCategory || moveState != null) return@LaunchedEffect

        val targetId = contextMovieId
        if (targetId == null) { contextMovieIndex = -1; return@LaunchedEffect }
        val items = movies.itemSnapshotList.items
        val idx = items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            // Item survived — re-focus it directly.
            scrollToIndex(idx)
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        } else {
            // Item was removed. Wait for the paged list to settle, then land on the nearest survivor.
            withFrameNanos { }
            val settled = movies.itemSnapshotList.items.filterNotNull()
            if (settled.isEmpty()) {
                runCatching { firstItemFocus.requestFocus() } // nothing left; firstItemFocus attaches to the next item that loads
            } else {
                val neighbor = settled.getOrNull(contextMovieIndex.coerceAtLeast(0)) ?: settled.last()
                val neighborIdx = items.indexOfFirst { it.id == neighbor.id }.coerceAtLeast(0)
                scrollToIndex(neighborIdx)
                // selFocus is bound to selectedMovie; reuse the generic firstItemFocus path only if that
                // fails. Here we re-purpose contextFocus by re-binding it: re-request after a frame so the
                // neighbour row (now at contextMovieIndex) receives focus.
                contextMovieId = neighbor.id
                withFrameNanos { }
                runCatching { contextFocus.requestFocus() }
            }
        }
        contextMovieIndex = -1
    }

    // The title the cursor was on: after a long-press it carries contextFocus instead of selFocus
    // (gridFocusTarget prefers it), so both are tried before the first title (fix playbook 6).
    fun focusCurrentTitle(): Boolean =
        runCatching { selFocus.requestFocus() }.getOrDefault(false) ||
            runCatching { contextFocus.requestFocus() }.getOrDefault(false) ||
            runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)
    // From the categories to the titles: the remembered movie, else the first; the search field when empty.
    fun focusTitles() {
        val targetId = if (rememberMovies) perCategoryMovieIds[selectedKey] ?: selectedMovie?.id else selectedMovie?.id
        scope.launch {
            if (movies.itemCount > 0) {
                val targetIdx = targetId?.let { id -> movies.itemSnapshotList.items.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
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
    val panelShares = rememberPanelShares(PanelSection.MOVIES, settingsVm)
    val cinematicSheetPct by settingsVm.cinematicSheetWidth(PanelSection.MOVIES).collectAsStateWithLifecycle()
    val selectedMeta = selectedMovieMeta?.takeIf { it.movieId == selectedMovie?.id }?.cache
    val titleInfo = selectedMovie?.let { movieTitleInfo(it, selectedMeta, metadataMode.tmdbWins) }
    val (categoryEntries, groupsHeading) = vodCategoryEntries(railItems, railCounts, playlistMarks, series = false)
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
    val targetMovieId = if (rememberMovies) perCategoryMovieIds[selectedKey] ?: selectedMovie?.id else selectedMovie?.id
    val openMenu: (MovieEntity, Int) -> Unit = { movie, index -> contextMovie = movie; contextMovieId = movie.id; contextMovieIndex = index }
    val onMovieFocus: (MovieEntity) -> Unit = { movie ->
        vm.onMovieFocused(movie)
        if (rememberMovies) perCategoryMovieIds[selectedKey] = movie.id
    }
    val posterOf: (MovieEntity) -> String? = { it.posterUrl?.takeIf { u -> u.isNotBlank() } ?: cachedPosters[it.id] }
    val playHint = stringResource(R.string.common_ok) to stringResource(R.string.content_play)
    val optionsHint = stringResource(R.string.content_key_hold_ok) to stringResource(R.string.content_key_options)

    // The toolbar and the titles: one focus group, entered on a title, never the search field.
    val paneModifier = Modifier
        .onFocusChanged { gridPaneFocused = it.hasFocus }
        .chNavPaging(
            enabled = chNavEnabled,
            upSkip = chNavUpSkip,
            downSkip = chNavDownSkip,
            isFocused = { gridPaneFocused },
            // On the "All" list (every movie) a long-press jump to the very last item is
            // pointless and janks, so disable long-press there — short-press skipping stays.
            longPressEnabled = { selectedKey != LiveKey.All },
            lastIndex = { movies.itemCount - 1 },
            currentTargetIndex = {
                val sel = selectedMovie
                val idx = if (sel != null) movies.itemSnapshotList.items.indexOfFirst { it.id == sel.id } else -1
                if (idx >= 0) idx
                else if (usesList) effectiveListState.firstVisibleItemIndex
                else effectiveGridState.firstVisibleItemIndex
            },
            onJumpToIndex = { idx ->
                // Scroll the target into view, then set it as the selected movie so selFocus binds to it
                // (gridFocusTarget keys on selectedMovie.id), and request focus after one frame.
                scope.launch {
                    val item = movies.itemSnapshotList.items.getOrNull(idx)
                    scrollToIndex(idx)
                    withFrameNanos { }
                    if (item != null) {
                        // The remembered title is the focus target, so it must move with the jump.
                        onMovieFocus(item)
                        withFrameNanos { }
                        runCatching { selFocus.requestFocus() }
                    } else {
                        runCatching { firstItemFocus.requestFocus() }
                    }
                }
            },
        )
        .focusProperties {
            onEnter = {
                val landed = (targetMovieId != null && focusCurrentTitle()) || runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)
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
    val emptyText = if (searchQuery.isNotBlank()) stringResource(R.string.content_no_movies_found, searchQuery.trim()) else stringResource(R.string.content_no_movies_here)

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
                VodCinematicBackdrop(titleInfo?.backdropUrl, series = false, Modifier.align(Alignment.TopEnd))
                VodHeader(
                    section = stringResource(R.string.common_nav_movies), category = headerLabel, count = vodCount(false, count),
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
                    if (movies.itemCount == 0) {
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
                                items(count = movies.itemCount, key = movies.itemKey { it.id }, contentType = movies.itemContentType { "movie" }) { index ->
                                    val movie = movies[index] ?: return@items
                                    val prog = movieProgress[movie.id]
                                    val done = prog?.let { vm.isMovieCompleted(it) } == true
                                    StagePoster(
                                        title = movie.name,
                                        rating = movie.rating?.takeIf { it > 0 }?.let(::vodRating),
                                        width = 196.mpx, height = 294.mpx,
                                        progress = prog?.takeIf { it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs },
                                        watched = done,
                                        onClick = { startMovie(movie) },
                                        onLongClick = { openMenu(movie, index) },
                                        modifier = Modifier
                                            .gridFocusTarget(
                                                itemId = movie.id, index = index,
                                                contextId = contextMovieId, contextFocus = contextFocus,
                                                selectedId = targetMovieId, selectedFocus = selFocus,
                                                firstItemFocus = firstItemFocus,
                                            )
                                            .onFocusChanged { if (it.isFocused) onMovieFocus(movie) }
                                            .onPreviewKeyEvent { e ->
                                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                when (e.key) {
                                                    Key.Menu -> { openMenu(movie, index); true }
                                                    // ◀ from the first column opens the categories.
                                                    Key.DirectionLeft -> index % columns == 0 && run { categoriesOpen = true; true }
                                                    else -> false
                                                }
                                            },
                                    ) { VodPosterArt(posterOf(movie), OwnTVIcon.MOVIES) }
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
                    section = stringResource(R.string.common_nav_movies), category = headerLabel, count = vodCount(false, count),
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
                if (movies.itemCount == 0) {
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
                            items(count = movies.itemCount, key = movies.itemKey { it.id }, contentType = movies.itemContentType { "movie" }) { index ->
                                val movie = movies[index] ?: return@items
                                val prog = movieProgress[movie.id]
                                val done = prog?.let { vm.isMovieCompleted(it) } == true
                                VodListRow(
                                    title = movie.name,
                                    line = vodLine(movie.year, movie.rating, movie.durationSecs),
                                    posterUrl = posterOf(movie),
                                    placeholder = OwnTVIcon.MOVIES,
                                    progress = if (done || prog == null || prog.durationMs <= 0) null else prog.positionMs.toFloat() / prog.durationMs,
                                    mark = playlistMarks[movie.sourceId],
                                    onClick = { startMovie(movie) },
                                    onLongClick = { openMenu(movie, index) },
                                    modifier = Modifier
                                        .gridFocusTarget(
                                            itemId = movie.id, index = index,
                                            contextId = contextMovieId, contextFocus = contextFocus,
                                            selectedId = targetMovieId, selectedFocus = selFocus,
                                            firstItemFocus = firstItemFocus,
                                        )
                                        .focusRequester(stepper.focus(index))
                                        .onFocusChanged { if (it.isFocused) onMovieFocus(movie) }
                                        .onPreviewKeyEvent { e ->
                                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                            when (e.key) {
                                                Key.Menu -> { openMenu(movie, index); true }
                                                Key.DirectionLeft -> !locked && run { runCatching { railFocus.requestFocus() }; true }
                                                // Step by row number: a held key outran the paged list and stopped (playbook 2).
                                                Key.DirectionDown -> (stepper.target ?: index).let { from -> from + 1 < movies.itemCount && run { stepper.stepTo(from + 1); true } }
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
                            items(count = movies.itemCount, key = movies.itemKey { it.id }, contentType = movies.itemContentType { "movie" }) { index ->
                                val movie = movies[index] ?: return@items
                                val prog = movieProgress[movie.id]
                                val done = prog?.let { vm.isMovieCompleted(it) } == true
                                StagePoster(
                                    title = movie.name,
                                    rating = movie.rating?.takeIf { it > 0 }?.let(::vodRating),
                                    width = posterW, height = posterW * 1.5f,
                                    compact = true,
                                    progress = prog?.takeIf { it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs },
                                    watched = done,
                                    onClick = { startMovie(movie) },
                                    onLongClick = { openMenu(movie, index) },
                                    modifier = Modifier
                                        .gridFocusTarget(
                                            itemId = movie.id, index = index,
                                            contextId = contextMovieId, contextFocus = contextFocus,
                                            selectedId = targetMovieId, selectedFocus = selFocus,
                                            firstItemFocus = firstItemFocus,
                                        )
                                        .onFocusChanged { if (it.isFocused) onMovieFocus(movie) }
                                        .onPreviewKeyEvent { e ->
                                            if (e.type == KeyEventType.KeyDown && e.key == Key.Menu) { openMenu(movie, index); true } else false
                                        },
                                ) { VodPosterArt(posterOf(movie), OwnTVIcon.MOVIES) }
                            }
                        }
                    }
                }
            }
            if (cardShown) {
                VodDetailsCard(
                    info = titleInfo,
                    hints = listOf(playHint, optionsHint),
                    series = false,
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
                sheetHint = stringResource(R.string.common_nav_movies),
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
            series = false, current = sortMode,
            x = if (cinematic) 470f else 880f, top = if (cinematic) 250.mpx else 186.mpx,
            onPick = vm::setSort,
            onDismiss = { sortOpen = false; runCatching { sortFocus.requestFocus() } },
        )
    }

    resumePrompt?.let { (m, pos) ->
        ResumeDialog(
            positionMs = pos,
            onResume = { resumePrompt = null; vm.play(m, pos); goFullscreen() },
            onStartOver = { resumePrompt = null; vm.play(m, 0); goFullscreen() },
            onDismiss = { resumePrompt = null },
        )
    }

    // Load the opened movie's downloaded subtitles so the menu can show "Delete subtitles" (§11).
    LaunchedEffect(contextMovie?.id) {
        contextMovieSubs = contextMovie?.let { runCatching { vm.downloadedSubtitles(it) }.getOrDefault(emptyList()) } ?: emptyList()
    }

    // Long-press (or the remote's Menu key) on a movie → ☰ Title options (P5-03).
    contextMovie?.let { m ->
        val alreadyDownloaded = downloadStates[m.id] != null
        // Movie details only when enrichment is on AND a confident match resolved for THIS movie.
        val cacheForM = selectedMovieMeta?.takeIf { it.movieId == m.id }?.cache
        val watched = selectedProgress?.takeIf { selectedMovie?.id == m.id }?.let { vm.isMovieCompleted(it) } ?: false
        val trailerKey = if (metadataMode.enrich) cacheForM?.trailerKey else null
        val inHistory by androidx.compose.runtime.produceState(selectedKey == LiveKey.History, m.id) { value = vm.isInHistory(m) }
        val canMove = selectedKey is LiveKey.Folder || selectedKey is LiveKey.Custom || selectedKey == LiveKey.Favorites
        val actions = buildList {
            add(MenuAction("play_trailer", stringResource(R.string.content_play_trailer), OwnTVIcon.PLAY_CIRCLE, group = VodGroupWatch) {
                trailerKey?.let { contextMovie = null; trailerTitle = m.name; trailerVideoKey = it }
            })
            // One-off external playback, independent of the global "External player" toggle.
            add(MenuAction("play_external", stringResource(R.string.content_play_external_short), OwnTVIcon.EXTERNAL, group = VodGroupWatch) {
                contextMovie = null; vm.playExternal(m)
            })
            add(MenuAction("favourite", stringResource(if (favoriteIds.contains(m.id)) R.string.content_remove_favourite else R.string.content_add_favourite), OwnTVIcon.FAVORITE, group = VodGroupLibrary) {
                vm.toggleFavorite(m); contextMovie = null
            })
            add(MenuAction("mark_watched", stringResource(if (watched) R.string.content_mark_unwatched else R.string.content_mark_watched), OwnTVIcon.CHECK, group = VodGroupLibrary) {
                if (watched) vm.markMovieUnwatched(m) else vm.markMovieWatched(m)
                contextMovie = null
            })
            add(MenuAction("download", stringResource(R.string.content_download), OwnTVIcon.DOWNLOADS, group = VodGroupLibrary) {
                contextMovie = null
                // Idempotent (§11.1): don't re-queue an existing download — nudge to the Downloads menu.
                if (alreadyDownloaded) toast.show(alreadyDownloadedMessage) else vm.download(m)
            })
            add(MenuAction("remove_history", stringResource(R.string.content_remove_history), OwnTVIcon.HISTORY, group = VodGroupLibrary) {
                vm.removeFromHistory(m.id); contextMovie = null
            })
            if (canMove) {
                add(MenuAction("move", stringResource(R.string.content_move), OwnTVIcon.MOVE, group = VodGroupOrganise) {
                    contextMovie = null; vm.enterMoveMode(m, selectedKey)
                })
                // "Move to category…" (issue #87): send this movie into a user's combined category.
                add(MenuAction("move_to_category", stringResource(R.string.content_move_to_category), OwnTVIcon.FOLDER, group = VodGroupOrganise) {
                    moveOriginKey = when (val k = selectedKey) {
                        is LiveKey.Folder -> vm.folderKey(k.id)
                        is LiveKey.Custom -> k.id
                        LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                        else -> null
                    }
                    moveOriginName = railItems.firstOrNull { it.key == selectedKey }?.title
                    moveItem = m
                    contextMovie = null
                })
            }
            add(MenuAction("hide", stringResource(R.string.common_hide), OwnTVIcon.EYE_OFF, group = VodGroupOrganise) { vm.hideMovie(m); contextMovie = null })
            add(MenuAction("tmdb_details", stringResource(R.string.content_movie_details), OwnTVIcon.INFO, group = VodGroupDetails) {
                if (cacheForM != null) { contextMovie = null; detailsMovie = m }
            })
            // Update details (§11.2 U5a) and Set TMDB name (U5b): only while enrichment is on.
            if (metadataMode.enrich) {
                add(MenuAction("refetch_tmdb", stringResource(R.string.content_refetch_tmdb), OwnTVIcon.REFRESH, group = VodGroupDetails) {
                    contextMovie = null
                    toast.show(refetchingTmdbMessage)
                    vm.refetchMovieMeta(m)
                })
                add(MenuAction("set_tmdb_name", stringResource(R.string.content_set_tmdb_name), OwnTVIcon.PENCIL, group = VodGroupDetails) {
                    contextMovie = null; setTmdbNameMovie = m
                })
            }
            add(MenuAction("delete_subtitles", stringResource(R.string.content_delete_subtitles), OwnTVIcon.SUBTITLE, group = VodGroupDetails) {
                if (contextMovieSubs.isNotEmpty()) showDeleteSubs = true
            })
        }
        VodOptionsMenu(
            title = m.name,
            subtitle = listOfNotNull(
                m.year?.toString(),
                m.categoryId?.let { id -> categoryEntries.firstOrNull { it.item.key == LiveKey.Folder(id) }?.label },
            ).joinToString(" · ").ifBlank { null },
            posterUrl = posterOf(m),
            x = if (cinematic) 1300f else 1320f,
            menu = ContentMenu.MOVIE,
            actions = actions,
            disabled = setOfNotNull(
                "play_trailer".takeIf { trailerKey == null },
                "remove_history".takeIf { !inHistory },
                "tmdb_details".takeIf { !(metadataMode.enrich && cacheForM != null) },
                "delete_subtitles".takeIf { contextMovieSubs.isEmpty() },
            ),
            onDismiss = { contextMovie = null },
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
        moveItem?.let { m ->
            val originKey = moveOriginKey
            if (originKey != null) {
                MoveToCategoryDialog(
                    moveTargets = moveTargets.filterNot { it.id == originKey },
                    originName = moveOriginName ?: stringResource(R.string.settings_customize_this_category),
                    onNewCategory = { creatingCategory = true },
                    onMove = { targetId, keepInOrigin ->
                        vm.moveToCategory(CustomizeKeys.movie(m), m.id, originKey, targetId, keepInOrigin)
                        moveItem = null
                    },
                    onDismiss = { moveItem = null },
                )
            }
        }
    }

    // Per-item "Delete subtitles" popup (§11) — individual deletion; closes when none remain.
    if (showDeleteSubs) {
        val m = contextMovie
        if (m == null || contextMovieSubs.isEmpty()) {
            showDeleteSubs = false
        } else {
            tv.own.owntv.features.subtitles.SubtitleDeletePopup(
                contentTitle = m.name,
                items = contextMovieSubs,
                onDelete = { sub ->
                    vm.deleteSubtitle(sub.cacheId)
                    contextMovieSubs = contextMovieSubs.filterNot { it.cacheId == sub.cacheId }
                    // Last one deleted → close the popup AND the context menu so focus returns to the
                    // movie tile (the menu's Delete action is gone anyway).
                    if (contextMovieSubs.isEmpty()) { showDeleteSubs = false; contextMovie = null }
                },
                onDismiss = { showDeleteSubs = false },
            )
        }
    }

    // When the TMDB Details window closes, return focus to the movie it was opened from (the window
    // trapped focus, so without this it would fall to the sidebar).
    LaunchedEffect(detailsMovie) {
        if (detailsMovie == null && contextMovieId != null) {
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        }
    }

    // Windowed TMDB details popup (§11.1) — read-only, Back exits.
    detailsMovie?.let { m ->
        val cache = selectedMovieMeta?.takeIf { it.movieId == m.id }?.cache
        MediaDetailsScreen(
            details = buildMovieDetails(m, cache, metadataMode.tmdbWins),
            onExit = { detailsMovie = null },
        )
    }

    // "Set TMDB name" override dialog (§11.2 U5b). Prefill once per target (saved override, else cleaned title).
    LaunchedEffect(setTmdbNameMovie) {
        if (setTmdbNameMovie == null && contextMovieId != null) {
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        }
    }
    setTmdbNameMovie?.let { m ->
        var prefill by remember(m.id) { mutableStateOf<MovieViewModel.TmdbNamePrefill?>(null) }
        LaunchedEffect(m.id) { prefill = vm.movieTmdbNamePrefill(m) }
        prefill?.let { p ->
            SetTmdbNameDialog(
                initialTitle = p.title,
                initialYear = p.year,
                hasOverride = p.hasOverride,
                onSave = { title, year ->
                    setTmdbNameMovie = null
                    vm.setMovieTmdbName(m, title, year)
                    toast.show(researchingTmdbMessage)
                },
                onClear = {
                    setTmdbNameMovie = null
                    vm.clearMovieTmdbName(m)
                    toast.show(researchingTmdbMessage)
                },
                onDismiss = { setTmdbNameMovie = null },
            )
        }
    }

    // In-app trailer player (§7.3 U4) — fullscreen over everything; Back/Exit closes and refocuses the movie.
    LaunchedEffect(trailerVideoKey) {
        if (trailerVideoKey == null && contextMovieId != null) {
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
            title = stringResource(R.string.content_reorder_movie),
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
            categoryName = item.displayLabel(R.string.content_category_all_movies),
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

@Composable
private fun metaLine(movie: MovieEntity, meta: tv.own.owntv.core.database.entity.MetadataCacheEntity? = null, tmdbWins: Boolean = false): String {
    val parts = mutableListOf<String>()
    // §7.1 / §4.1: precedence flips with the source mode.
    val year = if (tmdbWins) meta?.year ?: movie.year else movie.year ?: meta?.year
    val rating = if (tmdbWins) meta?.rating?.takeIf { it > 0 } ?: movie.rating?.takeIf { it > 0 }
        else movie.rating?.takeIf { it > 0 } ?: meta?.rating?.takeIf { it > 0 }
    year?.let { parts.add(localizedInteger(it, grouping = false)) }
    rating?.let { parts.add(stringResource(R.string.content_rating, it)) }
    movie.durationSecs?.takeIf { it > 0 }?.let { secs ->
        val h = secs / 3600
        val m = (secs % 3600) / 60
        parts.add(if (h > 0) stringResource(R.string.content_duration_hours, h, m) else stringResource(R.string.content_duration_minutes, m))
    }
    return parts.joinToString(stringResource(R.string.content_metadata_separator))
}

/** Build the fullscreen TMDB-details payload for a movie, applying the §7.1/§4.1 merge precedence. */
@Composable
private fun buildMovieDetails(
    movie: MovieEntity,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
): tv.own.owntv.features.shell.components.MediaDetailsUi {
    val providerPoster = movie.posterUrl?.takeIf { it.isNotBlank() }
    val tmdbPoster = tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
    val poster = if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster
    // Backdrop is TMDB-only (providers don't carry one); fall back to the provider's if it exists.
    val backdrop = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath)
        ?: movie.backdropUrl?.takeIf { it.isNotBlank() }
    val plot = if (tmdbWins) meta?.overview ?: movie.plot else movie.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
    return tv.own.owntv.features.shell.components.MediaDetailsUi(
        title = movie.name,
        backdropUrl = backdrop,
        logoUrl = tv.own.owntv.core.metadata.MetadataImages.logo(meta?.logoPath),
        posterUrl = poster,
        metaLine = metaLine(movie, meta, tmdbWins),
        genres = jsonList(meta?.genresJson),
        plot = plot,
        cast = tv.own.owntv.core.metadata.MetadataCast.parse(meta?.castJson),
    )
}

/** Parse a stored JSON array of strings (genres/cast) back to a list; empty on null/blank/bad JSON. */
private fun jsonList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())
}

/**
 * What the hero and the details card show for [movie], with the §7.1 / §4.1 precedence: provider first,
 * TMDB filling the gaps — flipped when the source mode is TMDB-only. Genres, cast and title art are TMDB's.
 */
internal fun movieTitleInfo(
    movie: MovieEntity,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
): VodTitleInfo {
    val providerPlot = movie.plot?.takeIf { it.isNotBlank() }
    val providerPoster = movie.posterUrl?.takeIf { it.isNotBlank() }
    val tmdbPoster = tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
    return VodTitleInfo(
        title = movie.name,
        logoUrl = tv.own.owntv.core.metadata.MetadataImages.logo(meta?.logoPath),
        backdropUrl = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath, size = "w1280")
            ?: movie.backdropUrl?.takeIf { it.isNotBlank() },
        posterUrl = if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster,
        year = if (tmdbWins) meta?.year ?: movie.year else movie.year ?: meta?.year,
        genres = jsonList(meta?.genresJson),
        runtimeSecs = movie.durationSecs?.takeIf { it > 0 },
        rating = if (tmdbWins) meta?.rating?.takeIf { it > 0 } ?: movie.rating?.takeIf { it > 0 }
            else movie.rating?.takeIf { it > 0 } ?: meta?.rating?.takeIf { it > 0 },
        tags = cinematicQualityBadges(movie.qualityRank, movie.advertisedCapabilities),
        plot = if (tmdbWins) meta?.overview ?: providerPlot else providerPlot ?: meta?.overview,
        cast = tv.own.owntv.core.metadata.MetadataCast.parse(meta?.castJson),
    )
}
