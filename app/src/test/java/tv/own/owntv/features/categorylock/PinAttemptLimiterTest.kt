package tv.own.owntv.features.categorylock

import org.junit.Assert.assertEquals
import org.junit.Test

class PinAttemptLimiterTest {

    private var now = 1_000L
    private val limiter = PinAttemptLimiter(clock = { now })

    private fun wrong() = limiter.attempt { false }
    private fun right() = limiter.attempt { true }

    @Test
    fun theFirstFourWrongEntriesAreJustWrong() {
        repeat(4) { assertEquals(PinAttempt.Wrong, wrong()) }
        assertEquals(0, limiter.waitSeconds())
    }

    @Test
    fun theFifthWrongEntryStartsThirtySeconds() {
        repeat(4) { wrong() }
        assertEquals(PinAttempt.Wait(30), wrong())
        assertEquals(30, limiter.waitSeconds())
    }

    @Test
    fun whileWaitingTheEntryIsNotChecked() {
        repeat(5) { wrong() }
        var checked = false
        now += 10_000
        assertEquals(PinAttempt.Wait(20), limiter.attempt { checked = true; true })
        assertEquals(false, checked)
    }

    @Test
    fun theWaitRoundsUpToWholeSeconds() {
        repeat(5) { wrong() }
        now += 29_001
        assertEquals(1, limiter.waitSeconds())
    }

    @Test
    fun afterTheWaitEntriesAreCheckedAgainWithAFreshCount() {
        repeat(5) { wrong() }
        now += 30_000
        assertEquals(0, limiter.waitSeconds())
        repeat(4) { assertEquals(PinAttempt.Wrong, wrong()) }
        assertEquals(PinAttempt.Accepted, right())
    }

    @Test
    fun aRightEntryClearsTheCount() {
        repeat(4) { wrong() }
        assertEquals(PinAttempt.Accepted, right())
        repeat(4) { assertEquals(PinAttempt.Wrong, wrong()) }
        assertEquals(PinAttempt.Wait(30), wrong())
    }
}
