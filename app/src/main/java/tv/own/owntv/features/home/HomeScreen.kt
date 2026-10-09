package tv.own.owntv.features.home

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.dao.TrendingDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.home.HeroItem
import tv.own.owntv.core.home.TrendingHomeItem
import tv.own.owntv.core.launcher.LauncherContinuationItem
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.model.HomeTrendingStyle
import tv.own.owntv.core.trending.ProviderVariantParser
import tv.own.owntv.features.shell.components.MediaDetailsScreen
import tv.own.owntv.features.shell.components.MediaDetailsUi
import tv.own.owntv.player.HeroPreviewEngine
import tv.own.owntv.ui.components.BrandLockup
import tv.own.owntv.ui.components.InAppToast
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.TrailerPlayerScreen
import tv.own.owntv.ui.components.rememberInAppToast
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.stage.StageStill
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.dissolveEdges
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.util.Locale

/*
 * Home, Stage (P2-01 hero, P2-02 rows, P2-03 pager). With Trending drawn full-bleed, the top of the screen
 * is the trending title over its dissolving backdrop, and Keep watching sits under it in large stills.
 * Once focus goes down into the rows, the hero folds into one line (eyebrow + title) and the rows move up.
 * Both states are one list, so the focused card is the same composable before and after the fold.
 */

/** The row gap; the headings' mockup positions below already include it. */
private val RowGap = 40.mpx

/** How far the full hero's text sits above the mockup's (`top: 170px`), and the rows with it: the
 *  top-left corner is empty, and the rows below are what the screen is short of. */
private val HeroLift = 60.mpx

/** Screen-top to the Keep watching heading under the full hero (`top: 744px`, less [HeroLift]), less the row gap. */
private val HeroHeight = 744.mpx - HeroLift - RowGap

/** Screen-top to the first row heading once the hero is folded (`top: 228px`), less the row gap. */
private val CompactHeroHeight = 228.mpx - RowGap

