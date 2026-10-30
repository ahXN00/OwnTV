package tv.own.owntv.features.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One visit to the first-run "Set up by Automation" page, from [start] to [close] or [finished]. There is
 * one for the app, so the setup routes can ask whether a device paired from the page ([paired]); the
 * page's view model drives it.
 *
 * [start] turns the API on as the Automation switch does and opens the pairing window, unless a device
 * already paired during this visit. [close] (Cancel, Back, "Set up here instead") ends the visit: the
 * window closes, the devices paired during it are forgotten, and the API goes back off when the visit
 * turned it on. [finished] ends it once the paired device has finished setup, and leaves the API on for
 * that device. [away] is the page going off screen (Home, another app, the screensaver): the PIN closes,
 * as it is only open while someone can see it, and a visit no device has paired from yet closes; [start]
 * picks up on the way back.
 *
 * A setup step a paired device takes runs [holding] the visit, and each change to the visit waits for
 * it: a Cancel lands before the step, which then finds the page closed, or after it, never halfway.
 *
 * Whether the visit turned the API on is kept on disk ([offBefore]): written before the API comes on and
 * cleared only after it is off again, so an app that dies mid-visit starts with the API off again
 * ([AutomationSettings.restarted]).
 */
class AutomationSetupSession(
    private val enabled: () -> Boolean,
    private val setEnabled: (Boolean) -> Unit,
    private val openPairing: () -> Unit,
    private val closePairing: () -> Unit,
    private val deviceIds: () -> Set<String>,
    private val unpair: (String) -> Unit,
    private val offBefore: () -> Boolean,
    private val setOffBefore: (Boolean) -> Unit,
    private val scope: CoroutineScope,
) {
    private val _known = MutableStateFlow<Set<String>?>(null)
    private val lock = Mutex()

    /** The devices paired before this visit began; null outside a visit. */
    val known: StateFlow<Set<String>?> = _known.asStateFlow()

    fun start() = change {
        val before = _known.value ?: deviceIds().also { setOffBefore(!enabled()) }
        if (!enabled()) setEnabled(true)
        if (!pairedSince(before)) openPairing()
        _known.value = before
    }

    /** A fresh PIN, as long as nothing has paired yet in this visit. */
    fun newPin() = change {
        val before = _known.value ?: return@change
        if (!pairedSince(before)) openPairing()
    }

    fun away() = change { if (paired()) closePairing() else end() }

    fun close() = change { end() }

    /** Whether a device paired from the page in a visit that is still open: what the setup routes need. */
    fun paired(): Boolean = _known.value?.let(::pairedSince) == true

    /** Runs [step] with the visit held as it is: [start], [close] and the rest wait until it returns. */
    suspend fun <T> holding(step: suspend () -> T): T = lock.withLock { step() }

    /** The paired device finished setup: the visit ends, the API left on for it. Only from a [holding] step. */
    fun finished() {
        closePairing()
        _known.value = null
        setOffBefore(false)
    }

    private fun end() {
        val before = _known.value ?: return
        closePairing()
        (deviceIds() - before).forEach(unpair)
        _known.value = null
        if (offBefore()) setEnabled(false)
        setOffBefore(false)
    }

    private fun pairedSince(before: Set<String>): Boolean = deviceIds().any { it !in before }

    // Undispatched: with the lock free, as it is but during a setup step, the change is made before this returns.
    private fun change(block: () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { lock.withLock { block() } }
    }
}
