package tv.own.owntv.features.automation

import android.content.Context

/** Turns the automation API on and off, and brings it back when the app or the box starts. */
class AutomationController(
    private val context: Context,
    private val store: AutomationStore,
    private val pairing: PairingWindow,
) {
    fun setEnabled(on: Boolean) {
        store.setEnabled(on)
        if (on) {
            AutomationService.start(context)
        } else {
            pairing.close()
            AutomationService.stop(context)
        }
    }

    fun startIfEnabled() {
        if (store.settings.value.enabled) AutomationService.start(context)
    }
}
