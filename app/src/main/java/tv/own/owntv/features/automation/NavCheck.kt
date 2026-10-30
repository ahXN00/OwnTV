package tv.own.owntv.features.automation

/**
 * The second look the shell takes at an API navigation before acting on it, since it can come up to
 * [AutomationNav.WAIT_MS] after it was asked for and the TV may have changed meanwhile. The request must
 * still be fresh, the shell and the API must both be on the profile it was asked for ([activeProfile]
 * is the API's view, [AutomationProfile.id]), and a show must still be one that profile may see
 * ([showVisible], as [ProfileCatalog.show] decides).
 */
class NavCheck(
    private val nav: AutomationNav,
    private val activeProfile: suspend () -> Long?,
    private val showVisible: suspend (profileId: Long, seriesId: Long) -> Boolean,
) {
    /** True when the shell, showing [shellProfileId], should still act on [request]. */
    suspend fun stillWanted(request: NavRequest, shellProfileId: Long?): Boolean {
        if (!nav.isFresh(request)) return false
        if (request.profileId != shellProfileId || request.profileId != activeProfile()) return false
        return when (request) {
            is NavRequest.Section -> true
            is NavRequest.OpenSeries -> showVisible(request.profileId, request.seriesId)
        }
    }
}
