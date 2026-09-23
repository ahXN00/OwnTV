package tv.own.owntv.features.home

import androidx.compose.runtime.Immutable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.TrendingDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.home.GuideSliceState
import tv.own.owntv.core.home.HeroItem
import tv.own.owntv.core.home.HomeFeedReader
import tv.own.owntv.core.home.TrendingHomeItem
import tv.own.owntv.core.home.homeKey
import tv.own.owntv.core.launcher.LauncherContinuationItem
import tv.own.owntv.core.launcher.LauncherContinuationKind
import tv.own.owntv.core.model.HomeConfig
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.player.HeroPreviewEngine

@Immutable
data class HomeHeroMetadata(
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val plot: String? = null,
)

@Immutable
data class TrendingDetailsMetadata(
    val cache: MetadataCacheEntity?,
    val tmdbWins: Boolean,
)

@Immutable
data class HomeUiState(
    val trendingItems: List<TrendingHomeItem> = emptyList(),
    val activeTrendingIndex: Int = 0,
    val trendingPreferredLanguage: String = "EN",
    val trendingSeasonCounts: Map<Long, Int> = emptyMap(),
    val heroItems: List<HeroItem> = emptyList(),
    val activeHeroIndex: Int = 0,
    val continueMovies: List<LauncherContinuationItem> = emptyList(),
    val continueSeries: List<LauncherContinuationItem> = emptyList(),
    val heroMetadata: Map<String, HomeHeroMetadata> = emptyMap(),
    val continuationArtwork: Map<String, String> = emptyMap(),
    val recentLive: List<ChannelEntity> = emptyList(),
    val favoriteLive: List<ChannelEntity> = emptyList(),
    val config: HomeConfig = HomeConfig(),
    val recentGuide: GuideSliceState = GuideSliceState(),
    val favoriteGuide: GuideSliceState = GuideSliceState(),
    /**
     * True until the first [HomeViewModel.loadHomeData] completes. Home's queries are profile-scoped and
     * already indexed, but on a cold boot their first reads come off slow eMMC (pages not yet in the OS
     * page cache) — that's the ~half-second gap between the shell painting and `home-data`. While that
     * runs we render a skeleton so the landing screen paints its *structure* instantly instead of flashing
     * the empty state (which looks wrong for a user who does have history). Flips to false the moment real
     * data publishes, and stays false thereafter (refreshes don't re-skeleton).
     */
    val isLoading: Boolean = true,
)

/** What the shared top-bar Continue chip points at (Batch 7). */
enum class ContinueKind { LIVE, MOVIE, EPISODE }
enum class ContinueAction { RESUME, PLAY, NEXT_UP, LAST_CHANNEL }

/** A single resumable target: semantic action and display name. */
@Immutable
data class ContinueTarget(
    val kind: ContinueKind,
    /** Display name (movie/series/channel). */
    val name: String,
    val action: ContinueAction,
    val channelId: Long = -1L,
    val movieId: Long = -1L,
    val seriesId: Long = -1L,
    val episodeId: Long = -1L,
    val positionMs: Long = 0L,
)

