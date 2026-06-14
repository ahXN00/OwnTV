@file:OptIn(ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.util.friendlySyncError
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.ui.theme.AccentColor
import tv.own.owntv.ui.theme.ThemeMode
import tv.own.owntv.ui.theme.UiZoom

/** Phase 13 — manage IPTV sources (list / add / re-sync / delete) for the active profile. */
class SettingsViewModel(
    private val sourceDao: SourceDao,
    private val sourceRepository: SourceRepository,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityObserver,
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
    private val importFinalizer: tv.own.owntv.core.sync.ImportFinalizer,
) : ViewModel() {

    /** Stored EPG programme count for a source — the row shows it as the EPG status. */
    fun epgCount(sourceId: Long): kotlinx.coroutines.flow.Flow<Int> = epgDao.countForSource(sourceId)

    /** Content counts (channels/movies/series) for a source — shown on each Playlists row. */
    fun contentCounts(sourceId: Long): kotlinx.coroutines.flow.Flow<tv.own.owntv.core.sync.SyncCounts> =
        kotlinx.coroutines.flow.flow { emit(importFinalizer.contentCounts(sourceId)) }

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

    // --- Video Player Settings ---
    val hwDecoding: StateFlow<Boolean> = settings.hwDecoding.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setHwDecoding(enabled: Boolean) { viewModelScope.launch { settings.setHwDecoding(enabled) } }

    val updateCheckOnStart: StateFlow<Boolean> =
        settings.updateCheckOnStart.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setUpdateCheckOnStart(enabled: Boolean) { viewModelScope.launch { settings.setUpdateCheckOnStart(enabled) } }

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

    val preferredAudioLang: StateFlow<String> = settings.preferredAudioLang.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setPreferredAudioLang(lang: String) { viewModelScope.launch { settings.setPreferredAudioLang(lang) } }

    val preferredSubLang: StateFlow<String> = settings.preferredSubLang.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setPreferredSubLang(lang: String) { viewModelScope.launch { settings.setPreferredSubLang(lang) } }

    // --- Personalization (theme / accent / UI zoom) ---
    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AMOLED_DARK)
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }

    val accent: StateFlow<AccentColor> = settings.accent.stateIn(viewModelScope, SharingStarted.Eagerly, AccentColor.TEAL)
    fun setAccent(accent: AccentColor) { viewModelScope.launch { settings.setAccent(accent) } }

    /** Custom accent hex ("#52DBC8"); blank = the preset is in effect. */
    val customAccent: StateFlow<String> = settings.customAccent.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setCustomAccent(hex: String) { viewModelScope.launch { settings.setCustomAccent(hex) } }

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent.stateIn(viewModelScope, SharingStarted.Eagerly, UiZoom.DEFAULT)
    fun setUiZoom(percent: Int) { viewModelScope.launch { settings.setUiZoomPercent(UiZoom.clamp(percent)) } }

    /** Source ids flagged "refresh on startup". */
    val refreshSourceIds: StateFlow<Set<Long>> = settings.refreshSourceIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun setSourceRefresh(sourceId: Long, enabled: Boolean) {
        viewModelScope.launch { settings.setSourceRefresh(sourceId, enabled) }
    }

    /** Edit an existing source's settings (no re-import unless the user re-syncs). */
    fun updateSource(id: Long, name: String, urlOrServer: String, user: String, pass: String, userAgent: String, epgUrl: String, refreshOnStart: Boolean) {
        viewModelScope.launch {
            val existing = sourceDao.getById(id) ?: return@launch
            sourceRepository.updateSource(
                existing.copy(
                    name = name.ifBlank { existing.name },
                    url = urlOrServer.trim().ifBlank { existing.url },
                    username = user.trim().takeIf { it.isNotBlank() } ?: existing.username,
                    password = pass.takeIf { it.isNotBlank() } ?: existing.password,
                    userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                    epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
                ),
            )
            settings.setSourceRefresh(id, refreshOnStart)
        }
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _progress = MutableStateFlow<ImportStage?>(null)
    val progress: StateFlow<ImportStage?> = _progress.asStateFlow()

    fun addXtream(name: String, server: String, user: String, pass: String, userAgent: String = "", epgUrl: String = "", refreshOnStart: Boolean = false) = runImport(refreshOnStart) { pid ->
        sourceRepository.addXtreamSource(
            pid, name.ifBlank { "My IPTV" }, server.trim(), user.trim(), pass,
            userAgent.trim().takeIf { it.isNotBlank() },
            epgUrl.trim().takeIf { it.isNotBlank() },
        )
    }

    fun addM3u(name: String, url: String, userAgent: String = "", epgUrl: String = "", refreshOnStart: Boolean = false) = runImport(refreshOnStart) { pid ->
        sourceRepository.addM3uSource(
            pid, name.ifBlank { "My Playlist" }, url.trim(),
            userAgent.trim().takeIf { it.isNotBlank() },
            epgUrl.trim().takeIf { it.isNotBlank() },
        )
    }

    private fun runImport(refreshOnStart: Boolean = false, addSource: suspend (Long) -> SourceEntity) {
        viewModelScope.launch {
            _importState.value = ImportState.Running
            _progress.value = null
            try {
                val pid = settings.activeProfileId.first()
                val source = addSource(pid)
                settings.setSourceRefresh(source.id, refreshOnStart)
                when (val r = sourceRepository.sync(source) { _progress.value = it }) {
                    SyncResult.Success -> {
                        // Settings playlist add: content breakdown only (EPG syncs silently and is
                        // shown on the EPG Sources screen, per the separated-EPG design).
                        val counts = importFinalizer.finalize(source)
                        _importState.value = ImportState.Success(counts.summary(includeEpg = false))
                    }
                    is SyncResult.Failed -> _importState.value = ImportState.Failed(friendlySyncError(r.message, connectivity.isOnlineNow()))
                    SyncResult.Cancelled -> _importState.value = ImportState.Idle
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _importState.value = ImportState.Failed(friendlySyncError(e.message, connectivity.isOnlineNow()))
            }
        }
    }

    /** Re-sync an existing source, driving the same import-progress UI as adding one. */
    fun resync(source: SourceEntity) {
        viewModelScope.launch {
            _importState.value = ImportState.Running
            _progress.value = null
            when (val r = sourceRepository.sync(source) { _progress.value = it }) {
                SyncResult.Success -> {
                    val counts = importFinalizer.finalize(source)
                    _importState.value = ImportState.Success(counts.summary(includeEpg = false))
                }
                is SyncResult.Failed -> _importState.value = ImportState.Failed(friendlySyncError(r.message, connectivity.isOnlineNow()))
                SyncResult.Cancelled -> _importState.value = ImportState.Idle
            }
        }
    }

    fun delete(source: SourceEntity) {
        viewModelScope.launch {
            sourceRepository.deleteSource(source)
            if (defaultSourceId.value == source.id) settings.setDefaultSource(-1L)
        }
    }

    fun resetImport() {
        _importState.value = ImportState.Idle
        _progress.value = null
    }
}
