@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.series

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.customize.applyCustomizationsWithCustoms
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SeriesSortOrderDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.repository.SeriesRepository
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.features.live.LiveKey
import tv.own.owntv.features.live.parseLiveKey
import tv.own.owntv.features.live.serialize
import tv.own.owntv.features.customize.MoveTarget
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.player.MediaMeta
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlaylistItem
import tv.own.owntv.ui.components.OwnTVIcon

class SeriesViewModel(
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val seriesRepository: SeriesRepository,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val player: OwnTVPlayer,
    private val downloadManager: DownloadManager,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val contentOrderDao: ContentOrderDao,
    private val customCategoryDao: CustomCategoryDao,
    private val seriesSortOrderDao: SeriesSortOrderDao,
    private val metadata: tv.own.owntv.core.metadata.MetadataRepository,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
    private val subtitleController: tv.own.owntv.core.subtitles.SubtitleController,
) : ViewModel() {

    data class SeriesMoveState(val items: List<SeriesEntity>, val activeIndex: Int, val contextKey: String)
    private val _moveState = MutableStateFlow<SeriesMoveState?>(null)
    val moveState: StateFlow<SeriesMoveState?> = _moveState.asStateFlow()

    data class CategoryMoveState(val items: List<String>, val keys: List<String>, val activeIndex: Int, val targetKey: String)
    private val _categoryMoveState = MutableStateFlow<CategoryMoveState?>(null)
    val categoryMoveState: StateFlow<CategoryMoveState?> = _categoryMoveState.asStateFlow()

    private data class Ctx(
        val profileId: Long,
        val sourceIds: List<Long>,
        val sourceNames: Map<Long, String>,
    )
    // Observe the active profile's sources reactively so adding/removing a playlist refreshes Series
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps ->
            val ids = aps.seriesSourceIds
            Ctx(aps.profileId, ids, aps.sources.filter { it.id in ids }.associate { it.id to it.name })
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList(), emptyMap()))

    val providerNames: StateFlow<Map<Long, String>> = ctx
        .map { c -> c.sourceNames.takeIf { it.size > 1 } ?: emptyMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val folderContextKeys: StateFlow<Map<Long, String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyMap())
            else categoryDao.observe(c.sourceIds, MediaType.SERIES).map { cats ->
                cats.associateBy({ it.id }, { CustomizeKeys.category(it) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Contexts that actually have manual-order rows (C3): only those folders pay the
     *  unindexable content_order join-sort; everything else stays on the plain indexed query. */
    private val orderedContexts: StateFlow<Set<String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptySet())
            else contentOrderDao.observeContextKeys(c.profileId, MediaType.SERIES).map { it.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** This profile's hide/rename/reorder customizations for Series. */
    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.SERIES)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /** The user's custom combined categories with live member counts — the "Move to…" dialog's list. */
    val moveTargets: StateFlow<List<MoveTarget>> = combine(ctx, custom) { c, cust -> c to cust }
        .flatMapLatest { (c, cust) ->
            if (c.profileId < 0 || cust.customCategories.isEmpty()) flowOf(emptyList())
            else customCategoryDao.observeCountsByContexts(
                c.profileId,
                MediaType.SERIES,
                cust.customCategories.map { it.id },
                c.sourceIds.ifEmpty { listOf(-1L) },
            ).map { counts ->
                cust.customCategories.map { cc ->
                    MoveTarget(
                        id = cc.id,
                        displayName = cust.categoryNames[cc.id] ?: cc.name,
                        count = counts.firstOrNull { it.contextKey == cc.id }?.count ?: 0,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The stable key of a provider folder ([null] when the folder vanished) — the Move dialog's origin. */
    fun folderKey(id: Long): String? = folderContextKeys.value[id]

    /** Creates a custom category (issue #87) — the Move dialog's "＋ New category…" flow. */
    fun createCustomCategory(name: String) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.createCustomCategory(pid, MediaType.SERIES, name)
        }
    }

    /**
     * Moves (or copies, [keepInOrigin]) one series into a custom category (issue #87). The item is
     * appended at the category's tail (maxPosition + 1). Without [keepInOrigin] the item leaves its
     * origin: a favorite row is deleted, a custom-category membership row is deleted, and a provider
     * folder is marked in movedFromOrigin — the pager chain then drops it from that folder while
     * keeping it in All / search / recent.
     */
    fun moveToCategory(itemKey: String, itemId: Long, originKey: String, targetId: String, keepInOrigin: Boolean) {
        if (targetId == originKey) return
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customCategoryDao.appendItem(pid, MediaType.SERIES, targetId, itemId)
            if (!keepInOrigin) {
                when {
                    originKey == ContentOrderEntity.FAV_CONTEXT -> favoriteDao.remove(pid, MediaType.SERIES, itemId)
                    CustomizeKeys.isCustom(originKey) -> customCategoryDao.deleteItem(pid, MediaType.SERIES, originKey, itemId)
                    else -> customize.setItemMovedFromOrigin(pid, MediaType.SERIES, itemKey, originKey, moved = true)
                }
            }
        }
    }

    /**
     * Category DB ids of this profile's hidden Series categories — so hiding a category hides its
     * series everywhere (All, search, Home rails), not just the rail folder (mirrors Live TV).
     */
    private val hiddenCategoryIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(emptySet())
            } else {
                combine(categoryDao.observe(c.sourceIds, MediaType.SERIES), custom, profileDao.observeById(c.profileId)) { cats, cust, profile ->
                    tv.own.owntv.core.content.AdultCategoryClassifier.hiddenCategoryIds(
                        cats,
                        cust.hiddenCategories,
                        profile?.isKids == true,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Customizations + resolved hidden-category ids, bundled so the list pipeline takes one flow. */
    private data class CustState(val cust: SectionCustomizations, val hiddenCats: Set<Long>)
    private val custResolved: StateFlow<CustState> = combine(custom, hiddenCategoryIds) { c, h -> CustState(c, h) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustState(SectionCustomizations(), emptySet()))

    /** List ordering for this section (Provider order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortSeries
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.ALPHA)

    fun toggleSort() {
        viewModelScope.launch {
            // Cycle Provider → A–Z → Rating → Provider.
            settings.setSortSeries(
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
    // PagingSource. Without this, unfavouriting on the Favorites category leaves the removed series in
    // the paged snapshot, which breaks focus restore (the stale row disposes under focus).
    private val _listRefresh = MutableStateFlow(0)
    private fun refreshList() { _listRefresh.value++ }

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _selectedSeries = MutableStateFlow<SeriesEntity?>(null)
    val selectedSeries: StateFlow<SeriesEntity?> = _selectedSeries.asStateFlow()

    /** On-demand TMDB enrichment for the focused series (show-level), tagged with the series id to avoid
     *  stale meta during the debounce. Null when off or no confident match. */
    /** Bumped by [refetchSeriesMeta] to force the focused series' TMDB resolve to re-run after clearing its cache. */
    private val _seriesMetaTick = MutableStateFlow(0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedSeriesMeta: StateFlow<SeriesMeta?> = combine(_selectedSeries, _seriesMetaTick) { s, tick -> s to tick }
        .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second }
        // See MetadataRepository.FOCUS_DEBOUNCE_MS — 700 ms so scrolling past cards costs nothing.
        .debounce(tv.own.owntv.core.metadata.MetadataRepository.FOCUS_DEBOUNCE_MS)
        .mapLatest { (s, _) ->
            if (s == null) null
            else SeriesMeta(s.id, runCatching { metadata.resolveSeries(s) }.getOrNull())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class SeriesMeta(val seriesId: Long, val cache: tv.own.owntv.core.database.entity.MetadataCacheEntity?)

    /** Source mode (plan §4.1) — the pane/details use it to flip provider/TMDB precedence. */
    val metadataMode: StateFlow<tv.own.owntv.core.metadata.MetadataMode> = settings.metadataMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)

    private val _openedSeries = MutableStateFlow<SeriesEntity?>(null)
    val openedSeries: StateFlow<SeriesEntity?> = _openedSeries.asStateFlow()

    // The series whose episode queue is currently playing — drives the player HUD's favorite toggle
    // (distinct from _openedSeries, which tracks the browse/detail selection).
    private val _playingSeries = MutableStateFlow<SeriesEntity?>(null)
    val playingSeries: StateFlow<SeriesEntity?> = _playingSeries.asStateFlow()

    // --- Download status for poster-panel strips (display-only) ---

    /** Active episode-download rows keyed by episode id — for the focused-episode strip. */
    val episodeDownloadStates: StateFlow<Map<Long, DownloadEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else downloadManager.observe(c.profileId) }
        .map { list -> list.filter { it.mediaType == MediaType.EPISODE }.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** All episode downloads for the grid-selected series (entire-series aggregate strip). */
    val selectedSeriesDownloads: StateFlow<List<DownloadEntity>> = _selectedSeries
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else downloadManager.observeForSeries(s.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All episode downloads for the opened series (aggregate strip inside the episode view). */
    val openedSeriesDownloads: StateFlow<List<DownloadEntity>> = _openedSeries
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else downloadManager.observeForSeries(s.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _lastPlayedEpisodeId = MutableStateFlow<Long?>(null)
    val lastPlayedEpisodeId: StateFlow<Long?> = _lastPlayedEpisodeId.asStateFlow()

    private val _episodesLoading = MutableStateFlow(false)
    val episodesLoading: StateFlow<Boolean> = _episodesLoading.asStateFlow()

    /**
     * The queue item the player is actually on, pinned when it starts playing — the only thing a
     * resume position may be written against.
     *
     * Progress used to be matched by searching the *opened* series' episode list for the player's
     * current URL, and the profile was read at save time. Both were wrong: open a different series
     * while an episode plays and the match silently stopped saving, a Stalker episode URL is minted
     * per play and never matched at all, and switching profile mid-episode wrote the position into
     * the new profile's Continue Watching.
     */
    private data class PlayingEpisodeRef(val episode: EpisodeEntity, val profileId: Long, val contentKey: String?)

    private var playingEpisodeRef: PlayingEpisodeRef? = null

    init {
        // Periodically persist the playing episode's resume position (same cadence as movies).
        // Episodes were previously *read* on play but never saved — resume never actually worked.
        // This is the crash backstop; the real saves are on pause and on leaving the player.
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                saveEpisodeProgressNow()
            }
        }
        // Save on pause too — pausing and walking away otherwise loses up to 10s, and everything
        // since the last tick if the app is killed while paused.
        viewModelScope.launch {
            var wasPlaying = false
            player.isPlaying.collect { playing ->
                if (wasPlaying && !playing) saveEpisodeProgressNow()
                wasPlaying = playing
            }
        }
        // Auto-play continuation across seasons: the player advances within a season itself, then signals
        // here when a season's last episode finishes so we can start the next season's first episode.
        viewModelScope.launch {
            player.queueEnded.collect { continueToNextSeason() }
        }
        // In-season advance (auto-next / HUD prev-next) happens inside the player — re-point the
        // subtitle context at the NEW episode (subtitle plan Phase 5), else a subtitle search or §9
        // restore mid-episode-2 would still target episode 1. Index into the queue this VM submitted.
        viewModelScope.launch {
            player.queueItemChanged.collect { index ->
                val q = playingQueue ?: return@collect
                val ep = q.episodes.getOrNull(index) ?: return@collect
                // The auto-advanced episode is now the one progress is saved against. No flush of the
                // outgoing one here: the player already loaded the new item, so its position has been
                // reset and saving now would overwrite a finished episode with ~0.
                playingEpisodeRef = PlayingEpisodeRef(
                    ep, q.profileId, tv.own.owntv.core.player.enginePinKey(q.show.sourceId, "EPISODE", ep.remoteId),
                )
                subtitleController.setEpisode(q.profileId, q.show, ep, q.parentTmdbId)
            }
        }
    }

    /** The episode queue currently loaded into the player, for mapping its index signals back to
     *  episodes (subtitle context on auto-advance). Replaced on every [playEpisodeQueue]. */
    private data class PlayingQueue(
        val show: SeriesEntity,
        val episodes: List<EpisodeEntity>,
        val profileId: Long,
        val parentTmdbId: Long?,
    )

    private var playingQueue: PlayingQueue? = null

    /** A season's last episode finished with auto-play on — start the next season's first episode, if any.
     *  Matches the just-finished episode by its stream URL (robust to in-season auto-advance). */
    private fun continueToNextSeason() {
        val url = player.currentMediaUrl ?: return
        val all = episodes.value
        val finished = all.firstOrNull { it.streamUrl == url } ?: return // not one of this series' episodes
        val nextEpisode = all
            .filter { it.seasonNumber == finished.seasonNumber + 1 }
            .minByOrNull { it.episodeNumber } ?: return // no next season — series finished
        playEpisode(nextEpisode)
    }

    /**
     * True while the player still holds the episode [ref] was pinned for. Matches on the stable engine
     * pin key; rows with no `remoteId` have no such key and fall back to the stream URL, which for
     * those rows is exactly as stable as it always was (see `enginePinKey`).
     */
    private fun playerIsOn(ref: PlayingEpisodeRef): Boolean =
        if (ref.contentKey != null) player.currentMediaContentKey == ref.contentKey
        else player.currentMediaUrl != null && player.currentMediaUrl == ref.episode.streamUrl

    /** Saves the position of the episode the player is actually on — including one the player itself
     *  switched to via prev/next or auto-advance, which re-pins the ref in [init]. */
    fun saveEpisodeProgressNow() {
        val ref = playingEpisodeRef ?: return
        val ep = ref.episode
        if (player.isLiveContent || !playerIsOn(ref)) return
        val pos = player.position.value
        val dur = player.duration.value
        if (pos > 0 && dur > 0) {
            viewModelScope.launch {
                // The position belongs to the profile that started playback. If the user switched
                // profiles mid-episode, drop it rather than writing it into the new profile's list.
                val pid = currentProfileId() ?: return@launch
                if (pid != ref.profileId) return@launch
                Log.d(TAG, "saveEpisodeProgressNow episodeId=${ep.id} profile=$pid positionMs=$pos durationMs=$dur")
                runCatching {
                    progressDao.save(
                        PlaybackProgressEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = ep.id, positionMs = pos, durationMs = dur),
                    )
                }.onFailure { t ->
                    Log.w(TAG, "saveEpisodeProgressNow progress save failed episodeId=${ep.id} profile=$pid", t)
                }
                launcherIntegrationRepository.publishEpisodeProgress(pid, ep.id, pos, dur)
            }
        }
    }

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else combine(
                categoryDao.observe(c.sourceIds, MediaType.SERIES),
                customize.observe(c.profileId, MediaType.SERIES),
                sortMode,
                profileDao.observeById(c.profileId),
            ) { cats, cust, sort, profile ->
                // A–Z also sorts the category folders (custom categories included); manually moved
                // categories stay pinned first. Custom categories ride the SAME customization keys,
                // so renames/hides/reorders apply to them with no extra code (#87).
                val kids = profile?.isKids == true
                val visibleCats = if (kids) cats.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) } else cats
                val visibleCustoms = if (kids) cust.customCategories.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) } else cust.customCategories
                val folders = visibleCats.applyCustomizationsWithCustoms(cust, visibleCustoms, alphaRest = sort == SettingsRepository.SortMode.ALPHA)
                val multiSourceNames = c.sourceNames.takeIf { it.size > 1 }.orEmpty()
                val categoriesById = visibleCats.associateBy { it.id }
                defaultRail + folders.map { e ->
                    LiveRailItem(
                        key = e.categoryId?.let { LiveKey.Folder(it) } ?: LiveKey.Custom(e.customId!!),
                        title = e.displayName,
                        providerName = e.categoryId
                            ?.let(categoriesById::get)
                            ?.sourceId
                            ?.let(multiSourceNames::get),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultRail)

    val series: Flow<PagingData<SeriesEntity>> = combine(
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
                val cust = cs.cust
                val movedFrom = cust.movedFromOrigin
                if (cust.hiddenItems.isEmpty() && cust.itemNames.isEmpty() && cs.hiddenCats.isEmpty() && movedFrom.isEmpty()) paging
                else paging.filter { s ->
                    CustomizeKeys.series(s) !in cust.hiddenItems &&
                        (s.categoryId == null || s.categoryId !in cs.hiddenCats) &&
                        // Moved-out items leave ONLY their origin folder (they stay in All/search).
                        (movedFrom[CustomizeKeys.series(s)]?.let { origin ->
                            args.key !is LiveKey.Folder || origin != folderContextKeys.value[args.key.id]
                        } ?: true)
                }.map { s ->
                    // Bulk-renamed titles (Customize items screen) show here like Live TV does.
                    cust.itemNames[CustomizeKeys.series(s)]?.let { s.copy(name = it) } ?: s
                }
            }
        }
        .cachedIn(viewModelScope)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode)

    val count: StateFlow<Int> = combine(_selected, ctx, hiddenCategoryIds) { key, c, hidden -> Triple(key, c, hidden) }
        .flatMapLatest { (key, c, hidden) -> countFlow(key, c, hidden).throttleLatest() } // C2: cap live COUNT re-runs during bulk sync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.SERIES) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val episodes: StateFlow<List<EpisodeEntity>> = _openedSeries
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else seriesDao.episodesBySeries(s.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Per-episode resume progress for the open series (keyed by episode id). Reactive so the UI's watched
     *  indicators, season counts, "Hide watched" filter, and "Next up" card update the instant a position
     *  is saved — no manual refresh needed. Re-seeds when the profile or open series changes. */
    val episodeProgress: StateFlow<Map<Long, PlaybackProgressEntity>> =
        combine(ctx, _openedSeries) { c, s -> c to s }
            .flatMapLatest { (c, s) ->
                if (c.profileId < 0 || s == null) flowOf(emptyList())
                else progressDao.observeSeriesEpisodeProgress(c.profileId, s.id)
            }
            .map { list -> list.associateBy { it.itemId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Episode ids in the open series that have been watched to ≥95% — drives ✓ marks, season "x/y" counts,
     *  and the "Hide watched" filter. */
    val completedEpisodeIds: StateFlow<Set<Long>> = episodeProgress
        .map { prog -> prog.values.filter { isEpisodeCompleted(it) }.map { it.itemId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** The episode to surface as the "Next up" Play card: the last-watched one if still in progress (resume),
     *  the first episode after a completed one, the first episode when nothing's been watched yet, or null
     *  once the whole series is finished (card hides). Mirrors LauncherRecommendationPlanner's CONTINUE/NEXT
     *  logic (threshold 0.95). */
    val nextUpEpisodeId: StateFlow<Long?> = combine(episodes, episodeProgress) { eps, prog ->
        if (eps.isEmpty()) null
        else {
            val ordered = eps.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
            val lastWatched = prog.values.maxByOrNull { it.updatedAt }
            when {
                lastWatched == null -> ordered.first().id
                isEpisodeCompleted(lastWatched) -> {
                    val idx = ordered.indexOfFirst { it.id == lastWatched.itemId }
                    if (idx in 0 until ordered.size - 1) ordered[idx + 1].id else null
                }
                else -> ordered.firstOrNull { it.id == lastWatched.itemId }?.id
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Hide watched" toggle for the episode list (off by default). */
    private val _hideWatched = MutableStateFlow(false)
    val hideWatched: StateFlow<Boolean> = _hideWatched.asStateFlow()
    fun setHideWatched(value: Boolean) { _hideWatched.value = value }

    /** ≥95% of duration watched = completed (mirrors LauncherRecommendationPlanner.isCompleted). */
    private fun isEpisodeCompleted(p: PlaybackProgressEntity): Boolean =
        p.durationMs > 0 && p.positionMs >= (p.durationMs * 0.95f).toLong()

    /** Mark an episode as watched (shows ✓) without playing it. A 1ms/1ms sentinel satisfies the ≥95%
     *  completed rule while keeping Play restarting from ~0 (NOT the end) — a real positionMs=durationMs
     *  would make AUTO/ASK resume jump to the credits. Replaces any existing resume position; the fresh
     *  updatedAt also re-orders "next up" past it. Reactive, so the ✓ appears immediately. */
    fun markEpisodeWatched(episode: EpisodeEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            progressDao.save(
                PlaybackProgressEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = episode.id, positionMs = 1L, durationMs = 1L),
            )
        }
    }

    /** Mark an episode as unwatched — clears its resume position (removes the ✓ and any progress bar). */
    fun markEpisodeUnwatched(episode: EpisodeEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            progressDao.clear(pid, MediaType.EPISODE, episode.id)
        }
    }

    fun select(key: LiveKey) { _selected.value = key }
    fun setSearchQuery(query: String) { _search.value = query }
    fun onSeriesFocused(s: SeriesEntity) { _selectedSeries.value = s }

    /**
     * Manual "Refetch TMDB details" (plan §11.2 U5a): clear this series' cached match/details (incl. a 7-day
     * negative cache) and re-trigger [metadata.resolveSeries] for the focused series via the series meta tick.
     */
    fun refetchSeriesMeta(series: SeriesEntity) {
        viewModelScope.launch {
            runCatching { metadata.clearSeries(series) }
            _seriesMetaTick.value++
        }
    }

    /**
     * Prefill for the "Set TMDB name" dialog (plan §11.2 U5b): the saved override if any, else the cleaned
     * provider title. [hasOverride] drives the dialog's Clear button. Episodes inherit the series match, so
     * the override lives at the series level (no separate episode override).
     */
    data class TmdbNamePrefill(val title: String, val year: Int?, val hasOverride: Boolean)

    suspend fun seriesTmdbNamePrefill(series: SeriesEntity): TmdbNamePrefill {
        metadata.seriesOverride(series)?.let { return TmdbNamePrefill(it.title, it.year, hasOverride = true) }
        val norm = tv.own.owntv.core.metadata.TitleNormalizer.normalize(series.name)
        return TmdbNamePrefill(norm.query, series.year ?: norm.year, hasOverride = false)
    }

    /** Save the hand-typed override and force a re-resolve under the new query (plan §11.2 U5b). */
    fun setSeriesTmdbName(series: SeriesEntity, title: String, year: Int?) {
        viewModelScope.launch {
            runCatching { metadata.setSeriesOverride(series, title, year) }
            _seriesMetaTick.value++
        }
    }

    /** Remove the override and re-resolve with the cleaned provider title (plan §11.2 U5b). */
    fun clearSeriesTmdbName(series: SeriesEntity) {
        viewModelScope.launch {
            runCatching { metadata.clearSeriesOverride(series) }
            _seriesMetaTick.value++
        }
    }

    /**
     * Season/episode order for the OPEN series (the "Sorting" popup). Per profile and per series,
     * backed by [SeriesSortOrderDao]; a show the user never changed reports [SeriesOrder.DEFAULT].
     *
     * PRESENTATION ONLY — playback (autoplay next episode) always runs in episode-number order.
     */
    data class SeriesOrder(val seasonsDescending: Boolean = false, val episodesDescending: Boolean = false) {
        companion object { val DEFAULT = SeriesOrder() }
    }

    val seriesOrder: StateFlow<SeriesOrder> = combine(ctx, _openedSeries) { c, s -> c.profileId to s }
        .flatMapLatest { (profileId, show) ->
            if (show == null || profileId < 0) flowOf(SeriesOrder.DEFAULT)
            else seriesSortOrderDao.observe(profileId, show.id).map { row ->
                if (row == null) SeriesOrder.DEFAULT
                else SeriesOrder(row.seasonsDescending, row.episodesDescending)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesOrder.DEFAULT)

    /** Applied immediately from the popup; writes one upserted row for the open series. */
    fun setSeriesOrder(seasonsDescending: Boolean, episodesDescending: Boolean) {
        val show = _openedSeries.value ?: return
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            seriesSortOrderDao.setOrder(pid, show.id, seasonsDescending, episodesDescending)
        }
    }

    fun selectSeason(season: Int) { _selectedSeason.value = season }

    // --- Episode enrichment (U3): the focused episode's TMDB still/plot/rating for the right detail pane ---
    private val _selectedEpisode = MutableStateFlow<EpisodeEntity?>(null)
    val selectedEpisode: StateFlow<EpisodeEntity?> = _selectedEpisode.asStateFlow()
    fun onEpisodeFocused(ep: EpisodeEntity) { _selectedEpisode.value = ep }

    /**
     * Manual "Refetch TMDB details" (plan §11.2 U5a): clear this episode's cache AND its show's match (so an
     * episode whose show was negative-cached also recovers), then re-trigger [metadata.resolveEpisode] for the
     * focused episode via the episode meta tick.
     */
    fun refetchEpisodeMeta(series: SeriesEntity, episode: EpisodeEntity) {
        viewModelScope.launch {
            runCatching { metadata.clearEpisode(series, episode) }
            _episodeMetaTick.value++
        }
    }

    /** TMDB metadata for the focused episode, tagged with its id to avoid stale meta during the debounce.
     *  Resolved lazily against the currently opened show. */
    /** Bumped by [refetchEpisodeMeta] to force the focused episode's TMDB resolve to re-run after clearing its cache. */
    private val _episodeMetaTick = MutableStateFlow(0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedEpisodeMeta: StateFlow<EpisodeMeta?> = combine(_selectedEpisode, _episodeMetaTick) { ep, tick -> ep to tick }
        .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second }
        // See MetadataRepository.FOCUS_DEBOUNCE_MS — episode lists are the fastest thing to scroll.
        .debounce(tv.own.owntv.core.metadata.MetadataRepository.FOCUS_DEBOUNCE_MS)
        .mapLatest { (ep, _) ->
            val show = _openedSeries.value
            if (ep == null || show == null) null
            else EpisodeMeta(ep.id, runCatching { metadata.resolveEpisode(show, ep) }.getOrNull())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class EpisodeMeta(val episodeId: Long, val cache: tv.own.owntv.core.database.entity.MetadataCacheEntity?)

    /** Whether the episode area draws as text rows or a wall of stills. Global, see SettingsRepository. */
    val episodeViewMode: StateFlow<SettingsRepository.VodViewMode> = settings.episodeViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.VodViewMode.LIST)

    fun setEpisodeViewMode(mode: SettingsRepository.VodViewMode) {
        viewModelScope.launch { settings.setEpisodeViewMode(mode) }
    }

    /**
     * TMDB rows for EVERY episode of the active season, keyed by local episode id — the grid needs all
     * of them at once, unlike the list which only ever shows the focused episode's still.
     *
     * Only collected in grid mode, and only after [GRID_DWELL_MS]: opening a show and immediately
     * pressing Back should cost nothing, and leaving cancels the fetch outright because `mapLatest`
     * tears down the previous coroutine. One request covers the whole season (see
     * `MetadataRepository.resolveSeasonEpisodes`).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val seasonEpisodeMeta: StateFlow<Map<Long, tv.own.owntv.core.database.entity.MetadataCacheEntity>> =
        combine(_openedSeries, _selectedSeason, episodes, episodeViewMode, _episodeMetaTick) { show, season, eps, mode, _ ->
            if (show == null || mode != SettingsRepository.VodViewMode.GRID) {
                null
            } else {
                // Mirror the screen's own fallback: a show whose seasons start at 0 or 2 displays its
                // first season, and fetching the requested-but-absent season would leave the grid blank.
                val available = eps.map { it.seasonNumber }.distinct().sorted()
                val active = if (available.contains(season)) season else available.firstOrNull() ?: season
                show to eps.filter { it.seasonNumber == active }
            }
        }
            .distinctUntilChanged { a, b ->
                a?.first?.id == b?.first?.id && a?.second?.map { it.id } == b?.second?.map { it.id }
            }
            .flatMapLatest { pair ->
                kotlinx.coroutines.flow.flow {
                    if (pair == null || pair.second.isEmpty()) {
                        emit(emptyMap())
                        return@flow
                    }
                    // 1. Whatever is already cached, immediately. Coming back to a season you have
                    //    already opened must not blank the tiles while a timer runs — that reads as
                    //    the pictures reloading, which is what the dwell used to cause on EVERY switch.
                    val cached = runCatching { metadata.cachedSeasonEpisodes(pair.first, pair.second) }
                        .getOrDefault(emptyMap())
                    emit(cached)
                    // 2. Nothing more to do when the season is already complete — no timer, no request.
                    if (cached.size >= pair.second.size) return@flow
                    // 3. Only an INCOMPLETE season waits out the dwell before going to the network, so
                    //    a show opened by accident still costs nothing.
                    kotlinx.coroutines.delay(GRID_DWELL_MS)
                    emit(
                        runCatching { metadata.resolveSeasonEpisodes(pair.first, pair.second) }
                            .getOrDefault(cached),
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun openSeries(s: SeriesEntity) {
        _openedSeries.value = s
        _selectedSeason.value = 1 // reset season when opening a different show
        _lastPlayedEpisodeId.value = null
        Log.d(TAG, "openSeries seriesId=${s.id} profile=${ctx.value.profileId}")
        viewModelScope.launch {
            _episodesLoading.value = true
            seriesRepository.loadEpisodes(s)
            // Jump to where you left off: seed the last-watched episode (and its season) from saved progress
            // BEFORE clearing loading, so the screen's focus effect lands on it instead of episode 1 (#22).
            val eps = seriesDao.episodesBySeries(s.id).first()
            val lastEp = progressDao.lastWatchedEpisodeId(ctx.value.profileId, s.id)
                ?.let { id -> eps.firstOrNull { it.id == id } }
            if (lastEp != null) {
                _selectedSeason.value = lastEp.seasonNumber
                _lastPlayedEpisodeId.value = lastEp.id
            }
            _episodesLoading.value = false
        }
    }

    fun openSeriesById(seriesId: Long) {
        viewModelScope.launch {
            val show = seriesDao.getSeriesById(seriesId) ?: return@launch
            openSeries(show)
        }
    }

    fun playFromHome(seriesId: Long, episodeId: Long, startPositionMs: Long = 0) {
        viewModelScope.launch {
            playFromHomeAsync(seriesId, episodeId, startPositionMs)
        }
    }

    suspend fun playFromHomeAsync(seriesId: Long, episodeId: Long, startPositionMs: Long = 0): Boolean {
        val episode = seriesDao.getEpisodeById(episodeId) ?: return false
        val showId = if (seriesId > 0) seriesId else episode.seriesId
        val show = seriesDao.getSeriesById(showId) ?: return false
        if (episode.seriesId != show.id) return false
        val pid = currentProfileId() ?: return false
        if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, show.categoryId, profileDao, categoryDao)) return false
        seriesRepository.loadEpisodes(show)
        val queue = seriesDao.episodesBySeries(show.id).first()
        if (queue.isEmpty()) return false
        playEpisodeQueue(show, queue, episode, startPositionMs)
        return true
    }

    fun closeSeries() {
        _openedSeries.value = null
    }

    /** The user's resume preference (Always / Ask / Never) — the screen drives the prompt. */
    val resumeMode: StateFlow<SettingsRepository.ResumeMode> = settings.resumeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.ResumeMode.ASK)

    /** Saved resume position for [episode] (0 when none) — used by the screen to decide the prompt. */
    suspend fun savedPositionMs(episode: EpisodeEntity): Long =
        currentProfileId()?.let { progressDao.get(it, MediaType.EPISODE, episode.id)?.positionMs ?: 0 } ?: 0

    /** Global "External player" toggle — screens must NOT open the fullscreen in-app player when on
     *  (mounting it spins up an mpv instance even though playback branched to the external app). */
    val externalPlayerOn: StateFlow<Boolean> = settings.externalPlayerSeries
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Phase B: long-press "Play with external player" — always external, regardless of the global toggle. */
    /** Stalker episodes resolve to a real URL at play time (create_link, series=<ep>); anything else
     *  returns streamUrl as-is. Null = resolve failed — the caller should not start playback. */
    private suspend fun resolvedEpisodeUrlOrNull(episode: EpisodeEntity): String? {
        val show = seriesDao.getSeriesById(episode.seriesId)
        val source = show?.let { sourceDao.getById(it.sourceId) }
        if (!streamUrlResolver.needsResolve(source)) return episode.streamUrl
        return try {
            streamUrlResolver.resolve(source!!, episode.streamUrl, vod = true, episode = episode.episodeNumber)
        } catch (e: Exception) {
            Log.w(TAG, "stalker resolve failed episodeId=${episode.id}", e)
            null
        }
    }

    /** The source User-Agent behind an episode — the external player needs it as an intent extra. */
    private suspend fun episodeSourceUa(episode: EpisodeEntity): String? =
        seriesDao.getSeriesById(episode.seriesId)?.let { sourceDao.getById(it.sourceId) }?.userAgent

    fun playEpisodeExternal(episode: EpisodeEntity) {
        _lastPlayedEpisodeId.value = episode.id
        viewModelScope.launch {
            val pid = currentProfileId()
            val show = seriesDao.getSeriesById(episode.seriesId) ?: return@launch
            if (pid != null && !tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, show.categoryId, profileDao, categoryDao)) return@launch
            Log.d(TAG, "playEpisodeExternal episodeId=${episode.id}")
            val url = resolvedEpisodeUrlOrNull(episode) ?: return@launch
            externalPlayerLauncher.launch(
                url = url,
                title = episode.name.takeIf { it.isNotBlank() },
                subtitle = show.name,
                userAgent = episodeSourceUa(episode),
                httpHeaders = episode.httpHeaders,
            )
            if (pid != null) {
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = episode.id))
                }.onFailure { t -> Log.w(TAG, "external play episode history record failed episodeId=${episode.id} profile=$pid", t) }
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = episode.seriesId))
                }.onFailure { t -> Log.w(TAG, "external play series history record failed seriesId=${episode.seriesId} profile=$pid", t) }
            }
        }
    }

    fun playEpisode(episode: EpisodeEntity, startPositionMs: Long = 0) {
        val show = _openedSeries.value ?: return
        val seasonEpisodes = episodes.value
            .filter { it.seasonNumber == episode.seasonNumber }
            .sortedBy { it.episodeNumber }
        playEpisodeQueue(show, seasonEpisodes, episode, startPositionMs)
    }

    fun playEpisodeQueue(show: SeriesEntity, queue: List<EpisodeEntity>, episode: EpisodeEntity, startPositionMs: Long = 0) {
        _openedSeries.value = show
        _playingSeries.value = show
        _lastPlayedEpisodeId.value = episode.id
        viewModelScope.launch {
            val pid = currentProfileId()
            if (pid != null && !tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, show.categoryId, profileDao, categoryDao)) return@launch
            // External player (global toggle): launch only the selected episode (external players are
            // single-item — no prev/next queue). History is still recorded; resume position and the
            // in-app HUD/progress tick are not, since OwnTV can't observe the external app.
            // #115 — a protected item cannot go to an external player: no standard intent extra
            // carries a licence URL, so the other app would open it and fail on the first segment.
            // Play it here instead, where the licence request can actually be made.
            if (settings.externalPlayerSeries.first() && episode.drmConfig == null) {
                Log.d(TAG, "playEpisodeQueue seriesId=${show.id} episodeId=${episode.id} -> external player")
                val url = resolvedEpisodeUrlOrNull(episode) ?: return@launch
                externalPlayerLauncher.launch(
                    url = url,
                    title = episode.name.takeIf { it.isNotBlank() },
                    subtitle = show.name,
                    userAgent = sourceDao.getById(show.sourceId)?.userAgent,
                    httpHeaders = episode.httpHeaders,
                )
                if (pid != null) {
                    runCatching {
                        historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = episode.id))
                    }.onFailure { t -> Log.w(TAG, "external play episode history record failed episodeId=${episode.id} profile=$pid", t) }
                    runCatching {
                        historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = episode.seriesId))
                    }.onFailure { t -> Log.w(TAG, "external play series history record failed seriesId=${episode.seriesId} profile=$pid", t) }
                }
                return@launch
            }
            val startIndex = queue.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
            Log.d(TAG, "playEpisodeQueue seriesId=${show.id} episodeId=${episode.id} profile=$pid queue=${queue.size} startIndex=$startIndex startPositionMs=$startPositionMs")
            val source = sourceDao.getById(show.sourceId)
            val sourceUa = source?.userAgent
            // Stalker (D-2): every queue item resolves its playable URL right before IT loads — the
            // stored streamUrl is the season cmd (shared by the whole season), and the per-episode
            // create_link URL is short-lived, so next/prev/autoplay must each mint a fresh one.
            val needsResolve = streamUrlResolver.needsResolve(source)
            playingEpisodeRef = pid?.let {
                PlayingEpisodeRef(
                    episode, it, tv.own.owntv.core.player.enginePinKey(show.sourceId, "EPISODE", episode.remoteId),
                )
            }
            player.playEpisodes(
                items = queue.map { ep ->
                    PlaylistItem(
                        url = ep.streamUrl,
                        meta = MediaMeta(
                            title = ep.name.takeIf { it.isNotBlank() },
                            subtitle = show.name,
                            seasonNumber = ep.seasonNumber,
                            episodeNumber = ep.episodeNumber,
                            // P6 — engine pins key on this, not on the URL: for Stalker the queue's
                            // stored URL is the shared season cmd and the played URL is minted per item.
                            contentKey = tv.own.owntv.core.player.enginePinKey(show.sourceId, "EPISODE", ep.remoteId),
                        ),
                        resolveUrl = if (needsResolve && source != null) {
                            { streamUrlResolver.resolve(source, ep.streamUrl, vod = true, episode = ep.episodeNumber) }
                        } else null,
                        httpHeaders = ep.httpHeaders,
                        drmConfig = ep.drmConfig,
                    )
                },
                startIndex = startIndex,
                startPositionMs = startPositionMs,
                userAgent = sourceUa,
            )
            // Enable the player's OpenSubtitles search for this episode (subtitle plan §4). The parent
            // series' TMDB id gives the strongest episode match (review R7) when metadata is available.
            if (pid != null) {
                val parentTmdbId = runCatching { metadata.resolveSeries(show)?.tmdbId?.toLong() }.getOrNull()
                subtitleController.setEpisode(pid, show, episode, parentTmdbId)
                // Remember the queue so player-driven advances re-point the context (Phase 5, init).
                playingQueue = PlayingQueue(show, queue, pid, parentTmdbId)
            }
            if (pid != null) {
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = episode.id))
                }.onFailure { t ->
                    Log.w(TAG, "playEpisodeQueue episode history record failed episodeId=${episode.id} profile=$pid", t)
                }
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = episode.seriesId))
                }.onFailure { t ->
                    Log.w(TAG, "playEpisodeQueue series history record failed seriesId=${episode.seriesId} profile=$pid", t)
                }
            }
        }
    }

    /** Downloaded OpenSubtitles subtitles for an episode (long-press "Delete subtitles" popup, §11).
     *  Uses the currently open series as the parent show for the content key. */
    suspend fun downloadedSubtitles(episode: EpisodeEntity): List<tv.own.owntv.core.database.dao.LinkedSubtitle> {
        val show = _openedSeries.value ?: return emptyList()
        return subtitleController.downloadsForEpisode(show, episode)
    }

    fun deleteSubtitle(cacheId: Long) {
        viewModelScope.launch { subtitleController.deleteCached(cacheId) }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    /** Download states for the open show's episodes, keyed by episode id. */
    val episodeDownloads: StateFlow<Map<Long, DownloadEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else downloadManager.observe(c.profileId) }
        .map { list -> list.filter { it.mediaType == MediaType.EPISODE }.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun downloadEpisode(episode: EpisodeEntity) {
        val show = _openedSeries.value
        val showDir = StorageAccess.sanitize(show?.name ?: "Series")
        val ext = episode.containerExt ?: StorageAccess.extOf(episode.streamUrl)
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val actualShow = show ?: seriesDao.getSeriesById(episode.seriesId)
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, actualShow?.categoryId, profileDao, categoryDao)) return@launch
            downloadManager.enqueue(
                profileId = pid,
                mediaType = MediaType.EPISODE,
                itemId = episode.id,
                title = episode.name.takeIf { it.isNotBlank() } ?: show?.name.orEmpty(),
                posterUrl = show?.posterUrl,
                streamUrl = episode.streamUrl,
                relativeDir = "Series/$showDir/Season ${episode.seasonNumber}",
                fileName = "${StorageAccess.sanitize(episode.name.ifBlank { "episode-${episode.episodeNumber}" })}.$ext",
            )
        }
    }

    fun downloadSeries(series: SeriesEntity) {
        val showDir = StorageAccess.sanitize(series.name)
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, series.categoryId, profileDao, categoryDao)) return@launch
            seriesDao.episodesBySeries(series.id).first().forEach { ep ->
                val ext = ep.containerExt ?: StorageAccess.extOf(ep.streamUrl)
                downloadManager.enqueue(
                    profileId = pid,
                    mediaType = MediaType.EPISODE,
                    itemId = ep.id,
                    title = ep.name.takeIf { it.isNotBlank() } ?: series.name,
                    posterUrl = series.posterUrl,
                    streamUrl = ep.streamUrl,
                    relativeDir = "Series/$showDir/Season ${ep.seasonNumber}",
                    fileName = "${StorageAccess.sanitize(ep.name.ifBlank { "episode-${ep.episodeNumber}" })}.$ext",
                )
            }
        }
    }

    fun toggleFavorite(s: SeriesEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteIds.value.contains(s.id)) favoriteDao.remove(pid, MediaType.SERIES, s.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = s.id))
            refreshList() // the Favorites category uses a manual PagingSource — force a rebuild
        }
    }

    /** Hide a category by its rail key (undo via Settings → Customize). */
    fun hideCategory(key: LiveKey) {
        if (key !is LiveKey.Folder && key !is LiveKey.Custom) return
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val customizationKey = when (key) {
                is LiveKey.Folder -> categoryDao.getById(key.id)?.let { CustomizeKeys.category(it) }
                is LiveKey.Custom -> key.id
            } ?: return@launch
            customize.setCategoryHidden(pid, MediaType.SERIES, customizationKey, true)
        }
    }

    fun enterCategoryMoveMode(key: LiveKey) {
        if (key !is LiveKey.Folder && key !is LiveKey.Custom) return
        viewModelScope.launch {
            val c = ctx.first { it.profileId >= 0 }
            val pid = c.profileId
            val type = MediaType.SERIES
            val cats = categoryDao.observe(c.sourceIds, type).first()
            val cust = customize.observe(pid, type).first()
            val sort = sortMode.value
            val profile = profileDao.getById(pid)

            val kids = profile?.isKids == true
            val visibleCats = if (kids) cats.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) } else cats
            val visibleCustoms = if (kids) cust.customCategories.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) } else cust.customCategories

            val targetKey = when (key) {
                is LiveKey.Folder -> categoryDao.getById(key.id)?.let { CustomizeKeys.category(it) }
                is LiveKey.Custom -> key.id
            } ?: return@launch

            // Filter hidden categories so reordering exactly mirrors applyCustomizationsWithCustoms.
            val filteredCats = if (cust.hiddenCategories.isEmpty()) visibleCats else visibleCats.filter { CustomizeKeys.category(it) !in cust.hiddenCategories }
            val filteredCustoms = visibleCustoms.filter { it.id !in cust.hiddenCategories }

            val orderIndex = cust.categoryOrder.withIndex().associate { (i, k) -> k to i }
            val entries = filteredCustoms.map { cc ->
                cc.id to (cust.categoryNames[cc.id] ?: cc.name)
            } + filteredCats.map { cat ->
                val k = CustomizeKeys.category(cat)
                k to (cust.categoryNames[k] ?: cat.name)
            }

            val alpha = sort == SettingsRepository.SortMode.ALPHA
            val (pinned, rest) = entries.partition { it.first in orderIndex }
            val sortedList = pinned.sortedBy { orderIndex.getValue(it.first) } +
                (if (alpha) rest.sortedBy { it.second.lowercase() } else rest)

            val fullKeys = sortedList.map { it.first }
            val entryIdx = fullKeys.indexOf(targetKey)
            if (entryIdx < 0) return@launch

            _categoryMoveState.value = CategoryMoveState(
                items = sortedList.map { it.second },
                keys = fullKeys,
                activeIndex = entryIdx,
                targetKey = targetKey,
            )
        }
    }

    fun moveCategoryUp() {
        val s = _categoryMoveState.value ?: return
        if (s.activeIndex <= 0) return
        val items = s.items.toMutableList()
        val keys = s.keys.toMutableList()
        val i = s.activeIndex
        items[i - 1] = s.items[i]; items[i] = s.items[i - 1]
        keys[i - 1] = s.keys[i]; keys[i] = s.keys[i - 1]
        _categoryMoveState.value = s.copy(items = items, keys = keys, activeIndex = i - 1)
    }

    fun moveCategoryDown() {
        val s = _categoryMoveState.value ?: return
        if (s.activeIndex >= s.items.lastIndex) return
        val items = s.items.toMutableList()
        val keys = s.keys.toMutableList()
        val i = s.activeIndex
        items[i + 1] = s.items[i]; items[i] = s.items[i + 1]
        keys[i + 1] = s.keys[i]; keys[i] = s.keys[i + 1]
        _categoryMoveState.value = s.copy(items = items, keys = keys, activeIndex = i + 1)
    }

    fun commitCategoryMove() {
        val s = _categoryMoveState.value ?: return
        _categoryMoveState.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setCategoryOrder(pid, MediaType.SERIES, s.keys)
        }
    }

    fun cancelCategoryMove() {
        _categoryMoveState.value = null
    }

    fun enterMoveMode(series: SeriesEntity, key: LiveKey) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val contextKey = when (key) {
                is LiveKey.Folder -> folderContextKeys.value[key.id] ?: return@launch
                is LiveKey.Custom -> key.id
                LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                else -> return@launch
            }
            val items = when (key) {
                is LiveKey.Folder -> seriesDao.snapshotByCategoryManual(key.id, pid, contextKey, 5000)
                is LiveKey.Custom -> customCategoryDao.snapshotSeries(pid, key.id, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                LiveKey.Favorites -> seriesDao.snapshotFavoritesManual(pid, contextKey, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                LiveKey.History, LiveKey.All -> return@launch
            }
            val idx = items.indexOfFirst { it.id == series.id }
            if (idx < 0) return@launch
            _moveState.value = SeriesMoveState(items, idx, contextKey)
            // Manual order is only visible in playlist order, so Move switches the list to it — but that
            // is a means, not a choice the user made. Remember what they had so Cancel can put it back.
            sortBeforeMove = sortMode.value
            settings.setSortSeries(SettingsRepository.SortMode.PLAYLIST)
        }
    }

    /** The sort the user was on before [enterMoveMode] switched the list to playlist order. */
    private var sortBeforeMove: SettingsRepository.SortMode? = null

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
        sortBeforeMove = null // the new order IS playlist order — staying on it is the point
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            contentOrderDao.replaceContext(
                profileId = pid,
                type = MediaType.SERIES,
                contextKey = s.contextKey,
                rows = s.items.mapIndexed { i, ser ->
                    ContentOrderEntity(profileId = pid, mediaType = MediaType.SERIES, contextKey = s.contextKey, itemId = ser.id, position = i)
                },
            )
        }
    }

    fun cancelMove() {
        _moveState.value = null
        // Cancel means nothing changed — including the sort Move switched away from.
        val previous = sortBeforeMove ?: return
        sortBeforeMove = null
        if (previous != SettingsRepository.SortMode.PLAYLIST) {
            viewModelScope.launch { settings.setSortSeries(previous) }
        }
    }

    /** Hide the series from all lists (undo via Settings → Customize Category → Hidden items). */
    fun hideSeries(series: SeriesEntity) {
        if (_selectedSeries.value?.id == series.id) _selectedSeries.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setItemHidden(pid, MediaType.SERIES, CustomizeKeys.series(series), series.name, true)
        }
    }

    fun removeFromHistory(seriesId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            historyDao.remove(pid, MediaType.SERIES, seriesId)
            // A show is watched one episode at a time, so its trace lives in the EPISODE rows too:
            // the resume positions Home's Continue watching row is built from, and the episode
            // history the Continue chip reads. Without these the removed show came straight back.
            historyDao.removeSeriesEpisodes(pid, seriesId)
            progressDao.clearSeriesEpisodes(pid, seriesId)
            refreshList() // the History category uses a manual PagingSource — force a rebuild
        }
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, SeriesEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        val playlist = sort == SettingsRepository.SortMode.PLAYLIST
        val rating = sort == SettingsRepository.SortMode.RATING
        val dateAdded = sort == SettingsRepository.SortMode.DATE_ADDED
        return if (query.isBlank()) when (key) {
            // Catch-up is a Live TV-only rail (channels have archives, series don't), but the rail model
            // is shared across all three sections — so it degrades to All here rather than existing.
            LiveKey.All, LiveKey.Catchup -> when {
                rating -> seriesDao.pagingAllRating(ids)
                playlist -> seriesDao.pagingAllOriginal(ids)
                dateAdded -> seriesDao.pagingAllDateAdded(ids)
                else -> seriesDao.pagingAll(ids)
            }
            LiveKey.Favorites -> seriesDao.pagingFavoritesManual(c.profileId, ContentOrderEntity.FAV_CONTEXT, ids)
            LiveKey.History -> seriesDao.pagingHistory(c.profileId, ids)
            is LiveKey.Custom -> customCategoryDao.pagingSeries(c.profileId, key.id, ids)
            is LiveKey.Folder -> {
                val ctxKey = folderContextKeys.value[key.id] ?: ""
                when {
                    rating -> seriesDao.pagingByCategoryRating(key.id)
                    dateAdded -> seriesDao.pagingByCategoryDateAdded(key.id)
                    // C3 fast path: no manual order in this folder → the plain indexed query has
                    // the identical (sortOrder, name) order without the join-sort.
                    ctxKey !in orderedContexts.value -> seriesDao.pagingByCategory(key.id)
                    else -> seriesDao.pagingByCategoryManual(key.id, c.profileId, ctxKey)
                }
            }
        } else when (key) {
            LiveKey.All, LiveKey.Catchup ->
                if (dateAdded) seriesDao.searchAllDateAdded(query, ids)
                else seriesDao.searchAll(query, ids)
            LiveKey.Favorites -> seriesDao.searchFavorites(query, c.profileId, ids)
            LiveKey.History -> seriesDao.searchHistory(query, c.profileId, ids)
            is LiveKey.Custom -> customCategoryDao.searchSeries(query, c.profileId, key.id, ids)
            is LiveKey.Folder ->
                if (dateAdded) seriesDao.searchInCategoryDateAdded(query, key.id)
                else seriesDao.searchInCategory(query, key.id)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx, hiddenCats: Set<Long>): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All, LiveKey.Catchup ->
                if (hiddenCats.isEmpty()) seriesDao.countAll(ids)
                else seriesDao.countAllExcluding(ids, hiddenCats.toList())
            LiveKey.Favorites -> seriesDao.countFavorites(c.profileId, ids)
            LiveKey.History -> seriesDao.countHistory(c.profileId, ids)
            is LiveKey.Custom -> customCategoryDao.countMembers(c.profileId, MediaType.SERIES, key.id, ids)
            is LiveKey.Folder -> seriesDao.countByCategory(key.id)
        }
    }

    // Remember the last selected category (Settings → Browsing & lists → "Remember last category —
    // Series", on by default). Declared LAST in the class so railItems is already assigned when this
    // init runs. Mirrors LiveViewModel's identical block.
    init {
        // Persist on change, debounced — the rail fires select() on focus as you scroll it.
        viewModelScope.launch {
            _selected.drop(1).debounce(800).distinctUntilChanged()
                .collect { settings.setLastSeriesCategory(it.serialize()) }
        }
        // Restore once at startup, and only while still on the default (never yank a user who already
        // navigated). A saved folder is honoured only once it exists in this profile's rail.
        viewModelScope.launch {
            if (!settings.rememberCategorySeries.first()) return@launch
            val saved = parseLiveKey(settings.lastSeriesCategory.first()) ?: return@launch
            // A saved Folder/Custom is honoured only while it still exists in this profile's rail —
            // a deleted custom category or a re-synced-away folder must not resurrect on restart.
            if (saved is LiveKey.Folder || saved is LiveKey.Custom) {
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

        /**
         * How long the user must stay on a season before the grid fetches its stills. Longer than the
         * 700 ms focus debounce because this fires on *entering* a show rather than on scrolling, and a
         * show opened by accident is backed out of well inside a second. Deliberately not longer: at a
         * few seconds the grid sits empty long enough to look broken, and the user backs out and
         * re-enters — which costs more requests than it saves.
         */
        const val GRID_DWELL_MS = 1_000L
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, icon = OwnTVIcon.FAVORITE),
            LiveRailItem(LiveKey.History, icon = OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All),
        )
    }
}
