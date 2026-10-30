package tv.own.owntv.features.automation

import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.features.automation.ApiReply.error
import java.io.BufferedInputStream

/**
 * The automation API's front door: pairing, the key check, and dispatch to the [routes] each area
 * declares (playlists, guide, channels, player, settings, cache, logs). `GET /api/v1` lists them all,
 * so a script or an AI client can learn the API from the app itself.
 *
 * Every route but pairing needs `Authorization: Bearer <token>`, and every request must name the TV
 * by IP address ([RequestGuard]).
 */
class AutomationRoutes(
    private val gate: AutomationGate,
    routes: List<ApiRoute>,
) {
    private val routes: List<ApiRoute> = listOf(ApiRoute("GET", "", "This list") { ApiReply.ok(describe()) }) + routes

    /**
     * Reads one request from [input] and answers it; null when the caller sent no request or a malformed
     * one. [onHead] runs once the head is in. Whatever the head alone rules out, a missing key above all,
     * is answered before any of the body is read: a caller without a key must not hold a connection
     * for the whole request deadline trickling a body nobody will look at.
     */
    suspend fun answer(input: BufferedInputStream, peer: Peer, onHead: () -> Unit = {}): HttpResponse? {
        val head = AutomationHttp.readHead(input) ?: return null
        onHead()
        refusal(head.method, head.path, head.headers, peer)?.let { return it }
        val request = AutomationHttp.readBody(input, head) ?: return null
        return handle(request, peer)
    }

    /** Answers [request]. It runs every check itself, so it is safe whoever calls it; repeating one costs a hash. */
    suspend fun handle(request: HttpRequest, peer: Peer): HttpResponse {
        refusal(request.method, request.path, request.headers, peer)?.let { return it }
        return if (request.path == PAIR) pair(request, peer) else route(request)
    }

    /**
     * Why a request may not go on, judged from its head: a Host that is not an IP address ([RequestGuard]),
     * pairing that is not a script's POST, a path outside the API, or a missing, unknown or misplaced
     * key. Null when it may. Pairing needs no key: its PIN is in the body.
     */
    private fun refusal(method: String, path: String, headers: Map<String, String>, peer: Peer): HttpResponse? {
        RequestGuard.host(headers)?.let { return it }
        if (path == PAIR) return if (method == "POST") RequestGuard.pairing(headers) else methodNotAllowed()
        if (path != PREFIX && !path.startsWith("$PREFIX/")) return error(404, "Not found")
        return when (gate.authorize(headers, peer)) {
            is AutomationGate.Access.Granted -> null
            AutomationGate.Access.NoKey, AutomationGate.Access.WrongKey -> error(401, "Missing or unknown key")
            AutomationGate.Access.WrongAddress -> error(403, "This key is not allowed from this address")
            AutomationGate.Access.Blocked -> error(429, "Too many failed attempts; try again later")
        }
    }

    private suspend fun route(request: HttpRequest): HttpResponse {
        val segments = request.path.removePrefix(PREFIX).trim('/').split('/')
        var pathKnown = false
        for (route in routes) {
            val params = route.match(segments) ?: continue
            pathKnown = true
            if (route.method == request.method) return route.handler(ApiCall(request, params))
        }
        return if (pathKnown) methodNotAllowed() else error(404, "Not found; GET $PREFIX lists every endpoint")
    }

    private fun describe(): JSONObject = JSONObject()
        .put("api", "OwnTV automation")
        .put("auth", "Authorization: Bearer <token>, from POST $PAIR while Pair a device is open on the TV")
        .put("endpoints", JSONArray().apply {
            put(JSONObject().put("method", "POST").put("path", PAIR).put("summary", "Pair this device").put("body", """{"pin","name"}"""))
            routes.forEach { put(it.describe()) }
        })

    private fun pair(request: HttpRequest, peer: Peer): HttpResponse {
        val json = runCatching { JSONObject(request.body) }.getOrNull() ?: return ApiReply.badBody()
        return when (val result = gate.pair(json.optString("pin"), json.optString("name"), peer)) {
            is AutomationGate.PairResult.Paired -> ApiReply.ok(
                JSONObject().put("token", result.token).put("deviceId", result.device.id).put("name", result.device.name),
            )
            AutomationGate.PairResult.WrongPin -> error(401, "Wrong PIN, or pairing is not open on the TV")
            AutomationGate.PairResult.NotFromHome -> error(403, "Pairing is only allowed from the home network")
            AutomationGate.PairResult.Blocked -> error(429, "Too many failed attempts; try again later")
        }
    }

    private fun methodNotAllowed() = error(405, "Method not allowed")

    companion object {
        const val PREFIX = "/api/v1"
        const val PAIR = "$PREFIX/pair"
    }
}
