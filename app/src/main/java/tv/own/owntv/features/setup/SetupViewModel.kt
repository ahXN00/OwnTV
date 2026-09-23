package tv.own.owntv.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.sync.SyncScopeChoice
import java.io.File

/**
 * Drives onboarding for a profile (first-run and "add profile"): create the profile, then add content
 * (new source, link an existing unlocked profile's playlists, restore a backup, or skip). The new
 * profile is only made active on [finish], so the wizard stays put until the user completes it.
 *
 * The import sequence itself is core's [SourceImporter], shared with the mobile app; this holds the
 * three things that are the television's own — the LAN companion server, the semi-auto EPG prompt,
 * and the job whose lifetime "Run in background" depends on.
 */
class SetupViewModel(
    private val importer: SourceImporter,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val epgSourceStore: tv.own.owntv.core.epg.EpgSourceStore,
    private val companion: tv.own.owntv.core.companion.CompanionController,
) : ViewModel() {

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

    // ---- Remote restore: another device uploads a backup JSON to the TV over the LAN companion server. ----
    /** Uploaded backup files — the remote-restore screen collects this and hands each to [importBackup]. */
    val remoteBackups get() = companion.backups

    fun startRemoteRestore(port: Int) = companion.startForBackupRestore(port)
    fun stopRemoteRestore() = companion.stop()

    val state: StateFlow<SourceImporter.ImportState> = importer.state
    val progress = importer.progress

    // Semi-auto EPG: after the first playlist imports, offer a one-tap guide sync (with a live count) if it
    // has a guide feed.
    private var pendingEpgSource: SourceEntity? = null
    private val _epgSync = MutableStateFlow<tv.own.owntv.features.settings.EpgSyncUi>(tv.own.owntv.features.settings.EpgSyncUi.Hidden)
    val epgSync: StateFlow<tv.own.owntv.features.settings.EpgSyncUi> = _epgSync.asStateFlow()

    private var importJob: Job? = null

    fun syncPendingEpg() {
        val src = pendingEpgSource ?: return
        viewModelScope.launch {
            tv.own.owntv.features.settings.runSemiAutoEpgSync(src, epgRepository, epgSourceStore) { _epgSync.value = it }
        }
    }

    fun dismissPendingEpg() { pendingEpgSource = null; _epgSync.value = tv.own.owntv.features.settings.EpgSyncUi.Hidden }

    /**
     * "Run in background" for the semi-auto EPG sync: enter the app now while the guide keeps
     * downloading. The sync launched by [syncPendingEpg] runs in this activity-scoped [viewModelScope],
     * so it survives leaving the wizard — exactly like the playlist [continueInBackground]. We only
     * finish onboarding; the in-flight job is deliberately not cancelled.
     */
    fun syncEpgInBackground(onDone: (Long?) -> Unit = {}) {
        pendingEpgSource = null
        finish(onDone)
    }

    /** Creates the profile (not active yet); the rest of onboarding attaches content to it. */
    fun createProfile(name: String, avatarId: Int, isKids: Boolean, pin: String?, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch { onCreated(importer.createProfile(name, avatarId, isKids, pin)) }
    }

    fun startXtream(
        name: String,
        server: String,
        username: String,
        password: String,
        userAgent: String = "",
        epgUrl: String = "",
        autoRefresh: PlaylistRefresh = PlaylistRefresh.OFF,
        live: SyncScopeChoice = SyncScopeChoice.Now,
        movies: SyncScopeChoice = SyncScopeChoice.Now,
        series: SyncScopeChoice = SyncScopeChoice.Now,
        preferHls: Boolean = false,
        httpReferer: String = "",
    ) = runImport {
        importer.xtream(name, server, username, password, userAgent, epgUrl, autoRefresh, live, movies, series, preferHls, httpReferer = httpReferer)
    }

    fun startStalker(
        name: String,
        portalUrl: String,
        mac: String,
        serialNumber: String = "",
        deviceId: String = "",
        deviceId2: String = "",
        signature: String = "",
        userAgent: String = "",
        autoRefresh: PlaylistRefresh = PlaylistRefresh.OFF,
        live: SyncScopeChoice = SyncScopeChoice.Now,
        movies: SyncScopeChoice = SyncScopeChoice.Later,
        series: SyncScopeChoice = SyncScopeChoice.Later,
        httpReferer: String = "",
    ) = runImport {
        importer.stalker(
            name, portalUrl, mac, serialNumber, deviceId, deviceId2, signature, userAgent,
            autoRefresh, live, movies, series, httpReferer = httpReferer,
        )
    }

    fun startM3u(name: String, url: String, userAgent: String = "", epgUrl: String = "", autoRefresh: PlaylistRefresh = PlaylistRefresh.OFF, httpReferer: String = "") =
        runImport { importer.m3u(name, url, userAgent, epgUrl, autoRefresh, httpReferer = httpReferer) }

    /**
     * Runs one import in the activity-scoped [viewModelScope] so "Run in background" can walk away
     * from it, then raises the semi-auto EPG prompt if the playlist that just landed has a guide feed.
     */
    private fun runImport(block: suspend () -> Unit) {
        importJob?.cancel()
        val job = viewModelScope.launch {
            block()
            val done = importer.state.value as? SourceImporter.ImportState.Success ?: return@launch
            val source = done.source ?: return@launch
            if (!importer.backgroundHandoff && epgRepository.guideUrl(source) != null) {
                pendingEpgSource = source
                _epgSync.value = tv.own.owntv.features.settings.EpgSyncUi.Ask(source.name)
            }
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    /** Playlists belonging to unlocked (no-PIN) profiles that aren't already on the new profile. */
    suspend fun availableExistingSources(): List<SourceEntity> = importer.availableExistingSources()

    /** Link the chosen existing sources to the new profile, then re-sync each one. */
    fun linkExisting(sourceIds: Set<Long>) = runImport { importer.linkExisting(sourceIds) }

    /**
     * Restore a backup file (merges profiles & sources, then activates one). Encrypted backups first
     * ask for the backup password via [SourceImporter.ImportState.NeedPassword].
     *
     * [sections] is what the user ticked before the restore began, and defaults to all of it — which
     * is what the wizard did unconditionally until it gained a picker of its own. Settings → Backup
     * & Restore has always asked; the first run, where a restore is most likely, did not.
     */
    fun importBackup(
        file: File,
        onDone: (Long?) -> Unit,
        sections: Set<BackupManager.Section> = BackupManager.Section.entries.toSet(),
        /** Take another device's hardware settings too — see [BackupManager.import]. */
        deviceSettings: Boolean = false,
    ) {
        viewModelScope.launch { if (importer.importBackup(file, sections, deviceSettings)) onRestored(onDone) }
    }

    /** Continue an encrypted restore once the user provides (or skips, password = null) the passphrase. */
    fun restoreWithPassword(
        file: File,
        password: String?,
        onDone: (Long?) -> Unit,
        sections: Set<BackupManager.Section> = BackupManager.Section.entries.toSet(),
        deviceSettings: Boolean = false,
    ) {
        viewModelScope.launch { if (importer.restoreWithPassword(file, password, sections, deviceSettings)) onRestored(onDone) }
    }

    // A backup may restore several profiles or a PIN-locked active profile. Restoring data is not
    // authenticating a viewer, so let MainActivity require the selected profile's PIN rather than
    // treating the restore as a gate pass.
    private fun onRestored(onDone: (Long?) -> Unit) = onDone(null)

    fun reset() = importer.reset()

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        importer.reset()
    }

    /**
     * "Run in background": enter the app now while the import keeps running. This ViewModel is
     * activity-scoped, so the in-flight import survives leaving the wizard — the sync continues
     * exactly as if the user had waited (success still runs ImportFinalizer + the remainder enqueue).
     * Deliberately does NOT cancel: cancelling would delete the half-imported source. The semi-auto
     * EPG prompt is skipped (its dialog lives in the wizard); EPG stays user-initiated from
     * Settings → EPG Sources, matching the app's EPG opt-in policy.
     */
    fun continueInBackground(onDone: (Long?) -> Unit = {}) {
        importer.backgroundHandoff = true
        dismissPendingEpg()
        finish(onDone)
    }

    /** Completes onboarding → makes the new profile active, routing the app into the shell. */
    fun finish(onDone: (Long?) -> Unit = {}) {
        viewModelScope.launch { onDone(importer.finish()) }
    }
}
