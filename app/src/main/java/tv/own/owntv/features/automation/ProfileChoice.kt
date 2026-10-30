package tv.own.owntv.features.automation

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.core.database.entity.ProfileEntity

/**
 * The profile the API opened (`POST profiles/{id}/use`, `POST setup/finish`), for "Who's watching?" to
 * pick. Making a profile active brings the gate up whenever there is more than one, and only the gate
 * lets a profile in, so the API leaves its choice here and the gate takes it ([ApiProfileChoice]). Like
 * [AutomationNav] it is a state the gate reads when it composes, and one left untaken for [WAIT_MS] is
 * dropped, so it cannot pick for someone who opens the gate long after.
 */
class ProfileChoice(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    /** [profileId] chosen at [atMs], elapsed realtime. */
    data class Request(val profileId: Long, val atMs: Long)

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    fun choose(profileId: Long) {
        _pending.value = Request(profileId, clock())
    }

    /**
     * The profile [request] chose, when it is one of [profiles] and has no PIN; taking it clears the
     * request, so it acts once. Null while the list does not hold it yet, and for a stale request, which
     * is dropped.
     */
    fun take(request: Request, profiles: List<ProfileEntity>): ProfileEntity? {
        if (clock() - request.atMs >= WAIT_MS) {
            _pending.compareAndSet(request, null)
            return null
        }
        val profile = profiles.firstOrNull { it.id == request.profileId && it.pinHash == null } ?: return null
        return profile.takeIf { _pending.compareAndSet(request, null) }
    }

    companion object {
        /** As long as a navigation waits ([AutomationNav.WAIT_MS]): enough for the gate to come up. */
        const val WAIT_MS = AutomationNav.WAIT_MS
    }
}
