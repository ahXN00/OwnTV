package tv.own.owntv.features.automation

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.core.nav.MainSection

/** A section the API may open, by the name `POST ui/navigate` takes. Settings is not one of them. */
enum class NavTarget(val section: MainSection) {
    HOME(MainSection.HOME),
    LIVE(MainSection.LIVE_TV),
    MOVIES(MainSection.MOVIES),
    SERIES(MainSection.SERIES),
    GUIDE(MainSection.EPG),
    SEARCH(MainSection.SEARCH),
    DOWNLOADS(MainSection.DOWNLOADS),
    ;

    /** The name the API uses, as `ui/navigate` takes it and `GET ui` reports it. */
    val apiName: String get() = name.lowercase()

    companion object {
        /** The target called [name] in any letter case, or null when there is none (Settings included). */
        fun parse(name: String): NavTarget? = entries.firstOrNull { it.apiName == name.trim().lowercase() }

        /** The target that opens [section], or null for one the API does not open. */
        fun of(section: MainSection): NavTarget? = entries.firstOrNull { it.section == section }
    }
}

/**
 * What the API asks the shell to show. [profileId] is the profile it was asked for, so another profile's
 * shell never acts on it; [atMs] is elapsed realtime, when it was asked.
 */
sealed interface NavRequest {
    val profileId: Long
    val atMs: Long

    data class Section(val target: NavTarget, override val profileId: Long, override val atMs: Long) : NavRequest
    data class OpenSeries(val seriesId: Long, override val profileId: Long, override val atMs: Long) : NavRequest
}

/**
 * The navigation the API last asked for, until the shell has acted on it. A state, not an event: a
 * request sent while OwnTV is still coming forward waits for the shell to compose. One the shell never
 * got to in time ([isFresh]) is dropped, so it cannot fire long after, when someone next opens OwnTV.
 */
class AutomationNav(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private val _pending = MutableStateFlow<NavRequest?>(null)
    val pending: StateFlow<NavRequest?> = _pending.asStateFlow()

    fun section(target: NavTarget, profileId: Long) {
        _pending.value = NavRequest.Section(target, profileId, clock())
    }

    fun openSeries(seriesId: Long, profileId: Long) {
        _pending.value = NavRequest.OpenSeries(seriesId, profileId, clock())
    }

    /** True while [request] is younger than [WAIT_MS]. */
    fun isFresh(request: NavRequest): Boolean = clock() - request.atMs < WAIT_MS

    /** The shell is done with [request]; one sent after it stays pending. */
    fun consumed(request: NavRequest) {
        _pending.compareAndSet(request, null)
    }

    companion object {
        /** How long a request waits for the shell: enough for OwnTV to start and load its profile. */
        const val WAIT_MS = 15_000L
    }
}
