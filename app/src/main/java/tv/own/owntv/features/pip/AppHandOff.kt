package tv.own.owntv.features.pip

/**
 * A screen of another app that OwnTV itself opens: an external player, the YouTube trailer, a system
 * permission screen. Leaving the app that way is not the user leaving it, so it must not open the
 * picture-in-picture window (the stream would play twice, once in the window and once in the external
 * player, and Back would land on the launcher instead of OwnTV).
 *
 * [launch] holds the window and stops playback before the other screen opens, the same stop Home makes.
 * The activity then stops as well ([onActivityStopped]), and its next start brings playback back as it
 * always has. A screen that only covers part of OwnTV (a chooser that is cancelled) never stops the
 * activity, so its resume brings playback back instead. A launch that fails gives both back at once.
 *
 * Main thread only, like the engines it stops.
 */
internal class AppHandOff(
    private val holdWindow: (Boolean) -> Unit,
    private val stopPlayback: () -> Unit,
    private val restorePlayback: () -> Unit,
) {

    private var awaitingStop = false

    fun <T> launch(start: () -> T): T {
        holdWindow(true)
        stopPlayback()
        awaitingStop = true
        return try {
            start()
        } catch (e: RuntimeException) {
            awaitingStop = false
            holdWindow(false)
            restorePlayback()
            throw e
        }
    }

    /** The other screen covers OwnTV: the activity's own start brings playback back later. */
    fun onActivityStopped() {
        awaitingStop = false
    }

    fun onActivityResumed() {
        holdWindow(false)
        if (awaitingStop) {
            awaitingStop = false
            restorePlayback()
        }
    }
}
