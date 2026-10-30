package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureLimiterTest {
    private var now = 0L
    private val limiter = FailureLimiter(maxFailures = 2, windowMs = 1_000, maxAddresses = 3, clock = { now })

    @Test
    fun `an address is blocked until its failures age out`() {
        limiter.recordFailure("a")
        assertFalse(limiter.isBlocked("a"))
        limiter.recordFailure("a")
        assertTrue(limiter.isBlocked("a"))
        now += 1_001
        assertFalse(limiter.isBlocked("a"))
    }

    @Test
    fun `checking an address does not add it`() {
        repeat(50) { assertFalse(limiter.isBlocked("probe$it")) }
        assertEquals(0, limiter.tracked)
    }

    @Test
    fun `expired entries are dropped on the next use`() {
        limiter.recordFailure("a")
        limiter.recordFailure("b")
        now += 1_001
        limiter.isBlocked("c")
        assertEquals(0, limiter.tracked)
    }

    @Test
    fun `a full table drops the address whose last failure is oldest`() {
        repeat(2) { limiter.recordFailure("a") }
        repeat(2) { limiter.recordFailure("b") }
        repeat(2) { limiter.recordFailure("c") }
        limiter.recordFailure("a")
        limiter.recordFailure("d")
        assertEquals(3, limiter.tracked)
        assertFalse(limiter.isBlocked("b"))
        assertTrue(limiter.isBlocked("a"))
        assertTrue(limiter.isBlocked("c"))
    }

    @Test
    fun `clearing forgets an address`() {
        repeat(2) { limiter.recordFailure("a") }
        limiter.clear("a")
        assertFalse(limiter.isBlocked("a"))
        assertEquals(0, limiter.tracked)
    }
}