/** Where the rows start when there is no hero: just below the top-right cluster. */
private val NoHeroTop = 110.mpx

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onPlayMovie: (movieId: Long, positionMs: Long) -> Unit,
    onPlayEpisode: (seriesId: Long, episodeId: Long, positionMs: Long) -> Unit,
    onPlayChannel: (channelId: Long, zapChannels: List<ChannelEntity>) -> Unit,
    onActivateTrending: (TrendingHomeItem, onUnavailable: () -> Unit) -> Unit,
    onOpenMovie: (MovieEntity) -> Unit,
    onOpenSeries: (SeriesEntity) -> Unit,
    onOpenTrendingSearch: (String) -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    restoreTrendingSearchFocus: Boolean = false,
    onRestored: () -> Unit = {},
    previewEnabled: Boolean = true,
    firstRowFocusRequester: FocusRequester? = null,
    onContentScrolled: (Boolean) -> Unit = {},
    /** The content's left edge: beside the resting capsule (150), or 64 inside a docked rail's reserve. */
    contentStart: Dp = 150.mpx,
    /**
     * Hands the shell Home's own way in, for ▶ out of the rail: the control that last had focus, or Play.
     * (A plain request on the content area drills to the nearest card instead.) Null on leaving.
     */
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val trendingUnavailableMessage = stringResource(R.string.home_trending_unavailable)
    val heroPreviewEngine = koinInject<HeroPreviewEngine>()
    val engineState by heroPreviewEngine.state.collectAsStateWithLifecycle()
    val isPreviewActive by vm.isPreviewActive.collectAsStateWithLifecycle()
    val lastInteractionMs by vm.lastHeroInteractionMs.collectAsStateWithLifecycle()
    val favoriteMovies by vm.favoriteMovieIds.collectAsStateWithLifecycle()
    val favoriteSeries by vm.favoriteSeriesIds.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val chromeScrollThresholdPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val contentScrolled by remember(listState, chromeScrollThresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > chromeScrollThresholdPx
        }
    }
    LaunchedEffect(contentScrolled) { onContentScrolled(contentScrolled) }
    val homeScope = rememberCoroutineScope()
    val heroFocus = remember { FocusRequester() }
    val fallbackFocus = remember { FocusRequester() }
    val trendingPrimaryFocus = remember { FocusRequester() }
    val trendingTrailerFocus = remember { FocusRequester() }
    val trendingDetailsFocus = remember { FocusRequester() }
    val trendingVersionsFocus = remember { FocusRequester() }
    val trendingToast = rememberInAppToast()
    var trailerVideoKey by remember { mutableStateOf<String?>(null) }
    var trailerTitle by remember { mutableStateOf<String?>(null) }
    var detailsItem by remember { mutableStateOf<TrendingHomeItem?>(null) }
    var detailsMetadata by remember { mutableStateOf<MetadataCacheEntity?>(null) }
    var detailsTmdbWins by remember { mutableStateOf(false) }
    // Focus is on the hero (its buttons or pager): the list must not scroll, and the hero stays unfolded.
    val heroFocused = remember { mutableStateOf(false) }
    // Focus has gone down into the rows: the hero folds into its one-line form (P2-02).
    var inRows by remember { mutableStateOf(false) }
    val rowFirstFocusRequesters = remember { HomeRow.entries.associateWith { FocusRequester() } }
    // A Keep watching card plays its preview only after 3 s of continuous focus (the dwell below).
    var previewIndex by remember { mutableStateOf(-1) }
    var focusedHeroIndex by remember { mutableStateOf(-1) }
    val orderedRows = state.config.visibleOrder
    val heroVisible = HomeRow.HERO in orderedRows
    val hasNonHeroContent = orderedRows.any { it != HomeRow.HERO && rowHasData(it, state) }
    val showHeroFallback = heroVisible && state.heroItems.isEmpty() && !hasNonHeroContent
    val renderRows = orderedRows.filter { rowCanRender(it, state, showHeroFallback) }
    val firstDataRow = renderRows.firstOrNull { it != HomeRow.HERO && rowHasData(it, state) }
    val showAllHiddenState = orderedRows.isEmpty()
    val showEmptyState = orderedRows.isNotEmpty() && renderRows.isEmpty()
    val fullBleed = renderRows.firstOrNull() == HomeRow.TRENDING &&
        state.config.trendingStyle == HomeTrendingStyle.HERO
    // Trending layout = Posters only (P2-04): the folded line on top follows the focused poster.
    val posterHero = renderRows.firstOrNull() == HomeRow.TRENDING &&
        state.config.trendingStyle == HomeTrendingStyle.POSTERS
    var posterIndex by remember { mutableIntStateOf(0) }
    val rowFocusRequester: (HomeRow) -> FocusRequester? = { row ->
        if (row == renderRows.firstOrNull() && firstRowFocusRequester != null) {
            firstRowFocusRequester
        } else when (row) {
            HomeRow.TRENDING -> trendingPrimaryFocus
            HomeRow.HERO -> when {
                state.heroItems.isNotEmpty() -> heroFocus
                showHeroFallback -> fallbackFocus
                else -> null
            }
            else -> rowFirstFocusRequesters[row]
        }
    }
    val playFocus = rowFocusRequester(HomeRow.TRENDING) ?: trendingPrimaryFocus
    // The control that last had focus on Home, so ▶ out of the rail comes back to exactly it (P1
    // carry-over). Compose's own restorer cannot follow a card inside a lazy row inside a lazy column.
    var lastFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val track: (FocusRequester) -> Unit = { lastFocus = it }
    val latestEntry by androidx.compose.runtime.rememberUpdatedState(
        lastFocus ?: if (fullBleed) playFocus else renderRows.firstOrNull()?.let(rowFocusRequester),
    )
    DisposableEffect(onEntryHook) {
        onEntryHook?.invoke { latestEntry?.let { runCatching { it.requestFocus() }.isSuccess } ?: false }
        onDispose { onEntryHook?.invoke(null) }
    }

    val onNonHeroFocused = remember(vm, heroPreviewEngine) {
        {
            vm.setHeroFocused(false)
            heroPreviewEngine.stop()
            previewIndex = -1
            inRows = true
            onChildFocused()
        }
    }

    LaunchedEffect(focusedHeroIndex) {
        if (focusedHeroIndex != -1) return@LaunchedEffect
        // Focus moves between cards very quickly (old loses focus before new gains). Debounce the
        // "left the row" signal so the preview state does not flap while navigating within the row.
        kotlinx.coroutines.delay(40L)
        if (focusedHeroIndex != -1) return@LaunchedEffect
        vm.setHeroFocused(false)
        heroPreviewEngine.stop()
        previewIndex = -1
    }

    // Dwell-to-preview: only after 3 s of uninterrupted focus, so quick D-pad sweeps never start a video.
    LaunchedEffect(focusedHeroIndex) {
        val index = focusedHeroIndex
        if (index < 0) return@LaunchedEffect
        kotlinx.coroutines.delay(3_000L)
        if (focusedHeroIndex == index) previewIndex = index
    }

    LaunchedEffect(previewEnabled) {
        vm.setPreviewEnabled(previewEnabled)
        if (!previewEnabled) vm.stopPreview()
    }

    // The engine is an app-scoped singleton; make sure the preview can't outlive the Home screen.
    DisposableEffect(heroPreviewEngine) {
        onDispose { heroPreviewEngine.stop() }
    }

    // The fold: the list starts again at the top, so the rows sit right under the one-line hero.
    LaunchedEffect(inRows) {
        if (fullBleed) runCatching { listState.scrollToItem(0) }
    }

    LaunchedEffect(orderedRows, state.trendingItems, state.heroItems, state.recentLive, state.favoriteLive, state.recentGuide, state.favoriteGuide, state.continueMovies, state.continueSeries, state.favoriteMovies, state.favoriteSeries, restoreFocus, restoreTrendingSearchFocus) {
        if (orderedRows.isEmpty()) {
            if (restoreFocus) onRestored()
            return@LaunchedEffect
        }

        val targetRow = when {
            restoreTrendingSearchFocus && state.trendingItems.isNotEmpty() -> HomeRow.TRENDING
            restoreFocus && heroVisible && state.heroItems.isNotEmpty() -> HomeRow.HERO
            restoreFocus && showHeroFallback -> HomeRow.HERO
            restoreFocus -> firstDataRow
            else -> null
        }
        val targetIndex = targetRow?.let { renderRows.indexOf(it) } ?: 0
        runCatching { listState.scrollToItem(targetIndex.coerceAtLeast(0)) }

        // Only pull focus INTO the Home content when returning from the player (restoreFocus). On a cold
        // start or a tab switch, leave focus on the rail's Home item so the nav is immediately navigable.
        if (restoreFocus || restoreTrendingSearchFocus) {
            kotlinx.coroutines.delay(60)
            val focusTarget = when {
                restoreTrendingSearchFocus && state.trendingItems.isNotEmpty() -> trendingVersionsFocus
                heroVisible && state.heroItems.isNotEmpty() -> rowFocusRequester(HomeRow.HERO)
                showHeroFallback -> rowFocusRequester(HomeRow.HERO)
                firstDataRow != null -> rowFocusRequester(firstDataRow)
                else -> null
            }
            if (focusTarget != null) runCatching { focusTarget.requestFocus() }
            onRestored()
        }
    }

    // While the first load runs, nothing but the Stage background behind: not the empty state (which
    // would look wrong, and vanish, for a user who does have history), and no placeholder blocks.
    if (state.isLoading) return
    if (showAllHiddenState) {
        AllRowsHiddenState(modifier = modifier.fillMaxSize())
        return
    }
    if (showEmptyState) {
        EmptyHomeState(modifier = modifier.fillMaxSize())
        return
    }

    val hero = state.heroItems.getOrNull(state.activeHeroIndex)
    // Scroll only as far as the focused row needs to be fully on screen (P2-02: focus on Favourite
    // channels leaves Keep watching and the folded title where they are). The TV default instead pins
    // the focused item a third of the way down, which slid the whole page up on every ▼. The hero
    // itself never scrolls.
    val homeBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                if (heroFocused.value) return 0f
                val trailing = offset + size - containerSize
                return when {
                    offset >= 0f && trailing <= 0f -> 0f
                    offset < 0f || size > containerSize -> offset
                    else -> trailing
                }
            }
        }
    }
    val trendingItem = state.trendingItems.getOrNull(state.activeTrendingIndex)
        ?.takeIf { fullBleed && state.trendingItems.size >= TrendingDao.MIN_ELIGIBLE_ITEMS }
    val posterItem = state.trendingItems.getOrNull(posterIndex)
        ?.takeIf { posterHero && state.trendingItems.size >= TrendingDao.MIN_ELIGIBLE_ITEMS }
    // ▲ from the row right under a folded hero unfolds it and lands on Play — there is nothing focusable
    // in the one-line form for focus search to find.
    val firstRowUnderHero = renderRows.getOrNull(1).takeIf { fullBleed }
    val unfoldOnUp = Modifier.onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
            inRows = false
            homeScope.launch {
                listState.scrollToItem(0)
                kotlinx.coroutines.delay(30)
                runCatching { playFocus.requestFocus() }
            }
            true
        } else false
    }

    // The Stage page itself is painted by the shell, full width, so it also runs under a docked rail.
    Box(modifier.fillMaxSize()) {
        trendingItem?.let { TrendingBackdrop(it, folded = inRows, modifier = Modifier.align(Alignment.TopEnd)) }
        posterItem?.let { TrendingBackdrop(it, folded = true, modifier = Modifier.align(Alignment.TopEnd)) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides homeBringIntoViewSpec) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { if (it.hasFocus) onChildFocused() }
                    // ▶ out of the rail comes back to the card it left from (P1 carry-over): every level
                    // of the list remembers its focused child.
                    .focusGroup(),
                state = listState,
                contentPadding = PaddingValues(top = if (fullBleed || posterItem != null) 0.dp else NoHeroTop, bottom = 48.mpx),
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                itemsIndexed(renderRows, key = { _, row -> row.name }) { _, row ->
                    val firstItemFocusRequester = rowFocusRequester(row)
                    val rowModifier = if (row == firstRowUnderHero) unfoldOnUp else Modifier
                    when (row) {
                        HomeRow.TRENDING -> if (state.trendingItems.size < TrendingDao.MIN_ELIGIBLE_ITEMS) {
                            // Too few titles to be a "trending" row at all, so the row is not drawn.
                        } else if (!fullBleed || trendingItem == null) {
                            posterItem?.let { FoldedHeroLine(it, contentStart, height = CompactHeroHeight + RowGap) }
                            TrendingPosterRow(
                                // Room for the focused poster's lift under the heading when the line sits above.
                                headerGap = if (posterItem != null) 16.mpx else 0.dp,
                                // Compose's one-line poster title is 10 px shorter than Chrome's (P2-04, measured).
                                bottomGap = if (posterItem != null) 10.mpx else 0.dp,
                                onItemFocused = { posterIndex = it },
                                title = row.displayTitle(),
                                items = state.trendingItems,
                                start = contentStart,
                                onItemClick = { item ->
                                    onActivateTrending(item) {
                                        trendingToast.show(trendingUnavailableMessage)
                                        vm.refresh()
                                    }
                                },
                                onFocus = onNonHeroFocused,
                                firstItemFocusRequester = firstItemFocusRequester ?: trendingPrimaryFocus,
                                track = track,
                            )
                        } else {
                            val favourite = when (trendingItem) {
                                is TrendingHomeItem.Movie -> trendingItem.movie.id in favoriteMovies
                                is TrendingHomeItem.Series -> trendingItem.series.id in favoriteSeries
                            }
                            TrendingHero(
                                item = trendingItem,
                                items = state.trendingItems,
                                activeIndex = state.activeTrendingIndex,
                                extras = state.trendingExtras[trendingItem.stableKey] ?: TrendingExtras(),
                                seasonCount = (trendingItem as? TrendingHomeItem.Series)?.let { state.trendingSeasonCounts[it.series.id] },
                                favourite = favourite,
                                folded = inRows,
                                start = contentStart,
                                primaryFocusRequester = playFocus,
                                trailerFocusRequester = trendingTrailerFocus,
                                detailsFocusRequester = trendingDetailsFocus,
                                versionsFocusRequester = trendingVersionsFocus,
                                onNavigate = vm::navigateTrending,
                                onActivate = { item ->
                                    onActivateTrending(item) {
                                        trendingToast.show(trendingUnavailableMessage)
                                        vm.refresh()
                                    }
                                },
                                onTrailer = { item ->
                                    vm.stopPreview()
                                    trailerTitle = item.snapshot.localizedTitle
                                    trailerVideoKey = item.snapshot.trailerKey
                                },
                                onDetails = { item ->
                                    vm.stopPreview()
                                    homeScope.launch {
                                        val current = vm.revalidateTrendingItem(item) ?: return@launch
                                        val resolved = vm.resolveTrendingDetails(current)
                                        detailsMetadata = resolved.cache
                                        detailsTmdbWins = resolved.tmdbWins
                                        detailsItem = current
                                    }
                                },
                                onAllVersions = { item ->
                                    vm.stopPreview()
                                    onOpenTrendingSearch(item.snapshot.canonicalTitle)
                                },
                                onToggleFavourite = vm::toggleTrendingFavorite,
                                track = track,
                                onFocusChanged = { focused ->
                                    heroFocused.value = focused
                                    if (focused) {
                                        inRows = false
                                        vm.setHeroFocused(false)
                                        heroPreviewEngine.stop()
                                        previewIndex = -1
                                        onChildFocused()
                                    }
                                },
                            )
                        }

                        HomeRow.HERO -> if (state.heroItems.isNotEmpty()) {
                            KeepWatchingRow(
                                title = row.displayTitle(),
                                items = state.heroItems,
                                big = fullBleed && !inRows,
                                start = contentStart,
                                metadata = state.heroMetadata,
                                previewIndex = previewIndex,
                                engine = heroPreviewEngine,
                                engineState = engineState,
                                firstFocusRequester = firstItemFocusRequester ?: heroFocus,
                                onCardFocusChanged = { index, hasFocus ->
                                    if (hasFocus) {
                                        if (previewIndex != index) {
                                            heroPreviewEngine.stop() // stop the previous card's video first
                                            previewIndex = -1 // the dwell timer starts it again after 3 s
                                        }
                                        inRows = true
                                        focusedHeroIndex = index
                                        vm.onHeroUserNavigate(index)
                                        vm.setHeroFocused(true)
                                        onChildFocused()
                                    } else if (focusedHeroIndex == index) {
                                        focusedHeroIndex = -1
                                    }
                                },
                                onPlay = { item ->
                                    when (item) {
                                        is HeroItem.MovieHero -> onPlayMovie(item.movie.id, item.positionMs)
                                        is HeroItem.SeriesHero -> onPlayEpisode(item.series.id, item.episode.id, item.positionMs)
                                        is HeroItem.LiveHero -> onPlayChannel(item.channel.id, state.recentLive)
                                    }
                                },
                                track = track,
                                modifier = rowModifier,
                            )
                        } else {
                            HeroFallbackPane(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = contentStart).then(rowModifier),
                                focusRequester = firstItemFocusRequester ?: fallbackFocus,
                                onChildFocused = onNonHeroFocused,
                            )
                        }

                        HomeRow.RECENT_CHANNELS -> HomeLiveRow(
                            title = row.displayTitle(),
                            mode = state.config.recentLiveMode,
                            channels = state.recentLive,
                            guide = state.recentGuide,
                            onChannelClick = onPlayChannel,
                            onFocus = onNonHeroFocused,
                            firstItemFocusRequester = firstItemFocusRequester,
                            start = contentStart,
                            track = track,
                            modifier = rowModifier,
                        )

                        HomeRow.FAVORITE_CHANNELS -> HomeLiveRow(
                            title = row.displayTitle(),
                            mode = state.config.favoriteLiveMode,
                            channels = state.favoriteLive,
                            guide = state.favoriteGuide,
                            onChannelClick = onPlayChannel,
                            onFocus = onNonHeroFocused,
                            firstItemFocusRequester = firstItemFocusRequester,
                            start = contentStart,
                            track = track,
                            modifier = rowModifier,
                        )

                        HomeRow.FAVORITE_MOVIES -> FavoritePosterRow(
                            title = row.displayTitle(),
                            items = state.favoriteMovies,
                            key = { it.id },
                            name = { it.name },
                            poster = { it.posterUrl },
                            start = contentStart,
                            onItemClick = onOpenMovie,
                            onFocus = onNonHeroFocused,
                            firstItemFocusRequester = firstItemFocusRequester,
                            track = track,
                            modifier = rowModifier,
                        )

                        HomeRow.FAVORITE_SERIES -> FavoritePosterRow(
                            title = row.displayTitle(),
                            items = state.favoriteSeries,
                            key = { it.id },
                            name = { it.name },
                            poster = { it.posterUrl },
                            start = contentStart,
                            onItemClick = onOpenSeries,
                            onFocus = onNonHeroFocused,
                            firstItemFocusRequester = firstItemFocusRequester,
                            track = track,
                            modifier = rowModifier,
                        )

                        HomeRow.CONTINUE_MOVIES -> ContinuePosterRow(
                            title = row.displayTitle(),
                            items = state.continueMovies,
                            start = contentStart,
                            onItemClick = { onPlayMovie(it.sourceItemId, it.positionMs) },
                            onFocus = onNonHeroFocused,
                            firstItemFocusRequester = firstItemFocusRequester,
                            track = track,
                            modifier = rowModifier,
                        )

                        HomeRow.CONTINUE_SERIES -> ContinuePosterRow(
                            title = row.displayTitle(),
                            items = state.continueSeries,
                            start = contentStart,
                            onItemClick = { onPlayEpisode(0L, it.targetItemId, it.positionMs) },
                            onFocus = onNonHeroFocused,
                            firstItemFocusRequester = firstItemFocusRequester,
                            track = track,
                            modifier = rowModifier,
                        )
                    }
                }
            }
        }
    }

    // The preview starts after the card has settled, so decoder setup does not compete with the focus
    // lift. A trailer counts as "not previewing": leaving the preview decoding behind the trailer window
    // costs a second video pipeline for a picture nobody can see.
    LaunchedEffect(isPreviewActive, hero, previewIndex, focusedHeroIndex, lastInteractionMs, trailerVideoKey) {
        if (!isPreviewActive || trailerVideoKey != null || hero == null || previewIndex < 0 ||
            focusedHeroIndex != previewIndex
        ) {
            heroPreviewEngine.stop()
            return@LaunchedEffect
        }

        val scheduledIndex = previewIndex
        val scheduledHero = hero
        val interactionStamp = lastInteractionMs

        heroPreviewEngine.stop()
        kotlinx.coroutines.delay(520L)
        if (!isPreviewActive || interactionStamp != lastInteractionMs) return@LaunchedEffect
        if (focusedHeroIndex != scheduledIndex || previewIndex != scheduledIndex) return@LaunchedEffect
        if (scheduledHero != state.heroItems.getOrNull(state.activeHeroIndex)) return@LaunchedEffect

        vm.startPreview(scheduledHero)
    }

    trailerVideoKey?.let { key ->
        TrailerPlayerScreen(videoKey = key, title = trailerTitle) {
            trailerVideoKey = null
            homeScope.launch {
                kotlinx.coroutines.delay(60)
                runCatching { trendingTrailerFocus.requestFocus() }
            }
        }
    }
    detailsItem?.let { item ->
        MediaDetailsScreen(details = item.toDetailsUi(detailsMetadata, detailsTmdbWins), onExit = {
            detailsItem = null
            detailsMetadata = null
            homeScope.launch {
                kotlinx.coroutines.delay(60)
                runCatching { trendingDetailsFocus.requestFocus() }
            }
        })
    }
    InAppToast(trendingToast)
}

