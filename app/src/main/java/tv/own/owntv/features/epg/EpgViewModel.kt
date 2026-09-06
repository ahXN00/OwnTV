@file:OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class) // debounce, flatMapLatest

package tv.own.owntv.features.epg

import tv.own.owntv.core.epg.displayLogoUrl
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.epg.CatchupUrl
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.customize.applyCustomizationsWithCustoms
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.GuideWidthShares

sealed interface EpgMessage {
    data object CreateProfile : EpgMessage
    data object AddPlaylist : EpgMessage
    data class NoChannelsForQuery(val query: String) : EpgMessage
    data object MismatchedIds : EpgMessage
}

data class EpgStats(
    val guideChannels: Int,
    val programmes: Int,
    val catchupChannels: Int,
)

sealed interface EpgMatchSummary {
    data object CatchupUnavailable : EpgMatchSummary
    data object MatchedNoProgrammes : EpgMatchSummary
    data object AddPlaylist : EpgMatchSummary
    data object NoData : EpgMatchSummary
    data class AutoMatched(val applied: Int, val review: Int) : EpgMatchSummary
    data object AllMatched : EpgMatchSummary
    data class NoMatch(val channelName: String) : EpgMatchSummary
}

data class EpgUiState(
    /** All channels with guide data in the window; each row loads its own programmes lazily. */
    val channels: List<ChannelEntity> = emptyList(),
    val windowStart: Long = 0,
    val windowEnd: Long = 0,
    val now: Long = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val message: EpgMessage? = null,
    val isError: Boolean = false,
    /** False when the user hasn't added any EPG source yet → the screen shows an "Add EPG" prompt. */
    val hasEpgSources: Boolean = true,
    /** Guide counts once data is stored — rendered with the active locale by the screen. */
    val stats: EpgStats? = null,
    /** How many of the profile's channels advertise catch-up — 0 hides the Catch-up sort option. */
    val catchupCount: Int = 0,
    /** How many of the profile's channels are favourited — 0 hides the Favorites sort option. */
    val favoriteCount: Int = 0,
)

/** Provider or user-created category offered by the Guide category picker. */
data class GuideCategory(
    val key: String,
    val name: String,
    val categoryId: Long? = null,
    val customId: String? = null,
    val providerName: String? = null,
)

/**
 * Drives the EPG guide grid. Loads the active profile's EPG-capable channels and the programmes in a
 * rolling [GRID_HOURS] window from the DB, and can re-download the bulk XMLTV guide via [EpgRepository].
 */
