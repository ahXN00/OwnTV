package tv.own.owntv.features.multiview

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.player.CornerEngine
import tv.own.owntv.player.MediaMeta

/**
 * App-wide state for the **picture-in-picture corner window** — the second simultaneous stream. Owns the
 * corner [CornerEngine] (its decoder) and tracks which channel the corner is on, so the shell can mount a
 * persistent corner overlay that survives moving between the browse UI and full-screen.
 *
 * This is intentionally thin: it starts/stops the corner stream and remembers the corner channel. Audio
 * arbitration (only one window audible at a time) and the swap-with-main gesture are orchestrated by the
 * shell, which is the only place that knows what the *main* player is currently doing (mpv vs ExoPlayer).
 *
 * A single corner today (true PiP = 1 + 1). The same controller generalises to the 2×2 MultiView grid
 * later by holding a list of engines instead of one.
 */
class PipController(val engine: CornerEngine) {

    private val _active = MutableStateFlow(false)
    /** True while a corner window is on screen (a second stream is running). */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _channel = MutableStateFlow<ChannelEntity?>(null)
    /** The channel playing in the corner (for its title, and to swap it into the main window). */
    val channel: StateFlow<ChannelEntity?> = _channel.asStateFlow()

    /** Open [channel] in the corner (or switch the corner to it). Starts muted so the main stream keeps the
     *  sound; the user hands audio to the corner explicitly. No-op-safe to call repeatedly with the same one. */
    fun openCorner(channel: ChannelEntity, muted: Boolean = true) {
        _channel.value = channel
        _active.value = true
        if (engine.currentUrl != channel.streamUrl) {
            engine.play(
                channel.streamUrl,
                meta = MediaMeta(title = channel.name, logoUrl = channel.logoUrl),
                muted = muted,
            )
        } else {
            engine.setMuted(muted)
        }
    }

    /** Close the corner window and free its decoder/connection. */
    fun closeCorner() {
        _active.value = false
        _channel.value = null
        engine.stop()
    }
}
