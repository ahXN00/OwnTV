package tv.own.owntv.features.categorylock

import androidx.compose.runtime.MonotonicFrameClock
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where focus goes when BACK closes the category sheet. */
class SheetBackFocusTest {

    /** Hands out frames one at a time, so a test sees what happens before and after each. */
    private class StepClock : MonotonicFrameClock {
        private val waiting = mutableListOf<CancellableContinuation<Unit>>()

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            suspendCancellableCoroutine { waiting += it }
            return onFrame(0L)
        }

        fun frame() {
            val due = waiting.toList()
            waiting.clear()
            due.forEach { it.resume(Unit) }
        }
    }

    private val clock = StepClock()
    private val scope = CoroutineScope(Dispatchers.Unconfined + clock)
    private var currentTries = 0
    private var listWaysIn = 0
    private var listFocused = false

    @After
    fun tearDown() = scope.cancel()

    private fun back() = focusListAfterSheet(scope, { currentTries++; listFocused }, { listFocused }) { listWaysIn++ }

    @Test
    fun theCurrentItemIsTriedAtOnce() {
        back()
        assertEquals(1, currentTries)
        assertEquals(0, listWaysIn)
    }

    @Test
    fun aListHoldingFocusIsLeftAlone() {
        listFocused = true
        back()
        repeat(3) { clock.frame() }
        assertEquals(0, listWaysIn)
    }

    @Test
    fun aListALockEmptiedTakesItsOwnWayInOnceSettled() {
        back()
        clock.frame()
        assertEquals(0, listWaysIn)
        clock.frame()
        assertEquals(1, listWaysIn)
        clock.frame()
        assertEquals(1, listWaysIn)
    }

    @Test
    fun anItemThatVanishesAfterTakingFocusStillGetsTheWayIn() {
        listFocused = true
        back()
        listFocused = false
        repeat(2) { clock.frame() }
        assertEquals(1, listWaysIn)
    }
}
