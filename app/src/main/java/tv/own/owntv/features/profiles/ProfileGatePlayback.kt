package tv.own.owntv.features.profiles

import tv.own.owntv.player.PlaybackEngines

/**
 * Stops what plays and forgets what Home would bring back, before the profile gate takes the screen:
 * the gate is a new authentication boundary, and nothing of the session before it may play on — in
 * any engine, Multiview's tiles included.
 */
internal fun stopPlaybackForProfileGate(engines: PlaybackEngines) {
    engines.onAppBackgrounded()
    engines.discardBackgroundRestore()
}
