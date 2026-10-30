package tv.own.owntv.features.settings

import android.util.Log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.repository.SourceEditor
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.stalker.StalkerAuthManager
import tv.own.owntv.core.sync.ImportFinalizer
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.work.CatalogSyncScheduler

/**
 * Re-sync, delete and launcher-refresh for a saved playlist, shared by Settings › Playlists and the
 * automation API so both do exactly the same thing.
 */
class SourceActions(
    private val profileDao: ProfileDao,
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val importFinalizer: ImportFinalizer,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val sourceEditor: SourceEditor,
    private val stalkerAuth: StalkerAuthManager,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
) {
    suspend fun activeProfileId(): Long? = profileDao.resolveExistingProfileId(settings.activeProfileId.first())

    /** Through WorkManager, so it carries on after the caller is gone. See [SettingsViewModel.resync] for [clean]. */
    suspend fun resync(source: SourceEntity, clean: Boolean) {
        val counts = importFinalizer.contentCounts(source.id)
        catalogSyncScheduler.enqueueSync(
            source.id,
            reason = if (clean) "manual_clean_resync" else "manual_resync",
            contentTypes = SyncContentTypes.enabledOf(source),
            baseItemCount = counts.channels + counts.movies + counts.series,
            forcePrune = clean,
        )
    }

    /** Saves an edited playlist; see [SourceEdit]. Core's [SourceEditor] stores it and decides whether to re-sync. */
    suspend fun update(edit: SourceEdit) {
        with(edit) {
            val existing = sourceRepository.getById(id) ?: return
            // A Stalker edit re-canonicalizes the MAC; a garbled edit keeps the stored one.
            val newMac = tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(mac) ?: existing.mac
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
                httpReferer = httpReferer.trim().takeIf { it.isNotEmpty() },
                epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
                syncLive = syncLive,
                syncMovies = syncMovies,
                syncSeries = syncSeries,
                preferHls = preferHls,
            )
            sourceEditor.save(existing, updated)
            settings.setPlaylistAutoRefresh(id, autoRefresh)
            // Apply the "Default playlist" toggle: on → this becomes the active playlist; off → if this was
            // the default, clear it back to All. Leaves another playlist's default untouched.
            when {
                isDefault -> settings.setDefaultSource(id)
                settings.defaultSourceId.first() == id -> settings.setDefaultSource(-1L)
            }
            // Hidden sections must drop from the Android TV launcher immediately.
            runCatching { refreshTvHome(allowBrowsableRequest = true) }
        }
    }

    suspend fun delete(source: SourceEntity) {
        catalogSyncScheduler.cancelSync(source.id)
        stalkerAuth.invalidate(source.id)
        // NonCancellable: once confirmed, finish the cascade even if the caller goes away mid-delete —
        // an interrupted half-deleted source is worse than a short wait.
        withContext(NonCancellable) {
            sourceRepository.deleteSource(source)
            if (settings.defaultSourceId.first() == source.id) settings.setDefaultSource(-1L)
            refreshTvHome(allowBrowsableRequest = true)
        }
    }

    suspend fun refreshTvHome(allowBrowsableRequest: Boolean = true) {
        val pid = activeProfileId() ?: return
        Log.d(TAG, "refreshActiveTvHome profile=$pid allowBrowsable=$allowBrowsableRequest")
        launcherIntegrationRepository.refreshProfile(pid, allowBrowsableRequest)
    }

    private companion object {
        const val TAG = "OwnTVHome"
    }
}
