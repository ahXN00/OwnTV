package tv.own.owntv.features.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The short window, opened from Settings on the television, in which a device may trade the
 * on-screen PIN for a lasting key. It closes on the first correct PIN, after [maxAttempts] wrong
 * ones, or when [durationMs] runs out — whichever comes first.
 */
class PairingWindow(
    private val clock: () -> Long = System::currentTimeMillis,
    private val durationMs: Long = 3 * 60_000L,
    private val maxAttempts: Int = 5,
) {
    data class Open(val pin: String, val expiresAt: Long)

    /** What a submitted PIN met: the right PIN in an open window, a wrong one, or no open window. */
    enum class PinCheck { ACCEPTED, WRONG, NOT_OPEN }

    private val _state = MutableStateFlow<Open?>(null)
    val state: StateFlow<Open?> = _state.asStateFlow()
    private var attempts = 0

    @Synchronized
    fun open(pin: String = AutomationAccess.newPin()) {
        attempts = 0
        _state.value = Open(pin, clock() + durationMs)
    }

    @Synchronized
    fun close() {
        _state.value = null
    }

    /** [PinCheck.ACCEPTED] exactly once, for the right PIN inside the window. */
    @Synchronized
    fun tryPin(submitted: String): PinCheck {
        val open = _state.value ?: return PinCheck.NOT_OPEN
        if (clock() >= open.expiresAt) {
            close()
            return PinCheck.NOT_OPEN
        }
        if (AutomationAccess.constantTimeEquals(submitted, open.pin)) {
            close()
            return PinCheck.ACCEPTED
        }
        attempts++
        if (attempts >= maxAttempts) close()
        return PinCheck.WRONG
    }
}
