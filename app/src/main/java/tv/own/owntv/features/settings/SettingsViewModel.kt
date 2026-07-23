@file:OptIn(ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.core.sync.withRemainderNote
import tv.own.owntv.core.sync.work.CatalogSyncState
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.util.friendlySyncError
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.features.settings.data.ChNavLimits
import tv.own.owntv.features.settings.data.EpgAutoRefresh
import tv.own.owntv.features.settings.data.PlaylistAutoRefresh
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.features.settings.data.SourceExpiryStatus
import tv.own.owntv.features.settings.data.parseStalkerExpiry
import tv.own.owntv.ui.theme.AccentColor
import tv.own.owntv.ui.theme.ThemeMode
import tv.own.owntv.ui.theme.UiZoom

/** Phase 13 — manage IPTV sources (list / add / re-sync / delete) for the active profile. */
class SettingsViewModel(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val sourceRepository: SourceRepository,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityObserver,
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
    private val importFinalizer: tv.own.owntv.core.sync.ImportFinalizer,
    private val channelDao: tv.own.owntv.core.database.dao.ChannelDao,
    private val movieDao: tv.own.owntv.core.database.dao.MovieDao,
    private val seriesDao: tv.own.owntv.core.database.dao.SeriesDao,
    private val historyDao: tv.own.owntv.core.database.dao.HistoryDao,
    private val progressDao: tv.own.owntv.core.database.dao.ProgressDao,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val epgSourceStore: tv.own.owntv.core.epg.EpgSourceStore,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val metadataProvider: tv.own.owntv.core.metadata.MetadataProvider,
    private val stalkerAuth: tv.own.owntv.core.stalker.StalkerAuthManager,
    private val stalkerClient: tv.own.owntv.core.stalker.StalkerClient,
    private val xtreamClient: tv.own.owntv.core.parser.XtreamClient,
    private val companion: tv.own.owntv.core.companion.CompanionController,
) : ViewModel() {
    companion object {
        private const val TAG = "OwnTVHome"

        /** Sentinel session key for pre-save "Test connection" handshakes (no real source id yet). */
        private const val STALKER_TEST_SOURCE_ID = -1L
    }

    // ---- Remote (companion) add-source: a LAN web form fills the Add Source screen from a phone. ----
    /** Server lifecycle (Idle / Starting / Listening with PIN+QR / Failed) for the Remote screen. */
    val remoteState get() = companion.state

    /** Live submission stream — the Remote screen collects it to hand off to the Manual form. */
    val remotePayloads get() = companion.payloads

    /** Retained last submission, so the Manual form pre-fills even after the Remote screen left. */
    val remotePayload get() = companion.lastPayload

    fun startRemoteListener(port: Int) = companion.start(port)
    fun stopRemoteListener() = companion.stop()
    fun consumeRemotePayload() = companion.consumePayload()

    // Semi-auto EPG: after a playlist import, if the playlist has a guide URL we offer to sync the EPG now
    // (instead of the old slow auto-sync). "Sync now" shows a live programme count, just like the import.
    private var pendingEpgSource: SourceEntity? = null
    private val _epgSync = MutableStateFlow<EpgSyncUi>(EpgSyncUi.Hidden)
    val epgSync: StateFlow<EpgSyncUi> = _epgSync.asStateFlow()

    fun syncPendingEpg() {
        val src = pendingEpgSource ?: return
        viewModelScope.launch { runSemiAutoEpgSync(src, epgRepository, epgSourceStore) { _epgSync.value = it } }
    }

    /** Skip (from the prompt) or acknowledge (after Done) — either way, close the EPG flow. */
    fun dismissPendingEpg() { pendingEpgSource = null; _epgSync.value = EpgSyncUi.Hidden }

    /** Clear the active profile's watch history (the "recently watched" / continue rows). #26
     *  [type] null = everything; otherwise just LIVE / MOVIE / SERIES. */
    fun clearWatchHistory(type: tv.own.owntv.core.model.MediaType? = null) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            if (pid < 0) return@launch
            if (type == null) {
                historyDao.clear(pid)
                progressDao.clearProfile(pid) // also wipe resume positions → empties Home's continue-watching
            } else {
                historyDao.clearType(pid, type)
                // Home's Movies/Series continue-watching comes from the resume (progress) table, not history;
                // series progress is stored under EPISODE. Live has no resume progress to clear.
                when (type) {
                    tv.own.owntv.core.model.MediaType.MOVIE ->
                        progressDao.clearProfileType(pid, tv.own.owntv.core.model.MediaType.MOVIE)
                    tv.own.owntv.core.model.MediaType.SERIES ->
                        progressDao.clearProfileType(pid, tv.own.owntv.core.model.MediaType.EPISODE)
                    else -> Unit
                }
            }
            // Rebuild the Android TV home cards so the cleared items also leave the system Continue Watching row.
            runCatching { launcherIntegrationRepository.refreshProfile(pid) }
        }
    }

    /** Stored EPG programme count for a source — the row shows it as the EPG status.
     *  Throttled (C2): an EPG sync commits in batches; each batch would otherwise re-run a
     *  COUNT(*) over the largest table mid-write. */
    fun epgCount(sourceId: Long): kotlinx.coroutines.flow.Flow<Int> =
        epgDao.countForSource(sourceId).throttleLatest()

    /** Content counts (channels/movies/series) for a source — shown on each Playlists row. */
    fun contentCounts(sourceId: Long): kotlinx.coroutines.flow.Flow<SyncCounts> =
        catalogSyncScheduler.observeSync(sourceId)
            .onStart { emit(CatalogSyncState.Idle) }
            .filter { !it.isActive }
            .map { importFinalizer.contentCounts(sourceId) }

    fun syncState(sourceId: Long): kotlinx.coroutines.flow.Flow<CatalogSyncState> =
        catalogSyncScheduler.observeSync(sourceId)

    sealed interface ImportState {
        data object Idle : ImportState
        data object Running : ImportState
        /** [summary] is the per-type breakdown, e.g. "40K channels · 100K movies · 30K series synced". */
        data class Success(val summary: String) : ImportState
        data class Failed(val message: String) : ImportState
    }

    val sources: StateFlow<List<SourceEntity>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else sourceRepository.observeSources(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Subscription expiry per source id — the "Expires …" note in the Manage-sources row (Phase F).
     * Xtream reads `user_info.exp_date` from the panel; Stalker reads `account_info`/`get_profile`
     * (see [stalkerExpiryOf]). M3U is a plain playlist file with no account concept — never listed.
     * Fetched once per source per ViewModel lifetime (in-memory cache; only while the screen is
     * subscribed); any failure simply leaves the line off the row.
     */
    private val expiryCache = java.util.concurrent.ConcurrentHashMap<Long, SourceExpiryStatus>()
    val sourceExpiry: StateFlow<Map<Long, SourceExpiryStatus>> = sources
        .map { list ->
            val out = HashMap<Long, SourceExpiryStatus>()
            for (s in list) {
                if (s.type != tv.own.owntv.core.model.SourceType.XTREAM &&
                    s.type != tv.own.owntv.core.model.SourceType.STALKER
                ) continue
                val value = expiryCache[s.id] ?: fetchExpiry(s)?.also { expiryCache[s.id] = it } ?: continue
                out[s.id] = value
            }
            out
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private suspend fun fetchExpiry(s: SourceEntity): SourceExpiryStatus? = runCatching {
        when (s.type) {
            tv.own.owntv.core.model.SourceType.XTREAM -> xtreamClient.accountExpiryMs(s)?.let { expiryMs ->
                val formatted = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(expiryMs))
                SourceExpiryStatus(label = formatted, isExpired = System.currentTimeMillis() > expiryMs)
            }
            tv.own.owntv.core.model.SourceType.STALKER ->
                s.mac?.let { tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(it) }?.let { mac ->
                    val creds = tv.own.owntv.core.stalker.StalkerCredentials(s.id, s.url, mac, s.userAgent)
                    stalkerAuth.withAuthRetry(creds) { session ->
                        val info = runCatching {
                            stalkerClient.getAccountInfo(session.apiBase, mac, session.token, creds.userAgent)
                        }.getOrDefault(emptyMap())
                        val label = stalkerExpiryOf(info) ?: stalkerExpiryOf(session.profile)
                        label?.let { parseStalkerExpiry(it) }
                    }
                }
            else -> null
        }
    }.getOrNull()

    /** How many of the active profile's channels advertise catch-up — for the Catch-up settings note. */
    val catchupChannelCount: StateFlow<Int> = sources
        .flatMapLatest { srcs ->
            val ids = srcs.map { it.id }
            kotlinx.coroutines.flow.flow { emit(if (ids.isEmpty()) 0 else channelDao.countCatchup(ids)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Configured download folder ("" = app-specific storage). */
    val downloadRoot: StateFlow<String> = settings.downloadRoot
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setDownloadRoot(path: String) {
        viewModelScope.launch { settings.setDownloadRoot(path) }
    }

    /** The source marked as default/active (shown in the sidebar). */
    val defaultSourceId: StateFlow<Long> = settings.defaultSourceId
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    fun setDefaultSource(id: Long) {
        viewModelScope.launch { settings.setDefaultSource(id) }
    }

    val livePreviewEnabled: StateFlow<Boolean> = settings.livePreviewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setLivePreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLivePreviewEnabled(enabled) }
    }

    val livePreviewAudio: StateFlow<Boolean> = settings.livePreviewAudio
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setLivePreviewAudio(enabled: Boolean) {
        viewModelScope.launch { settings.setLivePreviewAudio(enabled) }
    }

    val hdrEnabled: StateFlow<Boolean> = settings.hdrEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setHdrEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHdrEnabled(enabled) }
    }

    val surroundSound: StateFlow<Boolean> = settings.surroundSound
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setSurroundSound(enabled: Boolean) {
        viewModelScope.launch { settings.setSurroundSound(enabled) }
    }

    val autoPlayNext: StateFlow<Boolean> = settings.autoPlayNext
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAutoPlayNext(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoPlayNext(enabled) }
    }

    val catchupTimezone: StateFlow<SettingsRepository.CatchupTimezone> = settings.catchupTimezone
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.CatchupTimezone.MANUAL)

    val catchupOffsetMinutes: StateFlow<Int> = settings.catchupOffsetMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val catchupOffsetRangeMinutes: IntRange = settings.catchupOffsetRangeMinutes

    fun setCatchupTimezone(mode: SettingsRepository.CatchupTimezone) {
        viewModelScope.launch { settings.setCatchupTimezone(mode) }
    }

    /** Nudge the manual UTC offset by [deltaMinutes] (the picker's − / + steps), clamped to range. */
    fun adjustCatchupOffset(deltaMinutes: Int) {
        viewModelScope.launch { settings.setCatchupOffsetMinutes(catchupOffsetMinutes.value + deltaMinutes) }
    }

    val androidTvHomeEnabled: StateFlow<Boolean> = settings.androidTvHomeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAndroidTvHomeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "setAndroidTvHomeEnabled enabled=$enabled")
            settings.setAndroidTvHomeEnabled(enabled)
            if (enabled) {
                refreshActiveTvHome(allowBrowsableRequest = true)
            } else {
                profileDao.getAllOnce().forEach { profile -> launcherIntegrationRepository.clearProfile(profile.id) }
            }
        }
    }

    /** Status of the manual "Refresh now" so the UI can show Rebuilding… → Done. */
    enum class TvHomeRefresh { IDLE, REFRESHING, DONE }
    private val _tvHomeRefresh = MutableStateFlow(TvHomeRefresh.IDLE)
    val tvHomeRefresh: StateFlow<TvHomeRefresh> = _tvHomeRefresh.asStateFlow()

    fun refreshAndroidTvHome() {
        if (_tvHomeRefresh.value == TvHomeRefresh.REFRESHING) return
        viewModelScope.launch {
            _tvHomeRefresh.value = TvHomeRefresh.REFRESHING
            runCatching { refreshActiveTvHome(allowBrowsableRequest = true) }
            _tvHomeRefresh.value = TvHomeRefresh.DONE
            kotlinx.coroutines.delay(1_800)
            if (_tvHomeRefresh.value == TvHomeRefresh.DONE) _tvHomeRefresh.value = TvHomeRefresh.IDLE
        }
    }

    // --- Video Player Settings ---
    val hwDecoding: StateFlow<Boolean> = settings.hwDecoding.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setHwDecoding(enabled: Boolean) { viewModelScope.launch { settings.setHwDecoding(enabled) } }

    val vodPreferExo: StateFlow<Boolean> = settings.vodPreferExo.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setVodPreferExo(enabled: Boolean) { viewModelScope.launch { settings.setVodPreferExo(enabled) } }

    val measuredStreamStats: StateFlow<Boolean> = settings.measuredStreamStats.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setMeasuredStreamStats(enabled: Boolean) { viewModelScope.launch { settings.setMeasuredStreamStats(enabled) } }

    val externalPlayer: StateFlow<Boolean> = settings.externalPlayer.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setExternalPlayer(enabled: Boolean) { viewModelScope.launch { settings.setExternalPlayer(enabled) } }

    val updateCheckOnStart: StateFlow<Boolean> =
        settings.updateCheckOnStart.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setUpdateCheckOnStart(enabled: Boolean) { viewModelScope.launch { settings.setUpdateCheckOnStart(enabled) } }

    val resumeLastChannel: StateFlow<Boolean> =
        settings.resumeLastChannel.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setResumeLastChannel(enabled: Boolean) { viewModelScope.launch { settings.setResumeLastChannel(enabled) } }

    // Per-profile startup landing (v4.0.0): Home / Last channel / Live·Favorites.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val startupMode: StateFlow<tv.own.owntv.features.settings.data.StartupMode> =
        settings.activeProfileId
            .flatMapLatest { settings.startupMode(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.features.settings.data.StartupMode.HOME)
    fun setStartupMode(mode: tv.own.owntv.features.settings.data.StartupMode) {
        viewModelScope.launch { settings.setStartupMode(settings.activeProfileId.first(), mode) }
    }

    val resumeMode: StateFlow<SettingsRepository.ResumeMode> =
        settings.resumeMode.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.ResumeMode.ASK)
    fun setResumeMode(name: String) {
        viewModelScope.launch {
            settings.setResumeMode(runCatching { SettingsRepository.ResumeMode.valueOf(name) }.getOrDefault(SettingsRepository.ResumeMode.ASK))
        }
    }

    val defaultZoom: StateFlow<String> = settings.defaultZoom.stateIn(viewModelScope, SharingStarted.Eagerly, "FIT")
    fun setDefaultZoom(name: String) { viewModelScope.launch { settings.setDefaultZoom(name) } }

    val subtitleScale: StateFlow<Float> = settings.subtitleScale.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    fun setSubtitleScale(scale: Float) { viewModelScope.launch { settings.setSubtitleScale(scale) } }

    val audioDelayMs: StateFlow<Int> = settings.audioDelayMs.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    fun setAudioDelayMs(ms: Int) { viewModelScope.launch { settings.setAudioDelayMs(ms) } }

    // --- CH+- key paging (browse panels): master toggle + per-direction skip counts ---
    val chNavEnabled: StateFlow<Boolean> = settings.chNavEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setChNavEnabled(enabled: Boolean) { viewModelScope.launch { settings.setChNavEnabled(enabled) } }
    val chNavUpSkip: StateFlow<Int> = settings.chNavUpSkip.stateIn(viewModelScope, SharingStarted.Eagerly, ChNavLimits.DEFAULT_SKIP)
    fun setChNavUpSkip(n: Int) { viewModelScope.launch { settings.setChNavUpSkip(n) } }
    val chNavDownSkip: StateFlow<Int> = settings.chNavDownSkip.stateIn(viewModelScope, SharingStarted.Eagerly, ChNavLimits.DEFAULT_SKIP)
    fun setChNavDownSkip(n: Int) { viewModelScope.launch { settings.setChNavDownSkip(n) } }

    val preferredAudioLang: StateFlow<String> = settings.preferredAudioLang.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setPreferredAudioLang(lang: String) { viewModelScope.launch { settings.setPreferredAudioLang(lang) } }

    val preferredSubLang: StateFlow<String> = settings.preferredSubLang.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setPreferredSubLang(lang: String) { viewModelScope.launch { settings.setPreferredSubLang(lang) } }

    // --- Personalization (theme / accent / UI zoom) ---
    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }

    val accent: StateFlow<AccentColor> = settings.accent.stateIn(viewModelScope, SharingStarted.Eagerly, AccentColor.TEAL)
    fun setAccent(accent: AccentColor) { viewModelScope.launch { settings.setAccent(accent) } }

    /** Custom accent hex ("#52DBC8"); blank = the preset is in effect. */
    val customAccent: StateFlow<String> = settings.customAccent.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setCustomAccent(hex: String) { viewModelScope.launch { settings.setCustomAccent(hex) } }

    // --- Nav menu customization (v4.3.0) ---
    /** STATIC (default): user picks which icons to hide. DYNAMIC: icons adapt to the active playlist. */
    val navMenuMode: StateFlow<tv.own.owntv.features.settings.data.SettingsRepository.NavMenuMode> =
        settings.navMenuMode.stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.features.settings.data.SettingsRepository.NavMenuMode.STATIC)
    fun setNavMenuMode(mode: tv.own.owntv.features.settings.data.SettingsRepository.NavMenuMode) {
        viewModelScope.launch { settings.setNavMenuMode(mode) }
    }

    /** Browse sections the user has hidden (STATIC mode only). */
    val navMenuHidden: StateFlow<Set<tv.own.owntv.features.shell.MainSection>> = settings.navMenuHidden
        .map { raw -> raw.mapNotNull { name -> runCatching { tv.own.owntv.features.shell.MainSection.valueOf(name) }.getOrNull() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    fun setNavSectionHidden(section: tv.own.owntv.features.shell.MainSection, hidden: Boolean) {
        viewModelScope.launch {
            val current = navMenuHidden.first()
            val next = if (hidden) current + section else current - section
            settings.setNavMenuHidden(next.map { it.name }.toSet())
        }
    }

    /**
     * The DYNAMIC-mode icon set the active playlist *would* show (v4.3.0). Mirrors [ShellViewModel]'s rail
     * computation so the Nav menu settings screen's read-only DYNAMIC rows report the same state the rail
     * reflects. Re-emits automatically as content arrives (Room invalidates the count flows on every write).
     */
    val dynamicCaps: StateFlow<Set<tv.own.owntv.features.shell.MainSection>> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(tv.own.owntv.features.shell.MainSection.allBrowse)
            else sourceRepository.observeSources(pid)
                .flatMapLatest { sources -> settings.defaultSourceId.flatMapLatest { defaultId -> capsFlow(sources, defaultId) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.features.shell.MainSection.allBrowse)

    private fun capsFlow(
        sources: List<SourceEntity>,
        defaultId: Long,
    ): kotlinx.coroutines.flow.Flow<Set<tv.own.owntv.features.shell.MainSection>> {
        val scoped = if (defaultId > 0) sources.filter { it.id == defaultId } else sources
        if (scoped.isEmpty()) return flowOf(setOf(tv.own.owntv.features.shell.MainSection.HOME))
        val liveIds = scoped.filter { it.syncLive }.map { it.id }
        val movieIds = scoped.filter { it.syncMovies }.map { it.id }
        val seriesIds = scoped.filter { it.syncSeries }.map { it.id }
        val empty = listOf(-1L)
        return combine(
            channelDao.countAll(liveIds.ifEmpty { empty }),
            movieDao.countAll(movieIds.ifEmpty { empty }),
            seriesDao.countAll(seriesIds.ifEmpty { empty }),
        ) { channels, movies, series ->
            tv.own.owntv.features.shell.MainSection.dynamicVisible(
                hasLive = liveIds.isNotEmpty() && channels > 0,
                hasMovies = movieIds.isNotEmpty() && movies > 0,
                hasSeries = seriesIds.isNotEmpty() && series > 0,
            )
        }
    }

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent.stateIn(viewModelScope, SharingStarted.Eagerly, UiZoom.DEFAULT)
    fun setUiZoom(percent: Int) { viewModelScope.launch { settings.setUiZoomPercent(UiZoom.clamp(percent)) } }

    // Docked mini-player: size (% of screen width) and screen position.
    val miniPlayerSizePct: StateFlow<Int> =
        settings.miniPlayerSizePct.stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.player.MiniPlayerSize.DEFAULT)
    fun setMiniPlayerSize(percent: Int) { viewModelScope.launch { settings.setMiniPlayerSizePct(percent) } }

    val miniPlayerPosition: StateFlow<tv.own.owntv.player.MiniPlayerPosition> =
        settings.miniPlayerPosition
            .map { tv.own.owntv.player.MiniPlayerPosition.fromName(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.player.MiniPlayerPosition.DEFAULT)
    fun setMiniPlayerPosition(position: tv.own.owntv.player.MiniPlayerPosition) {
        viewModelScope.launch { settings.setMiniPlayerPosition(position.name) }
    }

    // Live TV latency (#72): preset + custom seconds.
    val liveLatencyMode: StateFlow<tv.own.owntv.features.settings.data.LiveLatency> =
        settings.liveLatencyMode
            .map { tv.own.owntv.features.settings.data.LiveLatency.fromName(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.features.settings.data.LiveLatency.DEFAULT)
    fun setLiveLatencyMode(mode: tv.own.owntv.features.settings.data.LiveLatency) {
        viewModelScope.launch { settings.setLiveLatencyMode(mode.name) }
    }

    val liveLatencyCustomSecs: StateFlow<Int> =
        settings.liveLatencyCustomSecs.stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.features.settings.data.LiveBuffer.CUSTOM_DEFAULT)
    fun setLiveLatencyCustomSecs(secs: Int) {
        viewModelScope.launch { settings.setLiveLatencyCustomSecs(secs) }
    }

    val animationLevel: StateFlow<tv.own.owntv.ui.theme.AnimationLevel> =
        settings.animationLevel.stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.ui.theme.AnimationLevel.FULL)
    fun setAnimationLevel(level: tv.own.owntv.ui.theme.AnimationLevel) { viewModelScope.launch { settings.setAnimationLevel(level) } }

    // Weather chip: visibility toggle + manual location override (for VPN users).
    val weatherEnabled: StateFlow<Boolean> =
        settings.weatherEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setWeatherEnabled(enabled: Boolean) { viewModelScope.launch { settings.setWeatherEnabled(enabled) } }
    val weatherLocation: StateFlow<String> =
        settings.weatherLocation.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setWeatherLocation(location: String) { viewModelScope.launch { settings.setWeatherLocation(location) } }
    val weatherFahrenheit: StateFlow<Boolean> =
        settings.weatherFahrenheit.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setWeatherFahrenheit(fahrenheit: Boolean) { viewModelScope.launch { settings.setWeatherFahrenheit(fahrenheit) } }

    /** Per-source playlist auto-refresh selection (Off / Startup / staleness threshold). */
    val playlistAutoRefresh: StateFlow<Map<Long, PlaylistAutoRefresh>> = settings.playlistAutoRefresh
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Per-source EPG auto-refresh selection (Off / Startup / staleness threshold). */
    val epgAutoRefresh: StateFlow<Map<Long, EpgAutoRefresh>> = settings.epgAutoRefresh
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setPlaylistAutoRefresh(sourceId: Long, mode: PlaylistAutoRefresh) {
        viewModelScope.launch { settings.setPlaylistAutoRefresh(sourceId, mode) }
    }

    fun setEpgAutoRefresh(sourceId: Long, mode: EpgAutoRefresh) {
        viewModelScope.launch { settings.setEpgAutoRefresh(sourceId, mode) }
    }

    /**
     * Edit an existing source's settings. When enabledScope changes (or lastSyncAt is still null),
     * cancel any in-flight sync and enqueue a scoped resync so completion stamps and newly-On
     * sections refresh. Cache is never deleted on Off.
     */
    fun updateSource(
        id: Long,
        name: String,
        urlOrServer: String,
        user: String,
        pass: String,
        userAgent: String,
        epgUrl: String,
        autoRefresh: PlaylistAutoRefresh,
        isDefault: Boolean = false,
        mac: String = "",
        syncLive: Boolean = true,
        syncMovies: Boolean = true,
        syncSeries: Boolean = true,
    ) {
        viewModelScope.launch {
            val existing = sourceDao.getById(id) ?: return@launch
            // A Stalker edit re-canonicalizes the MAC; a garbled edit keeps the stored one. On
            // MAC/URL change the cached portal session is stale — drop it so the next call re-handshakes.
            val newMac = tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(mac) ?: existing.mac
            if (existing.type == tv.own.owntv.core.model.SourceType.STALKER) stalkerAuth.invalidate(id)
            val scopeChanged =
                existing.syncLive != syncLive || existing.syncMovies != syncMovies || existing.syncSeries != syncSeries
            val scopeTurnedOn =
                (!existing.syncLive && syncLive) || (!existing.syncMovies && syncMovies) || (!existing.syncSeries && syncSeries)
            val updated = existing.copy(
                name = name.ifBlank { existing.name },
                url = urlOrServer.trim().ifBlank { existing.url },
                username = user.trim().takeIf { it.isNotBlank() } ?: existing.username,
                password = pass.takeIf { it.isNotBlank() } ?: existing.password,
                mac = newMac,
                userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
                syncLive = syncLive,
                syncMovies = syncMovies,
                syncSeries = syncSeries,
            )
            sourceRepository.updateSource(updated)
            settings.setPlaylistAutoRefresh(id, autoRefresh)
            // Apply the "Default playlist" toggle: on → this becomes the active playlist; off → if this was
            // the default, clear it back to All. Leaves another playlist's default untouched.
            when {
                isDefault -> settings.setDefaultSource(id)
                settings.defaultSourceId.first() == id -> settings.setDefaultSource(-1L)
            }
            if (scopeChanged) {
                catalogSyncScheduler.cancelSync(id)
            }
            if (scopeTurnedOn || updated.lastSyncAt == null) {
                val counts = importFinalizer.contentCounts(id)
                catalogSyncScheduler.enqueueSync(
                    id,
                    reason = "scope_edit",
                    contentTypes = SyncContentTypes.enabledFor(updated),
                    baseItemCount = counts.channels + counts.movies + counts.series,
                )
            }
            // Hidden sections must drop from the Android TV launcher immediately.
            runCatching { refreshActiveTvHome(allowBrowsableRequest = true) }
        }
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /** The last source whose sync failed — persisted so AddSourceScreen can pre-fill the form
     *  instead of making the user re-type everything on the remote after a typo. */
    private var _lastFailedSource: SourceEntity? = null
    val lastFailedSource: SourceEntity? get() = _lastFailedSource

    private val _progress = MutableStateFlow<ImportStage?>(null)
    val progress: StateFlow<ImportStage?> = _progress.asStateFlow()

    private var importJob: Job? = null

    fun addXtream(
        name: String,
        server: String,
        user: String,
        pass: String,
        userAgent: String = "",
        epgUrl: String = "",
        autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
        live: SyncScopeChoice = SyncScopeChoice.Now,
        movies: SyncScopeChoice = SyncScopeChoice.Now,
        series: SyncScopeChoice = SyncScopeChoice.Now,
        isDefault: Boolean = false,
    ) {
        val enabled = SyncContentTypes.fromChoices(live, movies, series)
        val priority = SyncContentTypes.priorityFromChoices(live, movies, series)
        runImport(
            autoRefresh, priority, enabledScope = enabled, enqueueRemainder = true,
            requiresNetwork = true, makeDefault = isDefault,
        ) { pid ->
            sourceRepository.addXtreamSource(
                pid, name.ifBlank { "My IPTV" }, server.trim(), user.trim(), pass,
                userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl.trim().takeIf { it.isNotBlank() },
                syncLive = enabled.live, syncMovies = enabled.movies, syncSeries = enabled.series,
            )
        }
    }

    // ---- Stalker portal (plan Phase B) ----

    sealed interface StalkerTestState {
        data object Idle : StalkerTestState
        data object Testing : StalkerTestState
        data class Ok(val summary: String) : StalkerTestState
        data class Failed(val message: String) : StalkerTestState
    }

    private val _stalkerTest = MutableStateFlow<StalkerTestState>(StalkerTestState.Idle)
    val stalkerTest: StateFlow<StalkerTestState> = _stalkerTest.asStateFlow()

    fun resetStalkerTest() {
        _stalkerTest.value = StalkerTestState.Idle
    }

    /** "Test connection": portal handshake + get_profile with the form's values, no source saved. */
    fun testStalker(portalUrl: String, mac: String, userAgent: String = "") {
        val canonicalMac = tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(mac)
        if (canonicalMac == null) {
            _stalkerTest.value = StalkerTestState.Failed("Invalid MAC address — use AA:BB:CC:DD:EE:FF")
            return
        }
        viewModelScope.launch {
            _stalkerTest.value = StalkerTestState.Testing
            _stalkerTest.value = try {
                if (!connectivity.isOnlineNow()) {
                    StalkerTestState.Failed(friendlySyncError(null, online = false))
                } else {
                    val session = stalkerAuth.testConnection(
                        tv.own.owntv.core.stalker.StalkerCredentials(
                            sourceId = STALKER_TEST_SOURCE_ID,
                            portalUrl = portalUrl.trim(),
                            mac = canonicalMac,
                            userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                        ),
                    )
                    val endpoint = session.apiBase.substringAfter("://").substringAfter('/', "portal")
                    // Subscription expiry line (Phase F, §1.2): optional account_info call. Portals
                    // surface expiry under varying keys (sometimes even `phone`) — best effort only.
                    val expiry = runCatching {
                        val info = stalkerClient.getAccountInfo(
                            session.apiBase, canonicalMac, session.token,
                            userAgent.trim().takeIf { it.isNotBlank() },
                        )
                        stalkerExpiryOf(info) ?: stalkerExpiryOf(session.profile)
                    }.getOrNull()
                    val expiryLine = expiry?.let { " · expires $it" } ?: ""
                    StalkerTestState.Ok("Connected via $endpoint (${session.profile.size} profile fields)$expiryLine")
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                StalkerTestState.Failed(stalkerErrorMessage(e))
            }
        }
    }

    /**
     * Save a Stalker source. Verifies the portal handshake first (same as Test connection) so a
     * typo'd portal/MAC fails with a clear error instead of saving a dead source. Staged
     * automatically: live syncs in the foreground (one bulk get_all_channels), movies/series are
     * enqueued as the background remainder — Stalker VOD has no bulk endpoint (~14 items/page), so
     * blocking the user on that crawl would take minutes on large catalogs.
     */
    fun addStalker(
        name: String,
        portalUrl: String,
        mac: String,
        userAgent: String = "",
        autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
        isDefault: Boolean = false,
        live: SyncScopeChoice = SyncScopeChoice.Now,
        movies: SyncScopeChoice = SyncScopeChoice.Later,
        series: SyncScopeChoice = SyncScopeChoice.Later,
    ) {
        val canonicalMac = tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(mac)
        if (canonicalMac == null) {
            _importState.value = ImportState.Failed("Invalid MAC address — use AA:BB:CC:DD:EE:FF")
            return
        }
        val enabled = SyncContentTypes.fromChoices(live, movies, series)
        val priority = SyncContentTypes.priorityFromChoices(live, movies, series)
        runImport(
            autoRefresh, priority, enabledScope = enabled, enqueueRemainder = true,
            requiresNetwork = true, makeDefault = isDefault,
        ) { pid ->
            stalkerAuth.testConnection(
                tv.own.owntv.core.stalker.StalkerCredentials(
                    sourceId = STALKER_TEST_SOURCE_ID,
                    portalUrl = portalUrl.trim(),
                    mac = canonicalMac,
                    userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                ),
            )
            sourceRepository.addStalkerSource(
                pid, name.ifBlank { "My Portal" }, portalUrl.trim(), canonicalMac,
                userAgent.trim().takeIf { it.isNotBlank() },
                syncLive = enabled.live, syncMovies = enabled.movies, syncSeries = enabled.series,
            )
        }
    }

    /**
     * Pull a subscription end date out of a Stalker `account_info`/`get_profile` map. Portals are
     * inconsistent: proper `end_date`/`exp_date` keys, or a date-looking string stuffed into `phone`
     * (§1.2). Values like "0000-00-00", "null" or empty are ignored.
     */
    private fun stalkerExpiryOf(fields: Map<String, String>): String? {
        val direct = listOf("end_date", "exp_date", "expire_date", "expire_billing_date", "tariff_expired_date")
            .firstNotNullOfOrNull { key -> fields[key]?.trim()?.takeIf { it.looksLikeExpiryValue() } }
        if (direct != null) return direct
        // Some portals put the expiry text in `phone` — accept it only when it actually contains a date.
        return fields["phone"]?.trim()?.takeIf { it.looksLikeExpiryValue() && it.contains(Regex("\\d{4}|\\d{1,2}[./-]\\d{1,2}")) }
    }

    private fun String.looksLikeExpiryValue(): Boolean =
        isNotEmpty() && !equals("null", true) && !startsWith("0000") && this != "0"

    private fun stalkerErrorMessage(e: Exception): String = when {
        e is tv.own.owntv.core.stalker.StalkerClient.StalkerAuthException ->
            "Portal refused the login — check the MAC address (and the TV's date & time)."
        else -> friendlySyncError(e.message, connectivity.isOnlineNow())
    }

    fun addM3u(name: String, url: String, userAgent: String = "", epgUrl: String = "", autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF, isDefault: Boolean = false) = runImport(
        autoRefresh,
        requiresNetwork = !url.isLocalPlaylistPath(),
        makeDefault = isDefault,
    ) { pid ->
        sourceRepository.addM3uSource(
            pid, name.ifBlank { "My Playlist" }, url.trim(),
            userAgent.trim().takeIf { it.isNotBlank() },
            epgUrl.trim().takeIf { it.isNotBlank() },
        )
    }

    private fun runImport(
        autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
        contentTypes: SyncContentTypes = SyncContentTypes(),
        enabledScope: SyncContentTypes = SyncContentTypes(),
        enqueueRemainder: Boolean = false,
        requiresNetwork: Boolean = true,
        makeDefault: Boolean = false,
        addSource: suspend (Long) -> SourceEntity,
    ) {
        importJob?.cancel()
        val job = viewModelScope.launch {
            _importState.value = ImportState.Running
            _progress.value = null
            var source: SourceEntity? = null
            try {
                if (requiresNetwork && !connectivity.isOnlineNow()) {
                    _importState.value = ImportState.Failed(friendlySyncError(null, online = false))
                    return@launch
                }
                val pid = profileDao.resolveExistingProfileId(settings.activeProfileId.first()) ?: return@launch
                Log.d(TAG, "runImport profile=$pid autoRefresh=$autoRefresh")
                source = addSource(pid)
                val freshSync = source.lastSyncAt == null
                val remainder = if (enqueueRemainder) {
                    enabledScope.remainderAfter(contentTypes)
                } else {
                    SyncContentTypes(live = false, movies = false, series = false)
                }
                settings.setPlaylistAutoRefresh(source.id, autoRefresh)
                when (val r = sourceRepository.sync(source, onProgress = { _progress.value = it }, contentTypes = contentTypes)) {
                    is SyncResult.Success -> {
                        // Settings playlist add: content breakdown only (EPG syncs silently and is
                        // shown on the EPG Sources screen, per the separated-EPG design).
                        val counts = importFinalizer.finalize(source, deferIndexes = freshSync)
                        if (makeDefault) settings.setDefaultSource(source.id)
                        val syncedSource = sourceDao.getById(source.id) ?: source
                        Log.d(TAG, "runImport sync success sourceId=${source.id} profile=$pid")
                        if (enqueueRemainder) enqueueRemainderSync(source, contentTypes, enabledScope)
                        if (freshSync && !remainder.hasAny) catalogSyncScheduler.enqueueContentIndexBuild(reason = "fresh_add")
                        _lastFailedSource = null
                        _importState.value = ImportState.Success(
                            counts.summary(includeEpg = false).withWarnings(r).withRemainderNote(remainder),
                        )
                        // Offer a one-tap EPG sync if this playlist actually has a guide feed.
                        if (epgRepository.guideUrl(syncedSource) != null) {
                            pendingEpgSource = syncedSource
                            _epgSync.value = EpgSyncUi.Ask(syncedSource.name)
                        }
                        viewModelScope.launch { runCatching { refreshActiveTvHome(allowBrowsableRequest = true) } }
                    }
                    is SyncResult.Failed -> {
                        cleanupFailedAdd(source)
                        _importState.value = ImportState.Failed(friendlySyncError(r.message, connectivity.isOnlineNow()))
                    }
                    SyncResult.Cancelled -> {
                        cleanupFailedAdd(source)
                        _importState.value = ImportState.Idle
                    }
                }
            } catch (c: CancellationException) {
                cleanupFailedAdd(source)
                _importState.value = ImportState.Idle
                _progress.value = null
                throw c
            } catch (e: Exception) {
                cleanupFailedAdd(source)
                _importState.value = ImportState.Failed(friendlySyncError(e.message, connectivity.isOnlineNow()))
            }
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    private fun String.isLocalPlaylistPath(): Boolean =
        startsWith("/") || startsWith("file://") || startsWith("content://")

    /** Re-sync an existing source through WorkManager so it can continue after leaving this screen. */
    fun resync(source: SourceEntity) {
        Log.d(TAG, "resync enqueue sourceId=${source.id}")
        viewModelScope.launch {
            val counts = importFinalizer.contentCounts(source.id)
            catalogSyncScheduler.enqueueSync(
                source.id,
                reason = "manual_resync",
                contentTypes = SyncContentTypes.enabledOf(source),
                baseItemCount = counts.channels + counts.movies + counts.series,
            )
        }
    }

    fun cancelResync(source: SourceEntity) {
        Log.d(TAG, "resync cancel sourceId=${source.id}")
        catalogSyncScheduler.cancelSync(source.id)
    }

    fun getLastSyncStats(sourceId: Long): tv.own.owntv.core.sync.SyncRunStats? =
        sourceRepository.getLastSyncStats(sourceId)

    // Deleting a huge source (hundreds of thousands of cascaded rows) takes a while — surface it
    // per-row so the user can see the removal is in progress instead of a silently frozen list.
    private val _deletingSourceIds = MutableStateFlow<Set<Long>>(emptySet())
    val deletingSourceIds: StateFlow<Set<Long>> = _deletingSourceIds.asStateFlow()

    fun delete(source: SourceEntity) {
        if (source.id in _deletingSourceIds.value) return
        viewModelScope.launch {
            Log.d(TAG, "delete sourceId=${source.id}")
            _deletingSourceIds.value = _deletingSourceIds.value + source.id
            try {
                catalogSyncScheduler.cancelSync(source.id)
                stalkerAuth.invalidate(source.id)
                // NonCancellable: once confirmed, finish the cascade even if the user leaves the
                // screen mid-delete — an interrupted half-deleted source is worse than a short wait.
                withContext(NonCancellable) {
                    sourceRepository.deleteSource(source)
                    if (defaultSourceId.value == source.id) settings.setDefaultSource(-1L)
                    refreshActiveTvHome(allowBrowsableRequest = true)
                }
            } finally {
                _deletingSourceIds.value = _deletingSourceIds.value - source.id
            }
        }
    }

    fun resetImport() {
        _importState.value = ImportState.Idle
        _progress.value = null
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _importState.value = ImportState.Idle
        _progress.value = null
    }

    private suspend fun refreshActiveTvHome(allowBrowsableRequest: Boolean = true) {
        val pid = profileDao.resolveExistingProfileId(settings.activeProfileId.first()) ?: return
        Log.d(TAG, "refreshActiveTvHome profile=$pid allowBrowsable=$allowBrowsableRequest")
        launcherIntegrationRepository.refreshProfile(pid, allowBrowsableRequest)
    }

    private fun enqueueRemainderSync(source: SourceEntity, priority: SyncContentTypes, enabledScope: SyncContentTypes) {
        val remainder = enabledScope.remainderAfter(priority)
        if (remainder.hasAny) {
            // The priority pass + this remainder cover every enabled section, so a successful
            // remainder run must mark the source synced (SyncManager only stamps complete passes).
            catalogSyncScheduler.enqueueSync(source.id, reason = "add_remainder", contentTypes = remainder, completesInitialSync = true)
        }
    }

    private suspend fun cleanupFailedAdd(source: SourceEntity?) {
        if (source == null) return
        withContext(NonCancellable) {
            catalogSyncScheduler.cancelSync(source.id)
            runCatching { sourceRepository.deleteSource(source) }
            runCatching { settings.setPlaylistAutoRefresh(source.id, PlaylistAutoRefresh.OFF) }
        }
    }

    private fun String.withWarnings(result: SyncResult.Success): String =
        listOfNotNull(this, result.warningSummary()).joinToString("\n")

    // --- Global proxy (Approach 1 — one app-wide HTTP proxy) ---

    val proxyConfig: StateFlow<tv.own.owntv.core.network.ProxyConfig> = settings.proxyConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.network.ProxyConfig())

    fun saveProxy(enabled: Boolean, host: String, port: Int, username: String, password: String) {
        viewModelScope.launch { settings.saveProxy(enabled, host, port, username, password) }
    }

    sealed interface ProxyTestState {
        data object Idle : ProxyTestState
        data object Testing : ProxyTestState
        data class Ok(val millis: Long) : ProxyTestState
        data class Fail(val message: String) : ProxyTestState
    }

    private val _proxyTest = MutableStateFlow<ProxyTestState>(ProxyTestState.Idle)
    val proxyTest: StateFlow<ProxyTestState> = _proxyTest.asStateFlow()

    fun resetProxyTest() { _proxyTest.value = ProxyTestState.Idle }

    fun testProxy(host: String, port: Int, username: String, password: String) {
        if (_proxyTest.value == ProxyTestState.Testing) return
        val h = host.trim()
        if (h.isBlank() || port !in 1..65535) {
            _proxyTest.value = ProxyTestState.Fail("Enter a valid host and port (1–65535).")
            return
        }
        _proxyTest.value = ProxyTestState.Testing
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val proxy = java.net.Proxy(
                        java.net.Proxy.Type.HTTP,
                        java.net.InetSocketAddress.createUnresolved(h, port),
                    )
                    val builder = okHttpClient.newBuilder()
                        .proxy(proxy)
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    if (username.trim().isNotBlank()) {
                        builder.proxyAuthenticator { _, response ->
                            if (response.request.header("Proxy-Authorization") != null) return@proxyAuthenticator null
                            response.request.newBuilder()
                                .header("Proxy-Authorization", okhttp3.Credentials.basic(username.trim(), password))
                                .build()
                        }
                    } else {
                        builder.proxyAuthenticator(okhttp3.Authenticator.NONE)
                    }
                    val client = builder.build()
                    val request = okhttp3.Request.Builder()
                        .url("https://www.gstatic.com/generate_204")
                        .head()
                        .build()
                    val start = System.currentTimeMillis()
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful && resp.code != 204) {
                            throw java.io.IOException("Proxy returned HTTP ${resp.code}")
                        }
                    }
                    System.currentTimeMillis() - start
                }
            }
            _proxyTest.value = result.fold(
                onSuccess = { ProxyTestState.Ok(it) },
                onFailure = { ProxyTestState.Fail(friendlyProxyError(it)) },
            )
        }
    }

    // --- TMDB metadata enrichment (plan §4) — Phase M1 config + manual "look up title" test ---

    val metadataMode: StateFlow<tv.own.owntv.core.metadata.MetadataMode> =
        settings.metadataMode.stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)
    fun setMetadataMode(mode: tv.own.owntv.core.metadata.MetadataMode) { viewModelScope.launch { settings.setMetadataMode(mode) } }

    val tmdbApiKey: StateFlow<String> =
        settings.tmdbApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setTmdbApiKey(key: String) { viewModelScope.launch { settings.setTmdbApiKey(key) } }

    val metadataServerUrl: StateFlow<String> =
        settings.metadataServerUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setMetadataServerUrl(url: String) { viewModelScope.launch { settings.setMetadataServerUrl(url) } }

    /** Which access tier the current config resolves to — shown as the Metadata screen's status chip. */
    val metadataTier: StateFlow<tv.own.owntv.core.metadata.MetadataConfig.Tier> =
        settings.metadataConfigFlow
            .map { it.tier }
            .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.metadata.MetadataConfig.Tier.DEFAULT_WORKER)

    sealed interface MetadataTestState {
        data object Idle : MetadataTestState
        data object Testing : MetadataTestState
        /** Top match summary, e.g. "Oppenheimer (2023) · tmdb #872585". */
        data class Ok(val summary: String) : MetadataTestState
        data class Fail(val message: String) : MetadataTestState
    }

    private val _metadataTest = MutableStateFlow<MetadataTestState>(MetadataTestState.Idle)
    val metadataTest: StateFlow<MetadataTestState> = _metadataTest.asStateFlow()

    fun resetMetadataTest() { _metadataTest.value = MetadataTestState.Idle }

    /** Manual "look up title" through the configured tier — proves the plumbing end-to-end (M1 deliverable). */
    fun testMetadataLookup(title: String) {
        if (_metadataTest.value == MetadataTestState.Testing) return
        val q = title.trim()
        if (q.isEmpty()) {
            _metadataTest.value = MetadataTestState.Fail("Enter a title to look up.")
            return
        }
        _metadataTest.value = MetadataTestState.Testing
        viewModelScope.launch {
            val result = runCatching { metadataProvider.searchMovie(q) }
            _metadataTest.value = result.fold(
                onSuccess = { hits ->
                    val top = hits?.firstOrNull()
                    if (hits == null) {
                        MetadataTestState.Fail("Couldn't reach the metadata server — check network / key / URL.")
                    } else if (top == null) {
                        MetadataTestState.Fail("No TMDB match for \"$q\".")
                    } else {
                        val yr = top.year?.let { " ($it)" } ?: ""
                        MetadataTestState.Ok("${top.title}$yr · tmdb #${top.tmdbId}")
                    }
                },
                onFailure = { MetadataTestState.Fail(it.message?.takeIf { m -> m.isNotBlank() } ?: "Lookup failed.") },
            )
        }
    }

    private fun friendlyProxyError(t: Throwable): String = when (t) {
        is java.net.UnknownHostException -> "Can't reach the proxy host."
        is java.net.SocketTimeoutException -> "Connection to the proxy timed out."
        is java.net.ConnectException -> "Couldn't connect to the proxy (check host/port)."
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Proxy test failed."
    }
}
