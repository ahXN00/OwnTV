package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.features.automation.ApiReply.invalid
import tv.own.owntv.features.automation.ApiReply.ok

/** What the ui routes ask of the app. The Android side is [AutomationUi]. */
interface UiBackend {
    /** What the TV shows, as [UiJson] shapes it. */
    suspend fun state(): JSONObject
    suspend fun navigate(target: NavTarget): NavResult
}

/** What is on the TV, and opening one of its sections the way the remote's shortcuts do. */
fun uiRoutes(backend: UiBackend): List<ApiRoute> = listOf(
    ApiRoute("GET", "ui", "What the TV shows: screen, player, focused and playing item, active playlist") { ok(backend.state()) },
    ApiRoute("POST", "ui/navigate", "Open a section on the TV, bringing OwnTV forward", """{"to":"$TARGET_HINT"}""") { call ->
        navigate(backend, call)
    },
)

private suspend fun navigate(backend: UiBackend, call: ApiCall): HttpResponse {
    val json = call.json() ?: return ApiReply.badBody()
    val fields = JsonFields(json)
    val to = fields.string("to")?.trim()
    fields.error?.let { return invalid(it) }
    if (to.isNullOrEmpty()) return invalid("to is required: one of $TARGETS")
    if (to.equals(SETTINGS, ignoreCase = true)) return invalid("Settings cannot be opened from the API")
    val target = NavTarget.parse(to) ?: return invalid("Unknown section \"$to\"; to must be one of $TARGETS")
    return when (backend.navigate(target)) {
        NavResult.SENT -> ApiReply.accepted(JSONObject().put("to", target.apiName))
        NavResult.BLOCKED -> ApiReply.blocked()
        NavResult.PRIVATE -> ApiReply.privateScreen()
        NavResult.NO_PROFILE -> ApiReply.noProfile()
    }
}

private val TARGETS = NavTarget.entries.joinToString { it.apiName }
private val TARGET_HINT = NavTarget.entries.joinToString("|") { it.apiName }
private const val SETTINGS = "settings"
