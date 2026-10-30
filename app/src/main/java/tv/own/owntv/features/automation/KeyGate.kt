package tv.own.owntv.features.automation

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The API's remote buttons, pressed in order with a person's pause between them. One app-wide gate, so
 * keys from every call take turns: a call runs its keys while no other call's run, and every key,
 * the first of a call included, waits [KEY_GAP_MS] after the API's previous key finished (sent or
 * refused, from any call). That gives a screen the previous key opened time to compose and count
 * itself before the next key is checked. A call still waiting for its turn after [turnWaitMs] is
 * refused as [KeyPress.BUSY] with nothing pressed, so a client that gave up and retried cannot have
 * the first call's keys pressed later, on top of the retry's.
 *
 * Each key is checked on the main thread, where screens open and close, right before [sender] delivers
 * it: OwnTV must be in front ([inFront]) with no private screen open ([SensitiveScreens]). A focused
 * window that is not OwnTV's refuses it. [clock], [pause] and [turnWaitMs] are the time source, the
 * wait and the turn limit, swapped in tests.
 *
 * On Android 12L and older ([InjectedKeys]) the key is injected from an IO thread about a frame after
 * the check on main, so a private screen can still open in between. The API's previous key cannot be
 * what opens it: the [KEY_GAP_MS] wait lets that screen open and count itself before the check. Only
 * one opened in that frame by the person at the TV, or by code that opens a screen asynchronously,
 * can still receive the key.
 */
class KeyGate(
    private val inFront: () -> Boolean,
    private val screens: SensitiveScreens,
    private val sender: KeySender,
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val turnWaitMs: Long = TURN_WAIT_MS,
) {
    private val turn = Mutex()

    /** When the API's last key finished; read and written only while holding [turn]. */
    private var lastKeyAt: Long? = null

    /**
     * The timeout can land after [Mutex.lock] returned, so [mine], not the timeout's result, says
     * whether this call took the turn; the finally gives it back even if the caller is cancelled.
     */
    suspend fun press(keys: List<RemoteKey>): KeyPress {
        var mine = false
        try {
            withTimeoutOrNull(turnWaitMs) {
                turn.lock()
                mine = true
            }
            if (!mine) {
                Log.i(TAG, "keys refused, another call still had the turn after $turnWaitMs ms")
                return KeyPress.BUSY
            }
            return pressAll(keys)
        } finally {
            if (mine) turn.unlock()
        }
    }

    private suspend fun pressAll(keys: List<RemoteKey>): KeyPress {
        for (key in keys) {
            val sent = pressOne(key)
            if (sent != KeyPress.PRESSED) return sent
        }
        Log.d(TAG, "pressed ${keys.size} keys")
        return KeyPress.PRESSED
    }

    private suspend fun pressOne(key: RemoteKey): KeyPress {
        lastKeyAt?.let { last ->
            val wait = KEY_GAP_MS - (clock() - last)
            if (wait > 0) pause(wait)
        }
        return try {
            sender.send(key.keyCode, ::allowed)
        } catch (e: SecurityException) {
            Log.i(TAG, "key not sent, the focused window is not OwnTV's: ${e.message}")
            KeyPress.NOT_IN_FRONT
        } finally {
            lastKeyAt = clock()
        }
    }

    /** Called by [sender] on the main thread, right before it delivers the key. */
    private fun allowed(): KeyPress = when {
        !inFront() -> KeyPress.NOT_IN_FRONT
        screens.anyOpen -> KeyPress.PAUSED
        else -> KeyPress.PRESSED
    }

    private companion object {
        const val TAG = "OwnTVAutomation"
        /** Long enough for Compose focus to settle between presses, like a person on the remote. */
        const val KEY_GAP_MS = 150L
        /** Room for one full call ahead: [RemoteKey.MAX_PER_CALL] keys a [KEY_GAP_MS] apart take about 7.5 s. */
        const val TURN_WAIT_MS = 10_000L
    }
}
