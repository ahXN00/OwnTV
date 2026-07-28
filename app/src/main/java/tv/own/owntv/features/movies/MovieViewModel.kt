@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.movies

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.filter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.features.live.LiveKey
import tv.own.owntv.features.live.parseLiveKey
import tv.own.owntv.features.live.serialize
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.ui.components.OwnTVIcon

class MovieViewModel(
    private val movieDao: MovieDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val player: OwnTVPlayer,
    private val downloadManager: DownloadManager,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val contentOrderDao: ContentOrderDao,
    private val metadata: tv.own.owntv.core.metadata.MetadataRepository,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
    private val subtitleController: tv.own.owntv.core.subtitles.SubtitleController,
) : ViewModel() {

    data class MovieMoveState(val items: List<MovieEntity>, val activeIndex: Int, val contextKey: String)
    private val _moveState = MutableStateFlow<MovieMoveState?>(null)
    val moveState: StateFlow<MovieMoveState?> = _moveState.asStateFlow()

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)
    // Observe the active profile's sources reactively so adding/removing a playlist refreshes Movies
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps -> Ctx(aps.profileId, aps.movieSourceIds) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    private val folderContextKeys: StateFlow<Map<Long, String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyMap())
            else categoryDao.observe(c.sourceIds, MediaType.MOVIE).map { cats ->
                cats.associateBy({ it.id }, { CustomizeKeys.category(it) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Contexts that actually have manual-order rows (C3): only those folders pay the
     *  unindexable content_order join-sort; everything else stays on the plain indexed query. */
    private val orderedContexts: StateFlow<Set<String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptySet())
            else contentOrderDao.observeContextKeys(c.profileId, MediaType.MOVIE).map { it.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** This profile's hide/rename/reorder customizations for Movies. */
    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.MOVIE)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /**
     * Category DB ids of this profile's hidden Movie categories — so hiding a category hides its
     * movies everywhere (All, search, Home rails), not just the rail folder (mirrors Live TV).
     */
    private val hiddenCategoryIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(emptySet())
            } else {
                combine(categoryDao.observe(c.sourceIds, MediaType.MOVIE), custom) { cats, cust ->
                    if (cust.hiddenCategories.isEmpty()) emptySet()
                    else cats.filter { CustomizeKeys.category(it) in cust.hiddenCategories }.map { it.id }.toSet()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Customizations + resolved hidden-category ids, bundled so the list pipeline takes one flow. */
    private data class CustState(val cust: SectionCustomizations, val hiddenCats: Set<Long>)
    private val custResolved: StateFlow<CustState> = combine(custom, hiddenCategoryIds) { c, h -> CustState(c, h) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustState(SectionCustomizations(), emptySet()))

    /** List ordering for this section (Provider order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortMovies
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.ALPHA)

    fun toggleSort() {
        viewModelScope.launch {
            // Cycle Provider → A–Z → Rating → Provider.
            settings.setSortMovies(
                when (sortMode.value) {
                    SettingsRepository.SortMode.PLAYLIST -> SettingsRepository.SortMode.ALPHA
                    SettingsRepository.SortMode.ALPHA -> SettingsRepository.SortMode.RATING
                    SettingsRepository.SortMode.RATING -> SettingsRepository.SortMode.DATE_ADDED
                    SettingsRepository.SortMode.DATE_ADDED -> SettingsRepository.SortMode.PLAYLIST
                },
            )
        }
    }

    val viewMode: StateFlow<SettingsRepository.VodViewMode> = settings.vodViewMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.VodViewMode.GRID)

    fun toggleViewMode() {
        viewModelScope.launch {
            settings.setVodViewMode(
                if (viewMode.value == SettingsRepository.VodViewMode.GRID) SettingsRepository.VodViewMode.LIST
                else SettingsRepository.VodViewMode.GRID,
            )
        }
    }

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()

    // Bumped after a favourite/history mutation so the pager rebuilds its (manual, non-reactive)
    // PagingSource. Without this, unfavouriting on the Favorites category (or removing from History)
    // can leave the removed movie in the paged snapshot, which breaks focus restore (the stale row
    // disposes under focus). Behaviour is intermittent because Room's invalidation timing varies.
    private val _listRefresh = MutableStateFlow(0)
    private fun refreshList() { _listRefresh.value++ }

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _selectedMovie = MutableStateFlow<MovieEntity?>(null)
    val selectedMovie: StateFlow<MovieEntity?> = _selectedMovie.asStateFlow()

    /**
     * On-demand TMDB enrichment for the focused movie (plan §7.2: detail screens resolve lazily). Debounced
     * so scrolling fast doesn't fire a lookup per card; cached in Room so a second focus is instant. Null
     * when enrichment is off or no confident match — the UI then shows pure provider data (§7.1).
     */
    /** Bumped by [refetchMovieMeta] to force the focused movie's TMDB resolve to re-run after clearing its cache. */
    private val _metaRefreshTick = MutableStateFlow(0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedMovieMeta: StateFlow<MovieMeta?> = combine(_selectedMovie, _metaRefreshTick) { m, tick -> m to tick }
        .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second }
        .debounce(350)
        .mapLatest { (m, _) ->
            if (m == null) null
            else MovieMeta(m.id, runCatching { metadata.resolveMovie(m) }.getOrNull())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** TMDB metadata tagged with the movie id it was resolved for, so the UI never shows stale meta on a
     *  different card during the debounce window. [cache] is null while resolving or on no match. */
    data class MovieMeta(val movieId: Long, val cache: tv.own.owntv.core.database.entity.MetadataCacheEntity?)

    /** Source mode (plan §4.1) — the detail pane uses it to flip provider/TMDB field precedence. */
    val metadataMode: StateFlow<tv.own.owntv.core.metadata.MetadataMode> = settings.metadataMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)

    // Observable so the player HUD's favorite toggle can reflect/act on the movie being played.
    private val _playingMovie = MutableStateFlow<MovieEntity?>(null)
    val playingMovie: StateFlow<MovieEntity?> = _playingMovie.asStateFlow()

    init {
        // Periodically persist resume position for the movie currently playing.
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                saveProgressNow()
            }
        }
    }

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else combine(
                categoryDao.observe(c.sourceIds, MediaType.MOVIE),
                customize.observe(c.profileId, MediaType.MOVIE),
                sortMode,
            ) { cats, cust, sort ->
                // A–Z also sorts the category folders; manually moved categories stay pinned first.
                val folders = cats.applyCustomizations(cust, alphaRest = sort == SettingsRepository.SortMode.ALPHA)
                defaultRail + folders.map { (cat, name) ->
                    LiveRailItem(LiveKey.Folder(cat.id), name)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultRail)

    val movies: Flow<PagingData<MovieEntity>> = combine(
        _selected, ctx, _search.map { it.trim() }.debounce(300).distinctUntilChanged(), sortMode, _listRefresh,
    ) { key, c, query, sort, _ -> Args(key, c, query, sort) }
        .combine(custResolved) { args, cs -> args to cs }
        // Rebuild the pager when a folder gains/loses manual order (C3): the fast-path plain
        // PagingSource doesn't observe content_order, so the switch must recreate it.
        .combine(orderedContexts) { p, _ -> p }
        .flatMapLatest { (args, cs) ->
            // Hidden items/categories are filtered on each fresh PagingData inside the pager chain —
            // a customization change re-creates the pager (same pattern as Live TV).
            Pager(PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 90, maxSize = 300)) {
                pagingSource(args.key, args.ctx, args.query, args.sort)
            }.flow.map { paging ->
                if (cs.cust.hiddenItems.isEmpty() && cs.hiddenCats.isEmpty()) paging
                else paging.filter { m ->
                    CustomizeKeys.movie(m) !in cs.cust.hiddenItems &&
                        (m.categoryId == null || m.categoryId !in cs.hiddenCats)
                }
            }
        }
        .cachedIn(viewModelScope)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode)

    val count: StateFlow<Int> = combine(_selected, ctx, hiddenCategoryIds) { key, c, hidden -> Triple(key, c, hidden) }
        .flatMapLatest { (key, c, hidden) -> countFlow(key, c, hidden).throttleLatest() } // C2: cap live COUNT re-runs during bulk sync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.MOVIE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Resume/watched progress for the visible movies, keyed by movie id — drives the ✓ tick and the
     *  in-progress bar on posters/list rows. Only started/finished movies have a row, so this is small. */
    val movieProgress: StateFlow<Map<Long, PlaybackProgressEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else progressDao.observeMovieProgress(c.profileId) }
        .map { list -> list.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val selectedProgress: StateFlow<PlaybackProgressEntity?> = combine(_selectedMovie, ctx) { m, c -> m to c }
        .flatMapLatest { (m, c) ->
            if (m == null) flowOf(null) else progressDao.observe(c.profileId, MediaType.MOVIE, m.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(key: LiveKey) { _selected.value = key }
    fun setSearchQuery(query: String) { _search.value = query }
    fun onMovieFocused(movie: MovieEntity) { _selectedMovie.value = movie }

    /**
     * Manual "Refetch TMDB details" (plan §11.2 U5a): clear this movie's cached match/details (incl. a 7-day
     * negative cache) and re-trigger [resolveMovie] for the focused movie via the meta-refresh tick.
     */
    fun refetchMovieMeta(movie: MovieEntity) {
        viewModelScope.launch {
            runCatching { metadata.clearMovie(movie) }
            _metaRefreshTick.value++
        }
    }

    /**
     * Prefill for the "Set TMDB name" dialog (plan §11.2 U5b): the saved override if any, else the cleaned
     * provider title. [hasOverride] drives the dialog's Clear button.
     */
    data class TmdbNamePrefill(val title: String, val year: Int?, val hasOverride: Boolean)

    suspend fun movieTmdbNamePrefill(movie: MovieEntity): TmdbNamePrefill {
        metadata.movieOverride(movie)?.let { return TmdbNamePrefill(it.title, it.year, hasOverride = true) }
        val norm = tv.own.owntv.core.metadata.TitleNormalizer.normalize(movie.name)
        return TmdbNamePrefill(norm.query, movie.year ?: norm.year, hasOverride = false)
    }

    /** Save the hand-typed override and force a re-resolve under the new query (plan §11.2 U5b). */
    fun setMovieTmdbName(movie: MovieEntity, title: String, year: Int?) {
        viewModelScope.launch {
            runCatching { metadata.setMovieOverride(movie, title, year) }
            _metaRefreshTick.value++
        }
    }

    /** Remove the override and re-resolve with the cleaned provider title (plan §11.2 U5b). */
    fun clearMovieTmdbName(movie: MovieEntity) {
        viewModelScope.launch {
            runCatching { metadata.clearMovieOverride(movie) }
            _metaRefreshTick.value++
        }
    }

    /** The user's resume preference (Always / Ask / Never) — the screen drives the prompt. */
    val resumeMode: StateFlow<SettingsRepository.ResumeMode> = settings.resumeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.ResumeMode.ASK)

    /** Saved resume position for [movie] (0 when none) — used by the screen to decide the prompt. */
    suspend fun savedPositionMs(movie: MovieEntity): Long =
        currentProfileId()?.let { progressDao.get(it, MediaType.MOVIE, movie.id)?.positionMs ?: 0 } ?: 0

    /** Global "External player" toggle — screens must NOT open the fullscreen in-app player when on
     *  (mounting it spins up an mpv instance even though play() branched to the external app). */
    val externalPlayerOn: StateFlow<Boolean> = settings.externalPlayerMovies
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Stalker movies resolve to a real URL at play time; anything else returns streamUrl as-is.
     *  Null = resolve failed (portal/auth error) — the caller should not start playback. */
    private suspend fun resolvedUrlOrNull(movie: MovieEntity): String? {
        val source = sourceDao.getById(movie.sourceId)
        if (!streamUrlResolver.needsResolve(source)) return movie.streamUrl
        return try {
            streamUrlResolver.resolve(source!!, movie.streamUrl, vod = true)
        } catch (e: Exception) {
            Log.w(TAG, "stalker resolve failed movieId=${movie.id}", e)
            null
        }
    }

    /** Phase B: long-press "Play with external player" — always external, regardless of the global toggle. */
    fun playExternal(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId()
            Log.d(TAG, "playExternal movieId=${movie.id}")
            val url = resolvedUrlOrNull(movie) ?: return@launch
            externalPlayerLauncher.launch(url, movie.name)
            if (pid != null) {
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
                }.onFailure { t -> Log.w(TAG, "external play history record failed movieId=${movie.id} profile=$pid", t) }
            }
        }
    }

    fun play(movie: MovieEntity, startPositionMs: Long = 0) {
        viewModelScope.launch {
            val pid = currentProfileId()
            // External player (global toggle): hand the stream URL to an external app and skip the
            // in-app engine entirely. History is still recorded (recently-watched); resume position
            // and the playing-movie HUD/progress tick are intentionally not — the external app owns
            // playback and OwnTV can't observe it.
            if (settings.externalPlayerMovies.first()) {
                Log.d(TAG, "play movieId=${movie.id} -> external player")
                val url = resolvedUrlOrNull(movie) ?: return@launch
                externalPlayerLauncher.launch(url, movie.name)
                if (pid != null) {
                    runCatching {
                        historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
                    }.onFailure { t -> Log.w(TAG, "external play history record failed movieId=${movie.id} profile=$pid", t) }
                }
                return@launch
            }
            val source = sourceDao.getById(movie.sourceId)
            val sourceUa = source?.userAgent
            // Stalker: mint the playable URL right before playback (create_link, type=vod). The stored
            // streamUrl stays the cmd — it's the item's identity (engine pins, downloads, history).
            val playUrl = if (streamUrlResolver.needsResolve(source)) {
                try {
                    streamUrlResolver.resolve(source!!, movie.streamUrl, vod = true)
                } catch (e: Exception) {
                    Log.w(TAG, "stalker resolve failed movieId=${movie.id}", e)
                    return@launch
                }
            } else {
                movie.streamUrl
            }
            Log.d(TAG, "play movieId=${movie.id} profile=$pid startPositionMs=$startPositionMs")
            player.play(
                playUrl,
                title = movie.name,
                year = movie.year?.toString(),
                isLive = false,
                startPositionMs = startPositionMs,
                userAgent = sourceUa,
                // P6 — engine pins key on this, not on playUrl (a Stalker playUrl is minted per play).
                contentKey = tv.own.owntv.core.player.enginePinKey(movie.sourceId, "MOVIE", movie.remoteId),
            )
            _playingMovie.value = movie
            // Enable the player's OpenSubtitles search for this movie (subtitle plan §4). tmdbId is
            // resolved from the metadata cache when available (review R7) for a stronger match.
            if (pid != null) {
                val tmdbId = runCatching { metadata.resolveMovie(movie)?.tmdbId?.toLong() }.getOrNull()
                subtitleController.setMovie(pid, movie, tmdbId)
            }
            if (pid != null) {
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
                }.onFailure { t ->
                    Log.w(TAG, "play history record failed movieId=${movie.id} profile=$pid", t)
                }
            }
        }
    }

    fun playById(movieId: Long, startPositionMs: Long = 0) {
        viewModelScope.launch {
            val movie = movieDao.getById(movieId) ?: return@launch
            play(movie, startPositionMs)
        }
    }

    suspend fun playByIdAsync(movieId: Long, startPositionMs: Long = 0): Boolean {
        val movie = movieDao.getById(movieId) ?: return false
        play(movie, startPositionMs)
        return true
    }

    /** Download states for the currently visible movies, keyed by movie id. */
    val downloadStates: StateFlow<Map<Long, DownloadEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else downloadManager.observe(c.profileId) }
        .map { list -> list.filter { it.mediaType == MediaType.MOVIE }.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun download(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            downloadManager.enqueue(
                profileId = pid,
                mediaType = MediaType.MOVIE,
                itemId = movie.id,
                title = movie.name,
                posterUrl = movie.posterUrl,
                streamUrl = movie.streamUrl,
                relativeDir = "Movies",
                fileName = "${StorageAccess.sanitize(movie.name)}.${movie.containerExt ?: StorageAccess.extOf(movie.streamUrl)}",
            )
        }
    }

    /** Downloaded OpenSubtitles subtitles for this movie (long-press "Delete subtitles" popup, §11). */
    suspend fun downloadedSubtitles(movie: MovieEntity): List<tv.own.owntv.core.database.dao.LinkedSubtitle> =
        subtitleController.downloadsForMovie(movie)

    fun deleteSubtitle(cacheId: Long) {
        viewModelScope.launch { subtitleController.deleteCached(cacheId) }
    }

    fun toggleFavorite(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteIds.value.contains(movie.id)) favoriteDao.remove(pid, MediaType.MOVIE, movie.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id))
            refreshList() // the Favorites category uses a manual PagingSource — force a rebuild
        }
    }

    /** ≥95% of duration watched = completed (mirrors SeriesViewModel.isEpisodeCompleted). */
    fun isMovieCompleted(p: PlaybackProgressEntity): Boolean =
        p.durationMs > 0 && p.positionMs >= (p.durationMs * 0.95f).toLong()

    /** Mark a movie as watched (shows ✓) without playing it — same synthetic 1ms/1ms sentinel trick used
     *  for episodes (satisfies the ≥95% completed rule while keeping Play restarting from ~0). */
    fun markMovieWatched(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            progressDao.save(
                PlaybackProgressEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = movie.id, positionMs = 1L, durationMs = 1L),
            )
        }
    }

    /** Mark a movie as unwatched — clears its resume position (removes the ✓ and any progress bar). */
    fun markMovieUnwatched(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            progressDao.clear(pid, MediaType.MOVIE, movie.id)
        }
    }

    /** Persist the resume position if the player is actually playing the tracked movie. */
    fun saveProgressNow() {
        val m = _playingMovie.value ?: return
        if (player.currentMediaUrl != m.streamUrl || !player.isPlaying.value) return
        val pos = player.position.value
        val dur = player.duration.value
        if (pos > 0 && dur > 0) {
            viewModelScope.launch {
                val pid = currentProfileId() ?: return@launch
                Log.d(TAG, "saveProgressNow movieId=${m.id} profile=$pid positionMs=$pos durationMs=$dur")
                runCatching {
                    progressDao.save(
                        PlaybackProgressEntity(profileId = pid, mediaType = MediaType.MOVIE, itemId = m.id, positionMs = pos, durationMs = dur),
                    )
                }.onFailure { t ->
                    Log.w(TAG, "saveProgressNow progress save failed movieId=${m.id} profile=$pid", t)
                }
                launcherIntegrationRepository.publishMovieProgress(pid, m.id, pos, dur)
            }
        }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    fun enterMoveMode(movie: MovieEntity, key: LiveKey) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val contextKey = when (key) {
                is LiveKey.Folder -> folderContextKeys.value[key.id] ?: return@launch
                LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                else -> return@launch
            }
            val items = when (key) {
                is LiveKey.Folder -> movieDao.snapshotByCategoryManual(key.id, pid, contextKey, 5000)
                LiveKey.Favorites -> movieDao.snapshotFavoritesManual(pid, contextKey, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                LiveKey.History, LiveKey.All -> return@launch
            }
            val idx = items.indexOfFirst { it.id == movie.id }
            if (idx < 0) return@launch
            _moveState.value = MovieMoveState(items, idx, contextKey)
            settings.setSortMovies(SettingsRepository.SortMode.PLAYLIST)
        }
    }

    fun moveUp() {
        val s = _moveState.value ?: return
        if (s.activeIndex == 0) return
        val list = s.items.toMutableList()
        val i = s.activeIndex
        list[i - 1] = s.items[i]; list[i] = s.items[i - 1]
        _moveState.value = s.copy(items = list, activeIndex = i - 1)
    }

    fun moveDown() {
        val s = _moveState.value ?: return
        if (s.activeIndex == s.items.size - 1) return
        val list = s.items.toMutableList()
        val i = s.activeIndex
        list[i + 1] = s.items[i]; list[i] = s.items[i + 1]
        _moveState.value = s.copy(items = list, activeIndex = i + 1)
    }

    fun commitMove() {
        val s = _moveState.value ?: return
        _moveState.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            contentOrderDao.replaceContext(
                profileId = pid,
                type = MediaType.MOVIE,
                contextKey = s.contextKey,
                rows = s.items.mapIndexed { i, m ->
                    ContentOrderEntity(profileId = pid, mediaType = MediaType.MOVIE, contextKey = s.contextKey, itemId = m.id, position = i)
                },
            )
        }
    }

    fun cancelMove() { _moveState.value = null }

    /** Hide the movie from all lists (undo via Settings → Customize Category → Hidden items). */
    fun hideMovie(movie: MovieEntity) {
        if (_selectedMovie.value?.id == movie.id) _selectedMovie.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setItemHidden(pid, MediaType.MOVIE, CustomizeKeys.movie(movie), movie.name, true)
        }
    }

    fun removeFromHistory(movieId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            historyDao.remove(pid, MediaType.MOVIE, movieId)
            progressDao.clear(pid, MediaType.MOVIE, movieId)
            refreshList() // the History category uses a manual PagingSource — force a rebuild
        }
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, MovieEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        val playlist = sort == SettingsRepository.SortMode.PLAYLIST
        val rating = sort == SettingsRepository.SortMode.RATING
        val dateAdded = sort == SettingsRepository.SortMode.DATE_ADDED
        return if (query.isBlank()) when (key) {
            LiveKey.All -> when {
                rating -> movieDao.pagingAllRating(ids)
                playlist -> movieDao.pagingAllOriginal(ids)
                dateAdded -> movieDao.pagingAllDateAdded(ids)
                else -> movieDao.pagingAll(ids)
            }
            LiveKey.Favorites -> movieDao.pagingFavoritesManual(c.profileId, ContentOrderEntity.FAV_CONTEXT, ids)
            LiveKey.History -> movieDao.pagingHistory(c.profileId, ids)
            is LiveKey.Folder -> {
                val ctxKey = folderContextKeys.value[key.id] ?: ""
                when {
                    rating -> movieDao.pagingByCategoryRating(key.id)
                    dateAdded -> movieDao.pagingByCategoryDateAdded(key.id)
                    // C3 fast path: no manual order in this folder → the plain indexed query has
                    // the identical (sortOrder, name) order without the join-sort.
                    ctxKey !in orderedContexts.value -> movieDao.pagingByCategory(key.id)
                    else -> movieDao.pagingByCategoryManual(key.id, c.profileId, ctxKey)
                }
            }
        } else when (key) {
            LiveKey.All ->
                if (dateAdded) movieDao.searchAllDateAdded(query, ids)
                else movieDao.searchAll(query, ids)
            LiveKey.Favorites -> movieDao.searchFavorites(query, c.profileId, ids)
            LiveKey.History -> movieDao.searchHistory(query, c.profileId, ids)
            is LiveKey.Folder ->
                if (dateAdded) movieDao.searchInCategoryDateAdded(query, key.id)
                else movieDao.searchInCategory(query, key.id)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx, hiddenCats: Set<Long>): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All ->
                if (hiddenCats.isEmpty()) movieDao.countAll(ids)
                else movieDao.countAllExcluding(ids, hiddenCats.toList())
            LiveKey.Favorites -> movieDao.countFavorites(c.profileId, ids)
            LiveKey.History -> movieDao.countHistory(c.profileId, ids)
            is LiveKey.Folder -> movieDao.countByCategory(key.id)
        }
    }

    // Remember the last selected category (Settings → Browsing & lists → "Remember last category —
    // Movies", on by default). Declared LAST in the class so railItems below/above is already assigned
    // when this init runs. Mirrors LiveViewModel's identical block.
    init {
        // Persist on change, debounced — the rail fires select() on focus as you scroll it.
        viewModelScope.launch {
            _selected.drop(1).debounce(800).distinctUntilChanged()
                .collect { settings.setLastMoviesCategory(it.serialize()) }
        }
        // Restore once at startup, and only while still on the default (never yank a user who already
        // navigated). A saved folder is honoured only once it exists in this profile's rail.
        viewModelScope.launch {
            if (!settings.rememberCategoryMovies.first()) return@launch
            val saved = parseLiveKey(settings.lastMoviesCategory.first()) ?: return@launch
            if (saved is LiveKey.Folder) {
                val ok = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                    railItems.first { list -> list.any { it.key == saved } }
                } != null
                if (ok && _selected.value == LiveKey.All) _selected.value = saved
            } else if (_selected.value == LiveKey.All) {
                _selected.value = saved
            }
        }
    }

    private companion object {
        const val TAG = "OwnTVHome"
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, "Favorites", OwnTVIcon.FAVORITE),
            LiveRailItem(LiveKey.History, "History", OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All, "All Movies"),
        )
    }
}
