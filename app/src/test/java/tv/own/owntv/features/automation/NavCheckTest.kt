package tv.own.owntv.features.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavCheckTest {
    private var now = 1_000L
    private val nav = AutomationNav { now }
    private var active: Long? = PROFILE
    private val visible = mutableSetOf(SHOW)
    private val check = NavCheck(nav, activeProfile = { active }, showVisible = { pid, id -> pid == PROFILE && id in visible })

    @Test
    fun `a fresh request for the profile on screen is acted on`() = runBlocking {
        assertTrue(check.stillWanted(NavRequest.Section(NavTarget.MOVIES, PROFILE, now), PROFILE))
        assertTrue(check.stillWanted(NavRequest.OpenSeries(SHOW, PROFILE, now), PROFILE))
    }

    @Test
    fun `a request goes unseen once it is stale`() = runBlocking {
        val request = NavRequest.OpenSeries(SHOW, PROFILE, now)
        now += AutomationNav.WAIT_MS
        assertFalse(check.stillWanted(request, PROFILE))
    }

    @Test
    fun `a request asked for another profile is dropped`() = runBlocking {
        assertFalse(check.stillWanted(NavRequest.OpenSeries(SHOW, PROFILE, now), KIDS))
        assertFalse(check.stillWanted(NavRequest.Section(NavTarget.HOME, PROFILE, now), null))
    }

    @Test
    fun `a request is dropped when the API no longer sees its profile`() = runBlocking {
        active = KIDS
        assertFalse(check.stillWanted(NavRequest.Section(NavTarget.SERIES, PROFILE, now), PROFILE))
        active = null
        assertFalse(check.stillWanted(NavRequest.OpenSeries(SHOW, PROFILE, now), PROFILE))
    }

    @Test
    fun `a show hidden since it was asked for is not opened`() = runBlocking {
        val request = NavRequest.OpenSeries(SHOW, PROFILE, now)
        visible.clear()
        assertFalse(check.stillWanted(request, PROFILE))
    }

    private companion object {
        const val PROFILE = 1L
        const val KIDS = 2L
        const val SHOW = 7L
    }
}
