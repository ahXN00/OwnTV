package tv.own.owntv.features.settings.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import tv.own.owntv.core.categorylock.MenuChangeGuard
import tv.own.owntv.core.categorylock.PinDoor
import tv.own.owntv.features.profiles.PinPrompt

/**
 * Runs a long-press change (hide, move, rename, lock a category, remove from History) at once, or —
 * with the Settings lock's long-press switch on — after the Settings PIN. [MenuChangeGateHost] draws
 * the prompt; one right PIN opens every change until the app is left or the profile changes.
 */
class MenuChangeGate(private val guard: MenuChangeGuard) {

    class Pending(val ask: MenuChangeGuard.Ask, val action: () -> Unit)

    var pending by mutableStateOf<Pending?>(null)
        private set

    fun run(action: () -> Unit) {
        val ask = guard.pinToAsk() ?: return action()
        pending = Pending(ask, action)
    }

    fun unlocked() {
        val done = pending ?: return
        pending = null
        if (guard.unlocked(done.ask)) done.action()
    }

    fun dismiss() {
        pending = null
    }
}

/** The Settings PIN prompt for a guarded long-press change. Placed once, over the whole app. */
@Composable
fun MenuChangeGateHost() {
    val gate: MenuChangeGate = koinInject()
    val waiting = gate.pending ?: return
    PinPrompt(pin = waiting.ask.pin, door = PinDoor.SETTINGS, onUnlock = gate::unlocked, onDismiss = gate::dismiss)
}
