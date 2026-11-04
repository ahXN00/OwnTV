package tv.own.owntv.features.profiles

import tv.own.owntv.features.shell.ShellViewModel
import tv.own.owntv.player.PlaybackEngines

/**
 * Stops what plays and forgets what Home would bring back, before the profile gate takes the screen:
 * the gate is a new authentication boundary, and nothing of the session before it may play on — in
 * any engine, Multiview's tiles included. The shell leaves the player first, so the profile comes back
 * to the screen it was browsing, not to a player whose stream is gone.
 */
internal fun stopPlaybackForProfileGate(shell: ShellViewModel, engines: PlaybackEngines) {
    shell.leavePlayback?.invoke()
    engines.onAppBackgrounded()
    engines.discardBackgroundRestore()
}
