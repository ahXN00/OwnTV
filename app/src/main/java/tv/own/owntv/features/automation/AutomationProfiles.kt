package tv.own.owntv.features.automation

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.dao.ProfileDao

/**
 * The profiles for the API. One is added the way Settings › Profiles adds it ([create],
 * ProfileManager.create), so it gets every playlist there is, but never with a PIN, as [create] takes
 * none, and only for first-run setup from the TV's "Set up by Automation" page ([SetupProfile.whileOpen]);
 * using one is [ProfileUse.of]'s call, and once setup is done opening it is [switchTo]'s ([ProfileSwitch]).
 */
class AutomationProfiles(
    private val profileDao: ProfileDao,
    private val create: suspend (name: String, avatar: Int, kids: Boolean) -> Long,
    private val target: PlaylistTarget,
    private val setup: SetupProfile,
    private val sensitive: SensitiveScreens,
    private val switchTo: suspend (profileId: Long) -> Unit,
) : ProfileBackend {

    override suspend fun profiles(): JSONObject {
        val active = target.id()
        val array = JSONArray()
        profileDao.getAllOnce().forEach { array.put(ProfileJson.of(it, active = it.id == active)) }
        return JSONObject().put("profiles", array)
    }

    override suspend fun add(profile: NewProfile): ProfileAdded = setup.whileOpen(
        refused = { if (it == SetupAccess.DONE) ProfileAdded.AfterSetup else ProfileAdded.SetupClosed },
    ) { addOpen(profile) }

    // Held with the visit, so two adds of one name cannot both pass the check before either is saved.
    private suspend fun addOpen(profile: NewProfile): ProfileAdded {
        if (profileDao.getAllOnce().any { it.name.equals(profile.name, ignoreCase = true) }) return ProfileAdded.Taken
        val id = create(profile.name, profile.avatar, profile.kids)
        Log.d(TAG, "profile add id=$id")
        val saved = checkNotNull(profileDao.getById(id)) { "profile $id was not saved" }
        return ProfileAdded.Added(ProfileJson.of(saved, active = id == target.id()))
    }

    override suspend fun use(id: Long): ProfileUse {
        val profile = profileDao.getById(id)
        val use = ProfileUse.of(profile, setup = setup.access(), openId = target.id(), privateOpen = sensitive.anyOpen)
        Log.d(TAG, "profile use id=$id result=$use")
        when (use) {
            ProfileUse.CHOSEN -> setup.choose(id)
            ProfileUse.SWITCHING -> switchTo(id)
            ProfileUse.NOT_FOUND, ProfileUse.LOCKED, ProfileUse.SETUP_CLOSED, ProfileUse.PRIVATE, ProfileUse.ACTIVE,
            -> Unit
        }
        return use
    }

    private companion object {
        const val TAG = "OwnTVAutomation"
    }
}
