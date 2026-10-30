package tv.own.owntv.features.automation

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.net.InetAddress

/** Sends requests through the real gate and dispatcher, as a device paired from 192.168.1.10. */
class ApiTestClient(routes: List<ApiRoute>) {
    private var settings = AutomationSettings(enabled = true)
    private val pairing = PairingWindow()
    private val api = AutomationRoutes(
        AutomationGate({ settings }, { settings = settings.copy(devices = settings.devices + it) }, pairing),
        routes,
    )
    private val device = Peer(InetAddress.getByName("192.168.1.10"))
    val token: String by lazy { pair() }

    fun call(
        method: String,
        path: String,
        body: String = "",
        query: Map<String, String> = emptyMap(),
        key: String? = token,
        headers: Map<String, String> = SCRIPT_HEADERS,
    ): HttpResponse = runBlocking {
        val auth = key?.let { mapOf("authorization" to "Bearer $it") }.orEmpty()
        api.handle(HttpRequest(method, path, query, headers + auth, body), device)
    }

    /** Sends [head], a request line and its headers, through [AutomationRoutes.answer] with [body] after it. */
    fun answer(head: String, body: InputStream): HttpResponse? = runBlocking {
        api.answer(BufferedInputStream(SequenceInputStream("$head\r\n\r\n".byteInputStream(), body)), device)
    }

    fun json(method: String, path: String, body: String = "", query: Map<String, String> = emptyMap()): JSONObject {
        val response = call(method, path, body, query)
        assertEquals(response.body, 200, response.status)
        return JSONObject(response.body)
    }

    /** Opens pairing on the TV and sends the right PIN with [headers]. */
    fun pairWith(headers: Map<String, String> = SCRIPT_HEADERS): HttpResponse {
        pairing.open("424242")
        return call("POST", "/api/v1/pair", """{"pin":"424242","name":"Test device"}""", key = null, headers = headers)
    }

    private fun pair(): String {
        val response = pairWith()
        assertEquals(200, response.status)
        return JSONObject(response.body).getString("token")
    }

    companion object {
        /** What a script sends: the TV by IP address, and a JSON body. */
        val SCRIPT_HEADERS = mapOf("host" to "192.168.1.20:8090", "content-type" to "application/json; charset=utf-8")
    }
}
