package tv.own.owntv.features.automation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** More › Automation: the on/off switch, the pairing window and the paired devices. */
class AutomationViewModel(
    private val store: AutomationStore,
    private val controller: AutomationController,
    private val pairing: PairingWindow,
    server: AutomationServer,
) : ViewModel() {
    val settings: StateFlow<AutomationSettings> = store.settings
    val pairingState: StateFlow<PairingWindow.Open?> = pairing.state
    val serverState: StateFlow<AutomationServer.State> = server.state

    /** The device whose Remove was pressed once; a second OK removes it. */
    private val _confirmRemove = MutableStateFlow<String?>(null)
    val confirmRemove: StateFlow<String?> = _confirmRemove.asStateFlow()

    fun setEnabled(on: Boolean) = controller.setEnabled(on)
    fun setAllowTailscale(on: Boolean) = store.setAllowTailscale(on)

    fun togglePairing() {
        if (pairing.state.value == null) pairing.open() else pairing.close()
    }

    fun closePairing() = pairing.close()

    fun remove(id: String) {
        if (_confirmRemove.value != id) {
            _confirmRemove.value = id
            return
        }
        store.removeDevice(id)
        _confirmRemove.value = null
    }

    fun removeAll() = store.removeAllDevices()

    // Pairing is open only while someone is looking at the PIN.
    override fun onCleared() = pairing.close()
}