private fun rowHasData(row: HomeRow, state: HomeUiState): Boolean = when (row) {
    HomeRow.TRENDING -> state.trendingItems.size >= TrendingDao.MIN_ELIGIBLE_ITEMS
    HomeRow.HERO -> state.heroItems.isNotEmpty()
    HomeRow.RECENT_CHANNELS -> when (state.config.recentLiveMode) {
        HomeLiveRowMode.CARDS -> state.recentLive.isNotEmpty()
        HomeLiveRowMode.ON_NOW -> state.recentGuide.hasContent
    }
    HomeRow.FAVORITE_CHANNELS -> when (state.config.favoriteLiveMode) {
        HomeLiveRowMode.CARDS -> state.favoriteLive.isNotEmpty()
        HomeLiveRowMode.ON_NOW -> state.favoriteGuide.hasContent
    }
    HomeRow.FAVORITE_MOVIES -> state.favoriteMovies.isNotEmpty()
    HomeRow.FAVORITE_SERIES -> state.favoriteSeries.isNotEmpty()
    HomeRow.CONTINUE_MOVIES -> state.continueMovies.isNotEmpty()
    HomeRow.CONTINUE_SERIES -> state.continueSeries.isNotEmpty()
}

private fun rowCanRender(row: HomeRow, state: HomeUiState, showHeroFallback: Boolean): Boolean =
    when (row) {
        HomeRow.HERO -> state.heroItems.isNotEmpty() || showHeroFallback
        else -> rowHasData(row, state)
    }

