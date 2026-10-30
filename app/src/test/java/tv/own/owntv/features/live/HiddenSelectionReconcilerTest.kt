package tv.own.owntv.features.live

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.live.LiveKey

/**
 * #322 selection move when All or Catch-up is hidden. No view model — the flows are the inputs.
 */
class HiddenSelectionReconcilerTest {

    private val folder = LiveRailItem(LiveKey.Folder(1), title = "News")

    private suspend fun await(what: String, condition: () -> Boolean) {
        try {
            withTimeout(2_000) {
                while (!condition()) delay(10)
            }
        } catch (_: TimeoutCancellationException) {
            throw AssertionError("timed out waiting for $what")
        }
    }

    private fun rail(showAll: Boolean, showCatchup: Boolean = true, hasCatchup: Boolean = false, withFolder: Boolean = true) =
        LiveViewModel.railWithCatchup(hasCatchup, showAll, showCatchup).let { if (withFolder) it + folder else it }

    @Test
    fun `hiding All moves to the first folder and unhiding does not jump back`() = runBlocking {
        val rail = MutableStateFlow(rail(showAll = false))
        val showAll = MutableStateFlow(false)
        val showCatchup = MutableStateFlow(true)
        val selected = MutableStateFlow<LiveKey>(LiveKey.All)
        val job = launch {
            reconcileHiddenSelection(rail, LiveViewModel.defaultRail, showAll, showCatchup, selected) { false }
        }
        await("folder") { selected.value == LiveKey.Folder(1) }
        showAll.value = true
        rail.value = rail(showAll = true)
        delay(40)
        assertEquals(LiveKey.Folder(1), selected.value)
        job.cancel()
    }

    @Test
    fun `hiding All with no folder falls back to Favorites`() = runBlocking {
        val rail = MutableStateFlow(rail(showAll = false, withFolder = false))
        val selected = MutableStateFlow<LiveKey>(LiveKey.All)
        val job = launch {
            reconcileHiddenSelection(rail, LiveViewModel.defaultRail, MutableStateFlow(false), MutableStateFlow(true), selected) { false }
        }
        await("favorites") { selected.value == LiveKey.Favorites }
        job.cancel()
    }

    @Test
    fun `hiding Catch-up moves to All when All is shown`() = runBlocking {
        val rail = MutableStateFlow(rail(showAll = true, showCatchup = true, hasCatchup = true))
        val showCatchup = MutableStateFlow(true)
        val selected = MutableStateFlow<LiveKey>(LiveKey.Catchup)
        val job = launch {
            reconcileHiddenSelection(rail, LiveViewModel.defaultRail, MutableStateFlow(true), showCatchup, selected) { false }
        }
        delay(40)
        assertEquals(LiveKey.Catchup, selected.value)
        showCatchup.value = false
        await("all") { selected.value == LiveKey.All }
        job.cancel()
    }

    @Test
    fun `the placeholder rail is ignored until a real rail arrives`() = runBlocking {
        val placeholder = LiveViewModel.defaultRail
        val rail = MutableStateFlow(placeholder)
        val selected = MutableStateFlow<LiveKey>(LiveKey.All)
        val job = launch {
            reconcileHiddenSelection(rail, placeholder, MutableStateFlow(false), MutableStateFlow(true), selected) { false }
        }
        delay(40)
        assertEquals(LiveKey.All, selected.value)
        rail.value = rail(showAll = false)
        await("folder") { selected.value == LiveKey.Folder(1) }
        job.cancel()
    }

    @Test
    fun `a lock leaves a hidden All selected`() = runBlocking {
        val selected = MutableStateFlow<LiveKey>(LiveKey.All)
        val job = launch {
            reconcileHiddenSelection(
                MutableStateFlow(rail(showAll = false)),
                LiveViewModel.defaultRail,
                MutableStateFlow(false),
                MutableStateFlow(true),
                selected,
            ) { true }
        }
        delay(40)
        assertEquals(LiveKey.All, selected.value)
        job.cancel()
    }

    @Test
    fun `a selected folder is never changed`() = runBlocking {
        val selected = MutableStateFlow<LiveKey>(LiveKey.Folder(1))
        val showAll = MutableStateFlow(false)
        val job = launch {
            reconcileHiddenSelection(
                MutableStateFlow(rail(showAll = false)),
                LiveViewModel.defaultRail,
                showAll,
                MutableStateFlow(false),
                selected,
            ) { false }
        }
        delay(40)
        showAll.value = true
        delay(40)
        assertEquals(LiveKey.Folder(1), selected.value)
        job.cancel()
    }
}
