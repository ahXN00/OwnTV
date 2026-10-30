package tv.own.owntv.features.automation

/**
 * Whether a paired computer may act for first-run setup: add a profile, choose the one setup opens, add
 * its playlists, finish. Only while the TV's "Set up by Automation" page is open and a device paired from
 * it ([AutomationSetupSession.paired]); once setup is done, never, as profiles are then added on the TV.
 */
enum class SetupAccess {
    OPEN,

    /** First run is over ([SetupProfile.done]). */
    DONE,

    /** First run is not over, but no device paired from the page in a visit that is still open. */
    CLOSED,
    ;

    companion object {
        fun of(done: Boolean, paired: Boolean): SetupAccess = when {
            done -> DONE
            paired -> OPEN
            else -> CLOSED
        }
    }
}
