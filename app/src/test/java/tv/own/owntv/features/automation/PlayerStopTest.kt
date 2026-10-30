package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStopTest {
    private val calls = mutableListOf<String>()
    private var shellUp = true
    private var playing = true

    private val stop = PlayerStop(
        shellStop = { if (shellUp) ({ calls += "shell"; playing = false }) else null },
        stillPlaying = { if (playing) ({ calls += "engine"; playing = false }) else null },
    )

    @Test
    fun `with the main screen up, its own stop closes full screen and nothing is left to stop`() {
        stop.stop()
        assertEquals(listOf("shell"), calls)
    }

    @Test
    fun `whatever still plays after the main screen's stop is stopped on its engine`() {
        val leaves = PlayerStop(shellStop = { { calls += "shell" } }, stillPlaying = { { calls += "engine" } })
        leaves.stop()
        assertEquals(listOf("shell", "engine"), calls)
    }

    @Test
    fun `with no main screen the playing engine is stopped`() {
        shellUp = false
        stop.stop()
        assertEquals(listOf("engine"), calls)
    }

    @Test
    fun `with nothing playing and no main screen nothing is stopped`() {
        shellUp = false
        playing = false
        stop.stop()
        assertEquals(emptyList<String>(), calls)
    }
}
