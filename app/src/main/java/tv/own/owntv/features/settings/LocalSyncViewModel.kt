package tv.own.owntv.features.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.core.sync.local.DiscoveredDevice
import tv.own.owntv.core.sync.local.LocalSyncManager
import tv.own.owntv.core.sync.local.PairedDevice
import tv.own.owntv.core.sync.local.SyncDirection
import tv.own.owntv.core.sync.local.SyncFailure

/**
 * Local sync from the television's side — the same feature the phone has, driven with a D-pad.
 *
 * The television is usually the one being connected TO: it shows a PIN and a QR code and the phone's
 * camera reads it. But it can also go the other way, because a sync only one device can start is not
 * much of a sync — so the phone can be found on the network here too, and the PIN typed with the
 * remote.
 *
 * Everything below is core's ([LocalSyncManager]); this only sequences the screen.
 */
class LocalSyncViewModel(
    private val sync: LocalSyncManager,
) : ViewModel() {

    val paired: StateFlow<List<PairedDevice>> = sync.pairedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val hosting: StateFlow<CompanionServerState> = sync.hostState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CompanionServerState.Idle)

    val deviceName: String get() = sync.deviceName

    var found by mutableStateOf<List<DiscoveredDevice>>(emptyList())
        private set

    var step by mutableStateOf<Step?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<SyncFailure?>(null)
        private set

    sealed interface Step {
        data object FindDevice : Step
        data class EnterPin(val address: String, val port: Int) : Step
        data class ChooseDirection(val device: PairedDevice) : Step
        data class ChooseSections(val device: PairedDevice, val direction: SyncDirection) : Step
        data class Confirm(
            val device: PairedDevice?,
            val direction: SyncDirection,
            val file: File,
            val preview: BackupManager.Preview,
            val sections: Set<BackupManager.Section>,
        ) : Step
        data class Result(val received: BackupManager.ImportSummary?, val sent: Boolean) : Step
    }

    private var discovery: Job? = null
    private var incomingWatcher: Job? = null

    /** Opens the listener the moment the screen appears: on a television, waiting is the usual role. */
    fun startHosting() {
        busy = true
        viewModelScope.launch {
            sync.startHosting().onFailure { error = SyncFailure.Unknown }
            busy = false
        }
        // Guarded: hosting can be switched off and on again, and a second collector would preview the
        // same arriving container twice.
        if (incomingWatcher?.isActive != true) incomingWatcher = viewModelScope.launch {
            sync.incoming.collect { file ->
                val preview = sync.preview(file).getOrNull() ?: return@collect
                step = Step.Confirm(
                    device = null,
                    direction = SyncDirection.RECEIVE,
                    file = file,
                    preview = preview,
                    sections = BackupManager.Section.entries.toSet(),
                )
            }
        }
    }

    fun stopHosting() = sync.stopHosting()

    fun beginPairing() {
        step = Step.FindDevice
        found = emptyList()
        discovery?.cancel()
        discovery = viewModelScope.launch {
            sync.discover().collect { device ->
                if (found.none { it.address == device.address }) found = found + device
            }
        }
    }

    fun chooseAddress(address: String, port: Int) {
        discovery?.cancel()
        step = Step.EnterPin(address, port)
    }

    fun submitPin(pin: String) {
        val current = step as? Step.EnterPin ?: return
        busy = true
        viewModelScope.launch {
            sync.pair(current.address, current.port, pin)
                .onSuccess { step = Step.ChooseDirection(it) }
                .onFailure { error = SyncFailure.NotAuthorized }
            busy = false
        }
    }

    fun unpair(device: PairedDevice) {
        viewModelScope.launch { sync.unpair(device.id) }
    }

    fun chooseDevice(device: PairedDevice) {
        step = Step.ChooseDirection(device)
    }

    fun chooseDirection(direction: SyncDirection) {
        val device = (step as? Step.ChooseDirection)?.device ?: return
        step = Step.ChooseSections(device, direction)
    }

    fun start(sections: Set<BackupManager.Section>) {
        val current = step as? Step.ChooseSections ?: return
        busy = true
        viewModelScope.launch {
            when (current.direction) {
                SyncDirection.SEND -> sync.send(current.device, sections)
                    .onSuccess { step = Step.Result(received = null, sent = true) }
                    .onFailure { error = reason() }
                SyncDirection.RECEIVE, SyncDirection.MERGE -> sync.fetch(current.device, sections)
                    .onSuccess { (file, preview) ->
                        step = Step.Confirm(current.device, current.direction, file, preview, sections)
                    }
                    .onFailure { error = reason() }
            }
            busy = false
        }
    }

    fun confirm() {
        val current = step as? Step.Confirm ?: return
        busy = true
        viewModelScope.launch {
            sync.apply(current.file, current.sections)
                .onSuccess { summary ->
                    val sent = current.device != null && current.direction == SyncDirection.MERGE &&
                        sync.send(current.device, current.sections).isSuccess
                    step = Step.Result(received = summary, sent = sent)
                }
                .onFailure { error = reason() }
            busy = false
        }
    }

    fun cancel() {
        (step as? Step.Confirm)?.file?.delete()
        discovery?.cancel()
        step = null
        sync.clearProgress()
    }

    fun dismissError() {
        error = null
    }

    override fun onCleared() {
        discovery?.cancel()
        sync.stopHosting()
        super.onCleared()
    }

    private fun reason(): SyncFailure =
        (sync.progress.value as? tv.own.owntv.core.sync.local.SyncProgress.Failed)?.reason ?: SyncFailure.Unknown

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
