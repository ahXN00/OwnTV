package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.features.automation.ApiReply.ok

/** What the setup routes ask of the app. The Android side is [AutomationSetup]. */
interface SetupBackend {
    /** `{"done":<first run finished>,"profiles":<how many>}`. */
    suspend fun state(): JSONObject

    suspend fun finish(finish: SetupFinish): SetupFinished
}

/** How `POST setup/finish` went. */
sealed interface SetupFinished {
    /** Setup is done; [json] says which profile opened and which values took ([SettingsDiff]). */
    data class Done(val json: JSONObject) : SetupFinished

    data object AlreadyDone : SetupFinished

    /** No device paired from the TV's "Set up by Automation" page, or the page is closed ([SetupAccess.CLOSED]). */
    data object Closed : SetupFinished

    /** There is no profile without a PIN for setup to open. */
    data object NoProfile : SetupFinished
}

/**
 * First-run setup from a paired computer, while the wizard waits on its "Set up by Automation" page:
 * whether it is done, and finishing it, which only a device paired from that page may do while the page
 * is open. [languages] are the language tags the wizard offers.
 */
fun setupRoutes(backend: SetupBackend, languages: Set<String>): List<ApiRoute> = listOf(
    ApiRoute("GET", "setup", "Whether first-run setup is done, and how many profiles there are") { ok(backend.state()) },
    ApiRoute(
        "POST", "setup/finish", "Finish first-run setup: apply language and sizes, open the profile and go Home",
        """{"language"?,"uiZoom"?,"fontSize"?}""",
    ) { call ->
        val json = call.json() ?: return@ApiRoute ApiReply.badBody()
        when (val parsed = SetupFinishRequest.parse(json, languages)) {
            is ParsedFinish.Invalid -> ApiReply.invalid(parsed.reason)
            is ParsedFinish.Ok -> when (val result = backend.finish(parsed.finish)) {
                is SetupFinished.Done -> ok(result.json)
                SetupFinished.AlreadyDone -> ApiReply.error(409, "Setup is already finished")
                SetupFinished.Closed -> ApiReply.setupClosed()
                SetupFinished.NoProfile -> ApiReply.error(409, "Add a profile without a PIN first (POST profiles)")
            }
        }
    },
)
