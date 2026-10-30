package tv.own.owntv.features.automation

import android.util.Log
import org.json.JSONObject
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.features.profiles.profileGateRequired

/**
 * Finishing first run for a computer paired from the "Set up by Automation" page, all of it while the
 * page's visit is held ([SetupProfile.whileOpen]). The values go in through the import `PATCH settings`
 * uses ([appSettings]); first run ends the way the wizard ends it ([finishFirstRun], SourceImporter.finish),
 * which makes the profile active and so takes the TV from the wizard into the app; "Who's watching?", when
 * more profiles make it come up, picks the same one ([ProfileChoice]); the visit ends with the API left on
 * for that computer ([session]); and the shell opens Home ([AutomationNav]).
 */
class AutomationSetup(
    private val setup: SetupProfile,
    private val session: AutomationSetupSession,
    private val profileDao: ProfileDao,
    private val finishFirstRun: suspend (profileId: Long) -> Unit,
    private val appSettings: SettingsBackend,
    private val choice: ProfileChoice,
    private val nav: AutomationNav,
) : SetupBackend {

    override suspend fun state(): JSONObject = JSONObject().put("done", setup.done()).put("profiles", profileDao.count())

    override suspend fun finish(finish: SetupFinish): SetupFinished = setup.whileOpen(
        refused = { if (it == SetupAccess.DONE) SetupFinished.AlreadyDone else SetupFinished.Closed },
    ) { finishOpen(finish) }

    private suspend fun finishOpen(finish: SetupFinish): SetupFinished {
        val profileId = setup.target() ?: return SetupFinished.NoProfile
        val changes = finish.settings()
        if (changes.length() > 0) appSettings.import(changes)
        val diff = SettingsDiff.of(changes, appSettings.export(), name = SetupFinish::fieldOf)
        if (profileGateRequired(profileDao.getAllOnce())) choice.choose(profileId)
        finishFirstRun(profileId)
        session.finished()
        nav.section(NavTarget.HOME, profileId)
        Log.d(TAG, "setup finished profile=$profileId")
        return SetupFinished.Done(diff.put("done", true).put("profileId", profileId))
    }

    private companion object {
        const val TAG = "OwnTVAutomation"
    }
}