private fun HeroItem.heroKey(): String = when (this) {
    is HeroItem.MovieHero -> "movie:${movie.id}"
    is HeroItem.SeriesHero -> "episode:${episode.id}"
    is HeroItem.LiveHero -> "live:${channel.id}"
}

private fun trendingBackdrop(item: TrendingHomeItem): String? =
    MetadataImages.backdrop(item.snapshot.backdropPath, size = "w1280")
        ?: when (item) {
            is TrendingHomeItem.Movie -> item.movie.backdropUrl ?: item.movie.posterUrl
            is TrendingHomeItem.Series -> item.series.backdropUrl ?: item.series.posterUrl
        }

/** A rating as the mockup writes it: one decimal, in the user's number format ("8.4", "8,4"). */
private fun ratingText(rating: Double): String = String.format(Locale.getDefault(), "%.1f", rating)

/**
 * `.fadeimg` behind the hero: right-aligned, 1500 of the 1920 width at 16:9, dissolving into the page on
 * its left (34%) and bottom (38%), with the 70% → 0 wash across its left 60% so the title always reads.
 * Folded (P2-02), it is 560 high at 55%, without the wash.
 */
@Composable
private fun TrendingBackdrop(item: TrendingHomeItem, folded: Boolean, modifier: Modifier = Modifier) {
    val url = trendingBackdrop(item) ?: return
    BoxWithConstraints(modifier.fillMaxWidth(1500f / 1920f)) {
        val height = maxWidth * (if (folded) 560f else 844f) / 1500f
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .alpha(if (folded) 0.55f else 1f)
                .dissolveEdges(start = 0.34f, bottom = 0.38f),
        ) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (!folded) {
                val wash = Color(5, 8, 10)
                Box(
                    Modifier.fillMaxSize().gradientWash(
                        false,
                        0f to wash.copy(alpha = 0.70f),
                        0.30f to wash.copy(alpha = 0.35f),
                        0.60f to wash.copy(alpha = 0f),
                        1f to wash.copy(alpha = 0f),
                    ),
                )
            }
        }
    }
}

