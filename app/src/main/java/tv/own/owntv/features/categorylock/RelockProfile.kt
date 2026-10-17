package tv.own.owntv.features.categorylock

/**
 * The profile whose PIN may have opened what plays, for a relock to check it against. On a profile
 * change that is the outgoing profile, not the new one: the PIN opened the category under the outgoing
 * profile's locks, which the new profile may not have. A change can skip the profile picker (deleting
 * the active profile, a restore), so the player may still be on that item when it lands. Main thread only.
 */
internal class RelockProfile {
    private var active: Long? = null

    /** The active profile, or null before the first one is known. */
    val current: Long? get() = active

    /** Makes [profileId] the active one and gives the profile a relock now checks: the outgoing one, if any. */
    fun changeTo(profileId: Long): Long {
        val outgoing = active ?: profileId
        active = profileId
        return outgoing
    }
}

/** The profiles whose locks a relock checks: [opener] and [active], each once, skipping "no profile" (-1). */
internal fun relockedProfiles(opener: Long?, active: Long): List<Long> =
    listOfNotNull(opener, active).distinct().filter { it >= 0 }
