package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.network.HostFailover

class HostFailoverRoundTest {

    private val a = "http://a.example:8080"
    private val b = "http://b.example:8080"
    private val c = "http://c.example:8080"
    private val pool = listOf(a, b, c)

    @Test
    fun `one round tries each host then stops`() {
        val failover = HostFailover(pool, a)

        val toB = failover.onHostFailure(a) as HostFailover.Step.Try
        assertEquals(b, toB.host)
        assertEquals(2, toB.position)
        assertEquals(3, toB.total)

        val toC = failover.onHostFailure(b) as HostFailover.Step.Try
        assertEquals(c, toC.host)
        assertEquals(3, toC.position)
        assertEquals(3, toC.total)

        assertEquals(HostFailover.Step.Exhausted(3), failover.onHostFailure(c))
        assertEquals(3, failover.attempts)
        assertTrue(failover.exhausted)
        assertEquals(HostFailover.Step.Exhausted(3), failover.onHostFailure(c))
        assertEquals(3, failover.attempts)
    }

    @Test
    fun `stale failure for a non-current host does not advance`() {
        val failover = HostFailover(pool, a)
        val stale = failover.onHostFailure(b) as HostFailover.Step.Try
        assertEquals(a, stale.host)
        assertEquals(0L, stale.delayMs.toLong())
        assertEquals(1, stale.position)
        assertEquals(3, stale.total)
        assertEquals(1, failover.attempts)
        assertFalse(failover.exhausted)
        assertEquals(a, failover.host)
    }

    @Test
    fun `reset starts a fresh round`() {
        val failover = HostFailover(pool, a)
        failover.onHostFailure(a)
        failover.onHostFailure(b)
        failover.onHostFailure(c)

        failover.reset(a)
        assertEquals(1, failover.attempts)
        assertFalse(failover.exhausted)
        assertEquals(a, failover.host)

        val toB = failover.onHostFailure(a) as HostFailover.Step.Try
        assertEquals(b, toB.host)
        assertEquals(2, toB.position)
        assertEquals(3, toB.total)
    }

    @Test
    fun `all servers failed notice is the round size`() {
        val notices = ServerNotices()
        notices.exhausted(3)
        assertEquals(3, notices.exhausted.value)
    }
}
