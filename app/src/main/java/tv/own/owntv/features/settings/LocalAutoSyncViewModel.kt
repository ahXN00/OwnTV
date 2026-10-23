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
import tv.own.owntv.core.sync.local.LocalAutoSyncSettings
import tv.own.owntv.core.sync.local.LocalAutoSyncStore
import tv.own.owntv.core.sync.local.LocalAutoSyncTiming
import tv.own.owntv.core.sync.work.LocalAutoSyncScheduler

/**
 * The auto-sync settings, seen from the screen that edits them.
 *
 * The settings themselves live in core ([LocalAutoSyncStore]) because they are shared with the
 * phone and because the job that runs them is core's; this only sequences the screen — read what
 * is saved, hold what the user is changing on one device's row, and put the result back when they
 * press Save.
 *
 * One device at a time, because that is the shape of the setting: "daily, everything, shared with
 * the television" and "weekly, playlists only, shared with the tablet" are two answers that can
 * both be true, and one screen that edited a list of devices could only hold one of them.
 *
 * Saving and scheduling are one act on purpose. A setting that says "daily" while the timer still
 * says "every six hours" is a setting the user has to trust blindly, and there is no way to tell
 * which of the two is wrong from the outside.
 */
class LocalAutoSyncViewModel(
    private val store: LocalAutoSyncStore,
    private val scheduler: LocalAutoSyncScheduler,
    profileDao: ProfileDao,
) : ViewModel() {

    /** Every paired device's answer, as it is saved right now. */
    val settings: StateFlow<LocalAutoSyncSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LocalAutoSyncSettings())

    /**
     * Who the settings can be scoped to.
     *
     * Live rather than read once: a profile created while the sheet is open is a row that should
     * become tickable, and a saved id that no longer matches anything is dropped by the screen
     * rather than shown as ticked.
     */
    val profiles: StateFlow<List<ProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Saves one device's settings and puts the new schedule in front of WorkManager.
     *
     * Everything else about that device's config is kept — the direction is not this sheet's
     * business, and a sheet that rewrote it with its own default would quietly undo a setting made
     * elsewhere. The other devices are not touched at all: their rows on the page are not this
     * sheet's business either.
     *
     * An empty profile set means "all" to the config, so that is what the screen passes when the
     * user has ticked every row: a viewer added next week is then included without anyone reopening
     * this sheet, which is what "all" was understood to mean.
     */
    fun save(
        deviceId: String,
        timing: LocalAutoSyncTiming,
        sections: Set<BackupManager.Section>,
        profileIds: Set<Long>,
    ) {
        viewModelScope.launch {
            val config = store.current()[deviceId].copy(
                timing = timing,
                sections = sections,
                profileIds = profileIds,
            )
            store.save(deviceId, config)
            scheduler.schedule(store.current())
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
