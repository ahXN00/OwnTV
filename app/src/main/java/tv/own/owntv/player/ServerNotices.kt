package tv.own.owntv.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What host failover tells the user (ui-design Q5): one toast per server that took over, and "All N
 * servers failed" on the player's error screen when a round ran out. Live, films and episodes all post
 * here; the shell draws the toast once and the player HUD reads [exhausted]. Failed hops stay silent.
 */
class ServerNotices {
    data class Switched(val position: Int, val total: Int, val server: String)

    private val _switched = MutableSharedFlow<Switched>(extraBufferCapacity = 4)
    val switched: SharedFlow<Switched> = _switched.asSharedFlow()

    private val _busy = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val busy: SharedFlow<Unit> = _busy.asSharedFlow()

    /** A server round paused because the device is offline. */
    private val _noNetwork = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val noNetwork: SharedFlow<Unit> = _noNetwork.asSharedFlow()

    /** A server failure was not switched because local timeshift is on. */
    private val _timeshiftOff = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val timeshiftOff: SharedFlow<Unit> = _timeshiftOff.asSharedFlow()

    private val _exhausted = MutableStateFlow<Int?>(null)

    /** Number of servers tried when the last round failed everywhere; null once anything plays again. */
    val exhausted: StateFlow<Int?> = _exhausted.asStateFlow()

    fun switched(position: Int, total: Int, server: String) {
        _exhausted.value = null
        _switched.tryEmit(Switched(position, total, server))
    }

    /** Compatibility-mode toggle ignored because a server switch or 458 wait is in progress. */
    fun switchingBusy() {
        _busy.tryEmit(Unit)
    }

    fun noNetwork() {
        _noNetwork.tryEmit(Unit)
    }

    /** A server failure was not switched because local timeshift is on. */
    fun timeshiftOff() {
        _timeshiftOff.tryEmit(Unit)
    }

    fun exhausted(total: Int) {
        _exhausted.value = total
    }

    fun clear() {
        _exhausted.value = null
    }
}
