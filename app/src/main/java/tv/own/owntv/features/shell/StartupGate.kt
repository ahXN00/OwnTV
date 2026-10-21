package tv.own.owntv.features.shell

/**
 * Whether the shell has opened where the profile's start-up choice says (the TV Guide, Live TV, the
 * player...). The shell first composes on Home and applies that choice a moment later, so the splash
 * stays up until then (see MainActivity) and Home never flashes on the way to the chosen screen.
 */
object StartupGate {
    @Volatile
    var settled = false
}
