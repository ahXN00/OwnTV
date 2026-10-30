package tv.own.owntv.features.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGateTest {
    private val nav = AutomationNav { 5L }
    private var forward: PlayResult = PlayResult.Started
    private val notices = mutableListOf<String>()

    /** Like [AppLauncher.open], the notice shows only when OwnTV did come forward. */
    private val gate = NavGate(nav, bringForward = { notice -> forward.also { if (it == PlayResult.Started) notices += notice } })

    @Test
    fun `a navigation brings OwnTV forward with its notice, then waits for the shell`() = runBlocking {
        assertEquals(NavResult.SENT, gate.section(NavTarget.SERIES, 3, "Remote: opened Series"))
        assertEquals(listOf("Remote: opened Series"), notices)
        assertEquals(NavRequest.Section(NavTarget.SERIES, 3, 5L), nav.pending.value)
    }

    @Test
    fun `opening a series hands the shell its id and profile`() = runBlocking {
        assertEquals(NavResult.SENT, gate.openSeries(7, 3, "Remote: opened Show"))
        assertEquals(NavRequest.OpenSeries(7, 3, 5L), nav.pending.value)
    }

    @Test
    fun `a private screen refuses before anything changes`() = runBlocking {
        forward = PlayResult.Private
        assertEquals(NavResult.PRIVATE, gate.openSeries(7, 3, "Remote: opened Show"))
        assertTrue(notices.isEmpty())
        assertNull(nav.pending.value)
    }

    @Test
    fun `when OwnTV may not come forward no request is left behind`() = runBlocking {
        forward = PlayResult.Blocked
        assertEquals(NavResult.BLOCKED, gate.section(NavTarget.HOME, 3, "Remote: opened Home"))
        assertNull(nav.pending.value)
        assertTrue(notices.isEmpty())
    }
}
