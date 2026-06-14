package tv.own.owntv.features.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.ui.theme.AccentColor
import tv.own.owntv.ui.theme.ThemeMode
import tv.own.owntv.ui.theme.UiZoom

/** Top-level navigation destinations rendered in the Layer-1 sidebar. */
enum class MainSection(val label: String) {
    SEARCH("Search"),
    LIVE_TV("Live TV"),
    MOVIES("Movies"),
    SERIES("Series"),
    DOWNLOADS("Downloads"),
    EPG("Guide"),
    SETTINGS("Settings"); // pinned at the bottom of the nav

    val isBrowse: Boolean get() = this != SETTINGS
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShellViewModel(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val profileDao: tv.own.owntv.core.database.dao.ProfileDao,
    connectivity: ConnectivityObserver,
    private val epgMigration: tv.own.owntv.core.epg.EpgMigration,
) : ViewModel() {

    private var refreshedThisSession = false

    init {
        // One-time: move any existing playlist EPG into the new standalone EPG sources (v2.2.0).
        viewModelScope.launch { runCatching { epgMigration.run() } }
    }

    /** Whether the device currently has internet (drives the offline banner). */
    val isOnline: StateFlow<Boolean> = connectivity.isOnline
        .stateIn(viewModelScope, SharingStarted.Eagerly, connectivity.isOnlineNow())

    /** Re-sync the sources flagged "refresh on startup" for the active profile (once per launch). */
    fun refreshOnStartIfEnabled() {
        if (refreshedThisSession) return
        refreshedThisSession = true
        viewModelScope.launch {
            val ids = settings.refreshSourceIds.first()
            if (ids.isEmpty()) return@launch
            val pid = settings.activeProfileId.first()
            if (pid < 0) return@launch
            sourceRepository.observeSources(pid).first()
                .filter { it.id in ids }
                .forEach { source -> runCatching { sourceRepository.sync(source) {} } }
        }
    }

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AMOLED_DARK)

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiZoom.DEFAULT)

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

    /** null = still loading; < 0 = first run (show setup wizard); >= 0 = active profile (show shell). */
    val activeProfileId: StateFlow<Long?> = settings.activeProfileId
        .map<Long, Long?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedSection = MutableStateFlow(MainSection.LIVE_TV)
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
            ThemeMode.AMOLED_DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
            ThemeMode.SYSTEM -> ThemeMode.AMOLED_DARK
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
            val pid = settings.activeProfileId.first()
            if (pid >= 0) profileDao.setAvatar(pid, id)
        }
    }

    /** Cycles through the accent presets. */
    fun cycleAccent() {
        val values = AccentColor.entries
        val next = values[(accent.value.ordinal + 1) % values.size]
        setAccent(next)
    }
}
