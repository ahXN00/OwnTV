package tv.own.owntv.features.pip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipPolicyTest {

    private val ready = PipConditions(
        optionOn = true, supported = true, playerShown = true, multiview = false, audioOnly = false, playing = true,
    )

    // --- eligible ---

    @Test
    fun allConditionsMet_offersTheWindow() {
        assertTrue(PipPolicy.eligible(ready))
    }

    @Test
    fun optionOff_neverOffers() {
        assertFalse(PipPolicy.eligible(ready.copy(optionOn = false)))
    }

    @Test
    fun unsupportedDevice_neverOffers() {
        assertFalse(PipPolicy.eligible(ready.copy(supported = false)))
    }

    @Test
    fun noPlayerOnScreen_doesNotOffer() {
        assertFalse(PipPolicy.eligible(ready.copy(playerShown = false)))
    }

    @Test
    fun multiview_doesNotOffer() {
        assertFalse(PipPolicy.eligible(ready.copy(multiview = true)))
    }

    @Test
    fun soundWithoutPicture_doesNotOffer() {
        assertFalse(PipPolicy.eligible(ready.copy(audioOnly = true)))
    }

    @Test
    fun pausedVideo_doesNotOffer() {
        assertFalse(PipPolicy.eligible(ready.copy(playing = false)))
    }

    // --- aspect ---

    @Test
    fun unknownAspect_isSixteenByNine() {
        assertEquals(PipAspect(16, 9), PipPolicy.aspect(null))
        assertEquals(PipAspect(16, 9), PipPolicy.aspect(0f))
        assertEquals(PipAspect(16, 9), PipPolicy.aspect(-1f))
        assertEquals(PipAspect(16, 9), PipPolicy.aspect(Float.NaN))
        assertEquals(PipAspect(16, 9), PipPolicy.aspect(Float.POSITIVE_INFINITY))
    }

    @Test
    fun ordinaryAspect_isKept() {
        assertEquals(PipAspect(1778, 1000), PipPolicy.aspect(16f / 9f))
        assertEquals(PipAspect(1333, 1000), PipPolicy.aspect(4f / 3f))
    }

    @Test
    fun tooWide_isClampedToTheWidestAllowed() {
        assertEquals(PipAspect(239, 100), PipPolicy.aspect(3.5f))
        assertEquals(PipAspect(239, 100), PipPolicy.aspect(1e9f))
    }

    @Test
    fun tooTall_isClampedToTheTallestAllowed() {
        assertEquals(PipAspect(100, 239), PipPolicy.aspect(0.2f))
        assertEquals(PipAspect(100, 239), PipPolicy.aspect(1e-9f))
    }

    @Test
    fun theLimitsThemselves_areAllowed() {
        assertEquals(PipAspect(2390, 1000), PipPolicy.aspect(2.39f))
    }

    // --- sourceRect ---

    @Test
    fun matchingShape_isTheWholePlayer() {
        val screen = PipBounds(0, 0, 1920, 1080)
        assertEquals(screen, PipPolicy.sourceRect(screen, PipAspect(16, 9)))
    }

    @Test
    fun narrowerVideo_isCentredWithBarsAtTheSides() {
        assertEquals(PipBounds(240, 0, 1680, 1080), PipPolicy.sourceRect(PipBounds(0, 0, 1920, 1080), PipAspect(4, 3)))
    }

    @Test
    fun widerVideo_isCentredWithBarsAboveAndBelow() {
        assertEquals(PipBounds(0, 138, 1920, 941), PipPolicy.sourceRect(PipBounds(0, 0, 1920, 1080), PipAspect(239, 100)))
    }

    @Test
    fun aMiniPlayerOffset_isKept() {
        assertEquals(PipBounds(1400, 760, 1880, 1030), PipPolicy.sourceRect(PipBounds(1400, 760, 1880, 1030), PipAspect(16, 9)))
    }

    @Test
    fun aPlayerNotLaidOutYet_hasNoSource() {
        assertNull(PipPolicy.sourceRect(PipBounds(0, 0, 0, 0), PipAspect(16, 9)))
    }
}
