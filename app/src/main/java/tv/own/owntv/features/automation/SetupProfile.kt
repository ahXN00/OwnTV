package tv.own.owntv.features.automation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.ProfileEntity

/**
 * First-run setup from a paired computer. Setup is done once a profile is active ([activeProfileId]), the
 * mark the wizard leaves when it finishes (SourceImporter.finish). Until then no profile is open on the TV,
 * so while a device paired from the "Set up by Automation" page ([access], [session]) the API works for
 * the one setup will open ([target]): the one `POST profiles/{id}/use` chose ([choose]), else the oldest.
 * Either must have no PIN, as the API may not open a profile that has one.
 */
class SetupProfile(
    private val activeProfileId: Flow<Long>,
    private val profileDao: ProfileDao,
    private val session: AutomationSetupSession,
) {
    @Volatile
    private var chosen: Long? = null

    suspend fun done(): Boolean = activeProfileId.first() >= 0L

    suspend fun access(): SetupAccess = SetupAccess.of(done(), session.paired())

    /**
     * Runs [step] while setup is [SetupAccess.OPEN], holding the visit ([AutomationSetupSession.holding]) so
     * the page cannot close halfway through; otherwise [step] does not run and [refused] answers instead.
     */
    suspend fun <T> whileOpen(refused: (SetupAccess) -> T, step: suspend () -> T): T = session.holding {
        val access = access()
        if (access == SetupAccess.OPEN) step() else refused(access)
    }

    /** The profile setup will open; null unless setup is [SetupAccess.OPEN], or while none is without a PIN. */
    suspend fun target(): Long? = if (access() == SetupAccess.OPEN) pick(profileDao.getAllOnce(), chosen) else null

    fun choose(profileId: Long) {
        chosen = profileId
    }

    companion object {
        /** [chosen] when it is one of [profiles] without a PIN, else the first of those (oldest first). */
        fun pick(profiles: List<ProfileEntity>, chosen: Long?): Long? {
            val open = profiles.filter { it.pinHash == null }
            return (open.firstOrNull { it.id == chosen } ?: open.firstOrNull())?.id
        }
    }
}
