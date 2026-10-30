package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.features.automation.ApiReply.accepted
import tv.own.owntv.features.automation.ApiReply.notFound
import tv.own.owntv.features.automation.ApiReply.ok

/** What the profile routes ask of the app. The Android side is [AutomationProfiles]. */
interface ProfileBackend {
    /** `{"profiles":[...]}` as [ProfileJson] shapes each one. */
    suspend fun profiles(): JSONObject

    /** Adds [profile], during first-run setup only ([SetupAccess]). */
    suspend fun add(profile: NewProfile): ProfileAdded

    suspend fun use(id: Long): ProfileUse
}

/** How `POST profiles` went. */
sealed interface ProfileAdded {
    data class Added(val json: JSONObject) : ProfileAdded

    /** A profile already has that name, in any letter case. */
    data object Taken : ProfileAdded

    /** First run is over: profiles are added on the TV from then on. */
    data object AfterSetup : ProfileAdded

    /** First run is not over, but no device paired from the "Set up by Automation" page ([SetupAccess.CLOSED]). */
    data object SetupClosed : ProfileAdded
}

/**
 * The profiles, adding one (never with a PIN, and only for first-run setup) and opening one that has none.
 * `active` marks the profile the API acts for: the open one, or during first-run setup the one setup will open.
 */
fun profileRoutes(backend: ProfileBackend): List<ApiRoute> = listOf(
    ApiRoute("GET", "profiles", "Profiles: id, name, kids, whether a PIN locks it, and the one the API acts for") {
        ok(backend.profiles())
    },
    ApiRoute(
        "POST", "profiles", "During setup, add a profile without a PIN; it gets every playlist, as in Settings › Profiles",
        """{"name","kids"?,"avatar"?}""",
    ) { call ->
        val json = call.json() ?: return@ApiRoute ApiReply.badBody()
        when (val parsed = ProfileRequest.parse(json)) {
            is ParsedProfile.Invalid -> ApiReply.invalid(parsed.reason)
            is ParsedProfile.Ok -> when (val added = backend.add(parsed.profile)) {
                is ProfileAdded.Added -> ok(added.json)
                ProfileAdded.Taken -> ApiReply.error(409, "A profile named \"${parsed.profile.name}\" already exists")
                ProfileAdded.AfterSetup -> ApiReply.error(409, "Profiles can only be added on the TV after setup")
                ProfileAdded.SetupClosed -> ApiReply.setupClosed()
            }
        }
    },
    ApiRoute("POST", "profiles/{id}/use", "Open a profile without a PIN; during setup, choose the one setup opens") { call ->
        use(backend, call.id())
    },
)

private suspend fun use(backend: ProfileBackend, id: Long): HttpResponse = when (backend.use(id)) {
    ProfileUse.NOT_FOUND -> notFound("profile", id)
    ProfileUse.LOCKED -> ApiReply.error(403, "This profile has a PIN; open it on the TV")
    ProfileUse.PRIVATE -> ApiReply.privateScreen()
    ProfileUse.CHOSEN -> ok(status("chosen", id))
    ProfileUse.SETUP_CLOSED -> ApiReply.setupClosed()
    ProfileUse.ACTIVE -> ok(status("active", id))
    ProfileUse.SWITCHING -> accepted(status("switching", id))
}

private fun status(status: String, id: Long): JSONObject = JSONObject().put("status", status).put("profileId", id)
