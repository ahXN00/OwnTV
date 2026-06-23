package tv.own.owntv.player

import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

/** Playback state of the picture-in-picture corner stream. */
enum class CornerState { IDLE, LOADING, PLAYING, ERROR }

/**
 * The picture-in-picture corner's video engine, abstracted so the orchestration ([tv.own.owntv.features.
 * multiview.PipController]) and UI bind to an interface rather than the concrete ExoPlayer wrapper. This
 * keeps the corner logic unit-testable with a lightweight fake — no Android, no ExoPlayer, no device — which
 * is also the cheapest way to verify it (a second real decoder is exactly what we *don't* want to spin up in
 * a test). [SecondaryLivePlayer] is the production implementation.
 */
interface CornerEngine {
    val state: StateFlow<CornerState>
    val meta: StateFlow<MediaMeta>

    /** URL the corner is currently on (null when stopped) — lets the controller skip a redundant reload. */
    val currentUrl: String?

    fun play(url: String, meta: MediaMeta = MediaMeta(), muted: Boolean = true)
    fun setMuted(muted: Boolean)
    fun stop()
    fun setSurface(surface: Surface?)
}
