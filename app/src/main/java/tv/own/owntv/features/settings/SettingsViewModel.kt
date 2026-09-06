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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.player.AudioOutputPolicy
import tv.own.owntv.core.player.SurroundMode
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.repository.SourceTestResult
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.core.sync.SyncWarning
import tv.own.owntv.core.stalker.stalkerCredentials
import tv.own.owntv.core.util.FriendlySyncFailure
import tv.own.owntv.core.util.classifySyncFailure
import tv.own.owntv.core.sync.work.CatalogSyncState
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.settings.ChNavLimits
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.PanelSection
import tv.own.owntv.core.settings.PanelShares
import tv.own.owntv.core.settings.GuideWidthShares
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.SubtitleStyle
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.UiZoom

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
    private val categoryDao: tv.own.owntv.core.database.dao.CategoryDao,
    private val customizationStore: tv.own.owntv.core.customize.CustomizationStore,
    private val navVisibility: tv.own.owntv.core.nav.NavVisibility,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val epgSourceStore: tv.own.owntv.core.epg.EpgSourceStore,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val metadataProvider: tv.own.owntv.core.metadata.MetadataProvider,
    private val metadataRepository: tv.own.owntv.core.metadata.MetadataRepository,
    private val metadataBudget: tv.own.owntv.core.metadata.MetadataBudget,
    private val stalkerAuth: tv.own.owntv.core.stalker.StalkerAuthManager,
    private val stalkerClient: tv.own.owntv.core.stalker.StalkerClient,
    private val xtreamClient: tv.own.owntv.core.parser.XtreamClient,
    private val sourceTester: tv.own.owntv.core.repository.SourceTester,
    private val companion: tv.own.owntv.core.companion.CompanionController,
    private val vodEngineStore: tv.own.owntv.core.player.VodEngineStore,
    private val playbackPrefs: tv.own.owntv.core.player.PlaybackPrefsStore,
) : ViewModel() {
    companion object {
        private const val TAG = "OwnTVHome"

        /** Sentinel session key for pre-save "Test connection" handshakes (no real source id yet). */
        private const val STALKER_TEST_SOURCE_ID = -1L
    }

    // ---- Remote (companion) add-source: a LAN web form fills the Add Source screen from another device. ----
    /** Server lifecycle (Idle / Starting / Listening with PIN+QR / Failed) for the Remote screen. */
    val remoteState get() = companion.state

    /** Live submission stream — the Remote screen collects it to hand off to the Manual form. */
    val remotePayloads get() = companion.payloads

    /** Retained last submission, so the Manual form pre-fills even after the Remote screen left. */
    val remotePayload get() = companion.lastPayload

    fun startRemoteListener(port: Int) = companion.start(port)
    fun stopRemoteListener() = companion.stop()
    fun consumeRemotePayload() = companion.consumePayload()

    // ---- Remote background image: another device uploads a photo over LAN (same PIN/QR companion flow). ----

    /** Background images received from the remote device in image-upload mode. */
    val remoteImages get() = companion.images

    fun startRemoteImageListener(port: Int) = companion.startForImageUpload(port)

    /** TMDB API keys handed over from another device, so a 32-character key never has to be typed with the remote. */
    val remoteTmdbKeys get() = companion.tmdbKeys
    val remoteTmdbConfigs get() = companion.tmdbConfigs
    val remoteOpenSubtitlesConfigs get() = companion.openSubtitlesConfigs

    fun startRemoteTmdbKeyListener(port: Int) = companion.startForTmdbKey(port)
    fun startRemoteTmdbConfigListener(port: Int) = companion.startForTmdbConfig(port)
    fun startRemoteOpenSubtitlesConfigListener(port: Int) = companion.startForOpenSubtitlesConfig(port)

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
            // Home's Movies/Series continue-watching comes from the resume (progress) table, not
            // history, and series progress is stored under EPISODE — so the resume rows go with it.
            // Live has none to clear. All of that lives in userDataWriter, which also records each
            // deletion so a local sync does not hand the cleared history straight back.
            userDataWriter.clearHistory(pid, type)
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
        data class Success(
            val counts: SyncCounts,
            val warnings: List<SyncWarning> = emptyList(),
            val remainder: SyncContentTypes = SyncContentTypes(false, false, false),
        ) : ImportState
        data class Failed(val failure: FriendlySyncFailure) : ImportState
    }

    val sources: StateFlow<List<SourceEntity>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else sourceRepository.observeSources(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Subscription expiry per source id — the "Expires …" note in the Manage-sources row (Phase F).
     * Xtream reads `user_info.exp_date` from the panel; Stalker reads `account_info`/`get_profile`
     * (see core's `stalkerExpiryOf`). M3U is a plain playlist file with no account concept — never listed.
     * Fetched once per source per ViewModel lifetime (in-memory cache; only while the screen is
     * subscribed); any failure simply leaves the line off the row.
     */
    private val expiryCache = java.util.concurrent.ConcurrentHashMap<Long, String>()
    val sourceExpiry: StateFlow<Map<Long, String>> = sources
        .map { list ->
            val out = HashMap<Long, String>()
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

    private suspend fun fetchExpiry(s: SourceEntity): String? = runCatching {
        when (s.type) {
            tv.own.owntv.core.model.SourceType.XTREAM -> xtreamClient.accountExpiryMs(s)?.let {
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it))
            }
            tv.own.owntv.core.model.SourceType.STALKER ->
                s.mac?.let { tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(it) }?.let { mac ->
                    val creds = s.stalkerCredentials(mac)
                    stalkerAuth.withAuthRetry(creds) { session ->
                        val info = runCatching {
                            stalkerClient.getAccountInfo(session.apiBase, mac, session.token, creds.userAgent)
                        }.getOrDefault(emptyMap())
                        tv.own.owntv.core.stalker.stalkerExpiryOf(info)
                            ?: tv.own.owntv.core.stalker.stalkerExpiryOf(session.profile)
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

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

    val livePreviewPanelActive: StateFlow<Boolean> = settings.livePreviewPanelActive
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setHdrEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHdrEnabled(enabled) }
    }

    val autoFrameRate: StateFlow<Boolean> = settings.autoFrameRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAutoFrameRate(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoFrameRate(enabled) }
    }

    // The old surround BOOLEAN is gone from here: [surroundMode] replaced it, and the leftover flow
    // defaulted to `true` where the setting's own default is `false` — a trap for anyone who wired a UI
    // to it. The legacy key itself still lives in SettingsRepository, which reads it so an upgrading
    // user's old choice carries into the three-state setting.
    val surroundMode: StateFlow<SurroundMode> = settings.surroundMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SurroundMode.AUTO)

    /** Cycle Auto → Stereo only → Surround → Auto. Any change clears the session's stereo latch: the
     *  user touching this control is explicitly asking the audio output for another chance. */
    fun cycleSurroundMode() {
        val next = when (surroundMode.value) {
            SurroundMode.AUTO -> SurroundMode.STEREO
            SurroundMode.STEREO -> SurroundMode.SURROUND
            SurroundMode.SURROUND -> SurroundMode.AUTO
        }
        AudioOutputPolicy.clearLatch()
        viewModelScope.launch { settings.setSurroundMode(next) }
    }

    val autoPlayNext: StateFlow<Boolean> = settings.autoPlayNext
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAutoPlayNext(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoPlayNext(enabled) }
    }

    // Seeded with the repository's own default (DEVICE). MANUAL here made the chip open reading "Manual"
    // for a frame before the stored value arrived — on a setting the user had never touched.
    val catchupTimezone: StateFlow<SettingsRepository.CatchupTimezone> = settings.catchupTimezone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.CatchupTimezone.DEVICE)

    val catchupOffsetMinutes: StateFlow<Int> = settings.catchupOffsetMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val catchupOffsetRangeMinutes: IntRange = settings.catchupOffsetRangeMinutes

    val catchupPlayer: StateFlow<SettingsRepository.CatchupPlayer> = settings.catchupPlayer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.CatchupPlayer.INTERNAL)

    fun setCatchupPlayer(mode: SettingsRepository.CatchupPlayer) {
        viewModelScope.launch { settings.setCatchupPlayer(mode) }
    }

    fun setCatchupTimezone(mode: SettingsRepository.CatchupTimezone) {
        viewModelScope.launch { settings.setCatchupTimezone(mode) }
    }

    /** Nudge the manual UTC offset by [deltaMinutes] (the picker's − / + steps), clamped to range. */
    fun adjustCatchupOffset(deltaMinutes: Int) {
        viewModelScope.launch { settings.setCatchupOffsetMinutes(catchupOffsetMinutes.value + deltaMinutes) }
    }

    /** Global guide shift in minutes (0 = off). Per-channel overrides live in the channel menu. */
    val epgOffsetMinutes: StateFlow<Int> = settings.epgOffsetMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val epgOffsetRangeMinutes: IntRange = settings.epgOffsetRangeMinutes

    /** Nudge the global EPG offset by [deltaMinutes] (the picker's − / + steps), clamped to range. */
    fun adjustEpgOffset(deltaMinutes: Int) {
        viewModelScope.launch { settings.setEpgOffsetMinutes(epgOffsetMinutes.value + deltaMinutes) }
    }

    fun setEpgOffsetMinutes(minutes: Int) {
        viewModelScope.launch { settings.setEpgOffsetMinutes(minutes) }
    }

    val androidTvHomeEnabled: StateFlow<Boolean> = settings.androidTvHomeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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
    val hwDecoding: StateFlow<Boolean> = settings.hwDecoding.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setHwDecoding(enabled: Boolean) { viewModelScope.launch { settings.setHwDecoding(enabled) } }

    val vodEnginePreference: StateFlow<tv.own.owntv.core.player.EnginePreference> = settings.vodEnginePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.player.EnginePreference.MPV_FIRST)
    fun setVodEnginePreference(preference: tv.own.owntv.core.player.EnginePreference) {
        viewModelScope.launch { settings.setVodEnginePreference(preference) }
    }

    val liveEnginePreference: StateFlow<tv.own.owntv.core.player.EnginePreference> = settings.liveEnginePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.player.EnginePreference.EXO_FIRST)
    fun setLiveEnginePreference(preference: tv.own.owntv.core.player.EnginePreference) {
        viewModelScope.launch { settings.setLiveEnginePreference(preference) }
    }

    /** How many movies/episodes are pinned to a specific engine — the row is only worth showing when
     *  there is something to forget. Counts both directions: a pin to mpv and a pin to ExoPlayer both
     *  override the setting above. */
    val vodEnginePinCount: StateFlow<Int> =
        combine(vodEngineStore.mpvUrls, vodEngineStore.exoUrls) { mpv, exo -> mpv.size + exo.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Forget every per-item engine pin, so everything follows the "Movies & Series player" setting
     *  again. Also the escape hatch for pins older builds wrote automatically after a decode failure —
     *  those are stored identically to the user's own, so they can only be cleared wholesale. */
    fun clearVodEnginePins() { viewModelScope.launch { vodEngineStore.clearAll() } }

    /** Volume every item starts at, before any per-item value the player remembered. */
    val defaultVolume: StateFlow<Int> = settings.defaultVolume.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)
    fun setDefaultVolume(percent: Int) { viewModelScope.launch { settings.setDefaultVolume(percent) } }

    /** How many individual channels/films/episodes have a remembered zoom, and how many a volume.
     *  Counted and reset separately — wanting every film back at the default aspect is not a request
     *  to lose the levels set on the quiet ones. */
    val savedZoomCount: StateFlow<Int> =
        playbackPrefs.observeZoomCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val savedVolumeCount: StateFlow<Int> =
        playbackPrefs.observeVolumeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun clearSavedZoom() { viewModelScope.launch { playbackPrefs.clearZoom() } }

    fun clearSavedVolume() { viewModelScope.launch { playbackPrefs.clearVolume() } }

    /** Same again for the per-item A/V-sync offsets (DB v35). */
    val savedAudioDelayCount: StateFlow<Int> =
        playbackPrefs.observeAudioDelayCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun clearSavedAudioDelay() { viewModelScope.launch { playbackPrefs.clearAudioDelay() } }

    /** Rewind/forward step in a movie or episode, and the separate one for a live archive. */
    val seekStepSec: StateFlow<Int> = settings.seekStepSec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SeekSteps.DEFAULT_SEEK_STEP_SEC)
    fun setSeekStepSec(seconds: Int) { viewModelScope.launch { settings.setSeekStepSec(seconds) } }

    val liveRewindStepSec: StateFlow<Int> = settings.liveRewindStepSec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SeekSteps.DEFAULT_LIVE_REWIND_STEP_SEC)
    fun setLiveRewindStepSec(seconds: Int) { viewModelScope.launch { settings.setLiveRewindStepSec(seconds) } }

    val deinterlace: StateFlow<Boolean> = settings.deinterlace.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setDeinterlace(enabled: Boolean) { viewModelScope.launch { settings.setDeinterlace(enabled) } }

    val measuredStreamStats: StateFlow<Boolean> = settings.measuredStreamStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setMeasuredStreamStats(enabled: Boolean) { viewModelScope.launch { settings.setMeasuredStreamStats(enabled) } }

    val detailedDiagnostics: StateFlow<Boolean> = settings.detailedDiagnostics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setDetailedDiagnostics(enabled: Boolean) { viewModelScope.launch { settings.setDetailedDiagnostics(enabled) } }

    val directTune: StateFlow<Boolean> = settings.directTune.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setDirectTune(enabled: Boolean) { viewModelScope.launch { settings.setDirectTune(enabled) } }

    // External player is per-section (Live TV / Movies / Series) — the settings row opens a popup with
    // one toggle each rather than a single global On/Off.
    val externalPlayerLive: StateFlow<Boolean> = settings.externalPlayerLive.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val externalPlayerMovies: StateFlow<Boolean> = settings.externalPlayerMovies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val externalPlayerSeries: StateFlow<Boolean> = settings.externalPlayerSeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setExternalPlayer(section: SettingsRepository.ExternalPlayerSection, enabled: Boolean) {
        viewModelScope.launch { settings.setExternalPlayer(section, enabled) }
    }

    val updateCheckOnStart: StateFlow<Boolean> =
        settings.updateCheckOnStart.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setUpdateCheckOnStart(enabled: Boolean) { viewModelScope.launch { settings.setUpdateCheckOnStart(enabled) } }

    /** The Settings rows pinned to the Quick group, in display order. */
    val quickPinnedKeys: StateFlow<List<String>> =
        settings.quickPinnedKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun setQuickPinnedKeys(keys: List<String>) { viewModelScope.launch { settings.setQuickPinnedKeys(keys) } }

    /** The saved order of one long-press content menu; empty = the order the app ships with. */
    fun menuOrder(menu: tv.own.owntv.core.model.ContentMenu) = settings.menuOrder(menu.name.lowercase())
    fun setMenuOrder(menu: tv.own.owntv.core.model.ContentMenu, keys: List<String>) {
        viewModelScope.launch { settings.setMenuOrder(menu.name.lowercase(), keys) }
    }

    val resumeLastChannel: StateFlow<Boolean> =
        settings.resumeLastChannel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setResumeLastChannel(enabled: Boolean) { viewModelScope.launch { settings.setResumeLastChannel(enabled) } }

    // Per-profile startup landing (v4.0.0): Home / Last channel / Live·Favorites.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val startupMode: StateFlow<tv.own.owntv.core.settings.StartupMode> =
        settings.activeProfileId
            .flatMapLatest { settings.startupMode(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.StartupMode.HOME)
    fun setStartupMode(mode: tv.own.owntv.core.settings.StartupMode) {
        viewModelScope.launch { settings.setStartupMode(settings.activeProfileId.first(), mode) }
    }

    val startupChannel: StateFlow<tv.own.owntv.core.settings.StartupChannelRef?> =
        settings.activeProfileId
            .flatMapLatest { profileId ->
                if (profileId < 0L) flowOf(null) else settings.startupChannel(profileId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _startupChannelQuery = MutableStateFlow("")
    val startupChannelQuery: StateFlow<String> = _startupChannelQuery.asStateFlow()
    private val startupChannelRefresh = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val startupChannelResults: StateFlow<List<tv.own.owntv.core.database.entity.ChannelEntity>> =
        combine(
            settings.activeProfileId,
            _startupChannelQuery.debounce(180),
            startupChannelRefresh,
        ) { profileId, query, _ -> profileId to query }
            .mapLatest { (profileId, query) -> loadStartupChannelResults(profileId, query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStartupChannelQuery(query: String) { _startupChannelQuery.value = query }

    fun refreshStartupChannelPicker() { startupChannelRefresh.value++ }

    fun setStartupChannel(channel: tv.own.owntv.core.database.entity.ChannelEntity) {
        viewModelScope.launch {
            val profileId = settings.activeProfileId.first()
            if (profileId < 0L) return@launch
            settings.setSpecificStartupChannel(
                profileId,
                tv.own.owntv.core.settings.StartupChannelRef(
                    sourceId = channel.sourceId,
                    remoteId = channel.remoteId,
                    name = channel.name,
                    itemId = channel.id,
                ),
            )
        }
    }

    private suspend fun loadStartupChannelResults(
        profileId: Long,
        query: String,
    ): List<tv.own.owntv.core.database.entity.ChannelEntity> {
        if (profileId < 0L) return emptyList()
        val sources = sourceDao.observeForProfile(profileId).first().filter { it.syncLive }
        val defaultSourceId = settings.defaultSourceId.first()
        val sourceIds = if (defaultSourceId > 0L && sources.any { it.id == defaultSourceId }) {
            listOf(defaultSourceId)
        } else {
            sources.map { it.id }
        }
        if (sourceIds.isEmpty()) return emptyList()
        val customizations = customizationStore.observe(profileId, tv.own.owntv.core.model.MediaType.LIVE).first()
        val isKids = profileDao.getById(profileId)?.isKids == true
        val hiddenCategoryIds = tv.own.owntv.core.content.AdultCategoryClassifier.hiddenCategoryIds(
            categoryDao.observe(sourceIds, tv.own.owntv.core.model.MediaType.LIVE).first(),
            customizations.hiddenCategories,
            isKids,
        )
        return channelDao.searchList(query.trim(), sourceIds, 500)
            .asSequence()
            .filter { tv.own.owntv.core.customize.CustomizeKeys.channel(it) !in customizations.hiddenItems }
            .filter { it.categoryId == null || it.categoryId !in hiddenCategoryIds }
            .take(300)
            .toList()
    }

    val resumeMode: StateFlow<SettingsRepository.ResumeMode> =
        settings.resumeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.ResumeMode.ASK)
    fun setResumeMode(name: String) {
        viewModelScope.launch {
            settings.setResumeMode(runCatching { SettingsRepository.ResumeMode.valueOf(name) }.getOrDefault(SettingsRepository.ResumeMode.ASK))
        }
    }

    val defaultZoom: StateFlow<String> = settings.defaultZoom.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "FIT")
    fun setDefaultZoom(name: String) { viewModelScope.launch { settings.setDefaultZoom(name) } }

    // Subtitle appearance (#96) — while subtitleStyleEnabled is false the other four are inert, and
    // each of them separately does nothing until moved off its own "Default" value.
    val subtitleStyleEnabled: StateFlow<Boolean> = settings.subtitleStyleEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setSubtitleStyleEnabled(enabled: Boolean) { viewModelScope.launch { settings.setSubtitleStyleEnabled(enabled) } }

    // One size per engine: the same multiplier reads much larger on ExoPlayer than on mpv.
    val subtitleScaleExo: StateFlow<Float> = settings.subtitleScaleExo.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle.SCALE_DEFAULT)
    fun setSubtitleScaleExo(scale: Float) { viewModelScope.launch { settings.setSubtitleScaleExo(scale) } }

    val subtitleScaleMpv: StateFlow<Float> = settings.subtitleScaleMpv.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle.SCALE_DEFAULT)
    fun setSubtitleScaleMpv(scale: Float) { viewModelScope.launch { settings.setSubtitleScaleMpv(scale) } }

    val subtitleFont: StateFlow<tv.own.owntv.core.theme.AppFontFamily?> = settings.subtitleFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setSubtitleFont(font: tv.own.owntv.core.theme.AppFontFamily?) { viewModelScope.launch { settings.setSubtitleFont(font) } }

    val subtitleColor: StateFlow<String> = settings.subtitleColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle.COLOR_DEFAULT)
    fun setSubtitleColor(hex: String) { viewModelScope.launch { settings.setSubtitleColor(hex) } }

    val subtitlePosition: StateFlow<SubtitleStyle.Position> = settings.subtitlePosition.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle.Position.DEFAULT)
    fun setSubtitlePosition(position: SubtitleStyle.Position) { viewModelScope.launch { settings.setSubtitlePosition(position) } }

    val subtitleBgOpacity: StateFlow<Int> = settings.subtitleBgOpacity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle.OPACITY_DEFAULT)
    fun setSubtitleBgOpacity(pct: Int) { viewModelScope.launch { settings.setSubtitleBgOpacity(pct) } }

    val audioDelayMs: StateFlow<Int> = settings.audioDelayMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    fun setAudioDelayMs(ms: Int) { viewModelScope.launch { settings.setAudioDelayMs(ms) } }

    // --- CH+- key paging (browse panels): master toggle + per-direction skip counts ---
    val chNavEnabled: StateFlow<Boolean> = settings.chNavEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setChNavEnabled(enabled: Boolean) { viewModelScope.launch { settings.setChNavEnabled(enabled) } }
    val chNavUpSkip: StateFlow<Int> = settings.chNavUpSkip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChNavLimits.DEFAULT_SKIP)
    fun setChNavUpSkip(n: Int) { viewModelScope.launch { settings.setChNavUpSkip(n) } }
    val chNavDownSkip: StateFlow<Int> = settings.chNavDownSkip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChNavLimits.DEFAULT_SKIP)
    fun setChNavDownSkip(n: Int) { viewModelScope.launch { settings.setChNavDownSkip(n) } }
    val remoteShortcutBindings: StateFlow<List<tv.own.owntv.core.settings.RemoteShortcutBinding>> =
        settings.remoteShortcutBindings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            tv.own.owntv.core.settings.RemoteShortcutBindings.defaults,
        )
    fun setRemoteShortcutBinding(binding: tv.own.owntv.core.settings.RemoteShortcutBinding) {
        viewModelScope.launch { settings.setRemoteShortcutBinding(binding) }
    }
    fun removeRemoteShortcutBinding(keyCode: Int, press: tv.own.owntv.core.settings.RemoteShortcutPress) {
        viewModelScope.launch { settings.removeRemoteShortcutBinding(keyCode, press) }
    }
    fun resetRemoteShortcutBindings() { viewModelScope.launch { settings.resetRemoteShortcutBindings() } }

    // --- Manual panel widths: one StateFlow per section, so Live/Movies/Series each read their own ---
    private fun <T> panelFlows(source: (PanelSection) -> kotlinx.coroutines.flow.Flow<T>, initial: T): Map<PanelSection, StateFlow<T>> =
        PanelSection.entries.associateWith {
            source(it).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
        }

    val panelWidthEnabled: Map<PanelSection, StateFlow<Boolean>> = panelFlows(settings::panelWidthEnabled, false)
    val panelShares: Map<PanelSection, StateFlow<PanelShares?>> = panelFlows(settings::panelShares, null)

    fun setPanelWidths(s: PanelSection, enabled: Boolean, shares: PanelShares) {
        viewModelScope.launch { settings.setPanelWidths(s, enabled, shares) }
    }

    val guideWidthEnabled: StateFlow<Boolean> = settings.guideWidthEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val guideWidthShares: StateFlow<GuideWidthShares?> = settings.guideWidthShares
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setGuideWidths(enabled: Boolean, shares: GuideWidthShares) {
        viewModelScope.launch { settings.setGuideWidths(enabled, shares) }
    }

    val preferredAudioLang: StateFlow<String> = settings.preferredAudioLang.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setPreferredAudioLang(lang: String) { viewModelScope.launch { settings.setPreferredAudioLang(lang) } }

    val preferredSubLang: StateFlow<String> = settings.preferredSubLang.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setPreferredSubLang(lang: String) { viewModelScope.launch { settings.setPreferredSubLang(lang) } }

    /** OpenSubtitles search language filter — off (the default) means results come back in every language. */
    val subSearchFilterEnabled: StateFlow<Boolean> =
        settings.subSearchFilterEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setSubSearchFilterEnabled(enabled: Boolean) { viewModelScope.launch { settings.setSubSearchFilterEnabled(enabled) } }

    val subSearchLanguages: StateFlow<String> =
        settings.subSearchLanguages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setSubSearchLanguages(codes: String) { viewModelScope.launch { settings.setSubSearchLanguages(codes) } }

    // --- Personalization (theme / accent / UI zoom) ---
    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }

    val accent: StateFlow<AccentColor> = settings.accent.stateIn(viewModelScope, SharingStarted.Eagerly, AccentColor.TEAL)
    fun setAccent(accent: AccentColor) { viewModelScope.launch { settings.setAccent(accent) } }

    /** Custom accent hex ("#52DBC8"); blank = the preset is in effect. */
    val customAccent: StateFlow<String> = settings.customAccent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setCustomAccent(hex: String) { viewModelScope.launch { settings.setCustomAccent(hex) } }

    /** Focus highlight (#121): ring color hex (blank = accent) and ring width in dp. */
    val focusHighlight: StateFlow<String> = settings.focusHighlight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val focusHighlightWidth: StateFlow<Int> = settings.focusHighlightWidth.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)
    fun setFocusHighlight(hex: String) { viewModelScope.launch { settings.setFocusHighlight(hex) } }
    fun setFocusHighlightWidth(dp: Int) { viewModelScope.launch { settings.setFocusHighlightWidth(dp) } }

    // --- Glass effect: background image + per-surface translucency ---
    val bgImagePath: StateFlow<String> = settings.bgImagePath.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val glassConfig: StateFlow<tv.own.owntv.core.theme.GlassConfig> = settings.glassConfig.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.theme.GlassConfig())
    fun setBgImagePath(path: String) { viewModelScope.launch { settings.setBgImagePath(path) } }
    fun setGlassScopeBitmask(bits: Int) { viewModelScope.launch { settings.setGlassScopeBitmask(bits) } }
    fun setGlassPreset(preset: tv.own.owntv.core.theme.GlassPreset) { viewModelScope.launch { settings.setGlassPreset(preset) } }
    fun setGlassAlphaPercent(pct: Int, currentBlurPct: Int) {
        viewModelScope.launch { settings.setGlassAlphaPercent(pct, currentBlurPct) }
    }
    fun setGlassBlurPercent(pct: Int, currentAlphaPct: Int) {
        viewModelScope.launch { settings.setGlassBlurPercent(pct, currentAlphaPct) }
    }
    fun setGlassHighlightPercent(pct: Int) { viewModelScope.launch { settings.setGlassHighlightPercent(pct) } }
    fun setGlassAllowFullTransparency(enabled: Boolean) {
        viewModelScope.launch { settings.setGlassAllowFullTransparency(enabled) }
    }
    fun setGlassDepthEffects(enabled: Boolean) {
        viewModelScope.launch { settings.setGlassDepthEffects(enabled) }
    }

    // --- Nav menu customization (v4.3.0) ---
    /** STATIC (default): user picks which icons to hide. DYNAMIC: icons adapt to the active playlist. */
    val navMenuMode: StateFlow<tv.own.owntv.core.settings.SettingsRepository.NavMenuMode> =
        settings.navMenuMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SettingsRepository.NavMenuMode.STATIC)
    fun setNavMenuMode(mode: tv.own.owntv.core.settings.SettingsRepository.NavMenuMode) {
        viewModelScope.launch { settings.setNavMenuMode(mode) }
    }

    /** Browse sections the user has hidden (STATIC mode only). */
    val navMenuHidden: StateFlow<Set<tv.own.owntv.core.nav.MainSection>> = settings.navMenuHidden
        .map { raw -> raw.mapNotNull { name -> runCatching { tv.own.owntv.core.nav.MainSection.valueOf(name) }.getOrNull() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    fun setNavSectionHidden(section: tv.own.owntv.core.nav.MainSection, hidden: Boolean) {
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
    val dynamicCaps: StateFlow<Set<tv.own.owntv.core.nav.MainSection>> = navVisibility.dynamicCaps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.nav.MainSection.allBrowse)

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiZoom.DEFAULT)
    fun setUiZoom(percent: Int) { viewModelScope.launch { settings.setUiZoomPercent(UiZoom.clamp(percent)) } }

    // Docked mini-player: size (% of screen width) and screen position.
    val miniPlayerSizePct: StateFlow<Int> =
        settings.miniPlayerSizePct.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.player.MiniPlayerSize.DEFAULT)
    fun setMiniPlayerSize(percent: Int) { viewModelScope.launch { settings.setMiniPlayerSizePct(percent) } }

    val miniPlayerPosition: StateFlow<tv.own.owntv.core.player.MiniPlayerPosition> =
        settings.miniPlayerPosition
            .map { tv.own.owntv.core.player.MiniPlayerPosition.fromName(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.player.MiniPlayerPosition.DEFAULT)
    fun setMiniPlayerPosition(position: tv.own.owntv.core.player.MiniPlayerPosition) {
        viewModelScope.launch { settings.setMiniPlayerPosition(position.name) }
    }

    // Live TV latency (#72): preset + custom seconds.
    val liveLatencyMode: StateFlow<tv.own.owntv.core.settings.LiveLatency> =
        settings.liveLatencyMode
            .map { tv.own.owntv.core.settings.LiveLatency.fromName(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.LiveLatency.DEFAULT)
    fun setLiveLatencyMode(mode: tv.own.owntv.core.settings.LiveLatency) {
        viewModelScope.launch { settings.setLiveLatencyMode(mode.name) }
    }

    val liveLatencyCustomSecs: StateFlow<Int> =
        settings.liveLatencyCustomSecs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.LiveBuffer.CUSTOM_DEFAULT)
    fun setLiveLatencyCustomSecs(secs: Int) {
        viewModelScope.launch { settings.setLiveLatencyCustomSecs(secs) }
    }

    /** "Pre-buffer" (F07): the global choice, in seconds (0 = Off). */
    val livePrerollSecs: StateFlow<Int> =
        settings.livePrerollSecs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.LiveBuffer.PREROLL_OFF)
    fun setLivePrerollSecs(secs: Int) {
        viewModelScope.launch { settings.setLivePrerollSecs(secs) }
    }

    /** "Give up on a channel after": the whole-tune budget in seconds (0 = Never). */
    val liveTuneTimeoutSecs: StateFlow<Int> =
        settings.liveTuneTimeoutSecs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.player.LiveLadder.DEFAULT_BUDGET_SECS)
    fun setLiveTuneTimeoutSecs(secs: Int) {
        viewModelScope.launch { settings.setLiveTuneTimeoutSecs(secs) }
    }

    /** Per-playlist override of the above. `-1` = follow the global value. */
    fun setSourcePreroll(sourceId: Long, secs: Int) {
        viewModelScope.launch { sourceDao.updateLivePreroll(sourceId, secs) }
    }

    /** Per-playlist Live TV engine override; `null` = follow the global setting. */
    fun setSourceLiveEngine(sourceId: Long, preference: String?) {
        viewModelScope.launch { sourceDao.updateLiveEnginePreference(sourceId, preference) }
    }

    /** Per-playlist Live latency override; `null` mode = follow the global setting. */
    fun setSourceLiveLatency(sourceId: Long, mode: String?, customSecs: Int) {
        viewModelScope.launch { sourceDao.updateLiveLatency(sourceId, mode, customSecs) }
    }

    val animationLevel: StateFlow<tv.own.owntv.core.theme.AnimationLevel> =
        settings.animationLevel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.theme.AnimationLevel.FULL)
    fun setAnimationLevel(level: tv.own.owntv.core.theme.AnimationLevel) { viewModelScope.launch { settings.setAnimationLevel(level) } }

    val ambientGlowEnabled: StateFlow<Boolean> =
        settings.ambientGlowEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setAmbientGlowEnabled(enabled: Boolean) { viewModelScope.launch { settings.setAmbientGlowEnabled(enabled) } }

    val ambientGlowPulse: StateFlow<Boolean> =
        settings.ambientGlowPulse.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setAmbientGlowPulse(enabled: Boolean) { viewModelScope.launch { settings.setAmbientGlowPulse(enabled) } }

    // Weather chip: visibility toggle + manual location override (for VPN users).
    val weatherEnabled: StateFlow<Boolean> =
        settings.weatherEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setWeatherEnabled(enabled: Boolean) { viewModelScope.launch { settings.setWeatherEnabled(enabled) } }
    val weatherLocation: StateFlow<String> =
        settings.weatherLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setWeatherLocation(location: String) { viewModelScope.launch { settings.setWeatherLocation(location) } }
    val weatherFahrenheit: StateFlow<Boolean> =
        settings.weatherFahrenheit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setWeatherFahrenheit(fahrenheit: Boolean) { viewModelScope.launch { settings.setWeatherFahrenheit(fahrenheit) } }

    // Per-section "remember last item per category" (default OFF). OFF resets the browse list to the top
    // when switching category; ON keeps a separate scroll position per category. The Live toggle also
    // gates the last-focused-channel restore on re-entry.
    val rememberCategoryLive: StateFlow<Boolean> =
        settings.rememberCategoryLive.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val rememberCategoryMovies: StateFlow<Boolean> =
        settings.rememberCategoryMovies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val rememberCategorySeries: StateFlow<Boolean> =
        settings.rememberCategorySeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setRememberCategoryLive(enabled: Boolean) {
        viewModelScope.launch { settings.setRememberCategoryLive(enabled) }
    }

    fun setRememberCategoryMovies(enabled: Boolean) {
        viewModelScope.launch { settings.setRememberCategoryMovies(enabled) }
    }

    fun setRememberCategorySeries(enabled: Boolean) {
        viewModelScope.launch { settings.setRememberCategorySeries(enabled) }
    }

    val rememberLastLive: StateFlow<Boolean> =
        settings.rememberLastLive.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setRememberLastLive(enabled: Boolean) { viewModelScope.launch { settings.setRememberLastLive(enabled) } }
    val rememberLastMovies: StateFlow<Boolean> =
        settings.rememberLastMovies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setRememberLastMovies(enabled: Boolean) { viewModelScope.launch { settings.setRememberLastMovies(enabled) } }
    val rememberLastSeries: StateFlow<Boolean> =
        settings.rememberLastSeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setRememberLastSeries(enabled: Boolean) { viewModelScope.launch { settings.setRememberLastSeries(enabled) } }

    /** Per-source playlist auto-refresh selection (Off / Startup / staleness threshold). */
    val playlistAutoRefresh: StateFlow<Map<Long, PlaylistRefresh>> = settings.playlistAutoRefresh
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Per-source EPG auto-refresh selection (Off / Startup / staleness threshold). */
    val epgAutoRefresh: StateFlow<Map<Long, EpgAutoRefresh>> = settings.epgAutoRefresh
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setPlaylistAutoRefresh(sourceId: Long, mode: PlaylistRefresh) {
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
        autoRefresh: PlaylistRefresh,
        isDefault: Boolean = false,
        mac: String = "",
        stalkerSerialNumber: String = "",
        stalkerDeviceId: String = "",
        stalkerDeviceId2: String = "",
        stalkerSignature: String = "",
        syncLive: Boolean = true,
        syncMovies: Boolean = true,
        syncSeries: Boolean = true,
        preferHls: Boolean = false,
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
                stalkerSerialNumber = stalkerSerialNumber.trim().takeIf { it.isNotBlank() },
                stalkerDeviceId = stalkerDeviceId.trim().takeIf { it.isNotBlank() },
                stalkerDeviceId2 = stalkerDeviceId2.trim().takeIf { it.isNotBlank() },
                stalkerSignature = stalkerSignature.trim().takeIf { it.isNotBlank() },
                userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
                syncLive = syncLive,
                syncMovies = syncMovies,
                syncSeries = syncSeries,
                preferHls = preferHls,
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

    fun togglePreferHls(sourceId: Long, preferHls: Boolean) {
        viewModelScope.launch {
            sourceDao.updatePreferHls(sourceId, preferHls)
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
        autoRefresh: PlaylistRefresh = PlaylistRefresh.OFF,
        live: SyncScopeChoice = SyncScopeChoice.Now,
        movies: SyncScopeChoice = SyncScopeChoice.Now,
        series: SyncScopeChoice = SyncScopeChoice.Now,
        isDefault: Boolean = false,
        preferHls: Boolean = false,
    ) {
        val enabled = SyncContentTypes.fromChoices(live, movies, series)
        val priority = SyncContentTypes.priorityFromChoices(live, movies, series)
        runImport(
            autoRefresh, priority, enabledScope = enabled, enqueueRemainder = true,
            requiresNetwork = true, makeDefault = isDefault,
        ) { pid ->
            sourceRepository.addXtreamSource(
                pid, name.trim(), server.trim(), user.trim(), pass,
                userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl.trim().takeIf { it.isNotBlank() },
                syncLive = enabled.live, syncMovies = enabled.movies, syncSeries = enabled.series,
                preferHls = preferHls,
            )
        }
    }

    // ---- "Test" on a saved playlist row ----

    /** Null while no test is on screen; [SourceTestUi.Running] while the call is in flight. */
    private val _sourceTest = MutableStateFlow<SourceTestUi?>(null)
    val sourceTest: StateFlow<SourceTestUi?> = _sourceTest.asStateFlow()

    fun testSource(source: SourceEntity) {
        viewModelScope.launch {
            _sourceTest.value = SourceTestUi.Running(source.name)
            val result = sourceTester.test(source)
            // Back may have closed the popup while the request was still running; don't re-open it.
            if (_sourceTest.value != null) _sourceTest.value = SourceTestUi.Done(source.name, result)
        }
    }

    fun dismissSourceTest() {
        _sourceTest.value = null
    }

    // ---- Stalker portal (plan Phase B) ----

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
        serialNumber: String = "",
        deviceId: String = "",
        deviceId2: String = "",
        signature: String = "",
        userAgent: String = "",
        autoRefresh: PlaylistRefresh = PlaylistRefresh.OFF,
        isDefault: Boolean = false,
        live: SyncScopeChoice = SyncScopeChoice.Now,
        movies: SyncScopeChoice = SyncScopeChoice.Later,
        series: SyncScopeChoice = SyncScopeChoice.Later,
    ) {
        val canonicalMac = tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(mac)
        if (canonicalMac == null) {
            _importState.value = ImportState.Failed(FriendlySyncFailure.InvalidMac)
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
                    deviceIdentity = tv.own.owntv.core.stalker.StalkerDeviceIdentity(
                        serialNumber = serialNumber.trim().takeIf { it.isNotBlank() },
                        deviceId = deviceId.trim().takeIf { it.isNotBlank() },
                        deviceId2 = deviceId2.trim().takeIf { it.isNotBlank() },
                        signature = signature.trim().takeIf { it.isNotBlank() },
                    ),
                ),
            )
            sourceRepository.addStalkerSource(
                pid, name.trim(), portalUrl.trim(), canonicalMac,
                serialNumber.trim().takeIf { it.isNotBlank() },
                deviceId.trim().takeIf { it.isNotBlank() },
                deviceId2.trim().takeIf { it.isNotBlank() },
                signature.trim().takeIf { it.isNotBlank() },
                userAgent.trim().takeIf { it.isNotBlank() },
                syncLive = enabled.live, syncMovies = enabled.movies, syncSeries = enabled.series,
            )
        }
    }

    fun addM3u(name: String, url: String, userAgent: String = "", epgUrl: String = "", autoRefresh: PlaylistRefresh = PlaylistRefresh.OFF, isDefault: Boolean = false) = runImport(
        autoRefresh,
        requiresNetwork = !url.isLocalPlaylistPath(),
        makeDefault = isDefault,
    ) { pid ->
        sourceRepository.addM3uSource(
            pid, name.trim(), url.trim(),
            userAgent.trim().takeIf { it.isNotBlank() },
            epgUrl.trim().takeIf { it.isNotBlank() },
        )
    }

    private fun runImport(
        autoRefresh: PlaylistRefresh = PlaylistRefresh.OFF,
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
                    _importState.value = ImportState.Failed(classifySyncFailure(null, online = false))
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
                            counts = counts,
                            warnings = r.warnings,
                            remainder = remainder,
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
                        _importState.value = ImportState.Failed(classifySyncFailure(r.message, connectivity.isOnlineNow()))
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
                _importState.value = ImportState.Failed(classifySyncFailure(e.message, connectivity.isOnlineNow()))
            }
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    private fun String.isLocalPlaylistPath(): Boolean =
        startsWith("/") || startsWith("file://") || startsWith("content://")

    /**
     * Re-sync an existing source through WorkManager so it can continue after leaving this screen.
     *
     * [clean] bypasses the catalog-shrink prune guard for this run only. That guard normally refuses
     * to delete more than half a source's rows, because a truncated provider response looks exactly
     * like a shrunken catalog — but it also means a provider that genuinely dropped a lot of titles
     * leaves them stuck in the app forever, with no way out short of deleting and re-adding the
     * playlist. This is that way out. It is *not* a delete-and-reimport: rows keep their ids, so
     * favorites, history and resume positions on everything still listed survive untouched.
     */
    fun resync(source: SourceEntity, clean: Boolean = false) {
        Log.d(TAG, "resync enqueue sourceId=${source.id} clean=$clean")
        viewModelScope.launch {
            val counts = importFinalizer.contentCounts(source.id)
            catalogSyncScheduler.enqueueSync(
                source.id,
                reason = if (clean) "manual_clean_resync" else "manual_resync",
                contentTypes = SyncContentTypes.enabledOf(source),
                baseItemCount = counts.channels + counts.movies + counts.series,
                forcePrune = clean,
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
            runCatching { settings.setPlaylistAutoRefresh(source.id, PlaylistRefresh.OFF) }
        }
    }

    // --- Global proxy (Approach 1 — one app-wide HTTP proxy) ---

    val proxyConfig: StateFlow<tv.own.owntv.core.network.ProxyConfig> = settings.proxyConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.network.ProxyConfig())

    fun saveProxy(enabled: Boolean, host: String, port: Int, username: String, password: String) {
        viewModelScope.launch { settings.saveProxy(enabled, host, port, username, password) }
    }

    sealed interface ProxyTestState {
        data object Idle : ProxyTestState
        data object Testing : ProxyTestState
        data class Ok(val millis: Long) : ProxyTestState
        data class Fail(val failure: ProxyFailure) : ProxyTestState
    }

    sealed interface ProxyFailure {
        data object InvalidAddress : ProxyFailure
        data object HostUnreachable : ProxyFailure
        data object TimedOut : ProxyFailure
        data object ConnectionFailed : ProxyFailure
        data class Http(val code: Int) : ProxyFailure
        data class Unknown(val rawMessage: String?) : ProxyFailure
    }

    private val _proxyTest = MutableStateFlow<ProxyTestState>(ProxyTestState.Idle)
    val proxyTest: StateFlow<ProxyTestState> = _proxyTest.asStateFlow()

    fun resetProxyTest() { _proxyTest.value = ProxyTestState.Idle }

    fun testProxy(host: String, port: Int, username: String, password: String) {
        if (_proxyTest.value == ProxyTestState.Testing) return
        val h = host.trim()
        if (h.isBlank() || port !in 1..65535) {
            _proxyTest.value = ProxyTestState.Fail(ProxyFailure.InvalidAddress)
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
                            throw ProxyHttpException(resp.code)
                        }
                    }
                    System.currentTimeMillis() - start
                }
            }
            _proxyTest.value = result.fold(
                onSuccess = { ProxyTestState.Ok(it) },
                onFailure = { ProxyTestState.Fail(proxyFailure(it)) },
            )
        }
    }

    // --- Global custom DNS — same pattern as proxy ---

    val dnsConfig: StateFlow<tv.own.owntv.core.network.DnsConfig> = settings.dnsConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.network.DnsConfig())

    fun saveDns(enabled: Boolean, host: String, port: Int, dohUrl: String) {
        viewModelScope.launch { settings.saveDns(enabled, host, port, dohUrl) }
    }

    sealed interface DnsTestState {
        data object Idle : DnsTestState
        data object Testing : DnsTestState
        data class Ok(val millis: Long) : DnsTestState
        data class Fail(val failure: DnsTestFailure) : DnsTestState
    }

    sealed interface DnsTestFailure {
        data object ServerRequired : DnsTestFailure
        data object ServerNotReachable : DnsTestFailure
        data object TimedOut : DnsTestFailure
        data object NetworkUnreachable : DnsTestFailure
        data object ConnectionRefused : DnsTestFailure
        data class NoAddresses(val host: String) : DnsTestFailure
        data class Unknown(val rawMessage: String) : DnsTestFailure
        data object Generic : DnsTestFailure
    }

    private val _dnsTest = MutableStateFlow<DnsTestState>(DnsTestState.Idle)
    val dnsTest: StateFlow<DnsTestState> = _dnsTest.asStateFlow()

    fun resetDnsTest() { _dnsTest.value = DnsTestState.Idle }

    fun testDns(enabled: Boolean, host: String, port: Int, dohUrl: String) {
        if (_dnsTest.value == DnsTestState.Testing) return
        val doh = dohUrl.trim()
        val h = host.trim()
        if (enabled && h.isBlank() && doh.isBlank()) {
            _dnsTest.value = DnsTestState.Fail(DnsTestFailure.ServerRequired)
            return
        }
        val testHost = "dns.google" // a reliable hostname for testing DNS
        _dnsTest.value = DnsTestState.Testing
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val cfg = tv.own.owntv.core.network.DnsConfig(
                        enabled = enabled,
                        host = h,
                        port = if (port in 1..65535) port else 53,
                        dohUrl = doh,
                    )
                    // Test the selected backend strictly: seed it synchronously so the first lookup
                    // cannot race the Flow collector, and do not let a failed custom server silently
                    // succeed through the device's system DNS.
                    val holder = tv.own.owntv.core.network.DnsConfigHolder(
                        configFlow = kotlinx.coroutines.flow.emptyFlow(),
                        initialConfig = cfg,
                        fallbackToSystem = false,
                    )
                    val start = System.currentTimeMillis()
                    val addrs = holder.dns.lookup(testHost)
                    val ms = System.currentTimeMillis() - start
                    if (addrs.isEmpty()) throw DnsNoAddressesException(testHost)
                    ms
                }
            }
            _dnsTest.value = result.fold(
                onSuccess = { DnsTestState.Ok(it) },
                onFailure = { DnsTestState.Fail(classifyDnsError(it)) },
            )
        }
    }

    private fun classifyDnsError(e: Throwable): DnsTestFailure {
        val msg = e.message.orEmpty()
        return when {
            e is DnsNoAddressesException -> DnsTestFailure.NoAddresses(e.host)
            msg.contains("UnknownHostException", ignoreCase = true) || msg.contains("unknown host", ignoreCase = true) ->
                DnsTestFailure.ServerNotReachable
            msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true) ->
                DnsTestFailure.TimedOut
            msg.contains("Network is unreachable", ignoreCase = true) ->
                DnsTestFailure.NetworkUnreachable
            msg.contains("refused", ignoreCase = true) ->
                DnsTestFailure.ConnectionRefused
            e is java.net.SocketTimeoutException ->
                DnsTestFailure.TimedOut
            e is java.io.IOException && msg.isNotBlank() -> DnsTestFailure.Unknown(msg)
            else -> DnsTestFailure.Generic
        }
    }

    private class DnsNoAddressesException(val host: String) : java.io.IOException()

    // --- TMDB metadata enrichment (plan §4) — Phase M1 config + manual "look up title" test ---

    val metadataMode: StateFlow<tv.own.owntv.core.metadata.MetadataMode> =
        settings.metadataMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)
    fun setMetadataMode(mode: tv.own.owntv.core.metadata.MetadataMode) { viewModelScope.launch { settings.setMetadataMode(mode) } }

    val tmdbApiKey: StateFlow<String> =
        settings.tmdbApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setTmdbApiKey(key: String) { viewModelScope.launch { settings.setTmdbApiKey(key) } }

    val metadataServerUrl: StateFlow<String> =
        settings.metadataServerUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setMetadataServerUrl(url: String) { viewModelScope.launch { settings.setMetadataServerUrl(url) } }

    val openSubtitlesApiKey: StateFlow<String> = settings.openSubtitlesApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setOpenSubtitlesApiKey(key: String) { viewModelScope.launch { settings.setOpenSubtitlesApiKey(key) } }

    val openSubtitlesServerUrl: StateFlow<String> = settings.openSubtitlesServerUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setOpenSubtitlesServerUrl(url: String) { viewModelScope.launch { settings.setOpenSubtitlesServerUrl(url) } }

    /** TMDB content language ("" = TMDB default en-US, "auto" = device locale, else an ISO 639-1 code). */
    val metadataLanguage: StateFlow<String> =
        settings.metadataLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * Persist the metadata language and wipe the cached TMDB detail rows, which hold text in the *old*
     * language under a language-agnostic key — without the wipe the change wouldn't show until the 60-day
     * TTL expired. Runs under NonCancellable so navigating away mid-write can't leave a half-cleared cache
     * paired with the new language. Matches are kept (title→tmdbId doesn't depend on language).
     */
    fun setMetadataLanguage(code: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                settings.setMetadataLanguage(code)
                runCatching { metadataRepository.clearCacheForLanguageChange() }
                    .onFailure { Log.w("SettingsViewModel", "Metadata cache wipe after language change failed: ${it.message}") }
            }
        }
    }

    /** Which access tier the current config resolves to — shown as the Metadata screen's status chip. */
    val metadataTier: StateFlow<tv.own.owntv.core.metadata.MetadataConfig.Tier> =
        settings.metadataConfigFlow
            .map { it.tier }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.metadata.MetadataConfig.Tier.DEFAULT_WORKER)

    /**
     * This install's remaining metadata allowance, for the status row on the Metadata screen. Only
     * meaningful on the shared default-Worker tier; the screen hides the row on the other two, which
     * are the user's own resource and are never metered.
     *
     * Refreshed when the screen asks ([refreshMetadataBudget]) rather than continuously — it is a
     * DataStore read and the number only moves while the user is browsing, not while they sit in
     * Settings.
     */
    private val _metadataBudgetStatus =
        MutableStateFlow<tv.own.owntv.core.metadata.MetadataBudgetStatus?>(null)
    val metadataBudgetStatus: StateFlow<tv.own.owntv.core.metadata.MetadataBudgetStatus?> =
        _metadataBudgetStatus.asStateFlow()

    fun refreshMetadataBudget() {
        viewModelScope.launch {
            _metadataBudgetStatus.value = runCatching { metadataBudget.status() }.getOrNull()
        }
    }

    sealed interface MetadataTestState {
        data object Idle : MetadataTestState
        data object Testing : MetadataTestState
        data class Ok(val title: String, val year: Int?, val tmdbId: Int) : MetadataTestState
        data class Fail(val failure: MetadataFailure) : MetadataTestState
    }

    sealed interface MetadataFailure {
        data object EmptyTitle : MetadataFailure
        data object ServerUnavailable : MetadataFailure
        data class NoMatch(val query: String) : MetadataFailure
        data class Unknown(val rawMessage: String?) : MetadataFailure
    }

    private val _metadataTest = MutableStateFlow<MetadataTestState>(MetadataTestState.Idle)
    val metadataTest: StateFlow<MetadataTestState> = _metadataTest.asStateFlow()

    fun resetMetadataTest() { _metadataTest.value = MetadataTestState.Idle }

    /** Manual "look up title" through the configured tier — proves the plumbing end-to-end (M1 deliverable). */
    fun testMetadataLookup(title: String) {
        if (_metadataTest.value == MetadataTestState.Testing) return
        val q = title.trim()
        if (q.isEmpty()) {
            _metadataTest.value = MetadataTestState.Fail(MetadataFailure.EmptyTitle)
            return
        }
        _metadataTest.value = MetadataTestState.Testing
        viewModelScope.launch {
            val profileId = settings.activeProfileId.first()
            val includeAdult = tv.own.owntv.core.metadata.profileAllowsAdultMetadata(profileDao.getById(profileId)?.isKids)
            val result = runCatching { metadataProvider.searchMovie(q, includeAdult = includeAdult) }
            _metadataTest.value = result.fold(
                onSuccess = { hits ->
                    val top = hits?.firstOrNull()
                    if (hits == null) {
                        MetadataTestState.Fail(MetadataFailure.ServerUnavailable)
                    } else if (top == null) {
                        MetadataTestState.Fail(MetadataFailure.NoMatch(q))
                    } else {
                        MetadataTestState.Ok(top.title, top.year, top.tmdbId)
                    }
                },
                onFailure = { MetadataTestState.Fail(MetadataFailure.Unknown(it.message?.takeIf { m -> m.isNotBlank() })) },
            )
        }
    }

    private class ProxyHttpException(val code: Int) : java.io.IOException()

    private fun proxyFailure(t: Throwable): ProxyFailure = when (t) {
        is ProxyHttpException -> ProxyFailure.Http(t.code)
        is java.net.UnknownHostException -> ProxyFailure.HostUnreachable
        is java.net.SocketTimeoutException -> ProxyFailure.TimedOut
        is java.net.ConnectException -> ProxyFailure.ConnectionFailed
        else -> ProxyFailure.Unknown(t.message?.takeIf { it.isNotBlank() })
    }
}

/** What the source-test popup is showing. */
sealed interface SourceTestUi {
    val sourceName: String

    data class Running(override val sourceName: String) : SourceTestUi
    data class Done(override val sourceName: String, val result: SourceTestResult) : SourceTestUi
}
