package tv.own.owntv.features.automation

import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.player.MpvPlaybackEngine
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlaybackEngine

/** The engine the API reports on and controls, with the two things [PlaybackEngine] does not carry. */
class Playing(val engine: PlaybackEngine, val active: Boolean, val fps: Float?, val stop: () -> Unit)

/**
 * Which engine is on screen. A live channel can play on the ExoPlayer live engine (the Live preview,
 * also promoted to full screen) instead of mpv, so the API cannot only ask mpv. mpv wins while it holds
 * a stream, as in the shell's own pick for the system media session; the muted Home hero preview and
 * Multiview tiles are not counted. Main thread only, like both engines.
 */
class PlayingEngine(private val mpv: OwnTVPlayer, private val live: LivePreviewEngine) {
    private val mpvEngine = MpvPlaybackEngine(mpv)

    fun current(): Playing = when {
        mpv.active.value -> Playing(mpvEngine, active = true, fps = mpv.videoFps.value, stop = mpv::stop)
        live.state.value != LivePreviewEngine.State.IDLE ->
            Playing(live, active = true, fps = live.videoFps.value, stop = live::stop)
        else -> Playing(mpvEngine, active = false, fps = null, stop = mpv::stop)
    }
}
