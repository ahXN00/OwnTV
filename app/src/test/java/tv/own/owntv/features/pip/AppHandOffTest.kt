package tv.own.owntv.features.pip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AppHandOffTest {

    private val events = mutableListOf<String>()
    private val handOff = AppHandOff(
        holdWindow = { held -> events += if (held) "hold" else "release" },
        stopPlayback = { events += "stop" },
        restorePlayback = { events += "restore" },
    )

    @Test
    fun launch_holdsTheWindowAndStopsPlaybackBeforeTheOtherScreenOpens() {
        handOff.launch { events += "start" }

        assertEquals(listOf("hold", "stop", "start"), events)
    }

    @Test
    fun launch_returnsWhatTheStartReturned() {
        assertEquals(true, handOff.launch { true })
    }

    @Test
    fun coveredActivity_leavesTheRestoreToItsOwnStart() {
        handOff.launch { }
        handOff.onActivityStopped()
        events.clear()

        handOff.onActivityResumed()

        assertEquals(listOf("release"), events)
    }

    @Test
    fun screenThatNeverStopsTheActivity_restoresPlaybackOnResume() {
        handOff.launch { }
        events.clear()

        handOff.onActivityResumed()
        handOff.onActivityResumed()

        assertEquals(listOf("release", "restore", "release"), events)
    }

    @Test
    fun failedLaunch_givesTheWindowAndPlaybackBackAndRethrows() {
        val failure = IllegalStateException("no activity")

        val thrown = assertThrows(IllegalStateException::class.java) { handOff.launch { throw failure } }

        assertSame(failure, thrown)
        assertEquals(listOf("hold", "stop", "release", "restore"), events)
        events.clear()
        handOff.onActivityResumed()
        assertEquals(listOf("release"), events)
    }

    @Test
    fun resumeWithoutAHandOff_onlyReleasesTheWindow() {
        handOff.onActivityStopped()
        handOff.onActivityResumed()

        assertEquals(listOf("release"), events)
    }
}
