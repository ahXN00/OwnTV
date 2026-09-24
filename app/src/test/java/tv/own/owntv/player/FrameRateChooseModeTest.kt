package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.player.FrameRateController.ModeSpec

/** The display-mode choice behind Auto frame rate, including N7's resolution matching. */
class FrameRateChooseModeTest {

    private val uhd60 = ModeSpec(1, 3840, 2160, 60f)
    private val uhd50 = ModeSpec(2, 3840, 2160, 50f)
    private val uhd24 = ModeSpec(3, 3840, 2160, 24f)
    private val fhd60 = ModeSpec(4, 1920, 1080, 60f)
    private val fhd24 = ModeSpec(5, 1920, 1080, 24f)
    private val sd50 = ModeSpec(6, 720, 576, 50f)
    private val all = listOf(uhd60, uhd50, uhd24, fhd60, fhd24, sd50)

    private fun choose(
        fps: Float,
        video: Pair<Int, Int>? = null,
        modes: List<ModeSpec> = all,
        current: ModeSpec = uhd60,
        base: ModeSpec = uhd60,
        seamlessRates: List<Float> = emptyList(),
        seamlessOnly: Boolean = false,
    ) = FrameRateController.chooseMode(modes, current, base.width, base.height, seamlessRates, seamlessOnly, fps, video)

    @Test
    fun `without resolution matching the resolution never changes`() {
        assertEquals(uhd24, choose(24f)?.mode)
        assertEquals(uhd50, choose(25f)?.mode)
    }

    @Test
    fun `resolution matching picks the smallest mode that holds the film`() {
        assertEquals(fhd24, choose(24f, video = 1920 to 1080)?.mode)
        // A scope film is shorter than the panel, not narrower: still 1080p.
        assertEquals(fhd24, choose(24f, video = 1920 to 800)?.mode)
        assertEquals(sd50, choose(25f, video = 720 to 576)?.mode)
    }

    @Test
    fun `no matching rate at the film's resolution falls back to the base one`() {
        assertEquals(uhd50, choose(25f, video = 1920 to 1080)?.mode)
    }

    @Test
    fun `never above where the display started`() {
        assertEquals(fhd24, choose(24f, video = 3840 to 2160, current = fhd60, base = fhd60)?.mode)
        // Already moved to 1080p by the last film: a 4K film goes back up to the base, no higher.
        assertEquals(uhd24, choose(24f, video = 3840 to 2160, current = fhd24, base = uhd60)?.mode)
    }

    @Test
    fun `seamless-only never changes resolution and says what is seamless`() {
        val chosen = choose(24f, video = 1920 to 1080, seamlessRates = listOf(24f), seamlessOnly = true)
        assertEquals(uhd24, chosen?.mode)
        assertTrue(chosen!!.seamless)
        assertNull(choose(25f, seamlessRates = listOf(24f), seamlessOnly = true))
    }

    @Test
    fun `a resolution change is never reported seamless`() {
        val chosen = choose(24f, video = 1920 to 1080, seamlessRates = listOf(24f))
        assertEquals(fhd24, chosen?.mode)
        assertFalse(chosen!!.seamless)
    }
}
