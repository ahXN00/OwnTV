package tv.own.owntv.features.automation

import kotlinx.coroutines.flow.first
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.settings.SourceActions

/**
 * The active profile as the API sees it: its id, its playlists, and the one the top bar has chosen. What
 * it may see of them is [ProfileCatalog]'s job.
 */
class AutomationProfile(
    private val sourceActions: SourceActions,
    private val sourceRepository: SourceRepository,
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    private val unlocked: UnlockedProfile,
) {
    /**
     * The active profile's id, or null when none is open on the TV: none is chosen yet, or OwnTV is
     * asking who is watching or for the profile's PIN ([UnlockedProfile]).
     */
    suspend fun id(): Long? = sourceActions.activeProfileId()?.takeIf { unlocked.admits(it, profileDao.getAllOnce()) }

    /** The profile's playlists. */
    suspend fun playlists(profileId: Long): List<SourceEntity> = sourceRepository.observeSources(profileId).first()

    /** The playlist the top bar has chosen; null for All playlists, or when no profile is chosen. */
    suspend fun chosenPlaylist(): SourceEntity? {
        val profileId = id() ?: return null
        val chosen = settings.defaultSourceId.first()
        return playlists(profileId).firstOrNull { it.id == chosen }
    }
}
