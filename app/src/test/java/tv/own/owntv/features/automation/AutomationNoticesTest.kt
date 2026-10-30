package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationNoticesTest {
    private var now = 1_000L
    private val notices = AutomationNotices { now }

    @Test
    fun `the latest notice wins and stays fresh only for a moment`() {
        assertNull(notices.latest.value)
        notices.post("Remote: playing Nick")
        now += 10
        notices.post("Remote: opened Movies")
        val notice = notices.latest.value!!
        assertEquals("Remote: opened Movies", notice.text)
        assertTrue(notices.isFresh(notice))
        now += AutomationNotices.SHOW_MS
        assertFalse(notices.isFresh(notice))
    }
}