/** "↗ #3 in Trending · Movies": 18/700 in the accent. */
@Composable
private fun TrendingEyebrow(item: TrendingHomeItem, withTag: Boolean) {
    val accent = stageAccent.accent
    Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
        OwnTVIcon(OwnTVIcon.TREND, tint = accent, modifier = Modifier.size(18.mpx))
        Text(
            stringResource(
                R.string.home_trending_rank,
                item.snapshot.trendingRank,
                stringResource(if (item is TrendingHomeItem.Movie) R.string.common_nav_movies else R.string.common_nav_series),
            ),
            style = stageText(18, 700, 0.02f.em()), color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (withTag) {
            Spacer(Modifier.width(6.mpx))
            StageTag(stringResource(R.string.home_trending_in_playlist))
        }
    }
}

/**
 * Reports this control to Home when it takes focus, as the place ▶ out of the rail returns to. Pass the
 * control's own [requester] when it already has one; otherwise one is made for it.
 */
@Composable
internal fun Modifier.tracked(requester: FocusRequester? = null, track: (FocusRequester) -> Unit): Modifier {
    val own = remember { FocusRequester() }
    return (if (requester == null) focusRequester(own) else this)
        .onFocusChanged { if (it.hasFocus) track(requester ?: own) }
}

private fun Float.em() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)

