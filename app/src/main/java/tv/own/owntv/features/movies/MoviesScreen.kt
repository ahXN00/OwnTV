package tv.own.owntv.features.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.MovieEntity
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
import tv.own.owntv.features.shell.components.MediaDetailsScreen
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.ui.components.InAppToast
import tv.own.owntv.ui.components.rememberInAppToast
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.ui.components.MenuAction
import tv.own.owntv.ui.components.arranged
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.PosterCard
import tv.own.owntv.ui.components.ProviderChip
import tv.own.owntv.ui.components.ResumeDialog
import tv.own.owntv.ui.components.SetTmdbNameDialog
import tv.own.owntv.ui.components.TrailerPlayerScreen
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.gridFocusTarget
import androidx.compose.foundation.layout.width
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.components.SortChip
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.PreviewPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.format.localizedInteger

@Composable
fun MoviesScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    onContentScrolled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: MovieViewModel = koinViewModel()
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
    val selectedMovie by vm.selectedMovie.collectAsStateWithLifecycle()
    val selectedMovieMeta by vm.selectedMovieMeta.collectAsStateWithLifecycle()
    val metadataMode by vm.metadataMode.collectAsStateWithLifecycle()
    val moveState by vm.moveState.collectAsStateWithLifecycle()
    var contextMovie by remember { mutableStateOf<MovieEntity?>(null) }
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

    // CH+- key paging: shared settings + hoisted rail state. gridPaneFocused/railPaneFocused let
    // chNavPaging consume the keys only for whichever pane is focused.
    val settingsVm: tv.own.owntv.features.settings.SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val rememberMovies by settingsVm.rememberLastMovies.collectAsStateWithLifecycle()

    // "Remember last item per category": ON → each category keeps its own scroll position (per-category
    // grid + list states, so view-mode toggles also keep their offsets). OFF → reset the shared grid/list
    // states to the top whenever the category changes (fixes the cross-category scroll-leak bug).
    val perCategoryGrid = remember { mutableStateMapOf<LiveKey, LazyGridState>() }
    val perCategoryList = remember { mutableStateMapOf<LiveKey, LazyListState>() }
    // NOTE: plain constructors, not remember*State() — these are created lazily inside getOrPut, so a
    // @Composable/rememberSaveable call here would register slots conditionally and corrupt the slot table.
    val effectiveGridState = if (rememberMovies) perCategoryGrid.getOrPut(selectedKey) { LazyGridState() } else gridState
    val effectiveListState = if (rememberMovies) perCategoryList.getOrPut(selectedKey) { LazyListState() } else listState
    LaunchedEffect(selectedKey, rememberMovies) {
        if (!rememberMovies) { runCatching { gridState.scrollToItem(0) }; runCatching { listState.scrollToItem(0) } }
    }
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
            val contentMoved = if (viewMode == SettingsRepository.VodViewMode.GRID) {
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
    // Returning from the player: scroll to and focus the movie you just played (waits for the grid to load).
    LaunchedEffect(restoreFocus, movies.itemCount) {
        if (!restoreFocus || movies.itemCount == 0) return@LaunchedEffect
        val sel = selectedMovie
        val idx = if (sel != null) movies.itemSnapshotList.items.indexOfFirst { it.id == sel.id } else -1
        if (idx >= 0) {
            // Scroll whichever layout is on screen: scrolling only the grid state left LIST view
            // unscrolled, so a movie further down was never composed and focus fell to the CategoryRail
            // instead of the film just played. Same defect as the Series back-from-show restore.
            if (viewMode == SettingsRepository.VodViewMode.LIST) {
                runCatching { effectiveListState.scrollToItem(idx) }
            } else {
                runCatching { effectiveGridState.scrollToItem(idx) }
            }
            delay(60)
            runCatching { selFocus.requestFocus() }
        }
        onRestored()
    }
    // Closing the long-press context menu must return focus inside this pane, never the CategoryRail.
    //   - Item still present (Favourite toggle / Download / Cancel): re-focus the same item by id.
    //   - Item removed (Remove from history, or un-Favourite on the Favorites category): the paged
    //     list no longer contains it, so focus the NEAREST surviving neighbour by position (the item
    //     that slid into the removed slot, else the new last item, else first item). Only if the whole
    //     category is now empty do we let focus leave (there's nothing here to land on).
    var reorderWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(contextMovie, moveItem, creatingCategory, moveState) {
        if (contextMovie != null) return@LaunchedEffect
        // Opening the TMDB Details window or the Set TMDB name dialog closes the menu; don't yank focus
        // back to the grid — they need it (and trap it). The grid is refocused when they close (see below).
        if (detailsMovie != null) return@LaunchedEffect
        if (setTmdbNameMovie != null) return@LaunchedEffect
        if (trailerVideoKey != null) return@LaunchedEffect
        // The context menu closes before MoveToCategoryDialog (and its nested name prompt) opens.
        // Do not focus the grid behind either modal; re-run this effect when the whole flow closes.
        if (moveItem != null || creatingCategory) return@LaunchedEffect

        if (moveState != null) { reorderWasOpen = true; return@LaunchedEffect }
        if (reorderWasOpen) {
            reorderWasOpen = false
            // Fall through to restoreToContextRow logic below.
        }

        val targetId = contextMovieId
        if (targetId == null) { contextMovieIndex = -1; return@LaunchedEffect }
        val items = movies.itemSnapshotList.items
        val idx = items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            // Item survived — re-focus it directly.
            runCatching {
                if (viewMode == SettingsRepository.VodViewMode.LIST) effectiveListState.scrollToItem(idx)
                else effectiveGridState.scrollToItem(idx)
            }
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
                runCatching {
                    if (viewMode == SettingsRepository.VodViewMode.LIST) effectiveListState.scrollToItem(neighborIdx)
                    else effectiveGridState.scrollToItem(neighborIdx)
                }
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

    // Manual panel widths (Settings → Panel Width Adjustment). The saved percentages now resolve
    // against the inside of one shared content container; no stored value is rewritten.
    val panelShares = rememberPanelShares(PanelSection.MOVIES, settingsVm)
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
                    it.displayLabel(R.string.content_category_all_movies),
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
                // CH+- key paging for this movies list/grid. currentTargetIndex falls back to the
                // visible top when the selected movie isn't in the loaded window (paged data).
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
                        if (sel != null) {
                            val idx = movies.itemSnapshotList.items.indexOfFirst { it.id == sel.id }
                            if (idx >= 0) idx
                            else if (viewMode == SettingsRepository.VodViewMode.GRID) effectiveGridState.firstVisibleItemIndex
                            else effectiveListState.firstVisibleItemIndex
                        } else {
                            if (viewMode == SettingsRepository.VodViewMode.GRID) effectiveGridState.firstVisibleItemIndex
                            else effectiveListState.firstVisibleItemIndex
                        }
                    },
                    onJumpToIndex = { idx ->
                        // Scroll the target into view (grid or list), then set it as the selected
                        // movie so selFocus binds to it (gridFocusTarget keys on selectedMovie.id),
                        // and request focus after one frame.
                        scope.launch {
                            val item = movies.itemSnapshotList.items.getOrNull(idx)
                            if (viewMode == SettingsRepository.VodViewMode.GRID) {
                                runCatching { effectiveGridState.scrollToItem(idx) }
                            } else {
                                runCatching { effectiveListState.scrollToItem(idx) }
                            }
                            withFrameNanos { }
                            if (item != null) {
                                vm.onMovieFocused(item)
                                runCatching { selFocus.requestFocus() }
                            } else {
                                runCatching { firstItemFocus.requestFocus() }
                            }
                        }
                    },
                )
                // Entering this pane must land on a poster, never the search bar: prefer the
                // last-focused movie, else the first one. onEnter fires only for directional entry
                // from outside (internal moves don't re-trigger it).
                .focusProperties {
                    onEnter = {
                        if (runCatching { selFocus.requestFocus() }.isFailure) {
                            runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                }
                // Held Up/Down can outrun the lazy grid's composition and escape this pane
                // (landing on the top bar) — trap vertical exits; Left/Right/Back leave normally.
                .trapVerticalFocusExit()
                .focusGroup()
        ) {
            Text(stringResource(R.string.content_section_category, stringResource(R.string.common_nav_movies), selectedLabel), style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.content_count_movies, count, selectedLabel, count),
                style = MaterialTheme.typography.titleMedium,
                color = OwnTVTheme.colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    placeholder = stringResource(R.string.content_search_movies, selectedLabel),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                SortChip(mode = sortMode, onToggle = vm::toggleSort, playlistLabel = stringResource(R.string.content_provider))
                Spacer(Modifier.width(10.dp))
                // View mode (#10): poster wall vs a compact list (more titles at once).
                tv.own.owntv.ui.components.OwnTVButton(
                    label = stringResource(if (viewMode == SettingsRepository.VodViewMode.GRID) R.string.settings_view_grid else R.string.settings_view_list),
                    onClick = vm::toggleViewMode,
                    icon = if (viewMode == SettingsRepository.VodViewMode.GRID) OwnTVIcon.MENU else OwnTVIcon.MOVIES,
                    style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY,
                )
            }
            Spacer(Modifier.height(14.dp))

            if (movies.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) stringResource(R.string.content_no_movies_found, searchQuery.trim()) else stringResource(R.string.content_no_movies_here),
                        style = MaterialTheme.typography.bodyLarge, color = OwnTVTheme.colors.onSurfaceVariant,
                    )
                }
            } else if (viewMode == SettingsRepository.VodViewMode.LIST) {
                LazyColumn(
                    state = effectiveListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = movies.itemCount,
                        key = movies.itemKey { it.id },
                        contentType = movies.itemContentType { "movie" },
                    ) { index ->
                        val movie = movies[index]
                        if (movie != null) {
                            val prog = movieProgress[movie.id]
                            MovieListRow(
                                movie = movie,
                                isFavorite = favoriteIds.contains(movie.id),
                                completed = prog?.let { vm.isMovieCompleted(it) } == true,
                                providerName = providerNames[movie.sourceId],
                                modifier = Modifier.gridFocusTarget(
                                    itemId = movie.id, index = index,
                                    contextId = contextMovieId, contextFocus = contextFocus,
                                    selectedId = selectedMovie?.id, selectedFocus = selFocus,
                                    firstItemFocus = firstItemFocus,
                                ),
                                onFocus = { vm.onMovieFocused(movie) },
                                onClick = { startMovie(movie) },
                                onLongClick = { contextMovie = movie; contextMovieId = movie.id; contextMovieIndex = index },
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
                        count = movies.itemCount,
                        key = movies.itemKey { it.id },
                        contentType = movies.itemContentType { "movie" },
                    ) { index ->
                        val movie = movies[index]
                        if (movie != null) {
                            val prog = movieProgress[movie.id]
                            val done = prog?.let { vm.isMovieCompleted(it) } == true
                            PosterCard(
                                posterUrl = movie.posterUrl,
                                title = movie.name,
                                rating = movie.rating,
                                completed = done,
                                progressFraction = if (done || prog == null || prog.durationMs <= 0) null
                                    else (prog.positionMs.toFloat() / prog.durationMs).takeIf { it > 0f },
                                isFavorite = favoriteIds.contains(movie.id),
                                providerName = providerNames[movie.sourceId],
                                modifier = Modifier.gridFocusTarget(
                                    itemId = movie.id, index = index,
                                    contextId = contextMovieId, contextFocus = contextFocus,
                                    selectedId = selectedMovie?.id, selectedFocus = selFocus,
                                    firstItemFocus = firstItemFocus,
                                ),
                                onFocus = { vm.onMovieFocused(movie) },
                                onClick = { startMovie(movie) },
                                onLongClick = { contextMovie = movie; contextMovieId = movie.id; contextMovieIndex = index },
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
                MovieDetailsPane(
                    movie = selectedMovie,
                    meta = selectedMovieMeta?.takeIf { it.movieId == selectedMovie?.id }?.cache,
                    tmdbWins = metadataMode.tmdbWins,
                    resumePositionMs = selectedProgress?.takeIf { !vm.isMovieCompleted(it) }?.positionMs?.takeIf { it > 0 },
                    downloadStrip = selectedMovie?.let { m -> downloadStates[m.id]?.let { tv.own.owntv.ui.components.downloadStripFor(listOf(it)) } },
                )
            }
        }
    }
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

    // Long-press a movie → context menu.
    contextMovie?.let { m ->
        val alreadyDownloaded = downloadStates[m.id] != null
        // TMDB Details is shown only when enrichment is on AND a confident match resolved for THIS movie.
        val cacheForM = selectedMovieMeta?.takeIf { it.movieId == m.id }?.cache
        val watched = selectedProgress?.takeIf { selectedMovie?.id == m.id }?.let { vm.isMovieCompleted(it) } ?: false
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { contextMovie = null }) { MovieContextMenu(
            title = m.name,
            isFavorite = favoriteIds.contains(m.id),
            watched = watched,
            canMove = selectedKey is LiveKey.Folder || selectedKey is LiveKey.Custom || selectedKey == LiveKey.Favorites,
            isHistory = selectedKey == LiveKey.History,
            hasTmdbDetails = metadataMode.enrich && cacheForM != null,
            trailerKey = if (metadataMode.enrich) cacheForM?.trailerKey else null,
            canRefetchTmdb = metadataMode.enrich,
            onShowDetails = { contextMovie = null; detailsMovie = m },
            onToggleFavorite = { vm.toggleFavorite(m); contextMovie = null },
            onToggleWatched = {
                if (watched) vm.markMovieUnwatched(m) else vm.markMovieWatched(m)
                contextMovie = null
            },
            onMove = { contextMovie = null; vm.enterMoveMode(m, selectedKey) },
            onMoveToCategory = {
                moveOriginKey = when (val k = selectedKey) {
                    is LiveKey.Folder -> vm.folderKey(k.id)
                    is LiveKey.Custom -> k.id
                    LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                    else -> null
                }
                moveOriginName = railItems.firstOrNull { it.key == selectedKey }?.title
                moveItem = m
                contextMovie = null
            },
            onHide = { vm.hideMovie(m); contextMovie = null },
            onRemoveFromHistory = { vm.removeFromHistory(m.id); contextMovie = null },
            onDownload = {
                contextMovie = null
                // Idempotent (§11.1): don't re-queue an existing download — nudge to the Downloads menu.
                if (alreadyDownloaded) {
                    toast.show(alreadyDownloadedMessage)
                } else vm.download(m)
            },
            onPlayExternal = { contextMovie = null; vm.playExternal(m) },
            onRefetch = {
                contextMovie = null
                toast.show(refetchingTmdbMessage)
                vm.refetchMovieMeta(m)
            },
            onSetTmdbName = { contextMovie = null; setTmdbNameMovie = m },
            onPlayTrailer = { key -> contextMovie = null; trailerVideoKey = key },
            onDeleteSubtitles = if (contextMovieSubs.isNotEmpty()) ({ showDeleteSubs = true }) else null,
            onDismiss = { contextMovie = null },
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
        TrailerPlayerScreen(videoKey = key, onExit = { trailerVideoKey = null })
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

    InAppToast(toast)
}

@Composable
private fun MovieContextMenu(
    title: String,
    isFavorite: Boolean,
    watched: Boolean,
    canMove: Boolean,
    isHistory: Boolean,
    hasTmdbDetails: Boolean,
    trailerKey: String?,
    canRefetchTmdb: Boolean,
    onShowDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWatched: () -> Unit,
    onMove: () -> Unit,
    // "Move to category…" (issue #87): send this movie into a user's combined category.
    onMoveToCategory: () -> Unit,
    onHide: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onDownload: () -> Unit,
    onPlayExternal: () -> Unit,
    onRefetch: () -> Unit,
    onSetTmdbName: () -> Unit,
    onPlayTrailer: (String) -> Unit,
    // Non-null only when this movie has downloaded OpenSubtitles subtitles (subtitle plan §11).
    onDeleteSubtitles: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
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
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            // The menu as data: same actions, same gating, same order as the buttons that used to be
            // written out here one by one. Close is not in the list — it stays pinned last.
            val actions = buildList {
                add(MenuAction("favourite", if (isFavorite) stringResource(R.string.content_remove_favourite) else stringResource(R.string.content_add_favourite), OwnTVIcon.FAVORITE, onClick = onToggleFavorite))
                add(MenuAction("mark_watched", if (watched) stringResource(R.string.content_mark_unwatched) else stringResource(R.string.content_mark_watched), onClick = onToggleWatched))
                if (canMove) add(MenuAction("move", stringResource(R.string.content_move), onClick = onMove))
                if (canMove) add(MenuAction("move_to_category", stringResource(R.string.content_move_to_category), onClick = onMoveToCategory))
                if (isHistory) add(MenuAction("remove_history", stringResource(R.string.content_remove_history), onClick = onRemoveFromHistory))
                add(MenuAction("hide", stringResource(R.string.common_hide), onClick = onHide))
                add(MenuAction("download", stringResource(R.string.content_download), OwnTVIcon.DOWNLOADS, onClick = onDownload))
                // Delete subtitles — only when this movie has downloaded OpenSubtitles subs (§11).
                onDeleteSubtitles?.let { add(MenuAction("delete_subtitles", stringResource(R.string.content_delete_subtitles), OwnTVIcon.SUBTITLE, onClick = it)) }
                // Phase B: one-off external playback, independent of the global "External player" toggle.
                add(MenuAction("play_external", stringResource(R.string.content_play_external), OwnTVIcon.PLAY, onClick = onPlayExternal))
                // TMDB Details — only when a confident match resolved (§11.1).
                if (hasTmdbDetails) add(MenuAction("tmdb_details", stringResource(R.string.content_tmdb_details), OwnTVIcon.MENU, group = 1, onClick = onShowDetails))
                // Play Trailer (§7.3 U4) — only when TMDB actually has a trailer for this title (§11.1 gating).
                trailerKey?.let { key -> add(MenuAction("play_trailer", stringResource(R.string.content_play_trailer), group = 1) { onPlayTrailer(key) }) }
                // Refetch TMDB details (§11.2 U5a) — always available when enrichment is on, so a "no match"
                // (7-day negative cache) or a stale match can be cleared and re-searched immediately.
                if (canRefetchTmdb) {
                    add(MenuAction("refetch_tmdb", stringResource(R.string.content_refetch_tmdb), group = 1, onClick = onRefetch))
                    // Set TMDB name (§11.2 U5b) — hand-type the exact title to override the auto-match.
                    add(MenuAction("set_tmdb_name", stringResource(R.string.content_set_tmdb_name), group = 1, onClick = onSetTmdbName))
                }
            }
            var previousGroup: Int? = null
            arranged(ContentMenu.MOVIE, actions).forEachIndexed { index, action ->
                if (previousGroup != null && action.group != previousGroup) Spacer(Modifier.height(4.dp))
                previousGroup = action.group
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
private fun MovieDetailsPane(
    movie: MovieEntity?,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
    resumePositionMs: Long? = null,
    downloadStrip: tv.own.owntv.ui.components.DownloadStripState? = null,
) {
    val colors = OwnTVTheme.colors
    if (movie == null) {
        PreviewPane(hint = stringResource(R.string.content_focus_movie))
        return
    }
    // Merge (§7.1 / §4.1). Provider+TMDB → provider wins (provider ?: tmdb); TMDB-only → tmdb wins
    // (tmdb ?: provider). TMDB fields are never written back to the content row.
    val providerPoster = movie.posterUrl?.takeIf { it.isNotBlank() }
    val tmdbPoster = tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
    val posterArt = (if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster)
        ?: movie.backdropUrl?.takeIf { it.isNotBlank() }
        ?: tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath)
    val providerPlot = movie.plot?.takeIf { it.isNotBlank() }
    val plot = if (tmdbWins) meta?.overview ?: providerPlot else providerPlot ?: meta?.overview
    // Outer details Box carries the rounded panel (Phase 6); no clip/background here.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.GapLarge),
    ) {
        // Non-focusable download status strip — only present while this movie is actually downloading.
        if (downloadStrip != null) {
            tv.own.owntv.ui.components.DownloadStatusStrip(downloadStrip)
            Spacer(Modifier.height(12.dp))
        }
        // Tall portrait poster (like the list / a phone screen), centred in the pane.
        Box(modifier = Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!posterArt.isNullOrBlank()) {
                    AsyncImage(
                        model = posterArt,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    OwnTVIcon(OwnTVIcon.MOVIES, tint = colors.onSurfaceVariant, modifier = Modifier.height(48.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // Always-visible, non-focusable resume label (kept above the title, not further down in the
        // pane, since movie metadata below can push a lower placement out of view once it scrolls long).
        if (resumePositionMs != null) {
            Text(
                stringResource(R.string.content_resume_at, tv.own.owntv.ui.components.formatTimestamp(resumePositionMs)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(movie.name, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(metaLine(movie, meta, tmdbWins), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        // Genres & cast are TMDB-only (§7.1) — a whole layer the provider never had.
        val genres = jsonList(meta?.genresJson)
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(genres.joinToString(stringResource(R.string.content_genres_separator)), style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
        if (!plot.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(plot, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        val cast = tv.own.owntv.core.metadata.MetadataCast.names(meta?.castJson)
        if (cast.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.content_media_cast), style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(cast.take(6).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(20.dp))
        // Display-only pane (§11.1): actions live on the poster — OK plays, long-press opens the menu
        // (Favorite / Download / TMDB Details). Keeping the pane non-focusable fixes grid→pane navigation.
        Text(
            stringResource(R.string.content_ok_play_options),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
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

/** Compact one-line row used by the List view mode — fits many titles on screen at once (#10). */
@Composable
private fun MovieListRow(
    movie: MovieEntity,
    isFavorite: Boolean,
    completed: Boolean = false,
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
                if (!movie.posterUrl.isNullOrBlank()) {
                    AsyncImage(model = movie.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.MOVIES, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    movie.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        focused -> colors.primary
                        completed -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                val meta = metaLine(movie)
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (completed) {
                Box(
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(50)).background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.onPrimary)
                }
            }
            if (isFavorite) {
                OwnTVIcon(OwnTVIcon.FAVORITE, tint = colors.favorite, filled = true, modifier = Modifier.size(18.dp))
            }
            providerName?.let { ProviderChip(name = it) }
        }
    }
}