class HomeViewModel(
    private val feed: HomeFeedReader,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    private val heroPreviewEngine: HeroPreviewEngine,
    private val historyDao: tv.own.owntv.core.database.dao.HistoryDao,
    private val progressDao: tv.own.owntv.core.database.dao.ProgressDao,
    private val metadata: MetadataRepository,
    private val trendingDao: TrendingDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Single entry point for "rebuild the Home rails". Every trigger goes through here rather than
     * calling loadHomeData directly, because there is more than one of them and on a cold start they
     * all fire at once: the trending-table observer below gets Room's immediate first emission, and the
     * shell runs its own refresh as soon as Home is the selected section. Measured on a TCL, that put
     * two full loads — ~15 dependent queries each — on the database concurrently at the single most
     * contended moment of launch, and the two completion stamps landed 6ms apart, so the second one was
     * pure duplicated work. Conflating buffer + debounce collapses that burst into one load, and
     * collectLatest keeps a genuine post-sync invalidation from queueing behind a stale in-flight pass.
     */
    private val reloadRequests = MutableSharedFlow<Long?>(
        replay = 1, // the first request is emitted before the collector below is running — don't lose it
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        viewModelScope.launch {
            reloadRequests.collectLatest { known ->
                (known ?: currentProfileId())?.let { profileId ->
                    loadHomeData(profileId)
                }
            }
        }
        // Room invalidates this after the worker atomically replaces a snapshot, so Home updates even
        // when the user stays on the screen throughout a background post-sync refresh.
        //
        // drop(1) discards Room's *initial* emission, which carries no news: it fires the moment this
        // flow is collected, and loadHomeData reads the trending table itself anyway, so acting on it
        // only duplicated the load the shell already asks for when Home becomes the selected section.
        // On a cold start that duplicate was the single largest delay on the screen — the two triggers
        // arrived hundreds of milliseconds apart, so no amount of coalescing could merge them without
        // holding the first one back. Only genuine post-first invalidations reach here now.
        viewModelScope.launch {
            trendingDao.observeAllItems().drop(1).collect {
                reloadRequests.emit(null)
            }
        }
    }

    // --- Batch 7: the single most-recent resumable item, for the shared top-bar Continue chip. ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val continueTarget: StateFlow<ContinueTarget?> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(null)
            else historyDao.observeMostRecent(pid).map { h -> h?.let { resolveContinue(pid, it) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun resolveContinue(
        pid: Long,
        h: tv.own.owntv.core.database.entity.WatchHistoryEntity,
    ): ContinueTarget? = when (h.mediaType) {
        MediaType.MOVIE -> movieDao.getById(h.itemId)?.let { m ->
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, m.categoryId, profileDao, categoryDao)) return@let null
            val pos = progressDao.get(pid, MediaType.MOVIE, m.id)?.positionMs ?: 0L
            ContinueTarget(ContinueKind.MOVIE, m.name, if (pos > 0) ContinueAction.RESUME else ContinueAction.PLAY, movieId = m.id, positionMs = pos)
        }
        MediaType.EPISODE -> seriesDao.getEpisodeById(h.itemId)?.let { ep ->
            seriesDao.getSeriesById(ep.seriesId)?.let { s ->
                if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, s.categoryId, profileDao, categoryDao)) return@let null
                val pos = progressDao.get(pid, MediaType.EPISODE, ep.id)?.positionMs ?: 0L
                ContinueTarget(ContinueKind.EPISODE, s.name, if (pos > 0) ContinueAction.RESUME else ContinueAction.NEXT_UP, seriesId = ep.seriesId, episodeId = ep.id, positionMs = pos)
            }
        }
        MediaType.LIVE -> channelDao.getById(h.itemId)?.let { c ->
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, c.categoryId, profileDao, categoryDao)) return@let null
            ContinueTarget(ContinueKind.LIVE, c.name, ContinueAction.LAST_CHANNEL, channelId = c.id)
        }
        else -> null
    }

    private val _heroFocused = MutableStateFlow(false)
    private val _previewEnabled = MutableStateFlow(true)
    private val _lastHeroInteractionMs = MutableStateFlow(0L)
    private val resolvingHeroKeys = mutableSetOf<String>()
    private val resolvingContinuationArtworkKeys = mutableSetOf<String>()

    val lastHeroInteractionMs: StateFlow<Long> = _lastHeroInteractionMs.asStateFlow()

    val isPreviewActive: StateFlow<Boolean> =
        combine(_heroFocused, _previewEnabled, settings.heroPreviewEnabled, _uiState) { focused, enabled, setting, state ->
            focused && enabled && setting && state.heroItems.isNotEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setPreviewEnabled(enabled: Boolean) {
        _previewEnabled.value = enabled
    }

    fun setHeroFocused(focused: Boolean) {
        _heroFocused.value = focused
    }

    fun navigateHero(index: Int) {
        val items = _uiState.value.heroItems
        if (index !in items.indices) return
        _uiState.value = _uiState.value.copy(activeHeroIndex = index)
    }

    fun navigateTrending(index: Int) {
        val items = _uiState.value.trendingItems
        if (index !in items.indices) return
        _uiState.value = _uiState.value.copy(activeTrendingIndex = index)
    }

    /** Re-resolve the exact saved provider row immediately before an action in case a sync replaced it. */
    suspend fun revalidateTrendingItem(item: TrendingHomeItem): TrendingHomeItem? = withContext(Dispatchers.IO) {
        when (item) {
            is TrendingHomeItem.Movie -> movieDao.getById(item.movie.id)
                ?.takeIf { it.sourceId == item.snapshot.sourceId }
                ?.let { item.copy(movie = it) }
            is TrendingHomeItem.Series -> seriesDao.getSeriesById(item.series.id)
                ?.takeIf { it.sourceId == item.snapshot.sourceId }
                ?.let { item.copy(series = it) }
        }
    }

    /** Load the same full cached TMDB payload used by Movies/Series details, using Trending's exact id. */
    suspend fun resolveTrendingDetails(item: TrendingHomeItem): TrendingDetailsMetadata = withContext(Dispatchers.IO) {
        val config = settings.metadataConfig()
        val cache = when (item) {
            is TrendingHomeItem.Movie -> metadata.resolveKnownMovie(item.movie, item.snapshot.tmdbId)
            is TrendingHomeItem.Series -> metadata.resolveKnownSeries(item.series, item.snapshot.tmdbId)
        }
        TrendingDetailsMetadata(cache = cache, tmdbWins = config.mode.tmdbWins)
    }

    fun onHeroUserNavigate(index: Int) {
        _lastHeroInteractionMs.value = System.currentTimeMillis()
        navigateHero(index)
        resolveHeroMetadata(index)
    }

    fun resolveSeriesContinuationArtwork(item: LauncherContinuationItem) {
        if (item.kind != LauncherContinuationKind.EPISODE) return
        if (_uiState.value.continuationArtwork.containsKey(item.stableKey)) return
        if (!resolvingContinuationArtworkKeys.add(item.stableKey)) return

        viewModelScope.launch {
            try {
                delay(250)
                val art = withContext(Dispatchers.IO) { seriesContinuationArtwork(item) } ?: return@launch
                _uiState.value = _uiState.value.copy(
                    continuationArtwork = _uiState.value.continuationArtwork + (item.stableKey to art),
                )
            } finally {
                resolvingContinuationArtworkKeys.remove(item.stableKey)
            }
        }
    }

    fun stopPreview() {
        heroPreviewEngine.stop()
    }

    /**
     * Start the hero preview for [hero] with the SAME request identity the player would use: the
     * playlist's User-Agent plus the item's own headers. Without them a source that needs a custom UA or
     * a Referer had a home screen that 403'd on every preview while the item itself played fine.
     */
    suspend fun startPreview(hero: HeroItem) {
        val source = withContext(Dispatchers.IO) {
            runCatching { sourceDao.getById(hero.sourceId) }.getOrNull()
        }
        heroPreviewEngine.play(
            hero.streamUrl, hero.seekToMs, source?.userAgent,
            tv.own.owntv.core.settings.SourceOverrides.headersWithReferer(hero.httpHeaders, source),
        )
    }

    /**
     * [profileId] lets a caller that already holds a *validated* active profile skip the lookup this
     * would otherwise do — a settings read plus a database round-trip that measured ~99ms on a TCL,
     * spent while the rails are still blank. The shell qualifies: it only composes once the launch
     * gate has confirmed the active id against Room's profile list. Everyone else passes nothing and
     * gets the lookup.
     */
    fun refresh(profileId: Long? = null) {
        reloadRequests.tryEmit(profileId?.takeIf { it >= 0 })
    }

    /**
     * The rails themselves are core's — the phone app builds the same feed from the same reader. What
     * stays here is what only a television does with it: the caches keyed to the previous pass, and the
     * hero carousel's position.
     */
    private suspend fun loadHomeData(profileId: Long) {
        val previous = _uiState.value
        val data = feed.load(profileId)
        _uiState.value = HomeUiState(
            trendingItems = data.trendingItems,
            activeTrendingIndex = previous.activeTrendingIndex
                .coerceIn(0, (data.trendingItems.size - 1).coerceAtLeast(0)),
            trendingPreferredLanguage = data.trendingPreferredLanguage,
            trendingSeasonCounts = data.trendingSeasonCounts,
            heroItems = data.heroItems,
            activeHeroIndex = 0,
            continueMovies = data.continueMovies,
            continueSeries = data.continueSeries,
            heroMetadata = previous.heroMetadata.filterKeys { key -> data.heroItems.any { it.homeKey == key } },
            continuationArtwork = previous.continuationArtwork
                .filterKeys { key -> data.continueSeries.any { it.stableKey == key } },
            recentLive = data.recentLive,
            favoriteLive = data.favoriteLive,
            config = data.config,
            recentGuide = data.recentGuide,
            favoriteGuide = data.favoriteGuide,
            isLoading = false,
        )
        tv.own.owntv.core.util.Perf.stamp("home-data")
    }

    private fun resolveHeroMetadata(index: Int) {
        val item = _uiState.value.heroItems.getOrNull(index) ?: return
        if (item is HeroItem.LiveHero) return

        val key = item.homeKey
        if (_uiState.value.heroMetadata.containsKey(key)) return
        if (!resolvingHeroKeys.add(key)) return

        viewModelScope.launch {
            try {
                delay(250)
                val current = _uiState.value.heroItems.getOrNull(_uiState.value.activeHeroIndex)
                if (current == null || current.homeKey != key) return@launch

                val resolved = withContext(Dispatchers.IO) { heroMetadata(item) } ?: return@launch
                _uiState.value = _uiState.value.copy(
                    heroMetadata = _uiState.value.heroMetadata + (key to resolved),
                )
            } finally {
                resolvingHeroKeys.remove(key)
            }
        }
    }

    private suspend fun heroMetadata(item: HeroItem): HomeHeroMetadata? = when (item) {
        is HeroItem.MovieHero -> metadata.resolveMovie(item.movie)?.let { cache ->
            HomeHeroMetadata(
                backdropUrl = MetadataImages.backdrop(cache.backdropPath, size = "w1280"),
                logoUrl = MetadataImages.logo(cache.logoPath),
                plot = cache.overview?.takeIf { it.isNotBlank() },
            )
        }
        is HeroItem.SeriesHero -> {
            val show = metadata.resolveSeries(item.series)
            val episode = if (show?.overview.isNullOrBlank()) metadata.resolveEpisode(item.series, item.episode) else null
            when {
                show != null || episode != null -> HomeHeroMetadata(
                    backdropUrl = MetadataImages.backdrop(show?.backdropPath, size = "w1280")
                        ?: MetadataImages.backdrop(episode?.backdropPath ?: episode?.posterPath, size = "w1280"),
                    logoUrl = MetadataImages.logo(show?.logoPath),
                    plot = show?.overview?.takeIf { it.isNotBlank() } ?: episode?.overview?.takeIf { it.isNotBlank() },
                )
                else -> null
            }
        }
        is HeroItem.LiveHero -> null
    }

    private suspend fun seriesContinuationArtwork(item: LauncherContinuationItem): String? {
        val episode = seriesDao.getEpisodeById(item.targetItemId) ?: return null
        val series = seriesDao.getSeriesById(episode.seriesId) ?: return null
        val episodeMeta = metadata.resolveEpisode(series, episode)
        val showMeta = if (episodeMeta == null) metadata.resolveSeries(series) else null
        return MetadataImages.backdrop(episodeMeta?.backdropPath ?: episodeMeta?.posterPath, size = "w780")
            ?: MetadataImages.backdrop(showMeta?.backdropPath, size = "w780")
            ?: series.backdropUrl?.takeIf { it.isNotBlank() }
            ?: series.posterUrl?.takeIf { it.isNotBlank() }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

}