@Composable
private fun TrendingHero(
    item: TrendingHomeItem,
    items: List<TrendingHomeItem>,
    activeIndex: Int,
    extras: TrendingExtras,
    seasonCount: Int?,
    favourite: Boolean,
    folded: Boolean,
    start: Dp,
    primaryFocusRequester: FocusRequester,
    trailerFocusRequester: FocusRequester,
    detailsFocusRequester: FocusRequester,
    versionsFocusRequester: FocusRequester,
    onNavigate: (Int) -> Unit,
    onActivate: (TrendingHomeItem) -> Unit,
    onTrailer: (TrendingHomeItem) -> Unit,
    onDetails: (TrendingHomeItem) -> Unit,
    onAllVersions: (TrendingHomeItem) -> Unit,
    onToggleFavourite: (TrendingHomeItem) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    track: (FocusRequester) -> Unit,
) {
    var heroHasFocus by remember { mutableStateOf(false) }
    var resetClock by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val intervalMs = 10_000L

    fun navigate(index: Int) {
        onNavigate(index)
        progress = 0f
        resetClock++
    }

    // The automatic advance, every 10 s. It waits while the hero holds focus, so the title never changes
    // under the user's finger — and, folded, nothing of it is on screen to advance.
    LaunchedEffect(activeIndex, heroHasFocus, folded, resetClock, items.size) {
        if (heroHasFocus || folded || items.size < 2) return@LaunchedEffect
        val startProgress = progress.coerceIn(0f, 1f)
        val duration = (intervalMs * (1f - startProgress)).toLong().coerceAtLeast(1L)
        val startedAt = System.nanoTime()
        while (true) {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            progress = (startProgress + (1f - startProgress) * elapsedMs.toFloat() / duration).coerceIn(0f, 1f)
            if (elapsedMs >= duration) break
            kotlinx.coroutines.delay(80L)
        }
        progress = 0f
        onNavigate((activeIndex + 1) % items.size)
    }

    if (folded) {
        FoldedHeroLine(item, start)
        return
    }

    val snapshot = item.snapshot
    val quality = ProviderVariantParser.displaySignals(snapshot.providerRawName).quality.label
    val runtime = when (item) {
        is TrendingHomeItem.Movie -> item.movie.durationSecs?.takeIf { it >= 60 }?.let { durationText(it / 60L) }
        is TrendingHomeItem.Series -> seasonCount?.let { pluralStringResource(R.plurals.home_trending_seasons, it, it) }
    }
    val meta = listOfNotNull(
        snapshot.year?.toString(),
        extras.genres.take(2).joinToString(", ").ifBlank { null },
        runtime,
    )
    var titleLines by remember(snapshot.localizedTitle) { mutableIntStateOf(1) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(HeroHeight)
            .onFocusChanged {
                heroHasFocus = it.hasFocus
                onFocusChanged(it.hasFocus)
            }
            .focusRestorer()
            .focusGroup(),
    ) {
        // Never squeezed by the fixed height: a child that did not fit would be measured to nothing.
        Column(
            Modifier.padding(start = start, top = 170.mpx - HeroLift).width(900.mpx)
                .wrapContentHeight(Alignment.Top, unbounded = true),
        ) {
            TrendingEyebrow(item, withTag = true)
            // 18 in the mockup; Compose's trimmed 112 line box starts its glyphs 10 px lower than
            // Chrome's `line-height: 1` box (measured on the TV against P2-01).
            Spacer(Modifier.height(8.mpx))
            // The title art when TMDB has it, as the films and series hero do; the text title otherwise,
            // and if the art fails to load. 170 high, the room the films hero gives it.
            var logoFailed by remember(extras.logoUrl) { mutableStateOf(false) }
            val showLogo = !extras.logoUrl.isNullOrBlank() && !logoFailed
            if (showLogo) {
                AsyncImage(
                    model = extras.logoUrl,
                    contentDescription = snapshot.localizedTitle,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onState = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
                    modifier = Modifier.fillMaxWidth().height(170.mpx),
                )
            } else {
                Text(
                    snapshot.localizedTitle,
                    style = stageText(112, 800, (-3).mpxSp).copy(
                        lineHeight = 112.mpxSp,
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                    ),
                    color = StageColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { titleLines = it.lineCount },
                )
            }
            Spacer(Modifier.height(18.mpx))
            Row(horizontalArrangement = Arrangement.spacedBy(16.mpx), verticalAlignment = Alignment.CenterVertically) {
                val metaStyle = stageText(21, 600)
                val metaColor = Color(0xFFD3DCD8)
                meta.forEachIndexed { i, part ->
                    if (i > 0) Text("•", style = metaStyle, color = metaColor.copy(alpha = 0.4f))
                    Text(part, style = metaStyle, color = metaColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                snapshot.rating?.let { rating ->
                    if (meta.isNotEmpty()) Text("•", style = metaStyle, color = metaColor.copy(alpha = 0.4f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
                        Text("★", style = metaStyle, color = StageColors.RatingStar)
                        Text(ratingText(rating), style = metaStyle, color = metaColor)
                    }
                }
                quality?.let { StageTag(it) }
            }
            snapshot.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(20.mpx))
                Text(
                    overview,
                    style = stageText(22, 400).copy(lineHeight = (22 * 1.55f).mpxSp),
                    color = Color(0xFFC9D3CF),
                    // Four lines as drawn; a title that needs a second line (112 high) takes three of
                    // them, so the pager still ends above the Keep watching heading.
                    // The title art is 170 high, 58 more than one title line, so it costs two of them.
                    maxLines = if (titleLines > 1) 1 else if (showLogo) 2 else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 760.mpx),
                )
            }
            Spacer(Modifier.height(36.mpx))
            Row(horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
                val isMovie = item is TrendingHomeItem.Movie
                // The labelled buttons get what the two round ones leave, as one group: long French or
                // German labels otherwise took the whole row and Info and ♥ got none (#4). Inside the group
                // each keeps its own width; only when they do not all fit does the last one shorten to "…".
                Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
                    StageButton(
                        text = stringResource(if (isMovie) R.string.home_trending_play else R.string.home_trending_open_episodes),
                        onClick = { onActivate(item) },
                        icon = if (isMovie) OwnTVIcon.PLAY else OwnTVIcon.SERIES,
                        iconFilled = isMovie,
                        modifier = Modifier.focusRequester(primaryFocusRequester).tracked(primaryFocusRequester, track),
                    )
                    if (!snapshot.trailerKey.isNullOrBlank()) {
                        StageButton(
                            text = stringResource(R.string.home_trending_trailer),
                            onClick = { onTrailer(item) },
                            icon = OwnTVIcon.PLAY_CIRCLE,
                            modifier = Modifier.focusRequester(trailerFocusRequester).tracked(trailerFocusRequester, track),
                        )
                    }
                    StageButton(
                        text = stringResource(R.string.home_trending_all_versions),
                        onClick = { onAllVersions(item) },
                        icon = OwnTVIcon.LAYERS,
                        trailing = extras.versions.takeIf { it > 1 }?.toString(),
                        modifier = Modifier.focusRequester(versionsFocusRequester).tracked(versionsFocusRequester, track),
                    )
                }
                StageButton(
                    text = null,
                    onClick = { onDetails(item) },
                    icon = OwnTVIcon.INFO,
                    round = true,
                    modifier = Modifier.focusRequester(detailsFocusRequester).tracked(detailsFocusRequester, track),
                )
                StageButton(
                    text = null,
                    onClick = { onToggleFavourite(item) },
                    icon = OwnTVIcon.FAVORITE,
                    iconFilled = favourite,
                    round = true,
                    modifier = Modifier.tracked(track = track),
                )
            }
            Spacer(Modifier.height(32.mpx))
            TrendingPager(
                count = items.size,
                activeIndex = activeIndex,
                progress = progress,
                onNavigate = ::navigate,
                modifier = Modifier.tracked(track = track),
            )
        }
    }
}

/** P2-02 / P2-04: the hero folded to its eyebrow and a 60/800 title, nothing focusable. */
@Composable
private fun FoldedHeroLine(item: TrendingHomeItem, start: Dp, height: Dp = CompactHeroHeight) {
    Box(Modifier.fillMaxWidth().height(height).padding(start = start, top = 64.mpx)) {
        Column {
            TrendingEyebrow(item, withTag = false)
            // 8 in the mockup; Compose's 60 px line box starts its glyphs 13 px higher than
            // Chrome's (measured on the TV against P2-02).
            Spacer(Modifier.height(21.mpx))
            Text(
                item.snapshot.localizedTitle,
                style = stageText(60, 800, (-1.5).mpxSp), color = StageColors.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 1100.mpx),
            )
        }
    }
}

