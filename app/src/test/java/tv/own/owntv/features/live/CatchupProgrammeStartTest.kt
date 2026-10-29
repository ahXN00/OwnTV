package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Start of …" in "Go back to…": when the row is offered, and how far back it goes. */
class CatchupProgrammeStartTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun `a programme on air that began inside the archive is offered`() {
        val p = CatchupProgrammeStart.of("  Evening News ", now - 25 * 60_000L, now + hour, now, windowSec = 7 * 86_400)
        assertEquals(CatchupProgrammeStart("Evening News", now - 25 * 60_000L), p)
        assertEquals(25 * 60, p!!.offsetSec(now))
    }

    @Test
    fun `the offset is taken when pressed, so reading the list does not skip the first seconds`() {
        val p = CatchupProgrammeStart.of("News", now - 25 * 60_000L, now + hour, now, 86_400)!!
        assertEquals(25 * 60 + 10, p.offsetSec(now + 10_000L))
    }

    @Test
    fun `not offered when it just began or has not begun`() {
        assertNull(CatchupProgrammeStart.of("x", now - 10_000L, now + hour, now, 86_400))
        assertNull(CatchupProgrammeStart.of("x", now + 60_000L, now + hour, now, 86_400))
        assertNull(CatchupProgrammeStart.of("x", 0L, now + hour, now, 86_400))
    }

    @Test
    fun `not offered when it began before the archive or the channel has none`() {
        assertNull(CatchupProgrammeStart.of("x", now - 3 * hour, now + hour, now, windowSec = 2 * 3_600))
        assertNull(CatchupProgrammeStart.of("x", now - 60_000L, now + hour, now, windowSec = 0))
    }

    @Test
    fun `not offered when the programme is already over`() {
        assertNull(CatchupProgrammeStart.of("x", now - hour, now - 60_000L, now, 86_400))
        assertNull(CatchupProgrammeStart.of("x", now - hour, now, now, 86_400))
    }

    @Test
    fun `an unknown end does not hide the row`() {
        assertEquals(now - hour, CatchupProgrammeStart.of("x", now - hour, 0L, now, 86_400)!!.startMs)
    }

    @Test
    fun `a missing title is kept empty`() {
        assertEquals("", CatchupProgrammeStart.of(null, now - 600_000L, now + hour, now, 86_400)!!.title)
    }
}
