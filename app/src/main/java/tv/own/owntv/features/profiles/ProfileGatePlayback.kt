package tv.own.owntv.features.profiles

import tv.own.owntv.player.HeroPreviewEngine
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.player.OwnTVPlayer

/**
 * Stops what plays and forgets what Home would bring back, before the profile gate takes the screen:
 * the gate is a new authentication boundary, and nothing of the session before it may play on.
 */
internal fun stopPlaybackForProfileGate(player: OwnTVPlayer, livePreview: LivePreviewEngine, heroPreview: HeroPreviewEngine) {
    player.onAppBackgrounded()
    player.discardBackgroundRestore()
    livePreview.stop()
    livePreview.discardBackgroundRestore()
    heroPreview.stop()
}
