package tv.own.owntv.features.shell

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.nav.NavVisibility
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.weather.WeatherInfo
import tv.own.owntv.core.weather.WeatherRepository
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShellViewModel(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val profileDao: tv.own.owntv.core.database.dao.ProfileDao,
    connectivity: ConnectivityObserver,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val epgMigration: tv.own.owntv.core.epg.EpgMigration,
    private val autoRefresh: tv.own.owntv.core.sync.AutoRefresh,
    private val weatherRepository: WeatherRepository,
    private val navVisibility: NavVisibility,
    private val profiles: tv.own.owntv.core.profile.ProfileManager,
) : ViewModel() {

    companion object {
        private const val TAG = "OwnTVHome"
    }

    /** Which player presentation is on screen, and which section armed it. Held here rather than in
     *  the composition so a recreation of the Activity (a script-family language switch) does not
     *  close a film that is playing. */
    internal val playerMode = mutableStateOf(PlayerMode.NONE)
    internal val zapSource = mutableStateOf<MainSection?>(null)

    /** The startup update check runs once per launch, not once per shell composition. */
    var startupUpdateCheckDone = false

    init {
        // One-time: move any existing playlist EPG into the new standalone EPG sources (v2.2.0).
        viewModelScope.launch { runCatching { epgMigration.run() } }
        // The one-shot settings migrations run from core at process start (PlaybackStartup).
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), connectivity.isOnlineNow())

    /** The Auto refresh settings — core's [tv.own.owntv.core.sync.AutoRefresh], shared with the phone. */
    fun checkAutoRefresh(includeStartup: Boolean) {
        viewModelScope.launch { autoRefresh.check(includeStartup) }
    }

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiZoom.DEFAULT)

    val fontCustomization: StateFlow<FontCustomization> = settings.fontCustomization
        .stateIn(viewModelScope, SharingStarted.Eagerly, FontCustomization())

    val animationLevel: StateFlow<tv.own.owntv.core.theme.AnimationLevel> = settings.animationLevel
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.theme.AnimationLevel.FULL)

    val accent: StateFlow<AccentColor> = settings.accent
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccentColor.TEAL)

    /** Custom accent hex ("#52DBC8"); blank = the preset above is in effect. */
    val customAccent: StateFlow<String> = settings.customAccent
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Focus ring color hex (#121); blank = follow the accent. */
    val focusHighlight: StateFlow<String> = settings.focusHighlight
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Focus ring width in dp (#121). */
    val focusHighlightWidth: StateFlow<Int> = settings.focusHighlightWidth
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    /** Glass effect background image path (app-private); blank = no background (panels solid). */
    val bgImagePath: StateFlow<String> = settings.bgImagePath
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Resolved glass config (which surfaces + alpha). Empty scope = feature off. */
    val glassConfig: StateFlow<tv.own.owntv.core.theme.GlassConfig> = settings.glassConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.theme.GlassConfig())

    /** Glass & background: what sits behind every screen. */
    val backgroundConfig: StateFlow<tv.own.owntv.core.theme.BackgroundConfig> = settings.backgroundConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.theme.BackgroundConfig())

    /** The active profile's avatar (so the sidebar reflects profile edits, not a separate setting). */
    val avatarId: StateFlow<Int> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(0) else profileDao.observeById(pid).map { it?.avatarId ?: 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * The active profile's own picture, when it has one — blank means the drawn tile named by
     * [avatarId]. Every surface that draws an avatar reads this alongside the id, so a picture set
     * here shows up in the sidebar, the profile gate and the profile list without any of them
     * knowing where it came from.
     */
    val avatarPath: StateFlow<String> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf("") else profileDao.observeById(pid).map { it?.avatarPath.orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** The active profile's name, shown in the sidebar profile card. */
    val profileName: StateFlow<String> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf("") else profileDao.observeById(pid).map { it?.name ?: "" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** The active (default) source's name for the sidebar; null means the profile has none. */
    val sourceSummary: StateFlow<String?> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList<tv.own.owntv.core.database.entity.SourceEntity>()) else sourceRepository.observeSources(pid) }
        .combine(settings.defaultSourceId) { sources, defaultId ->
            when {
                sources.isEmpty() -> null
                else -> (sources.firstOrNull { it.id == defaultId } ?: sources.first()).name
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The active profile's playlists, for the top-bar quick switcher (empty when the profile has none). */
    val playlists: StateFlow<List<tv.own.owntv.core.database.entity.SourceEntity>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else sourceRepository.observeSources(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The chosen active-playlist filter: -1 = All playlists (merged view), else a single playlist id. */
    val activePlaylistId: StateFlow<Long> = settings.defaultSourceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1L)

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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as WeatherInfo?)

    /** °F display for the weather chip (default °C). */
    val weatherFahrenheit: StateFlow<Boolean> = settings.weatherFahrenheit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** null = still loading; < 0 = first run (show setup wizard); >= 0 = active profile (show shell). */
    val activeProfileId: StateFlow<Long?> = settings.activeProfileId
        .map<Long, Long?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedSection = MutableStateFlow(MainSection.HOME)
    val selectedSection: StateFlow<MainSection> = _selectedSection.asStateFlow()

    fun selectSection(section: MainSection) {
        _selectedSection.value = section
    }

    /** Which browse sections currently show as icons in the rail (v4.3.0 — Nav menu customization).
     *  The rule itself lives in core's [NavVisibility], shared with the mobile app's bottom bar. */
    val visibleSections: StateFlow<Set<MainSection>> = navVisibility.visibleSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainSection.allBrowse)

    init {
        // v4.3.0 — if the section the user is viewing becomes hidden (in either mode), jump to the first
        // still-visible browse item; if every browse item is hidden, fall back to Settings (always pinned).
        // SEARCH and SETTINGS are never auto-redirected away from.
        visibleSections
            .onEach { visible ->
                val current = _selectedSection.value
                // MORE is exempt for the same reason SETTINGS is: it is a pinned rail item, not a
                // browse section, so it is never in `visible` and would otherwise be redirected away
                // from the moment the user changed anything in Nav menu customization.
                if (current in visible ||
                    current == MainSection.SETTINGS ||
                    current == MainSection.SEARCH ||
                    current == MainSection.MORE
                ) {
                    return@onEach
                }
                _selectedSection.value = MainSection.browseOrder.firstOrNull { it in visible } ?: MainSection.SETTINGS
            }
            .launchIn(viewModelScope)
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

    fun setFontCustomization(value: FontCustomization) {
        viewModelScope.launch {
            settings.setFontCustomization(
                value.copy(
                    sizePercent = UiFontScale.clamp(value.sizePercent),
                    popupFontSizePercent = tv.own.owntv.core.theme.PopupFontScale.clamp(value.popupFontSizePercent),
                    popupSizePercent = tv.own.owntv.core.theme.PopupSizeScale.clamp(value.popupSizePercent),
                ),
            )
        }
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

    /**
     * Give the active profile a picture of its own, from a file the user picked on this device or
     * one a phone sent over the local network. [onResult] reports whether the file was a readable
     * image, so the caller can say so rather than leaving a dialog that appears to have done nothing.
     */
    fun setCustomAvatar(source: java.io.File, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val pid = currentProfileId()
            val profile = pid?.let { profileDao.getById(it) }
            onResult(profile != null && profiles.setCustomAvatar(profile, source))
        }
    }

    /** Drop the active profile's own picture; the drawn tile it already had comes back. */
    fun clearCustomAvatar() {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            profileDao.getById(pid)?.let { profiles.clearCustomAvatar(it) }
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
