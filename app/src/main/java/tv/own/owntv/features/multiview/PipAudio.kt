package tv.own.owntv.features.multiview

/**
 * Which window should be muted while a picture-in-picture corner is on screen. Only **one** window is
 * audible at a time (two simultaneous soundtracks would be cacophony). Pure + Android-free so the rule is
 * unit-testable without spinning up real decoders.
 *
 * @property muteMain null = leave the main player's mute untouched (there is no main window present, e.g.
 *   while browsing — touching it would interfere with normal playback); true/false = set it explicitly.
 * @property muteCorner the corner engine's target mute state.
 */
data class PipAudioPlan(val muteMain: Boolean?, val muteCorner: Boolean)

object PipAudio {
    /**
     * @param cornerActive  a corner stream is on screen.
     * @param mainPresent   a main player (full-screen or docked) is also showing.
     * @param audioOnCorner the user has handed the sound to the corner.
     * @return the mute plan, or null when no corner is active (don't touch either engine — normal playback).
     */
    fun plan(cornerActive: Boolean, mainPresent: Boolean, audioOnCorner: Boolean): PipAudioPlan? = when {
        !cornerActive -> null
        // Browse with only a corner up → it's the only stream, so it gets the sound; leave the main alone.
        !mainPresent -> PipAudioPlan(muteMain = null, muteCorner = false)
        // Both windows present → exactly one is audible, per the user's choice.
        audioOnCorner -> PipAudioPlan(muteMain = true, muteCorner = false)
        else -> PipAudioPlan(muteMain = false, muteCorner = true)
    }
}
