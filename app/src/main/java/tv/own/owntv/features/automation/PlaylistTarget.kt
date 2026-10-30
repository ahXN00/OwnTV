package tv.own.owntv.features.automation

import tv.own.owntv.core.database.entity.SourceEntity

/**
 * The profile the playlist routes act for: the open one ([openId], [AutomationProfile.id]) once first run
 * is done, and, while the wizard is still on screen, the one a computer paired from its "Set up by
 * Automation" page is setting up ([SetupProfile.target]). So that computer can add playlists before any
 * profile is open. The guide and settings routes act for the whole app, not a profile, so they need
 * nothing like it.
 */
class PlaylistTarget(
    private val openId: suspend () -> Long?,
    private val openPlaylists: suspend (profileId: Long) -> List<SourceEntity>,
    private val setup: SetupProfile,
) {
    suspend fun id(): Long? = openId() ?: setup.target()

    /** The profile's playlists. */
    suspend fun playlists(profileId: Long): List<SourceEntity> = openPlaylists(profileId)
}
