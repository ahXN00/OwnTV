package tv.own.owntv.features.shell

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.weather.WeatherInfo
import tv.own.owntv.core.weather.WeatherRepository
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.sync.ImportFinalizer
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.sync.work.EpgSyncScheduler
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.features.settings.data.EpgAutoRefresh
import tv.own.owntv.features.settings.data.PlaylistAutoRefresh
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.ui.theme.AccentColor
import tv.own.owntv.ui.theme.ThemeMode
import tv.own.owntv.ui.theme.UiZoom

/** Top-level navigation destinations rendered in the Layer-1 sidebar. */
enum class MainSection(val label: String) {
    SEARCH("搜索"),
    HOME("首页"),
    LIVE_TV("直播"),
    MOVIES("电影"),
    SERIES("剧集"),
    DOWNLOADS("下载"),
    EPG("节目指南"),
    SETTINGS("设置"); // pinned at the bottom of the nav

    /** Shown as an icon in the left nav rail. Phase 4 moved Search to the top bar, so it's excluded. */
    val isBrowse: Boolean get() = this != SETTINGS && this != SEARCH
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShellViewModel(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val profileDao: tv.own.owntv.core.database.dao.ProfileDao,
    connectivity: ConnectivityObserver,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val epgMigration: tv.own.owntv.core.epg.EpgMigration,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val epgSyncScheduler: EpgSyncScheduler,
    private val epgSourceStore: EpgSourceStore,
    private val epgDao: EpgDao,
    private val importFinalizer: ImportFinalizer,
    private val weatherRepository: WeatherRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "OwnTVHome"
        /** Minimum gap between resume-triggered staleness checks, to avoid re-running when onStart fires
         *  close to a prior check (rotation, rapid background/foreground). Cold-start checks are NOT
         *  throttled by time — see [coldStartCheckDone]. */
        private const val RESUME_THROTTLE_MS = 60_000L
    }

    /** Cold-start pass runs exactly once per process — STARTUP sources rely on this. Not time-throttled. */
    private var coldStartCheckDone = false
    /** Timestamp (elapsedRealtime) of the last resume-triggered staleness check. */
    private var lastResumeCheckAtElapsed = 0L

    init {
        // One-time: move any existing playlist EPG into the new standalone EPG sources (v2.2.0).
        viewModelScope.launch { runCatching { epgMigration.run() } }
        // One-time: migrate the legacy binary refresh-on-startup set → per-source STARTUP entries.
        viewModelScope.launch { runCatching { settings.migrateLegacyRefreshFlags() } }
        viewModelScope.launch {
            settings.activeProfileId
                .distinctUntilChanged()
                .collect { pid ->
                    Log.d(TAG, "activeProfileChanged profile=$pid androidTvHomeEnabled=${settings.androidTvHomeEnabled.first()}")
                    if (pid >= 0 && settings.androidTvHomeEnabled.first()) {
                        runCatching { launcherIntegrationRepository.refreshProfile(pid, allowBrowsableRequest = true) }
                    }
                }
        }
    }

    /** Whether the device currently has internet (drives the offline banner). */
    val isOnline: StateFlow<Boolean> = connectivity.isOnline
        .stateIn(viewModelScope, SharingStarted.Eagerly, connectivity.isOnlineNow())

