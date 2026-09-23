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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.epg.CatchupUrl
import tv.own.owntv.core.epg.EpgAutoMatcher
import tv.own.owntv.core.epg.GuideCandidate
import tv.own.owntv.core.epg.GuideCandidates
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.customize.railCategories
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
 * rolling window from the DB — as many days forward as Settings' "Guide days to keep" stores —
 * and can re-download the bulk XMLTV guide via [EpgRepository].
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
    private val recordings: tv.own.owntv.core.recording.RecordingManager,
    /** The provider-guide half of a row, for channels whose stored guide stops short — the app-wide
     *  reader Live TV uses too (T18), so both screens share one cache. */
    private val liveEpgReader: tv.own.owntv.core.live.LiveEpgReader,
) : ViewModel() {

    /** The one candidate set the picker and both auto-match paths read — see [GuideCandidates]. */
    private val guideCandidates = GuideCandidates(epgDao)

    /** The match scan itself — core's, so the television, the phone and the single-channel run agree. */
    private val autoMatcher = EpgAutoMatcher(channelDao, guideCandidates)

    // --- Recording, from the guide (Plan D, Feature A) ------------------------------------------

    /**
     * This profile's recordings, so a programme cell knows whether it is already spoken for.
     *
     * The whole list rather than a per-programme lookup: the guide draws hundreds of cells and a
     * query each would be hundreds of queries. There are never many recordings.
     */
    val recordingRows: StateFlow<List<tv.own.owntv.core.database.entity.RecordingEntity>> =
        settings.activeProfileId
            .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else recordings.observe(pid) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The recording already covering this programme, if there is one. */
    fun recordingFor(
        channel: ChannelEntity,
        programme: EpgProgrammeEntity,
    ): tv.own.owntv.core.database.entity.RecordingEntity? = recordingRows.value.firstOrNull {
        it.channelId == channel.id && it.programmeStartMs == programme.startMs &&
            it.status != tv.own.owntv.core.model.RecordingStatus.CANCELLED
    }

    /**
     * Record this programme — scheduled if it is still to come, pulled from the archive if it has
     * already been on and the channel keeps one.
     *
     * A programme that has already finished and whose channel has no catch-up cannot be recorded at
     * all; [canRecord] is what stops the button being offered for it.
     */
    fun record(channel: ChannelEntity, programme: EpgProgrammeEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val source = sourceDao.getById(channel.sourceId) ?: return@launch
            val now = System.currentTimeMillis()
            if (programme.stopMs <= now) {
                recordings.recordFromArchive(
                    profileId = pid,
                    channel = channel,
                    programme = programme,
                    source = source,
                    timeZone = settings.resolveCatchupTimeZone(source),
                    xtream = xtream,
                )
                return@launch
            }
            val window = recordings.windowFor(programme.startMs, programme.stopMs)
            recordings.schedule(
                tv.own.owntv.core.database.entity.RecordingEntity(
                    profileId = pid,
                    sourceId = channel.sourceId,
                    channelId = channel.id,
                    channelName = channel.name,
                    channelIconUrl = channel.logoUrl,
                    epgChannelId = channel.epgChannelId,
                    streamUrl = channel.streamUrl,
                    httpHeaders = channel.httpHeaders,
                    title = programme.title,
                    description = programme.description,
                    programmeStartMs = programme.startMs,
                    programmeStopMs = programme.stopMs,
                    startMs = window.first,
                    stopMs = window.last,
                ),
            )
        }
    }

    /**
     * Whether Record can be offered for this programme at all: still to come, or already been on and
     * within a catch-up channel's archive.
     */
    fun canRecord(channel: ChannelEntity, programme: EpgProgrammeEntity, now: Long): Boolean =
        programme.stopMs > now || canCatchup(channel, programme, now)

    fun stopRecording(recording: tv.own.owntv.core.database.entity.RecordingEntity) =
        recordings.stop(recording)

    fun cancelRecording(recording: tv.own.owntv.core.database.entity.RecordingEntity) =
        recordings.cancel(recording)

    /** The standing "record every showing" rule covering this programme, or null (D7). */
    suspend fun seriesRuleFor(
        channel: ChannelEntity,
        programme: EpgProgrammeEntity,
    ): tv.own.owntv.core.database.entity.RecordingRuleEntity? {
        val pid = currentProfileId() ?: return null
        return recordings.ruleFor(pid, channel.id, programme.title)
    }

    /** Record every future showing of this title on this channel. */
    fun recordSeries(channel: ChannelEntity, programme: EpgProgrammeEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            recordings.addSeriesRule(pid, channel, programme.title)
        }
    }

    /** Stop the standing rule, and drop the showings it had queued but not yet recorded. */
    fun stopSeries(rule: tv.own.owntv.core.database.entity.RecordingRuleEntity) {
        viewModelScope.launch { recordings.removeSeriesRule(rule) }
    }

    /**
     * The title of a recording this one would contend with, or null when there is no conflict.
     *
     * Shown **before** the user commits, because a live programme cannot wait its turn — "start when
     * the other finishes" means "start half-way through" (D10). Only the playlist's own recordings
     * count: two playlists with a connection each can record two things at once.
     */
    suspend fun clashFor(channel: ChannelEntity, programme: EpgProgrammeEntity): String? {
        val window = recordings.windowFor(programme.startMs, programme.stopMs)
        val existing = recordingFor(channel, programme)
        return recordings
            .clashesWith(channel.sourceId, window.first, window.last, existing?.id ?: 0)
            .firstOrNull()
            ?.title
    }

    /** Every windowed guide read this screen makes. The caches around it stay here — what to keep
     *  depends on how the grid scrolls, which is the screen's business, not core's. */
    private val guideReader =
        tv.own.owntv.core.live.GuideReader(epgDao, epgSourceStore, sourceDao, liveEpgReader)

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
                // The same rail Live TV shows: hidden filtered + renames + pinned order.
                cats.railCategories(
                    cust,
                    kids = profile?.isKids == true,
                    alphaRest = sort == SettingsRepository.SortMode.ALPHA,
                    ).let { entries ->
                        val multiSourceNames = aps.sources
                            .filter { it.id in aps.liveSourceIds }
                            .associate { it.id to it.name }
                            .takeIf { it.size > 1 }
                            .orEmpty()
                        val categoriesById = cats.associateBy { it.id }
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

    /**
     * The rows the grid has already read, newest-used first and **hard-capped**.
     *
     * This used to be an unbounded [java.util.concurrent.ConcurrentHashMap] pre-filled by one batch
     * query that read the entire guide window. On the owner's television that batch was 349,077
     * programmes in a single list, and the app died of an OutOfMemoryError inside it. Rows are now
     * read one channel at a time as they scroll into view, and the cache is an LRU: the grid keeps
     * what the user is actually looking at and forgets the rest, so memory is a function of the
     * screen rather than of the catalogue.
     *
     * Keyed by channel id and shift, not by EPG id: two channels can share one guide channel, and the
     * same channel at two offsets is two different rows.
     */
    private val rowCache = object : LinkedHashMap<String, List<EpgProgrammeEntity>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<EpgProgrammeEntity>>?) =
            size > MAX_CACHED_ROWS
    }

    private fun cacheKey(channel: ChannelEntity, shift: Int) = "${channel.id}|$shift"

    private fun cachedRow(key: String): List<EpgProgrammeEntity>? = synchronized(rowCache) { rowCache[key] }

    private fun putRow(key: String, rows: List<EpgProgrammeEntity>) {
        synchronized(rowCache) { rowCache[key] = rows }
    }

    private fun clearRows() {
        synchronized(rowCache) { rowCache.clear() }
    }

    private fun cachedRowCount(): Int = synchronized(rowCache) { rowCache.size }

    /** The row prefetch in flight. One at a time — see [prefetchAfter]. */
    private var prefetchJob: kotlinx.coroutines.Job? = null

    /** The load in flight, so the next one can cancel it instead of racing it. */
    private var loadJob: kotlinx.coroutines.Job? = null
    private val loadMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile private var loadedSourceIds: List<Long> = emptyList()
    // The window the cached rows belong to. A sort / filter / category change keeps the same window,
    // so the rows stay valid and nothing is re-read.
    @Volatile private var cachedWindow: Pair<Long, Long>? = null
    @Volatile private var lastStored = -1 // stored programme count the cache was built from (data-change guard)
    // Bumped when cached rows are dropped, so visible rows know to ask again.
    private val _cacheRevision = MutableStateFlow(0)
    val cacheRevision: StateFlow<Int> = _cacheRevision.asStateFlow()

    /** Minutes this channel's guide is shifted by (per-channel override, else the global offset). */
    private fun shiftFor(channel: ChannelEntity): Int =
        tv.own.owntv.core.epg.EpgShift.minutesFor(custom.value, channel, epgOffset.value)

    /** Synchronous cache peek — lets a re-composed row render instantly without a loading flash. */
    fun cachedProgrammes(channel: ChannelEntity): List<EpgProgrammeEntity>? =
        cachedRow(cacheKey(channel, shiftFor(channel)))

    /**
     * One row's programmes, read as the row scrolls into view.
     *
     * This is now the **only** way the grid gets programmes. It is one indexed query per channel, so
     * eight visible rows cost eight small reads instead of one read of the whole database — which is
     * what the deleted batch did, and what ran the television out of memory.
     */
    suspend fun programmesFor(channel: ChannelEntity): List<EpgProgrammeEntity> {
        val s = _state.value
        val shift = shiftFor(channel)
        val key = cacheKey(channel, shift)
        cachedRow(key)?.let { return it }
        // The read itself — window, shift and all — is core's; only what to keep is this screen's.
        val list = guideReader.row(channel, custom.value, epgOffset.value, s.windowStart, s.windowEnd)
        putRow(key, list)
        prefetchAfter(channel)
        return list
    }

    /**
     * Warm the rows just below the one that was asked for, so flinging the grid does not draw blanks.
     *
     * One job at a time, and the newest wins: rows come into view one after another, so each request
     * cancels the previous run rather than adding to it. That is what keeps this a prefetch and not
     * the N+1 query storm the deleted batch load was originally written to prevent — at most
     * [PREFETCH_AHEAD] small indexed reads are ever in flight, sequentially, and the LRU bounds what
     * they cost to keep.
     */
    private fun prefetchAfter(channel: ChannelEntity) {
        val channels = _state.value.channels
        val from = channels.indexOfFirst { it.id == channel.id }
        if (from < 0) return
        val next = channels.subList((from + 1).coerceAtMost(channels.size), (from + 1 + PREFETCH_AHEAD).coerceAtMost(channels.size))
        if (next.isEmpty()) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            val s = _state.value
            for (ahead in next) {
                val key = cacheKey(ahead, shiftFor(ahead))
                if (cachedRow(key) != null) continue
                putRow(key, guideReader.row(ahead, custom.value, epgOffset.value, s.windowStart, s.windowEnd))
            }
        }
    }

    /** Synopsis for one programme, fetched on demand for the detail dialog (the grid load drops it). */
    suspend fun programmeDescription(programmeId: Long): String? =
        runCatching { epgDao.programmeDescription(programmeId) }.getOrNull()

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
            val source = withContext(kotlinx.coroutines.Dispatchers.IO) { sourceDao.getById(channel.sourceId) }
            externalPlayerLauncher.launch(
                url = url,
                title = channel.name,
                subtitle = programme.title,
                userAgent = source?.userAgent,
                httpHeaders = tv.own.owntv.core.settings.SourceOverrides.headersWithReferer(channel.httpHeaders, source),
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
        return CatchupUrl.forSource(channel, programme, source, settings.resolveCatchupTimeZone(source), xtream)
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
    fun currentEpgMatch(channel: ChannelEntity): String? = custom.value.epgMatchResolver.epgIdFor(channel)

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
            // Cached rows are keyed by shift, so the old offset's rows are simply stale. One cache now,
            // so this drops them all rather than only the shifted ones.
            clearRows()
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
                val cust = customize.observe(pid, MediaType.LIVE).first()

                val outcome = autoMatcher.run(cust, playlistIds)
                if (!outcome.hadCandidates) { _matchSummary.value = EpgMatchSummary.NoData; return@launch }

                // Persist the confident hits (DataStore writes are cheap, but do them off the scan).
                for ((key, epgId) in outcome.applied) customize.setEpgMatch(pid, MediaType.LIVE, key, epgId)

                _review.value = outcome.review.map {
                    EpgMatchSuggestion(it.channel, it.epgChannelId, it.displayName, it.score)
                }
                val applied = outcome.applied.size
                _matchSummary.value = when {
                    applied == 0 && outcome.review.isEmpty() -> EpgMatchSummary.AllMatched
                    // Everything found pointed at a guide channel with nothing scheduled. Saying
                    // "matched" here would be a success message for a row that stays blank.
                    applied == 0 && outcome.withheldForNoProgrammes == outcome.review.size ->
                        EpgMatchSummary.MatchedNoProgrammes
                    else -> EpgMatchSummary.AutoMatched(applied, outcome.review.size)
                }
                if (applied > 0) fillMatchedInBackground(outcome.applied.map { it.second }) // off the spinner
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
                val best = autoMatcher.one(channel)
                if (best == null) {
                    _matchSummary.value = EpgMatchSummary.NoMatch(channel.name)
                } else {
                    // Show it in the review dialog (accept/skip) instead of applying silently. acceptSuggestion
                    // persists the match + fills the guide; dismissSuggestion just drops it.
                    _review.value = listOf(EpgMatchSuggestion(channel, best.epgChannelId, best.displayName, best.score))
                    // Say so up front when the winner's guide channel is empty, instead of letting the
                    // user accept it and find a blank row.
                    if (!best.hasProgrammes) _matchSummary.value = EpgMatchSummary.MatchedNoProgrammes
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

    /**
     * Rebuild the grid's channel list.
     *
     * **Serialised, and the newest wins.** A guide sync writes in batches and each batch changes the
     * stored programme count, so this is called again and again while one is running. It used to start
     * a fresh coroutine every time with nothing to stop the previous one; on the owner's television
     * six of them were in flight at once, each holding its own copy of the window, and the app died of
     * an OutOfMemoryError. Cancelling the one in progress makes the answer the latest one rather than
     * a race between six stale ones, and the mutex closes the gap while a cancelled load unwinds.
     */
    fun load() {
        val previous = loadJob
        loadJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            loadMutex.withLock { runLoad() }
        }
    }

    private suspend fun runLoad() {
        val loadStartedAt = android.os.SystemClock.elapsedRealtime()
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
            return
        }
        val playlistIds = activeSourceIds(settings, sourceDao, pid, MediaType.LIVE)
        val epgIds = epgSourceStore.getAll().map { it.id }
        // Channels come from the playlists; guide data is matched from BOTH the playlists' own EPG
        // (kept for compatibility) and the standalone EPG sources — by epgChannelId across all ids.
        val ids = playlistIds + epgIds

        if (playlistIds.isEmpty()) {
            _state.value = EpgUiState(loading = false, message = EpgMessage.AddPlaylist)
            return
        }

        val now = System.currentTimeMillis()
        val nowAligned = now - (now % HALF_HOUR_MS) // align to the half hour
        // Always retain two recent hours so the centered "now" marker has programmes on its left.
        // Catch-up channels can extend the same window farther back to their archive limit.
        val maxCatchupDays = channelDao.maxCatchupDays(playlistIds)
        val catchupLookbackMs = if (maxCatchupDays > 0)
            minOf(maxCatchupDays * DAY_MS, CATCHUP_LOOKBACK_CAP_MS) else 0L
        val lookbackMs = maxOf(GUIDE_VISIBLE_PAST_MS, catchupLookbackMs)
        val windowStart = nowAligned - lookbackMs
        // How far the grid can scroll forward is the SAME value that decides how much is stored.
        // Storing a week and only letting the user reach tomorrow would be half a feature — and the
        // 48-hour horizon this replaced is the reason the guide appeared to "run dry" at all.
        val windowEnd = nowAligned + settings.guideDaysToKeep.first() * DAY_MS

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
        // Which of the three conditions fired is the question Phase 6 has to answer — R5 says a
        // sync can re-trigger the whole-table scan through the count alone.
        val rebuildReason = when {
            !windowChanged -> "cache-reused"
            cachedWindow == null -> "first-load"
            cachedWindow != (windowStart to windowEnd) -> "window-moved"
            loadedSourceIds != ids -> "sources-changed"
            else -> "stored-count-changed"
        }
        loadedSourceIds = ids
        lastStored = stored
        if (windowChanged) {
            // Dropping the cached rows is the whole of it. There is no preload any more: the rows
            // on screen re-read themselves through the indexed per-channel query, which costs one
            // small query each instead of materialising the window. That is also why this is now
            // safe to run repeatedly during a sync — it is a map clear, not a 349,077-row read.
            cachedWindow = windowStart to windowEnd
            clearRows()
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

        tv.own.owntv.core.CorePerf.log {
            "guide_load channels=${channels.size} rawChannels=${rawChannels.size} " +
                "stored=$stored guideChannels=$guideChannels sources=${ids.size} " +
                "rebuilt=$windowChanged reason=$rebuildReason cachedRows=${cachedRowCount()} " +
                "lookbackH=${lookbackMs / 3_600_000} " +
                "totalMs=${android.os.SystemClock.elapsedRealtime() - loadStartedAt}"
        }

        // A second background pass used to merge the older catch-up history in afterwards, because
        // the blocking pass could not afford the whole lookback. Both are gone: a row is read for
        // the full window in one query, so the history is simply there when the row is drawn.
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
        val resolver = cust.epgMatchResolver
        val byKey = auto.associateBy { CustomizeKeys.channel(it) }
        // Override the epg id of channels already in the list. Through the resolver, so a match made
        // before the playlist was deleted and re-added still applies under the channel's new key.
        val overridden = auto.map { ch -> resolver.epgIdFor(ch)?.let { ch.copy(epgChannelId = it) } ?: ch }.toMutableList()
        // Add matched channels that didn't auto-appear. Resolve them with ONE bulk query (all channels keyed
        // by their customize key) instead of two DB lookups per match — the old per-match resolveChannel was a
        // query-storm that dominated guide load time once a lot of channels had been smart-matched.
        val missingKeys = matches.keys.filter { it !in cust.hiddenItems }
        if (missingKeys.isNotEmpty()) {
            // Resolve only the matched channels (their key's tail is the remoteId/Xtream stream id) in one
            // query, instead of loading the entire channel table — that table-load was seconds on big lists.
            // In batches: SQLite rejects a statement with more than 999 bound parameters on older
            // Android, and someone who has matched a thousand channels by hand would otherwise crash
            // the app on every guide load. Still one query per batch, not the two per match this
            // replaced.
            //
            // Grouped by the key's TAIL rather than the whole key: the stored key carries the source id
            // the match was made under, which is not the one the channel has after a re-import. Only an
            // unambiguous tail is used — two playlists carrying one provider id must not pick one.
            val remoteIds = missingKeys.mapNotNull { CustomizeKeys.tailOf(it).takeIf { r -> r.isNotEmpty() } }.distinct()
            val resolved = remoteIds.chunked(QUERY_CHUNK)
                .flatMap { channelDao.findByRemoteIds(playlistIds, it) }
                .groupBy { it.remoteId ?: it.name }
            for (key in missingKeys) {
                val epgId = matches[key] ?: continue
                val ch = resolved[CustomizeKeys.tailOf(key)]?.singleOrNull() ?: continue
                if (CustomizeKeys.channel(ch) in byKey) continue // already in the list, already overridden
                val name = cust.itemNames[key] ?: ch.name
                if (query.isNotBlank() && !name.contains(query, ignoreCase = true)) continue
                overridden.add(ch.copy(epgChannelId = epgId, name = name))
            }
        }
        return overridden
    }

    /** Guide channels for the manual "Match EPG" picker, ranked so ones resembling [channelName]
     *  come first. The candidate set is core's and is filtered by no source — see [GuideCandidates]. */
    suspend fun availableEpgChannels(channelName: String, query: String): List<GuideCandidate> {
        currentProfileId() ?: return emptyList()
        return guideCandidates.forPicker(channelName, query)
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    companion object {
        // A sync writes the guide in batches, so every batch is reported. This must be longer than
        // the gap between two batch writes, or it coalesces nothing and the grid reloads per batch —
        // which is exactly what it used to do. Measured on the owner's television (2026-09-18):
        // batches land 2.3-6.1 s apart, and 1,500 ms never once coalesced them. 10 s clears the
        // longest observed gap with room to spare; a background refresh showing up ten seconds after
        // it settles is not something anyone is waiting on.
        private const val GUIDE_DATA_SETTLE_MS = 10_000L
        private const val HALF_HOUR_MS = 30L * 60 * 1000
        private const val GUIDE_VISIBLE_PAST_MS = 2L * 60 * 60 * 1000
        // How many guide rows to keep read. A screen shows ~8; this is generous enough that scrolling
        // back never re-reads, and small enough that the grid's memory does not grow with the
        // catalogue. ~100 programmes a row, so a few megabytes at the cap.
        private const val MAX_CACHED_ROWS = 240
        // Rows read ahead of the one that came into view. Roughly a screen's worth on a television,
        // which is what the guide plan asked to start with; the Phase 0 numbers decide whether it
        // should be two screens, so do not raise it on a hunch.
        private const val PREFETCH_AHEAD = 10
        private const val DAY_MS = 24L * 60 * 60 * 1000
        // How far back the Guide may extend for catch-up (must stay within EpgRepository's retention).
        private const val CATCHUP_LOOKBACK_CAP_MS = 7L * 24 * 60 * 60 * 1000
        // Generous safety bound only (rows load lazily, so this is about the channel list itself).
        private const val MAX_CHANNELS = 20_000
        // Cap the candidate set the bulk matcher scans against (keeps the O(channels×candidates) scan bounded).
        // Bound values sent to a single `IN (...)`. SQLite's parameter ceiling is 999 on older
        // Android; core chunks its own bulk lookups at the same size.
        private const val QUERY_CHUNK = 500
    }
}