/**
 * P2-03: the segmented progress under the hero buttons, one segment per trending title. The current one is
 * 70 wide and fills over the 10 s; the ones before it are full. ▼ from the buttons focuses it (the FX
 * look, current segment in the focus colour) and ◀ ▶ then change the title directly — see
 * [trendingPagerTarget].
 */
@Composable
private fun TrendingPager(count: Int, activeIndex: Int, progress: Float, onNavigate: (Int) -> Unit, modifier: Modifier = Modifier) {
    val a = stageAccent
    StageSurface(
        onClick = {},
        radius = 14.mpx,
        focusStyle = StageFocus.FX,
        modifier = modifier
            .offset(x = (-14).mpx)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val delta = when (e.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                trendingPagerTarget(activeIndex, count, delta)?.let { onNavigate(it); true } ?: false
            },
    ) { focused ->
        Row(Modifier.padding(horizontal = 14.mpx, vertical = 12.mpx), horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
            repeat(count) { i ->
                val fill = when {
                    i < activeIndex -> 1f
                    i == activeIndex -> progress
                    else -> 0f
                }
                Box(
                    Modifier
                        .size(if (i == activeIndex) 70.mpx else 34.mpx, 5.mpx)
                        .clip(RoundedCornerShape(3.mpx))
                        .background(Color.White.copy(alpha = if (focused) 0.28f else 0.18f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(fill).height(5.mpx)
                            .background(if (focused && i == activeIndex) a.focus else a.accent),
                    )
                }
            }
        }
    }
}

/**
 * Keep watching: 16:9 stills (G12), 384 × 216 under the full hero, 336 × 189 once folded. After 3 s on a
 * card its preview plays inside the still (the card does not widen, nothing is drawn over it).
 */
@Composable
private fun KeepWatchingRow(
    title: String,
    items: List<HeroItem>,
    big: Boolean,
    start: Dp,
    metadata: Map<String, HomeHeroMetadata>,
    previewIndex: Int,
    engine: HeroPreviewEngine,
    engineState: HeroPreviewEngine.State,
    firstFocusRequester: FocusRequester,
    onCardFocusChanged: (index: Int, hasFocus: Boolean) -> Unit,
    onPlay: (HeroItem) -> Unit,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    val w = if (big) 384 else 336
    val h = if (big) 216 else 189
    Column(modifier) {
        HomeRowHeader(title, small = if (big) null else items.size.toString(), start = start, big = big)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy((if (big) 24 else 20).mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(items, key = { _, item -> item.heroKey() }) { index, item ->
                val meta = metadata[item.heroKey()]
                val still = when (item) {
                    is HeroItem.MovieHero -> homeStill(meta?.backdropUrl, item.movie.backdropUrl, item.movie.posterUrl, null)
                    is HeroItem.SeriesHero -> homeStill(meta?.backdropUrl, item.series.backdropUrl, item.series.posterUrl, null)
                    is HeroItem.LiveHero -> homeStill(null, null, null, item.channel.logoUrl)
                }
                StageStill(
                    title = when (item) {
                        is HeroItem.MovieHero -> item.item.title
                        is HeroItem.SeriesHero -> item.series.name
                        is HeroItem.LiveHero -> item.channel.name
                    },
                    line = keepWatchingLine(item),
                    onClick = { onPlay(item) },
                    width = w.mpx,
                    height = h.mpx,
                    titleSize = if (big) 20 else 19,
                    lineSize = if (big) 16 else 15,
                    progress = item.durationMs.takeIf { it > 0 }?.let { item.positionMs.toFloat() / it },
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { onCardFocusChanged(index, it.hasFocus) },
                ) {
                    // The video sits under the still, which lifts off once frames arrive. (A TextureView
                    // hidden at alpha 0 is never drawn, so it never gets the surface the engine needs.)
                    if (index == previewIndex) PreviewTexture(engine, Modifier.fillMaxSize())
                    if (index != previewIndex || engineState != HeroPreviewEngine.State.PLAYING) StillArtwork(still)
                }
            }
        }
    }
}

/** "S1 · E2 · 31 min left", "1 h 05 min left", or when a channel was last watched. */
@Composable
private fun keepWatchingLine(item: HeroItem): String? {
    val left = if (item.durationMs > 0 && item.positionMs > 0) {
        timeLeftText(((item.durationMs - item.positionMs).coerceAtLeast(0L) + 59_999L) / 60_000L)
    } else null
    return when (item) {
        is HeroItem.MovieHero -> left
        is HeroItem.SeriesHero -> listOfNotNull(
            stringResource(R.string.content_season_episode, item.episode.seasonNumber, item.episode.episodeNumber),
            left,
        ).joinToString(" · ")
        is HeroItem.LiveHero -> relativeLastWatchedLabel(item.lastEngagementAt, System.currentTimeMillis())
    }
}

@Composable
private fun StillArtwork(still: HomeStill) {
    when (still) {
        is HomeStill.Picture -> AsyncImage(model = still.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        is HomeStill.Logo -> Box(Modifier.fillMaxSize().background(Color(0xFF0F1518)), contentAlignment = Alignment.Center) {
            tv.own.owntv.ui.components.ChannelLogoTile(
                logoUrl = still.url,
                modifier = Modifier.fillMaxSize(0.5f).clip(RoundedCornerShape(12.mpx)),
                imageModifier = Modifier.fillMaxSize().padding(8.mpx),
                fill = Color.Transparent,
            ) {}
        }
        HomeStill.None -> Box(Modifier.fillMaxSize().background(Color(0xFF0F1518)), contentAlignment = Alignment.Center) {
            OwnTVIcon(OwnTVIcon.PLAY_CIRCLE, tint = StageColors.Dim, modifier = Modifier.size(40.mpx))
        }
    }
}

/**
 * The preview's video. A TextureView, not a SurfaceView: the focused card is lifted by 1.08 and clipped
 * to its corners, and only a TextureView is drawn with the card — a SurfaceView would sit unscaled and
 * square behind it.
 */
@Composable
private fun PreviewTexture(engine: HeroPreviewEngine, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        surface = Surface(st).also(engine::setSurface)
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        engine.setSurface(null)
                        surface?.release()
                        surface = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}

@Composable
internal fun relativeLastWatchedLabel(lastEngagementAt: Long, nowMs: Long): String {
    val elapsedMs = nowMs - lastEngagementAt
    if (elapsedMs < 60_000L) return stringResource(R.string.home_last_watched_now)

    val elapsedMinutes = elapsedMs / 60_000L
    if (elapsedMinutes < 60L) {
        return pluralStringResource(R.plurals.home_last_watched_minutes, elapsedMinutes.toInt(), elapsedMinutes.toInt())
    }

    val elapsedHours = elapsedMinutes / 60L
    if (elapsedHours < 24L) {
        return pluralStringResource(R.plurals.home_last_watched_hours, elapsedHours.toInt(), elapsedHours.toInt())
    }

    val elapsedDays = elapsedHours / 24L
    return pluralStringResource(R.plurals.home_last_watched_days, elapsedDays.toInt(), elapsedDays.toInt())
}

/** Continue watching movies / series: 196 × 294 posters (`.pc`), as P2-02 draws the series row. */
@Composable
private fun ContinuePosterRow(
    title: String,
    items: List<LauncherContinuationItem>,
    start: Dp,
    onItemClick: (LauncherContinuationItem) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        HomeRowHeader(title, small = items.size.toString(), start = start)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(items, key = { _, item -> item.stableKey }) { index, item ->
                StagePoster(
                    title = item.title,
                    onClick = { onItemClick(item) },
                    width = 196.mpx,
                    height = 294.mpx,
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                ) {
                    AsyncImage(model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** Favourite films or shows, in the user's Favorites order. OK opens the item, not a resume. */
@Composable
private fun <T> FavoritePosterRow(
    title: String,
    items: List<T>,
    key: (T) -> Long,
    name: (T) -> String,
    poster: (T) -> String?,
    start: Dp,
    onItemClick: (T) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        HomeRowHeader(title, small = items.size.toString(), start = start)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                StagePoster(
                    title = name(item),
                    onClick = { onItemClick(item) },
                    width = 196.mpx,
                    height = 294.mpx,
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                ) {
                    AsyncImage(model = poster(item), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Now Trending drawn as posters — the alternative to the full-bleed hero, for anyone who wants Home to
 * be one consistent set of rows. Read-only, like the hero: core's worker decides what is in it.
 */
@Composable
private fun TrendingPosterRow(
    title: String,
    items: List<TrendingHomeItem>,
    start: Dp,
    onItemClick: (TrendingHomeItem) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    track: (FocusRequester) -> Unit,
    headerGap: Dp = 0.dp,
    bottomGap: Dp = 0.dp,
    onItemFocused: (Int) -> Unit = {},
) {
    Column {
        HomeRowHeader(title, small = null, start = start)
        Spacer(Modifier.height(headerGap))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(items, key = { _, item -> item.stableKey }) { index, item ->
                StagePoster(
                    title = when (item) {
                        is TrendingHomeItem.Movie -> item.movie.name
                        is TrendingHomeItem.Series -> item.series.name
                    },
                    onClick = { onItemClick(item) },
                    width = 196.mpx,
                    height = 294.mpx,
                    rating = item.snapshot.rating?.let(::ratingText),
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                onItemFocused(index)
                                onFocus()
                            }
                        },
                ) {
                    AsyncImage(
                        model = when (item) {
                            is TrendingHomeItem.Movie -> item.movie.posterUrl
                            is TrendingHomeItem.Series -> item.series.posterUrl
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Spacer(Modifier.height(bottomGap))
    }
}

@Composable
private fun TrendingHomeItem.toDetailsUi(meta: MetadataCacheEntity?, tmdbWins: Boolean): MediaDetailsUi {
    val snapshot = snapshot
    val providerPoster = when (this) {
        is TrendingHomeItem.Movie -> movie.posterUrl
        is TrendingHomeItem.Series -> series.posterUrl
    }
    val providerBackdrop = when (this) {
        is TrendingHomeItem.Movie -> movie.backdropUrl
        is TrendingHomeItem.Series -> series.backdropUrl
    }
    val providerTitle = when (this) {
        is TrendingHomeItem.Movie -> movie.name
        is TrendingHomeItem.Series -> series.name
    }
    val providerPlot = when (this) {
        is TrendingHomeItem.Movie -> movie.plot?.takeIf { it.isNotBlank() }
        is TrendingHomeItem.Series -> series.plot?.takeIf { it.isNotBlank() }
    }
    val tmdbPlot = meta?.overview?.takeIf { it.isNotBlank() } ?: snapshot.overview
    return MediaDetailsUi(
        title = providerTitle,
        subtitle = stringResource(if (this is TrendingHomeItem.Movie) R.string.home_trending_movie else R.string.home_trending_series),
        backdropUrl = MetadataImages.backdrop(meta?.backdropPath ?: snapshot.backdropPath, size = "w1280") ?: providerBackdrop,
        posterUrl = if (tmdbWins) {
            MetadataImages.poster(meta?.posterPath ?: snapshot.posterPath, size = "w500") ?: providerPoster
        } else {
            providerPoster ?: MetadataImages.poster(meta?.posterPath ?: snapshot.posterPath, size = "w500")
        },
        metaLine = listOfNotNull(snapshot.year?.toString(), snapshot.rating?.let { stringResource(R.string.content_rating, it) }).joinToString(" · "),
        genres = trendingJsonList(meta?.genresJson),
        plot = if (tmdbWins) tmdbPlot ?: providerPlot else providerPlot ?: tmdbPlot,
        cast = tv.own.owntv.core.metadata.MetadataCast.parse(meta?.castJson),
    )
}

private fun trendingJsonList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = org.json.JSONArray(json)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())
}

@Composable
private fun HeroFallbackPane(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    onChildFocused: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .clip(RoundedCornerShape(20.dp))
            .background(colors.panel)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 72)
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.home_no_preview),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_continue_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyHomeState(
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = modifier
            .focusProperties { canFocus = false }
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 84)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_start_watching),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_continue_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AllRowsHiddenState(
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = modifier
            .focusProperties { canFocus = false }
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 84)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_no_rows),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_enable_rows),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

