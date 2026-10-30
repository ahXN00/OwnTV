package tv.own.owntv.features.automation

/** How a navigation from the API went. */
enum class NavResult {
    /** OwnTV is in front, or coming forward, and the shell has the request. */
    SENT,

    /** OwnTV is in the background and Android will not let it come forward on its own. */
    BLOCKED,

    /** A private screen ([SensitiveScreens]) is open on the TV. */
    PRIVATE,

    /** No profile is open on the TV ([AutomationProfile.id]), so there is none the API may act for. */
    NO_PROFILE,
}

/**
 * Hands the shell a navigation from the API ([AutomationNav]). OwnTV comes forward when it is not in
 * front, with [bringForward] showing the notice ([AppLauncher.open], which refuses while a private screen
 * is open), and only then does the request wait for the shell.
 */
class NavGate(
    private val nav: AutomationNav,
    private val bringForward: suspend (notice: String) -> PlayResult,
) {
    suspend fun section(target: NavTarget, profileId: Long, notice: String): NavResult =
        send(notice) { nav.section(target, profileId) }

    suspend fun openSeries(seriesId: Long, profileId: Long, notice: String): NavResult =
        send(notice) { nav.openSeries(seriesId, profileId) }

    private suspend fun send(notice: String, request: () -> Unit): NavResult = when (bringForward(notice)) {
        PlayResult.Started -> NavResult.SENT.also { request() }
        PlayResult.Private -> NavResult.PRIVATE
        PlayResult.NoProfile -> NavResult.NO_PROFILE
        PlayResult.Blocked, PlayResult.NotFound -> NavResult.BLOCKED
    }
}
