package tv.own.owntv.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.sync.local.AutoSyncEnableResult
import tv.own.owntv.core.sync.local.LocalAutoSyncSettings
import tv.own.owntv.core.sync.local.LocalAutoSyncStore
import tv.own.owntv.core.sync.local.LocalAutoSyncTiming
import tv.own.owntv.core.sync.local.LocalSyncManager
import tv.own.owntv.core.sync.local.PairedDevice
import tv.own.owntv.core.sync.local.SyncFailure
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
 * The switch at the top of the sheet is not saved like the rest: turning it on asks the other device
 * and waits for its answer ([enable]), and only then writes the schedule. Turning it off is local
 * and immediate. Everything else in the sheet is a draft of a schedule that already exists, which is
 * why it is inert until the switch is on.
 *
 * Saving and scheduling are one act on purpose. A setting that says "daily" while the timer still
 * says "every six hours" is a setting the user has to trust blindly, and there is no way to tell
 * which of the two is wrong from the outside.
 */
class LocalAutoSyncViewModel(
    private val store: LocalAutoSyncStore,
    private val scheduler: LocalAutoSyncScheduler,
    private val sync: LocalSyncManager,
    profileDao: ProfileDao,
) : ViewModel() {

    /** Every paired device's answer, as it is saved right now. */
    val settings: StateFlow<LocalAutoSyncSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LocalAutoSyncSettings())

    /**
     * The devices that have accepted this one syncing with them automatically.
     *
     * Live, because accepting happens on the other device while this screen is open: the row for it
     * must change without the user reopening the sheet.
     */
    val consents: StateFlow<Set<String>> = store.consents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

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
     * What the enable switch is doing.
     *
     * [Idle] is not "off": whether auto-sync is on is read from the saved device's own answer. This
     * only describes the request in flight and what came of the last one, which is what the sheet
     * has to say under the switch.
     */
    sealed interface EnableState {
        data object Idle : EnableState

        /** The other device has been asked and its screen should be showing the question. */
        data class Asking(val deviceName: String) : EnableState

        /** They said no, or nobody answered in time. */
        data class Declined(val deviceName: String) : EnableState

        /** The request never reached them. */
        data class Failed(val reason: SyncFailure) : EnableState
    }

    private val _enableState = MutableStateFlow<EnableState>(EnableState.Idle)
    val enableState: StateFlow<EnableState> = _enableState.asStateFlow()

    private var enableJob: Job? = null

    /**
     * Turns auto-sync on for [device], which means asking the person at that device first.
     *
     * Nothing is written here: [LocalSyncManager.enableAutoSync] only saves a schedule after the
     * far side has answered yes, and its result is what the sheet shows while it waits and after it
     * is refused. [timing] is the schedule the user already picked (or the default when the sheet
     * has never had one), so acceptance starts the timer they were looking at.
     */
    fun enable(device: PairedDevice, timing: LocalAutoSyncTiming) {
        if (_enableState.value is EnableState.Asking) return
        enableJob?.cancel()
        enableJob = viewModelScope.launch {
            _enableState.value = EnableState.Asking(device.name)
            _enableState.value = when (val result = sync.enableAutoSync(device, timing)) {
                AutoSyncEnableResult.Enabled -> EnableState.Idle
                AutoSyncEnableResult.Declined -> EnableState.Declined(device.name)
                is AutoSyncEnableResult.Failed -> EnableState.Failed(result.reason)
            }
        }
    }

    /** Stops waiting for an answer — the sheet was closed, or the user changed their mind. */
    fun cancelEnable() {
        enableJob?.cancel()
        enableJob = null
        _enableState.value = EnableState.Idle
    }

    /** Turns this device's own schedule for [deviceId] off, now. The far side is not asked. */
    fun disable(deviceId: String) {
        viewModelScope.launch { sync.disableAutoSync(deviceId) }
    }

    /** Withdraws this device's acceptance of [deviceId]'s automatic sync. */
    fun revokeConsent(deviceId: String) {
        viewModelScope.launch { sync.revokeAutoSyncConsent(deviceId) }
    }

    /**
     * Saves one device's settings and puts the new schedule in front of WorkManager.
     *
     * Everything else about that device's config is kept — which profiles and sections this device
     * chose is this sheet's business, but the last run is the pass's record, and a sheet that
     * rewrote it with its own default would quietly lose it. The other devices are not touched at
     * all: their rows on the page are not this sheet's business either.
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
