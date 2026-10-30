package tv.own.owntv.features.automation

import tv.own.owntv.player.SleepTimer

/**
 * What `POST player/stop` does. Stopping the engine alone left the full-screen player open on its last
 * frame, and `GET ui` still named the channel, until someone pressed Back. While the main screen is up it
 * has told the sleep timer what stopping means on a television ([SleepTimer.stopPlayback]): close full
 * screen back to where the user came from, and stop Multiview and the preview pane. The API's stop is
 * that same stop. Whatever still plays after it, or everything when there is no main screen to ask
 * (first run, "Who's watching?"), is stopped on its engine. Main thread only, like both.
 */
class PlayerStop(
    private val shellStop: () -> (() -> Unit)?,
    private val stillPlaying: () -> (() -> Unit)?,
) {
    constructor(sleepTimer: SleepTimer, engines: PlayingEngine) : this(
        shellStop = { sleepTimer.stopPlayback },
        stillPlaying = { engines.current().takeIf { it.active }?.stop },
    )

    fun stop() {
        shellStop()?.invoke()
        stillPlaying()?.invoke()
    }
}
