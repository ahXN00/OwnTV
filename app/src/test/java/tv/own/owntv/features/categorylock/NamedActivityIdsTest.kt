package tv.own.owntv.features.categorylock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which running download or recording titles the status pill may name. */
class NamedActivityIdsTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /** Stands in for the lock state: a new value is a relock, a PIN entry or a lock edit. */
    private val locks = MutableStateFlow(0)
    private val running = MutableStateFlow(setOf(1L, 2L))

    /** The ids whose category is locked and sealed; 2 is in a locked category. */
    private var sealed = setOf(2L)
    private val asked = mutableListOf<Long>()
    private val hides: suspend (Long) -> Boolean = { id -> asked += id; id in sealed }

    @After
    fun tearDown() = scope.cancel()

    private fun <T> Flow<T>.values(): List<T> = mutableListOf<T>().also { seen -> onEach { seen += it }.launchIn(scope) }

    @Test
    fun aSealedTitleIsNotNamedAndAnOpenOneIs() {
        val named = namedIds(running, locks, hides).values()
        assertEquals(listOf(emptySet(), setOf(1L)), named)
    }

    @Test
    fun thePinOpeningTheCategoryNamesItsTitle() {
        val named = namedIds(running, locks, hides).values()
        sealed = emptySet()
        locks.value = 1
        assertEquals(setOf(1L, 2L), named.last())
    }

    @Test
    fun aRelockHidesTheTitleAgainAndNamesNothingWhileItLooks() {
        sealed = emptySet()
        val named = namedIds(running, locks, hides).values()
        assertEquals(setOf(1L, 2L), named.last())
        sealed = setOf(1L, 2L)
        locks.value = 1
        assertEquals(listOf(emptySet(), setOf(1L, 2L), emptySet(), emptySet()), named)
    }

    @Test
    fun aNewRecordingIsLookedAtBeforeItIsNamed() {
        val named = namedIds(running, locks, hides).values()
        asked.clear()
        running.value = setOf(1L, 2L, 3L)
        assertEquals(listOf(1L, 2L, 3L), asked)
        assertEquals(setOf(1L, 3L), named.last())
    }

    @Test
    fun nothingRunningNamesNothingAndAsksNothing() {
        running.value = emptySet()
        val named = namedIds(running, locks, hides).values()
        assertEquals(emptyList<Long>(), asked)
        assertEquals(emptySet<Long>(), named.last())
    }
}
