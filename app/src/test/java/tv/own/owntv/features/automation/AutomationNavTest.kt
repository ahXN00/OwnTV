package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.nav.MainSection

class AutomationNavTest {
    private var now = 1_000L
    private val nav = AutomationNav { now }

    @Test
    fun `every target opens its section and is named in lower case`() {
        assertEquals(listOf("home", "live", "movies", "series", "guide", "search", "downloads"), NavTarget.entries.map { it.apiName })
        assertEquals(MainSection.LIVE_TV, NavTarget.LIVE.section)
        assertEquals(MainSection.EPG, NavTarget.GUIDE.section)
        NavTarget.entries.forEach { assertEquals(it, NavTarget.of(it.section)) }
        assertNull(NavTarget.of(MainSection.SETTINGS))
        assertNull(NavTarget.of(MainSection.MORE))
    }

    @Test
    fun `names parse in any letter case and settings is not one`() {
        assertEquals(NavTarget.GUIDE, NavTarget.parse(" Guide "))
        assertEquals(NavTarget.DOWNLOADS, NavTarget.parse("DOWNLOADS"))
        assertNull(NavTarget.parse("settings"))
        assertNull(NavTarget.parse("epg"))
        assertNull(NavTarget.parse(""))
    }

    @Test
    fun `a request waits for the shell until it is stale`() {
        nav.section(NavTarget.MOVIES, 3)
        val request = nav.pending.value!!
        assertEquals(NavRequest.Section(NavTarget.MOVIES, 3, 1_000L), request)
        now += AutomationNav.WAIT_MS - 1
        assertTrue(nav.isFresh(request))
        now += 1
        assertFalse(nav.isFresh(request))
    }

    @Test
    fun `consuming an older request keeps the newer one`() {
        nav.section(NavTarget.HOME, 3)
        val first = nav.pending.value!!
        now += 1
        nav.openSeries(7, 3)
        nav.consumed(first)
        val second = nav.pending.value
        assertEquals(NavRequest.OpenSeries(7, 3, 1_001L), second)
        nav.consumed(second!!)
        assertNull(nav.pending.value)
    }
}