    /**
     * Staleness-based auto-refresh check.
     *
     * - `includeStartup = true` (cold start): runs once per process, refreshes STARTUP sources unconditionally
     *   and interval sources whose data is at least as old as their threshold. Never time-throttled so STARTUP
     *   always fires on a real cold start.
     * - `includeStartup = false` (app resume/foreground): skips STARTUP sources (STARTUP is cold-start only)
     *   and refreshes interval sources whose threshold is exceeded. Throttled to once per [RESUME_THROTTLE_MS]
     *   so a quick background→foreground toggle doesn't re-run the check.
     *
     * Auto-refresh enqueues use [ExistingWorkPolicy.KEEP] so a source already syncing/queued is left alone
     * (no churn). Manual re-synces still use REPLACE (handled at their call sites).
     */
    fun checkAutoRefresh(includeStartup: Boolean) {
        if (includeStartup) {
            if (coldStartCheckDone) return
            coldStartCheckDone = true
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastResumeCheckAtElapsed < RESUME_THROTTLE_MS) return
            lastResumeCheckAtElapsed = now
        }
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            val pid = currentProfileId() ?: return@launch
            // --- Playlist sources ---
            val playlistModes = settings.playlistAutoRefresh.first()
            if (playlistModes.isNotEmpty()) {
                val sources = sourceRepository.observeSources(pid).first()
                sources.forEach { source ->
                    val mode = playlistModes[source.id] ?: PlaylistAutoRefresh.OFF
                    if (shouldRefresh(mode, source.lastSyncAt, nowMs, includeStartup)) {
                        val counts = importFinalizer.contentCounts(source.id)
                        Log.d(TAG, "checkAutoRefresh playlist sourceId=${source.id} mode=$mode — enqueuing")
                        catalogSyncScheduler.enqueueSync(
                            source.id,
                            reason = "auto_refresh",
                            baseItemCount = counts.channels + counts.movies + counts.series,
                            policy = ExistingWorkPolicy.KEEP,
                        )
                    }
                }
            }
            // --- EPG sources ---
            val epgModes = settings.epgAutoRefresh.first()
            if (epgModes.isNotEmpty()) {
                val epgSources = epgSourceStore.getAll()
                epgSources.forEach { src ->
                    val mode = epgModes[src.id] ?: EpgAutoRefresh.OFF
                    if (shouldRefreshEpg(mode, src.lastSyncAt, nowMs, includeStartup)) {
                        val base = epgDao.countForSources(listOf(src.id))
                        Log.d(TAG, "checkAutoRefresh epg sourceId=${src.id} mode=$mode — enqueuing")
                        epgSyncScheduler.enqueueSync(
                            src.id,
                            reason = "auto_refresh",
                            baseProgrammes = base,
                            policy = ExistingWorkPolicy.KEEP,
                        )
                    }
                }
            }
        }
    }

    /**
     * Whether a playlist source should auto-refresh now. OFF never; STARTUP only on cold start
     * ([includeStartup]); interval modes when `now - lastSyncAt >= threshold` (a null lastSyncAt — never
     * successfully synced — counts as infinitely stale so recovery happens).
     */
    private fun shouldRefresh(
        mode: PlaylistAutoRefresh,
        lastSyncAt: Long?,
        now: Long,
        includeStartup: Boolean,
    ): Boolean = when (mode) {
        PlaylistAutoRefresh.OFF -> false
        PlaylistAutoRefresh.STARTUP -> includeStartup
        else -> (now - (lastSyncAt ?: 0L)) >= (mode.thresholdMs ?: Long.MAX_VALUE)
    }

    /** EPG equivalent of [shouldRefresh]. */
    private fun shouldRefreshEpg(
        mode: EpgAutoRefresh,
        lastSyncAt: Long?,
        now: Long,
        includeStartup: Boolean,
    ): Boolean = when (mode) {
        EpgAutoRefresh.OFF -> false
        EpgAutoRefresh.STARTUP -> includeStartup
        else -> (now - (lastSyncAt ?: 0L)) >= (mode.thresholdMs ?: Long.MAX_VALUE)
    }

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiZoom.DEFAULT)

    val animationLevel: StateFlow<tv.own.owntv.ui.theme.AnimationLevel> = settings.animationLevel
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.ui.theme.AnimationLevel.FULL)

    val accent: StateFlow<AccentColor> = settings.accent
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccentColor.TEAL)

    /** Custom accent hex ("#52DBC8"); blank = the preset above is in effect. */
    val customAccent: StateFlow<String> = settings.customAccent
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** The active profile's avatar (so the sidebar reflects profile edits, not a separate setting). */
    val avatarId: StateFlow<Int> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(0) else profileDao.observeById(pid).map { it?.avatarId ?: 0 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** The active profile's name, shown in the sidebar profile card. */
    val profileName: StateFlow<String> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf("") else profileDao.observeById(pid).map { it?.name ?: "" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** The active (default) source's name for the sidebar; "No source" when the profile has none. */
    val sourceSummary: StateFlow<String> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList<tv.own.owntv.core.database.entity.SourceEntity>()) else sourceRepository.observeSources(pid) }
        .combine(settings.defaultSourceId) { sources, defaultId ->
            when {
                sources.isEmpty() -> "No source"
                else -> (sources.firstOrNull { it.id == defaultId } ?: sources.first()).name
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "No source")

    /** The active profile's playlists, for the top-bar quick switcher (empty when the profile has none). */
    val playlists: StateFlow<List<tv.own.owntv.core.database.entity.SourceEntity>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else sourceRepository.observeSources(pid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The chosen active-playlist filter: -1 = All playlists (merged view), else a single playlist id. */
    val activePlaylistId: StateFlow<Long> = settings.defaultSourceId
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    /** Switch the active-playlist filter from the top-bar picker. Persists (survives restart). */
    fun setActivePlaylist(id: Long) {
        viewModelScope.launch { settings.setDefaultSource(id) }
    }

    /**
     * Phase 7 — weather chip. Refreshes when connectivity returns, cached 30 min by repository.
     * Gated by the "Show weather" setting (OFF hides the chip) and honouring a manual location
     * override so users on a VPN get their real city instead of the VPN server's.
     */
    val weather: StateFlow<WeatherInfo?> =
        combine(connectivity.isOnline, settings.weatherEnabled, settings.weatherLocation) { online, enabled, loc ->
            Triple(online, enabled, loc)
        }.flatMapLatest { (online, enabled, loc) ->
            if (!online || !enabled) flowOf(null as WeatherInfo?)
            else flow { emit(weatherRepository.get(loc)) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null as WeatherInfo?)

    /** °F display for the weather chip (default °C). */
    val weatherFahrenheit: StateFlow<Boolean> = settings.weatherFahrenheit
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** null = still loading; < 0 = first run (show setup wizard); >= 0 = active profile (show shell). */
    val activeProfileId: StateFlow<Long?> = settings.activeProfileId
        .map<Long, Long?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedSection = MutableStateFlow(MainSection.HOME)
    val selectedSection: StateFlow<MainSection> = _selectedSection.asStateFlow()

    fun selectSection(section: MainSection) {
        _selectedSection.value = section
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** Cycles through the available themes — wired to a temporary button until the Theme screen exists. */
    fun cycleTheme() {
        val next = when (themeMode.value) {
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        setThemeMode(next)
    }

    fun setUiZoom(percent: Int) {
        viewModelScope.launch { settings.setUiZoomPercent(UiZoom.clamp(percent)) }
    }

    fun setAccent(accent: AccentColor) {
        viewModelScope.launch { settings.setAccent(accent) }
    }

    fun setAvatar(id: Int) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            profileDao.setAvatar(pid, id)
        }
    }

    /** Cycles through the accent presets. */
    fun cycleAccent() {
        val values = AccentColor.entries
        val next = values[(accent.value.ordinal + 1) % values.size]
        setAccent(next)
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return profileDao.resolveExistingProfileId(preferred)
    }
}
