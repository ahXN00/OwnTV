package tv.own.owntv.features.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The first-run "Set up by Automation" page: it drives the [AutomationSetupSession] and shows its state. */
class AutomationSetupViewModel(
    private val session: AutomationSetupSession,
    store: AutomationStore,
    private val pairing: PairingWindow,
    server: AutomationServer,
) : ViewModel() {
    val status: StateFlow<AutomationSetupStatus> =
        combine(session.known, store.settings, pairing.state, server.state) { known, settings, open, state ->
            AutomationSetupStatus.of(known, settings.devices, open, state)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutomationSetupStatus.Starting)

    fun start() = session.start()
    fun newPin() = session.newPin()
    fun expire() = pairing.close()
    fun away() = session.away()
    fun close() = session.close()

    // The session is the app's; a visit still open when this view model goes ends with it.
    override fun onCleared() = session.close()
}
