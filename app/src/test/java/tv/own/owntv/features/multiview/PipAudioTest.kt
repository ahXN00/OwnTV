package tv.own.owntv.features.multiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "only one window is audible at a time" rule for true picture-in-picture. */
class PipAudioTest {

    @Test
    fun noCorner_leavesBothEnginesAlone() {
        // No corner → null plan, so the shell touches neither mute (normal playback is never disturbed).
        assertNull(PipAudio.plan(cornerActive = false, mainPresent = false, audioOnCorner = false))
        assertNull(PipAudio.plan(cornerActive = false, mainPresent = true, audioOnCorner = true))
    }

    @Test
    fun browseOnlyCorner_cornerIsAudible_mainUntouched() {
        // Browsing with just a corner up: it's the only stream, so it gets the sound; main left alone (null).
        val plan = PipAudio.plan(cornerActive = true, mainPresent = false, audioOnCorner = false)
        assertEquals(PipAudioPlan(muteMain = null, muteCorner = false), plan)
    }

    @Test
    fun browseOnlyCorner_audioFlagIrrelevant_whenNoMain() {
        // With no main present the audioOnCorner flag can't steal sound from a window that isn't there.
        val plan = PipAudio.plan(cornerActive = true, mainPresent = false, audioOnCorner = true)
        assertEquals(PipAudioPlan(muteMain = null, muteCorner = false), plan)
    }

    @Test
    fun bothWindows_default_mainAudible_cornerMuted() {
        // Full-screen + corner, default: the main stream keeps the sound, the corner is silent video.
        val plan = PipAudio.plan(cornerActive = true, mainPresent = true, audioOnCorner = false)
        assertEquals(PipAudioPlan(muteMain = false, muteCorner = true), plan)
    }

    @Test
    fun bothWindows_audioOnCorner_cornerAudible_mainMuted() {
        // User hands sound to the corner: corner unmuted, main muted — exactly one is audible.
        val plan = PipAudio.plan(cornerActive = true, mainPresent = true, audioOnCorner = true)
        assertEquals(PipAudioPlan(muteMain = true, muteCorner = false), plan)
    }
}