class EpgViewModel(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val profileDao: ProfileDao,
    private val epgRepository: EpgRepository,
    private val epgSourceStore: tv.own.owntv.core.epg.EpgSourceStore,
    private val connectivity: ConnectivityObserver,
    private val customize: CustomizationStore,
    private val sourceDao: SourceDao,
    private val xtream: XtreamClient,
    private val favoriteDao: tv.own.owntv.core.database.dao.FavoriteDao,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val categoryDao: tv.own.owntv.core.database.dao.CategoryDao,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
    private val customCategoryDao: tv.own.owntv.core.database.dao.CustomCategoryDao,
) : ViewModel() {

    /** Every windowed guide read this screen makes. The caches around it stay here — what to keep
     *  depends on how the grid scrolls, which is the screen's business, not core's. */
    private val guideReader = tv.own.owntv.core.live.GuideReader(epgDao, epgSourceStore, sourceDao)

    // This profile's customizations — shared by the Guide picker, guide rows, and manual EPG match.
    // It must be initialized before guideCategories (Kotlin property initializers run top-to-bottom).
    private val custom: StateFlow<SectionCustomizations> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(SectionCustomizations()) else customize.observe(pid, MediaType.LIVE) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /** Global guide shift in minutes; a per-channel override in [custom] wins over it. */
    private val epgOffset: StateFlow<Int> = settings.epgOffsetMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Guide category filter: null = all channels, otherwise a provider/custom stable key. */
    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val activeSources: StateFlow<ActiveProfileSources> = activeProfileSources(settings, sourceDao)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveProfileSources(-1L, emptyList()))

    /** Empty for zero/one active Live source so the Guide stays unchanged for single-playlist users. */
    val providerNames: StateFlow<Map<Long, String>> = activeSources
        .map { aps ->
            val liveIds = aps.liveSourceIds
            aps.sources
                .filter { it.id in liveIds }
                .associate { it.id to it.name }
                .takeIf { it.size > 1 }
                ?: emptyMap()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val guideWidthShares: StateFlow<GuideWidthShares?> = combine(
        settings.guideWidthEnabled,
        settings.guideWidthShares,
    ) { enabled, shares -> shares.takeIf { enabled } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Live categories for the active profile — drives the guide's "Category" picker. Applies the
     *  profile's Live customizations like the Live TV rail does: hidden categories stay out of the
     *  picker, renames show, manually reordered categories stay pinned first. */
    val guideCategories: StateFlow<List<GuideCategory>> =
        activeSources
            .flatMapLatest { aps ->
                if (aps.sources.isEmpty()) flowOf(emptyList())
            else combine(categoryDao.observe(aps.liveSourceIds, MediaType.LIVE), settings.sortLive, custom, profileDao.observeById(aps.profileId)) { cats, sort, cust, profile ->
                // Mirror Live TV: hidden filtered + renames + pinned order; A–Z sorts the rest.
                val visibleCats = if (profile?.isKids == true) {
                    cats.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) }
                } else cats
                val visibleCustoms = if (profile?.isKids == true) {
                    cust.customCategories.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) }
                } else cust.customCategories
                visibleCats.applyCustomizationsWithCustoms(
                    cust,
                    visibleCustoms,
                    alphaRest = sort == SettingsRepository.SortMode.ALPHA,
                    ).let { entries ->
                        val multiSourceNames = aps.sources
                            .filter { it.id in aps.liveSourceIds }
                            .associate { it.id to it.name }
                            .takeIf { it.size > 1 }
                            .orEmpty()
                        val categoriesById = visibleCats.associateBy { it.id }
                        entries.map { entry ->
                        GuideCategory(
                            key = entry.key,
                            name = entry.displayName,
                            categoryId = entry.categoryId,
                            customId = entry.customId,
                            providerName = entry.categoryId
                                ?.let(categoriesById::get)
                                ?.sourceId
                                ?.let(multiSourceNames::get),
                        )
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCategoryFilter(categoryKey: String?) { _categoryFilter.value = categoryKey }

    private val _state = MutableStateFlow(EpgUiState())
    val state: StateFlow<EpgUiState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Per-row programme cache (epg key → programmes in the current window). Rows re-read it instantly
    // when scrolled back into view; cleared whenever the window/data reloads.
    private val rowCache = java.util.concurrent.ConcurrentHashMap<String, List<EpgProgrammeEntity>>()
    @Volatile private var loadedSourceIds: List<Long> = emptyList()
    // The window the rowCache was filled for — re-batch only when it actually changes (a sort/filter change
    // keeps the same window, so the cache stays valid and we skip the reload entirely).
    @Volatile private var cachedWindow: Pair<Long, Long>? = null
    @Volatile private var lastStored = -1 // stored programme count the cache was built from (data-change guard)
    // Bumped when the background catch-up lookback (pass 2) merges into rowCache, so visible rows re-read it.
    private val _cacheRevision = MutableStateFlow(0)
    val cacheRevision: StateFlow<Int> = _cacheRevision.asStateFlow()

    // Rows whose guide is shifted can't share the batch cache — their window is a different slice of
    // stored time — so they get their own cache, keyed "<epg key>|<minutes>". Empty unless the user
    // has actually set an offset, which keeps the normal guide exactly as fast as before.
    private val shiftedRowCache = java.util.concurrent.ConcurrentHashMap<String, List<EpgProgrammeEntity>>()

    /** Minutes this channel's guide is shifted by (per-channel override, else the global offset). */
    private fun shiftFor(channel: ChannelEntity): Int =
        tv.own.owntv.core.epg.EpgShift.minutesFor(custom.value, channel, epgOffset.value)

    /** Synchronous cache peek — lets a re-composed row render instantly without a loading flash. */
    fun cachedProgrammes(channel: ChannelEntity): List<EpgProgrammeEntity>? {
        val key = channel.epgChannelId?.trim()?.lowercase() ?: return null
        val shift = shiftFor(channel)
        return if (shift == 0) rowCache[key] else shiftedRowCache["$key|$shift"]
    }

    /** Lazily loads one row's programmes (indexed query + cache) as the row scrolls into view. */
    suspend fun programmesFor(channel: ChannelEntity): List<EpgProgrammeEntity> {
        val key = channel.epgChannelId?.trim()?.lowercase() ?: return emptyList()
        val s = _state.value
        val shift = shiftFor(channel)
        val cacheKey = if (shift == 0) key else "$key|$shift"
        val cache = if (shift == 0) rowCache else shiftedRowCache
        cache[cacheKey]?.let { return it }
        // The read itself — window, shift and all — is core's; only what to keep is this screen's.
        val list = guideReader.row(channel, custom.value, epgOffset.value, loadedSourceIds, s.windowStart, s.windowEnd)
        cache[cacheKey] = list
        return list
    }

    /** Synopsis for one programme, fetched on demand for the detail dialog (the grid load drops it). */
    suspend fun programmeDescription(programmeId: Long): String? =
        runCatching { epgDao.programmeDescription(programmeId) }.getOrNull()

    /** The whole guide window, grouped by EPG channel id — paged, off the main thread, in core. */
    private suspend fun loadWindowGrouped(ids: List<Long>, from: Long, to: Long): Map<String, List<EpgProgrammeEntity>> =
        guideReader.window(ids, from, to)

    init {
        // Re-filter the grid as the user types (DB-level, so it searches ALL guide channels, not
        // just the visible rows). drop(1): the screen triggers the initial load itself.
        _query
            .drop(1)
            .debounce(300)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        // Reload the guide when its own sort, or (for the LIVE_TV mode) the Live sort, changes.
        // drop(1): the screen triggers the initial load itself.
        settings.sortGuide
            .drop(1)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        // Reload when the category filter changes (#8).
        _categoryFilter
            .drop(1)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        settings.sortLive
            .drop(1)
            .distinctUntilChanged()
            .onEach { if (settings.sortGuide.first() == SettingsRepository.GuideSort.LIVE_TV) load() }
            .launchIn(viewModelScope)
        // Reload the guide when the active-playlist filter (Settings "Default" / Browse picker) changes,
        // so the grid narrows to the chosen playlist's channels (or back to all). drop(1): initial load
        // is triggered by the screen.
        settings.defaultSourceId
            .drop(1)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        // Reload when the guide's own data changes underneath an open screen — a feed added,
        // re-synced or deleted. Leaving the Guide and coming back already picks it up (`load()` runs
        // on every mount and compares the stored count), but a background auto-refresh finishing
        // while the grid is on screen had no way to show itself. Room reports every write to the
        // programme table, so this waits for the writes to stop rather than reloading per batch, and
        // `load()`'s own stored-count guard means a settled sync that changed nothing costs nothing.
        // drop(1): the first is the guide as it already stands.
        epgSourceStore.sources
            .map { sources -> sources.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(0) else combine(ids.map { epgDao.countForSource(it) }) { it.sum() }
            }
            .debounce(GUIDE_DATA_SETTLE_MS)
            .drop(1)
            .onEach { load() }
            .launchIn(viewModelScope)
    }

    /** The Guide's current sort, for the header button. */
    val sortGuide: StateFlow<SettingsRepository.GuideSort> = settings.sortGuide
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.GuideSort.LIVE_TV)

    /** Cycle the Guide sort: A–Z → Provider → Live TV → Catch-up → … (Catch-up only when one exists). */
    fun cycleGuideSort() {
        viewModelScope.launch {
            val modes = SettingsRepository.GuideSort.entries
                .filter { it != SettingsRepository.GuideSort.CATCHUP || _state.value.catchupCount > 0 }
                .filter { it != SettingsRepository.GuideSort.FAVORITES || _state.value.favoriteCount > 0 }
            val cur = modes.indexOf(sortGuide.value).let { if (it < 0) 0 else it }
            settings.setSortGuide(modes[(cur + 1) % modes.size])
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    /** The channel last tuned from the guide — the screen refocuses its row after fullscreen exits. */
    var lastTunedChannelId: Long? = null
        private set

    /** Record that this channel was tuned from the guide, so focus returns to its row on Back.
     *  Call this before delegating playback to liveVm, so lastTunedChannelId is set correctly. */
    fun noteChannelTuned(channel: ChannelEntity) {
        lastTunedChannelId = channel.id
    }

    // Two more live-start paths used to live here — a `play(channel)` that tuned a Guide channel
    // straight on mpv, and a `playCatchup(...)` that opened the archive itself. Both bypassed the
    // ExoPlayer-first ladder, the per-channel engine pin and the learned decode quirks, and `play()`
    // also recorded its own history row. They were unreachable (the shell always supplies the
    // callbacks), so EpgScreen now REQUIRES them and these copies are gone: LiveViewModel is the one
    // place a live channel or an archive programme starts. `playCatchupExternal` below is different —
    // it hands the URL to another app, so it stays.

    /** Which player takes a catch-up archive — read by the Guide's programme dialog to route itself. */
    val catchupPlayer: StateFlow<SettingsRepository.CatchupPlayer> = settings.catchupPlayer
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.CatchupPlayer.INTERNAL)

    /** Hand an archive programme to an external app (VLC, MX Player) instead of the in-app player. */
    fun playCatchupExternal(channel: ChannelEntity, programme: EpgProgrammeEntity) {
        viewModelScope.launch {
            val profileId = settings.activeProfileId.first()
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(profileId, channel.categoryId, profileDao, categoryDao)) return@launch
            val url = withContext(kotlinx.coroutines.Dispatchers.IO) { catchupUrlFor(channel, programme) }
            if (url == null) {
                _matchSummary.value = EpgMatchSummary.CatchupUnavailable
                return@launch
            }
            val sourceUa = withContext(kotlinx.coroutines.Dispatchers.IO) { sourceDao.getById(channel.sourceId)?.userAgent }
            externalPlayerLauncher.launch(
                url = url,
                title = channel.name,
                subtitle = programme.title,
                userAgent = sourceUa,
                httpHeaders = channel.httpHeaders,
            )
        }
    }

    /** Build the catch-up URL for a [programme] on [channel], or null if the provider can't serve it. */
    private suspend fun catchupUrlFor(channel: ChannelEntity, programme: EpgProgrammeEntity): String? {
        val source = sourceDao.getById(channel.sourceId) ?: return null
        // Stalker archive URLs are minted per-play via create_link (Phase E §5.6); the others are
        // pure string templates handled by CatchupUrl.
        if (source.type == SourceType.STALKER) {
            return channel.remoteId?.let { rid ->
                runCatching { streamUrlResolver.resolveCatchup(source, rid, programme.startMs, programme.stopMs) }
                    .onFailure { android.util.Log.w("EpgViewModel", "Stalker catch-up resolve failed channelId=${channel.id}", it) }
                    .getOrNull()
            }
        }
        return CatchupUrl.forSource(channel, programme, source, settings.resolveCatchupTimeZone(), xtream)
    }

    /** True when a programme can be played from the archive: a catch-up channel, already started, and
     *  still inside the channel's archive window. The Guide gates its "Watch from start" button on this. */
    fun canCatchup(channel: ChannelEntity, programme: EpgProgrammeEntity, now: Long): Boolean {
        if (!channel.catchup || programme.startMs > now) return false
        val windowMs = channel.catchupDays.coerceAtLeast(1) * 24L * 60 * 60 * 1000
        return now - programme.startMs <= windowMs
    }

    /** Live channels this profile has favourited — so the Guide can show/toggle a channel's star. */
    val favoriteChannelIds: StateFlow<Set<Long>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else favoriteDao.observeFavoriteIds(pid, MediaType.LIVE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Add/remove a channel from Favourites directly from the Guide (channel long-press / detail). */
    fun toggleFavoriteChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteChannelIds.value.contains(channel.id)) {
                userDataWriter.removeFavorite(pid, MediaType.LIVE, channel.id)
            } else {
                favoriteDao.add(tv.own.owntv.core.database.entity.FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }
            // The "Favorites" guide sort shows only favourited channels — refresh it to match.
            if (settings.sortGuide.first() == SettingsRepository.GuideSort.FAVORITES) load()
        }
    }

    /** The channel's current manual EPG match (or null if auto-matched). */
    fun currentEpgMatch(channel: ChannelEntity): String? = custom.value.epgMatches[CustomizeKeys.channel(channel)]

    /** The channel's own guide shift in minutes, or null when it follows the global offset. */
    fun currentEpgShift(channel: ChannelEntity): Int? =
        tv.own.owntv.core.epg.EpgShift.overrideFor(custom.value, channel)

    /** The global guide shift — the per-channel dialog's "follow global" default. */
    fun globalEpgShift(): Int = epgOffset.value

    /** Shift this channel's guide by [minutes] (null → follow the global offset), then refresh. */
    fun setEpgShift(channel: ChannelEntity, minutes: Int?) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            val key = CustomizeKeys.channel(channel)
            customize.setEpgShift(pid, MediaType.LIVE, key, minutes)
            // Rows re-read the shift from `custom` — wait for the DataStore edit to land there first,
            // or the refresh below can still render with the old offset.
            kotlinx.coroutines.withTimeoutOrNull(1_000) { custom.first { it.epgShifts[key]?.toIntOrNull() == minutes } }
            shiftedRowCache.clear()
            _cacheRevision.value++ // visible rows re-read with the new shift
        }
    }

    /** Set/clear a channel's manual EPG match (null clears → auto-match), then reload the guide. */
    fun setEpgMatch(channel: ChannelEntity, epgChannelId: String?) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(channel), epgChannelId)
            if (epgChannelId != null) fillMatchedInBackground(listOf(epgChannelId)) else load()
        }
    }

    /** Fill in matched channels' programmes from the cached XMLTV (no network) and refresh the guide — in the
     *  BACKGROUND so the match action/spinner returns instantly and doesn't wait on a full cache re-parse. */
    private fun fillMatchedInBackground(epgIds: Collection<String>) {
        viewModelScope.launch {
            val ids = epgIds.map { it.trim().lowercase() }.filterTo(HashSet()) { it.isNotBlank() }
            if (ids.isEmpty()) return@launch
            // One cache pass for the whole set; only re-sync over the network if the cache is gone/stale
            // (returns false when it held none of the matched channels' programmes).
            val handled = runCatching { epgRepository.storeProgrammesForIdsFromCache(ids) }.getOrDefault(false)
            if (!handled) runCatching { refreshAllEpgFromNetwork() }
            load()
            // Single-channel match (manual pick / review Accept / single auto-match): if the feed has no
            // current-or-upcoming programmes for it, its guide row will be empty even though the match
            // succeeded — say so, instead of leaving the user staring at a blank row (the provider simply
            // hasn't published a current schedule for that channel).
            if (ids.size == 1) {
                val upcoming = runCatching { epgDao.countUpcomingForChannel(ids.first(), System.currentTimeMillis()) }.getOrDefault(1)
                if (upcoming == 0) {
                    _matchSummary.value = EpgMatchSummary.MatchedNoProgrammes
                }
            }
        }
    }


    private suspend fun refreshAllEpgFromNetwork() {
        val pid = settings.activeProfileId.first()
        if (pid >= 0) sourceRepository.observeSources(pid).first().forEach { runCatching { epgRepository.refresh(it) } }
        epgSourceStore.getAll().forEach { runCatching { epgRepository.refreshUrl(it.id, it.url, it.userAgent) } }
    }

    // ---- Smart EPG matching (#13): scan channels with no working guide and match them by name ----

    /** A proposed EPG match for a channel that didn't auto-resolve confidently enough to apply. */
    data class EpgMatchSuggestion(
        val channel: ChannelEntity,
        val epgChannelId: String,
        val epgName: String?,
        val score: Double,
    )

    private val _matching = MutableStateFlow(false)
    val matching: StateFlow<Boolean> = _matching.asStateFlow()

    /** Low-confidence suggestions awaiting the user's accept/skip (high-confidence ones auto-apply). */
    private val _review = MutableStateFlow<List<EpgMatchSuggestion>>(emptyList())
    val review: StateFlow<List<EpgMatchSuggestion>> = _review.asStateFlow()

    /** One-line outcome of the last auto-match run, shown as a transient banner. */
    private val _matchSummary = MutableStateFlow<EpgMatchSummary?>(null)
    val matchSummary: StateFlow<EpgMatchSummary?> = _matchSummary.asStateFlow()

    /**
     * Scan every channel that has no working guide (no manual match and its tvg-id isn't in the EPG
     * feed), match it by name, auto-apply high-confidence hits and queue the rest for review.
     */
    fun autoMatchEpg() {
        if (_matching.value) return
        viewModelScope.launch {
            _matching.value = true
            try {
                val pid = settings.activeProfileId.first()
                val playlistIds = if (pid < 0) emptyList() else sourceRepository.observeSources(pid).first().map { it.id }
                if (playlistIds.isEmpty()) { _matchSummary.value = EpgMatchSummary.AddPlaylist; return@launch }
                val ids = playlistIds + epgSourceStore.getAll().map { it.id }
                val cust = customize.observe(pid, MediaType.LIVE).first()

                val candidates = epgDao.listEpgChannels(ids, "", MAX_EPG_CANDIDATES)
                if (candidates.isEmpty()) { _matchSummary.value = EpgMatchSummary.NoData; return@launch }
                val knownIds = candidates.mapTo(HashSet()) { it.epgChannelId.trim().lowercase() }

                val (applied, review, appliedIds) = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val prepared = tv.own.owntv.core.epg.EpgMatcher.prepare(
                        candidates.map { tv.own.owntv.core.epg.EpgMatcher.Candidate(it.epgChannelId, it.displayName) },
                    )
                    // Narrow to the channels that actually need a match first, then score them in one
                    // bulk pass: the scan is channels × candidates, which is millions of comparisons on
                    // a full catalogue and minutes of spinner on TV silicon if it runs on one thread.
                    val unmatched = channelDao.allForSources(playlistIds, MAX_CHANNELS).filter { ch ->
                        val key = CustomizeKeys.channel(ch)
                        if (key in cust.epgMatches || key in cust.hiddenItems) return@filter false // already matched/hidden
                        val tvg = ch.epgChannelId?.trim()?.lowercase()
                        tvg.isNullOrEmpty() || tvg !in knownIds // anything else already has a working guide
                    }
                    val best = tv.own.owntv.core.epg.EpgMatcher.bestEpgMatchBulk(unmatched.map { it.name }, prepared)
                    var applied = 0
                    val toApply = mutableListOf<Pair<String, String>>() // key -> epgId
                    val review = mutableListOf<EpgMatchSuggestion>()
                    for ((ch, match) in unmatched.zip(best)) {
                        if (match == null) continue
                        if (match.score >= tv.own.owntv.core.epg.EpgMatcher.AUTO_THRESHOLD) {
                            toApply.add(CustomizeKeys.channel(ch) to match.epgChannelId)
                            applied++
                        } else {
                            review.add(EpgMatchSuggestion(ch, match.epgChannelId, match.displayName, match.score))
                        }
                    }
                    // Persist the confident matches (DataStore writes are cheap but do them off the scan).
                    for ((key, epgId) in toApply) customize.setEpgMatch(pid, MediaType.LIVE, key, epgId)
                    Triple(applied, review.sortedByDescending { it.score }, toApply.map { it.second })
                }

                _review.value = review
                _matchSummary.value = if (applied == 0 && review.isEmpty()) {
                    EpgMatchSummary.AllMatched
                } else {
                    EpgMatchSummary.AutoMatched(applied, review.size)
                }
                if (applied > 0) fillMatchedInBackground(appliedIds) // off the spinner — fills in shortly after
            } finally {
                _matching.value = false
            }
        }
    }

    /**
     * Auto-match a SINGLE channel by name (Guide long-press → "Auto-match"). Surfaces the best candidate
     * in the same review dialog (one entry, so no accept/skip-all) for the user to accept or skip, rather
     * than applying silently — or reports none found.
     */
    fun autoMatchOne(channel: ChannelEntity) {
        if (_matching.value) return
        viewModelScope.launch {
            _matching.value = true
            try {
                val pid = settings.activeProfileId.first()
                val playlistIds = if (pid < 0) emptyList() else sourceRepository.observeSources(pid).first().map { it.id }
                val ids = playlistIds + epgSourceStore.getAll().map { it.id }
                val candidates = epgDao.listEpgChannels(ids, "", MAX_EPG_CANDIDATES)
                if (candidates.isEmpty()) { _matchSummary.value = EpgMatchSummary.NoData; return@launch }
                val best = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val prepared = tv.own.owntv.core.epg.EpgMatcher.prepare(
                        candidates.map { tv.own.owntv.core.epg.EpgMatcher.Candidate(it.epgChannelId, it.displayName) },
                    )
                    tv.own.owntv.core.epg.EpgMatcher.bestEpgMatchPrepared(channel.name, prepared)
                }
                if (best == null) {
                    _matchSummary.value = EpgMatchSummary.NoMatch(channel.name)
                } else {
                    // Show it in the review dialog (accept/skip) instead of applying silently. acceptSuggestion
                    // persists the match + fills the guide; dismissSuggestion just drops it.
                    _review.value = listOf(EpgMatchSuggestion(channel, best.epgChannelId, best.displayName, best.score))
                }
            } finally {
                _matching.value = false
            }
        }
    }

    /** Accept a reviewed suggestion → persist the match and drop it from the review list. */
    fun acceptSuggestion(s: EpgMatchSuggestion) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(s.channel), s.epgChannelId)
            _review.value = _review.value.filterNot { it.channel.id == s.channel.id }
            fillMatchedInBackground(listOf(s.epgChannelId))
        }
    }

    /** Skip a suggestion without matching it (just remove it from the review list). */
    fun dismissSuggestion(s: EpgMatchSuggestion) {
        _review.value = _review.value.filterNot { it.channel.id == s.channel.id }
    }

    /** Accept every remaining suggestion at once, then clear the review list and reload the guide. */
    fun acceptAllSuggestions() {
        val all = _review.value
        if (all.isEmpty()) return
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            for (s in all) customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(s.channel), s.epgChannelId)
            _review.value = emptyList()
            fillMatchedInBackground(all.map { it.epgChannelId })
        }
    }

    /** Close the review list / clear the summary banner. */
    fun clearReview() {
        _review.value = emptyList()
        _matchSummary.value = null
    }

    // A guide-local `zap(delta)` used to live here, stepping the guide list and calling the deleted
    // `play()`. It was already unreachable: a Guide tune goes through LiveViewModel.watchFromGuide,
    // which sets the shell's zapSource to LIVE_TV, so CH+/CH- always used LiveViewModel.zap — the one
    // with the ExoPlayer ladder, the pending-tune anchor and the bounded rebuild.

    fun load() {
        viewModelScope.launch {
            // Show the spinner only on the FIRST load. The EpgViewModel is shared, so on re-entry the guide
            // is already populated — keep the existing list on screen and refresh it silently, so opening the
            // Guide menu is instant instead of flashing a spinner and re-rendering from scratch every time.
            if (_state.value.channels.isEmpty()) _state.value = _state.value.copy(loading = true, message = null)
            val pid = currentProfileId()
            if (pid == null) {
                _state.value = EpgUiState(
                    loading = false,
                    message = EpgMessage.CreateProfile,
                    hasEpgSources = epgSourceStore.getAll().isNotEmpty(),
                )
                return@launch
            }
            val playlistIds = activeSourceIds(settings, sourceDao, pid, MediaType.LIVE)
            val epgIds = epgSourceStore.getAll().map { it.id }
            // Channels come from the playlists; guide data is matched from BOTH the playlists' own EPG
            // (kept for compatibility) and the standalone EPG sources — by epgChannelId across all ids.
            val ids = playlistIds + epgIds

            if (playlistIds.isEmpty()) {
                _state.value = EpgUiState(loading = false, message = EpgMessage.AddPlaylist)
                return@launch
            }

            val now = System.currentTimeMillis()
            val nowAligned = now - (now % HALF_HOUR_MS) // align to the half hour
            // Always retain two recent hours so the centered "now" marker has programmes on its left.
            // Catch-up channels can extend the same window farther back to their archive limit.
            val maxCatchupDays = channelDao.maxCatchupDays(playlistIds)
            val catchupLookbackMs = if (maxCatchupDays > 0)
                minOf(maxCatchupDays * DAY_MS, CATCHUP_LOOKBACK_CAP_MS) else 0L
            val visiblePastStart = nowAligned - GUIDE_VISIBLE_PAST_MS
            val lookbackMs = maxOf(GUIDE_VISIBLE_PAST_MS, catchupLookbackMs)
            val windowStart = nowAligned - lookbackMs
            val windowEnd = nowAligned + GRID_HOURS * 60L * 60 * 1000

            // Respect customizations: hidden channels stay out of the guide, renames show.
            val cust = customize.observe(pid, MediaType.LIVE).first()
            val q = _query.value.trim()
            val rawChannels = channelDao.channelsWithGuide(ids, q, MAX_CHANNELS)
            // Catch-up count comes from the playlist channels (the tv_archive flag), so it shows even
            // before any XMLTV guide is downloaded — it tells the user their provider supports catch-up.
            val catchupCount = channelDao.countCatchup(playlistIds)
            val favoriteIds = favoriteDao.observeFavoriteIds(pid, MediaType.LIVE).first().toSet()
            val sortLiveMode = settings.sortLive.first()
            val sortGuideMode = settings.sortGuide.first()
            // Hidden categories keep their channels out of the guide too (parity with Live TV), and a
            // filter pointing at a now-hidden category falls back to "All" instead of an empty grid.
        val isKidsProfile = profileDao.getById(pid)?.isKids == true
        val hiddenCatIds = if (cust.hiddenCategories.isEmpty() && !isKidsProfile) {
            emptySet()
        } else {
            tv.own.owntv.core.content.AdultCategoryClassifier.hiddenCategoryIds(
                categoryDao.observe(ids, MediaType.LIVE).first(),
                cust.hiddenCategories,
                isKidsProfile,
            )
        }
            val categoryFilter = _categoryFilter.value
                ?.let { key -> guideCategories.value.firstOrNull { it.key == key } }
            val customMemberIds = categoryFilter?.customId
                ?.let { customCategoryDao.itemIds(pid, MediaType.LIVE, it).toSet() }
            // Heavy work — filter hidden, apply renames + manual EPG matches, sort, category-filter — runs off
            // the main thread (#3/#5) so a 50k-channel playlist never freezes the UI building the guide list.
            val channels = withContext(Dispatchers.Default) {
                val auto = rawChannels
                    .filter { CustomizeKeys.channel(it) !in cust.hiddenItems }
                    .filter {
                        customMemberIds != null || it.categoryId == null || it.categoryId !in hiddenCatIds
                    }
                    .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
                val matched = applyEpgMatches(auto, cust, playlistIds, q)
                // Order the guide by its own sort. LIVE_TV mirrors the Live sort; CATCHUP floats archive
                // channels to the top; ALPHA/PROVIDER are explicit. CATCHUP with none available falls to LIVE_TV.
                val byAlpha = compareBy<ChannelEntity> { it.name.lowercase() }
                val byProvider = compareBy<ChannelEntity>({ it.sourceId }, { it.sortOrder }, { it.name.lowercase() })
                val liveOrdered = when (sortLiveMode) {
                    SettingsRepository.SortMode.ALPHA -> matched.sortedWith(byAlpha)
                    // Live/EPG have no rating; RATING can't be selected there, so treat it as provider order.
                    SettingsRepository.SortMode.PLAYLIST, SettingsRepository.SortMode.RATING, SettingsRepository.SortMode.DATE_ADDED -> matched.sortedWith(byProvider)
                }
                when (sortGuideMode) {
                    SettingsRepository.GuideSort.ALPHA -> matched.sortedWith(byAlpha)
                    SettingsRepository.GuideSort.PROVIDER -> matched.sortedWith(byProvider)
                    SettingsRepository.GuideSort.CATCHUP ->
                        if (catchupCount > 0) matched.sortedWith(compareByDescending<ChannelEntity> { it.catchup }.then(byAlpha)) else liveOrdered
                    // Favorites: show ONLY favourited channels (in the Live order); none favourited → fall back.
                    SettingsRepository.GuideSort.FAVORITES ->
                        if (favoriteIds.isNotEmpty()) liveOrdered.filter { it.id in favoriteIds } else liveOrdered
                    SettingsRepository.GuideSort.LIVE_TV -> liveOrdered
                }.let { sorted ->
                    // Category filter (#8): when a group is chosen, show only its channels.
                    when {
                        customMemberIds != null -> sorted.filter { it.id in customMemberIds }
                        categoryFilter?.categoryId != null -> sorted.filter { ch ->
                            ch.categoryId == categoryFilter.categoryId &&
                                cust.movedFromOrigin[CustomizeKeys.channel(ch)] != categoryFilter.key
                        }
                        else -> sorted
                    }
                }
            }
            val stored = epgDao.countForSources(ids)


            // Per-row programmes are loaded in ONE batched query and grouped into rowCache here, instead of
            // each row firing its own programmesForChannel (an N+1 query storm on cold open). Only re-batch
            // when the window or sources actually change — a sort / filter / category change keeps the same
            // window, so the cache stays valid and we reuse it. Grouping runs off the main thread.
            // Also re-batch when the stored programme count changed (a sync, or a post-match top-up, added
            // data) even if the window is unchanged — so freshly-matched channels' guides appear.
            val windowChanged = cachedWindow != (windowStart to windowEnd) || loadedSourceIds != ids || stored != lastStored
            loadedSourceIds = ids
            lastStored = stored
            if (windowChanged) {
                cachedWindow = windowStart to windowEnd
                rowCache.clear()
                shiftedRowCache.clear()
                // Pass 1 (blocking): current view plus two recent hours, so the centered grid is complete.
                val forward = loadWindowGrouped(ids, visiblePastStart, windowEnd)
                forward.forEach { (k, list) -> rowCache[k] = list }
                // Signal GuideChannelRow's produceState to re-read rowCache now that the batch load
                // is complete — without this, the initial composition renders channels with empty
                // programmes until the user scrolls and triggers fresh item composition.
                _cacheRevision.value++
            }

            val hasEpg = epgIds.isNotEmpty()
            val message = when {
                stored == 0 -> null // handled by the "No EPG added" prompt (hasEpgSources=false)
                channels.isEmpty() && q.isNotBlank() -> EpgMessage.NoChannelsForQuery(q)
                channels.isEmpty() -> EpgMessage.MismatchedIds
                else -> null
            }
            val guideChannels = if (stored > 0) epgDao.countGuideChannels(ids) else 0
            val stats = EpgStats(
                guideChannels = guideChannels,
                programmes = stored,
                catchupChannels = catchupCount,
            )

            _state.value = EpgUiState(
                channels = channels, windowStart = windowStart, windowEnd = windowEnd, now = now,
                loading = false, message = message, hasEpgSources = hasEpg, stats = stats, catchupCount = catchupCount,
                favoriteCount = favoriteIds.size,
            )

            // Pass 2 (background): merge older catch-up history gradually, so a multi-day × many-channel
            // window never blocks opening or spikes memory on low-RAM (1.8 GB) boxes. It runs only when
            // history exists beyond the normal two-hour view; visible rows then re-read the completed cache.
            if (windowChanged && windowStart < visiblePastStart) {
                viewModelScope.launch {
                    val back = loadWindowGrouped(ids, windowStart, visiblePastStart)
                    if (back.isNotEmpty()) {
                        back.forEach { (k, extra) ->
                            rowCache[k] = (extra + (rowCache[k] ?: emptyList())).distinctBy { it.id }.sortedBy { it.startMs }
                        }
                        _cacheRevision.value++
                    }
                }
            }
        }
    }

    /** Apply per-channel manual EPG overrides to the auto-matched guide list. */
    private suspend fun applyEpgMatches(
        auto: List<ChannelEntity>,
        cust: tv.own.owntv.core.customize.SectionCustomizations,
        playlistIds: List<Long>,
        query: String,
    ): List<ChannelEntity> {
        val matches = cust.epgMatches
        if (matches.isEmpty()) return auto
        val byKey = auto.associateBy { CustomizeKeys.channel(it) }
        // Override the epg id of channels already in the list.
        val overridden = auto.map { ch -> matches[CustomizeKeys.channel(ch)]?.let { ch.copy(epgChannelId = it) } ?: ch }.toMutableList()
        // Add matched channels that didn't auto-appear. Resolve them with ONE bulk query (all channels keyed
        // by their customize key) instead of two DB lookups per match — the old per-match resolveChannel was a
        // query-storm that dominated guide load time once a lot of channels had been smart-matched.
        val missingKeys = matches.keys.filter { it !in byKey && it !in cust.hiddenItems }
        if (missingKeys.isNotEmpty()) {
            // Resolve only the matched channels (their key's tail is the remoteId/Xtream stream id) in one
            // query, instead of loading the entire channel table — that table-load was seconds on big lists.
            val remoteIds = missingKeys.mapNotNull { it.substringAfter(':', "").takeIf { r -> r.isNotEmpty() } }.distinct()
            val resolved = channelDao.findByRemoteIds(playlistIds, remoteIds).associateBy { CustomizeKeys.channel(it) }
            for (key in missingKeys) {
                val epgId = matches[key] ?: continue
                val ch = resolved[key] ?: continue
                val name = cust.itemNames[key] ?: ch.name
                if (query.isNotBlank() && !name.contains(query, ignoreCase = true)) continue
                overridden.add(ch.copy(epgChannelId = epgId, name = name))
            }
        }
        return overridden
    }

    /** Distinct EPG channels for the manual "Match EPG" picker (across the profile's feeds),
     *  ranked so guide channels resembling [channelName] come first instead of a plain A-Z list. */
    suspend fun availableEpgChannels(channelName: String, query: String): List<tv.own.owntv.core.database.entity.EpgChannelEntity> {
        val pid = currentProfileId() ?: return emptyList()
        val playlistIds = sourceRepository.observeSources(pid).first().map { it.id }
        val ids = playlistIds + epgSourceStore.getAll().map { it.id }
        if (ids.isEmpty()) return emptyList()
        // Fetch the whole (filtered) candidate set, not just the first 300 alphabetically — the best
        // name match may sit far down the alphabet. Rank off-main, then cap for the dialog list.
        val all = epgDao.listEpgChannels(ids, query.trim().lowercase(), MAX_EPG_CANDIDATES)
        return withContext(kotlinx.coroutines.Dispatchers.Default) {
            tv.own.owntv.core.epg.EpgMatcher.rankForPicker(channelName, all, { it.displayName }, { it.epgChannelId }).take(EPG_PICKER_RESULT_LIMIT)
        }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    companion object {
        const val GRID_HOURS = 24
        // A sync writes the guide in batches, so every batch is reported. Long enough that one
        // download reloads once at the end rather than on every batch.
        private const val GUIDE_DATA_SETTLE_MS = 1_500L
        private const val HALF_HOUR_MS = 30L * 60 * 1000
        private const val GUIDE_VISIBLE_PAST_MS = 2L * 60 * 60 * 1000
        private const val DAY_MS = 24L * 60 * 60 * 1000
        // How far back the Guide may extend for catch-up (must stay within EpgRepository's retention).
        private const val CATCHUP_LOOKBACK_CAP_MS = 7L * 24 * 60 * 60 * 1000
        // Generous safety bound only (rows load lazily, so this is about the channel list itself).
        private const val MAX_CHANNELS = 20_000
        // Cap the candidate set the bulk matcher scans against (keeps the O(channels×candidates) scan bounded).
        private const val MAX_EPG_CANDIDATES = 20_000
        private const val EPG_PICKER_RESULT_LIMIT = 300
    }
}
