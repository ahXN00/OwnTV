package tv.own.owntv.features.automation

import org.json.JSONObject

/**
 * One endpoint: method, path under `/api/v1` (a `{name}` part matches a whole number, negative too),
 * the one-line [summary] and optional [body] hint that `GET /api/v1` lists, and the handler.
 */
class ApiRoute(
    val method: String,
    val pattern: String,
    val summary: String,
    val body: String? = null,
    val handler: suspend (ApiCall) -> HttpResponse,
) {
    private val parts = pattern.split('/')

    /** The `{name}` values when [segments] fit the pattern, else null. */
    fun match(segments: List<String>): Map<String, Long>? {
        if (segments.size != parts.size) return null
        val params = HashMap<String, Long>()
        for ((part, segment) in parts.zip(segments)) {
            if (part.startsWith("{")) {
                params[part.trim('{', '}')] = segment.toLongOrNull() ?: return null
            } else if (part != segment) {
                return null
            }
        }
        return params
    }

    fun describe(): JSONObject = JSONObject()
        .put("method", method)
        .put("path", "${AutomationRoutes.PREFIX}/$pattern".trimEnd('/'))
        .put("summary", summary)
        .apply { body?.let { put("body", it) } }
}

/** A matched request: the path numbers, the query and the JSON body. */
class ApiCall(val request: HttpRequest, private val params: Map<String, Long>) {
    fun id(name: String = "id"): Long = params.getValue(name)

    fun query(name: String): String? = request.query[name]

    /** The body as a JSON object; an empty body reads as `{}`. Null when it is not a JSON object. */
    fun json(): JSONObject? =
        if (request.body.isBlank()) JSONObject() else runCatching { JSONObject(request.body) }.getOrNull()
}

/** The API's response shapes, so every area answers alike. */
object ApiReply {
    fun ok(json: JSONObject) = HttpResponse(200, json.toString())
    fun accepted(status: String) = HttpResponse(202, JSONObject().put("status", status).toString())
    fun accepted(json: JSONObject) = HttpResponse(202, json.toString())
    fun error(status: Int, message: String) = HttpResponse(status, JSONObject().put("error", message).toString())
    fun notFound(what: String, id: Long) = error(404, "No $what with id $id")
    fun badBody() = error(400, "Body must be a JSON object")
    fun invalid(reason: String) = error(400, reason)

    /** OwnTV is in the background and Android will not let it come forward ([PlayResult.Blocked]). */
    fun blocked() = error(
        409,
        "OwnTV is in the background and may not open itself; open it on the TV, or grant it once: " +
            "adb shell appops set <package> SYSTEM_ALERT_WINDOW allow",
    )

    /** A private screen ([SensitiveScreens]) is open on the TV. Scripts tell this 409 apart by the word "private". */
    fun privateScreen() = error(409, "A private screen is open on the TV (logins, PINs, profiles, network, backups or pairing); close it there first")

    /**
     * No profile is open on the TV ([AutomationProfile.id]): none is chosen yet, or OwnTV is asking who is
     * watching or for a PIN. It says "private" too, as what is in front is a screen the API may not drive.
     */
    fun noProfile() = error(409, NO_PROFILE)

    /** First run is not over, and no device paired from the TV's "Set up by Automation" page ([SetupAccess.CLOSED]). */
    fun setupClosed() = error(409, "Set up by Automation is not open on the TV; start it there and pair from that page")

    const val NO_PROFILE = "No profile is open on the TV; pick one there first (private)"
}
