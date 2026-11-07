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
import tv.own.owntv.core.sync.local.AutoSyncAgreement
import tv.own.owntv.core.sync.local.AutoSyncEnableResult
import tv.own.owntv.core.sync.local.AutoSyncEvent
import tv.own.owntv.core.sync.local.LocalAutoSyncRunner
import tv.own.owntv.core.sync.local.LocalAutoSyncServer
import tv.own.owntv.core.sync.local.LocalAutoSyncStore
import tv.own.owntv.core.sync.local.LocalAutoSyncTiming
import tv.own.owntv.core.sync.local.LocalSyncManager
import tv.own.owntv.core.sync.local.PairedDevice
import tv.own.owntv.core.sync.local.SyncFailure

/** Local sync's automatic side: each paired device's agreement, its status, and the listener switch. */
class LocalAutoSyncViewModel(
    private val store: LocalAutoSyncStore,
    private val sync: LocalSyncManager,
    runner: LocalAutoSyncRunner,
    server: LocalAutoSyncServer,
    profileDao: ProfileDao,
    settings: tv.own.owntv.core.settings.SettingsRepository,
) : ViewModel() {

    /** The profile in use: the one an auto-sync set up now pairs with the other device's. */
    val activeProfileId: StateFlow<Long> = settings.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), -1L)


    val agreements: StateFlow<Map<String, AutoSyncAgreement>> = store.agreements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** Devices whose own side turned it off, by when. */
    val turnedOff: StateFlow<Map<String, Long>> = store.turnedOffByThem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** The last pass per device, this session. */
    val events: StateFlow<Map<String, AutoSyncEvent>> = runner.events

    val profiles: StateFlow<List<ProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val listenAlways: StateFlow<Boolean> = store.listenAlways
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Whether the listener is up right now. */
    val listening: StateFlow<Boolean> = server.listening

    sealed interface EnableState {
        data object Idle : EnableState
        data class Asking(val deviceName: String) : EnableState
        data class Declined(val deviceName: String) : EnableState
        data class Failed(val reason: SyncFailure) : EnableState
    }

    private val _enableState = MutableStateFlow<EnableState>(EnableState.Idle)
    val enableState: StateFlow<EnableState> = _enableState.asStateFlow()
    private var enableJob: Job? = null

    /** Asks [device] (its Local sync page open) to agree; this side's terms are saved only on yes. */
    fun enable(device: PairedDevice, sections: Set<BackupManager.Section>, timing: LocalAutoSyncTiming) {
        if (_enableState.value is EnableState.Asking) return
        enableJob?.cancel()
        enableJob = viewModelScope.launch {
            _enableState.value = EnableState.Asking(device.name)
            _enableState.value = when (val result = sync.enableAutoSync(device, sections, timing)) {
                AutoSyncEnableResult.Enabled -> EnableState.Idle
                AutoSyncEnableResult.Declined -> EnableState.Declined(device.name)
                is AutoSyncEnableResult.Failed -> EnableState.Failed(result.reason)
            }
        }
    }

    fun cancelEnable() {
        enableJob?.cancel()
        enableJob = null
        _enableState.value = EnableState.Idle
    }

    fun save(deviceId: String, sections: Set<BackupManager.Section>, timing: LocalAutoSyncTiming) {
        viewModelScope.launch { sync.updateAutoSync(deviceId, sections, timing) }
    }

    fun disable(deviceId: String) {
        viewModelScope.launch { sync.disableAutoSync(deviceId) }
    }

    fun setListenAlways(on: Boolean) {
        viewModelScope.launch { store.setListenAlways(on) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
