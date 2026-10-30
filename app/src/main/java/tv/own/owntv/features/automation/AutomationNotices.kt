package tv.own.owntv.features.automation

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One line the API shows on the TV for a moment, such as "Remote: playing Nick". [atMs] is elapsed realtime. */
data class AutomationNotice(val text: String, val atMs: Long)

/**
 * The latest notice from the API. A state, not an event: a notice posted while OwnTV is still coming
 * forward is shown once the shell composes, as long as it is fresh ([isFresh]).
 */
class AutomationNotices(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private val _latest = MutableStateFlow<AutomationNotice?>(null)
    val latest: StateFlow<AutomationNotice?> = _latest.asStateFlow()

    fun post(text: String) {
        _latest.value = AutomationNotice(text, clock())
    }

    /** True while [notice] is younger than [SHOW_MS]. */
    fun isFresh(notice: AutomationNotice): Boolean = clock() - notice.atMs < SHOW_MS

    companion object {
        const val SHOW_MS = 4_000L
    }
}
