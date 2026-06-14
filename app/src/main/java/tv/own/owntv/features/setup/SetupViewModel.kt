package tv.own.owntv.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.util.Pin
import tv.own.owntv.core.util.friendlySyncError
import tv.own.owntv.features.settings.data.SettingsRepository
import java.io.File

/**
 * Drives onboarding for a profile (first-run and "add profile"): create the profile, then add content
 * (new source, link an existing unlocked profile's playlists, restore a backup, or skip). The new
 * profile is only made active on [finish], so the wizard stays put until the user completes it.
 */
class SetupViewModel(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val sourceRepository: SourceRepository,
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityObserver,
    private val importFinalizer: tv.own.owntv.core.sync.ImportFinalizer,
) : ViewModel() {

    sealed interface ImportState {
        data object Idle : ImportState
        data object Running : ImportState
        /** Per-type breakdown (incl. EPG) shown on the onboarding "All set" screen. */
        data class Success(val summary: String) : ImportState
        data class Failed(val message: String) : ImportState
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _progress = MutableStateFlow<ImportStage?>(null)
    val progress: StateFlow<ImportStage?> = _progress.asStateFlow()

    private var createdProfileId = -1L

    /** Creates the profile (not active yet); the rest of onboarding attaches content to it. */
    fun createProfile(name: String, avatarId: Int, isKids: Boolean, pin: String?) {
        viewModelScope.launch {
            createdProfileId = profileDao.insert(
                ProfileEntity(
                    name = name.ifBlank { "Profile" },
                    avatarColor = 0,
                    avatarId = avatarId,
                    isKids = isKids,
                    pinHash = pin?.takeIf { it.isNotBlank() }?.let { Pin.hash(it) },
                ),
            )
        }
    }

    fun startXtream(name: String, server: String, username: String, password: String, userAgent: String = "", epgUrl: String = "", refreshOnStart: Boolean = false) =
        runImport(refreshOnStart) { profileId ->
            sourceRepository.addXtreamSource(
                profileId = profileId,
                name = name.ifBlank { "My IPTV" },
                serverUrl = server.trim(),
                username = username.trim(),
                password = password,
                userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
            )
        }

    fun startM3u(name: String, url: String, userAgent: String = "", epgUrl: String = "", refreshOnStart: Boolean = false) =
        runImport(refreshOnStart) { profileId ->
            sourceRepository.addM3uSource(
                profileId = profileId,
                name = name.ifBlank { "My Playlist" },
                url = url.trim(),
                userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
            )
        }

    private fun runImport(refreshOnStart: Boolean = false, addSource: suspend (Long) -> SourceEntity) {
        viewModelScope.launch {
            _state.value = ImportState.Running
            _progress.value = null
            try {
                val profileId = createdProfileId.takeIf { it > 0 } ?: ensureFallbackProfile()
                val source = addSource(profileId)
                settings.setSourceRefresh(source.id, refreshOnStart)
                when (val result = sourceRepository.sync(source) { _progress.value = it }) {
                    SyncResult.Success -> {
                        // Onboarding: also auto-sync the playlist's EPG and show the full breakdown.
                        val counts = importFinalizer.finalize(source)
                        _state.value = ImportState.Success(counts.summary(includeEpg = true))
                    }
                    is SyncResult.Failed -> _state.value = ImportState.Failed(friendlySyncError(result.message, connectivity.isOnlineNow()))
                    SyncResult.Cancelled -> _state.value = ImportState.Idle
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _state.value = ImportState.Failed(friendlySyncError(e.message, connectivity.isOnlineNow()))
            }
        }
    }

    /** Playlists belonging to unlocked (no-PIN) profiles that aren't already on the new profile. */
    suspend fun availableExistingSources(): List<SourceEntity> {
        val unlocked = profileDao.getAllOnce().filter { it.pinHash == null && it.id != createdProfileId }.map { it.id }.toSet()
        if (unlocked.isEmpty()) return emptyList()
        val links = sourceDao.allLinks()
        val fromUnlocked = links.filter { it.profileId in unlocked }.map { it.sourceId }.toSet()
        val alreadyMine = links.filter { it.profileId == createdProfileId }.map { it.sourceId }.toSet()
        val wanted = fromUnlocked - alreadyMine
        return sourceDao.getAllOnce().filter { it.id in wanted }
    }

    /**
     * Link the chosen existing sources to the new profile (shared content, separate favorites/history),
     * then re-sync each one so its catalog is fresh — exactly like adding a brand-new source. Drives the
     * same [state]/[progress] as [runImport], so the wizard can show the import screen.
     */
    fun linkExisting(sourceIds: Set<Long>) {
        viewModelScope.launch {
            _state.value = ImportState.Running
            _progress.value = null
            try {
                val pid = createdProfileId.takeIf { it > 0 } ?: ensureFallbackProfile()
                sourceIds.forEach { sourceDao.link(ProfileSourceCrossRef(profileId = pid, sourceId = it)) }
                val sources = sourceDao.getAllOnce().filter { it.id in sourceIds }
                var total = tv.own.owntv.core.sync.SyncCounts(0, 0, 0, 0)
                var failure: String? = null
                for (source in sources) {
                    when (val result = sourceRepository.sync(source) { _progress.value = it }) {
                        SyncResult.Success -> {
                            val c = importFinalizer.finalize(source)
                            total = tv.own.owntv.core.sync.SyncCounts(total.channels + c.channels, total.movies + c.movies, total.series + c.series, total.epg + c.epg)
                        }
                        is SyncResult.Failed -> failure = result.message
                        SyncResult.Cancelled -> {}
                    }
                }
                _state.value = failure?.let { ImportState.Failed(friendlySyncError(it, connectivity.isOnlineNow())) } ?: ImportState.Success(total.summary(includeEpg = true))
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _state.value = ImportState.Failed(friendlySyncError(e.message, connectivity.isOnlineNow()))
            }
        }
    }

    /** Restore everything from a backup file (replaces profiles & sources, then activates one). */
    fun importBackup(file: File, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = ImportState.Running
            backup.import(file).fold(
                onSuccess = { _state.value = ImportState.Success("Restored $it items. Re-sync your sources to load content."); onDone() },
                onFailure = { _state.value = ImportState.Failed(it.message ?: "Restore failed") },
            )
        }
    }

    private suspend fun ensureFallbackProfile(): Long {
        if (createdProfileId > 0) return createdProfileId
        createdProfileId = profileDao.insert(ProfileEntity(name = "Profile", avatarColor = 0, avatarId = 0))
        return createdProfileId
    }

    fun reset() {
        _state.value = ImportState.Idle
        _progress.value = null
    }

    /** Completes onboarding → makes the new profile active, routing the app into the shell. */
    fun finish() {
        viewModelScope.launch {
            if (createdProfileId > 0) settings.setActiveProfile(createdProfileId)
        }
    }
}
