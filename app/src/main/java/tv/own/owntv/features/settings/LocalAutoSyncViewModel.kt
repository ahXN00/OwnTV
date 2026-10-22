package tv.own.owntv.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.sync.local.LocalAutoSyncConfig
import tv.own.owntv.core.sync.local.LocalAutoSyncStore
import tv.own.owntv.core.sync.local.LocalAutoSyncTiming
import tv.own.owntv.core.sync.local.LocalSyncManager
import tv.own.owntv.core.sync.local.PairedDevice
import tv.own.owntv.core.sync.work.LocalAutoSyncScheduler

/**
 * The auto-sync settings, seen from the screen that edits them.
 *
 * The settings themselves live in core ([LocalAutoSyncStore]) because they are shared with the
 * phone and because the job that runs them is core's; this only sequences the screen — read what
 * is saved and who could be synced with, hold what the user is changing, and put the result back
 * when they press Save.
 *
 * Saving and scheduling are one act on purpose. A setting that says "daily" while the timer still
 * says "every six hours" is a setting the user has to trust blindly, and there is no way to tell
 * which of the two is wrong from the outside.
 */
class LocalAutoSyncViewModel(
    private val store: LocalAutoSyncStore,
    private val scheduler: LocalAutoSyncScheduler,
    profileDao: ProfileDao,
    localSync: LocalSyncManager,
) : ViewModel() {

    /** What is saved right now. */
    val config: StateFlow<LocalAutoSyncConfig> = store.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LocalAutoSyncConfig())

    /**
     * Who the settings can be scoped to.
     *
     * Both lists are live rather than read once: a profile created while the sheet is open, or a
     * device unpaired on the page behind it, is a row that should not be tickable — and a saved
     * id that no longer matches anything is dropped by the screen rather than shown as ticked.
     */
    val profiles: StateFlow<List<ProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val devices: StateFlow<List<PairedDevice>> = localSync.pairedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Saves the settings and puts the new schedule in front of WorkManager.
     *
     * Everything else about the config is kept — the direction is not this sheet's business, and a
     * sheet that rewrote it with its own default would quietly undo a setting made elsewhere.
     *
     * An empty profile or device set means "all" to the config, so that is what the screen passes
     * when the user has ticked every row: a viewer or a device added next week is then included
     * without anyone reopening this sheet, which is what "all" was understood to mean.
     */
    fun save(
        timing: LocalAutoSyncTiming,
        sections: Set<BackupManager.Section>,
        profileIds: Set<Long>,
        deviceIds: Set<String>,
    ) {
        viewModelScope.launch {
            val config = store.current().copy(
                timing = timing,
                sections = sections,
                profileIds = profileIds,
                deviceIds = deviceIds,
            )
            store.save(config)
            scheduler.schedule(config)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
