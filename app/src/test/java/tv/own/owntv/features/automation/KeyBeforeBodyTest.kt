package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyBeforeBodyTest {
    private val client = ApiTestClient(emptyList())

    private fun post(path: String, vararg headers: String) =
        (listOf("POST $path HTTP/1.1", "Host: 192.168.1.20:8090", "Content-Length: 65536") + headers).joinToString("\r\n")

    @Test
    fun `a request without a good key is refused before its body is read`() {
        assertEquals(401, client.answer(post("/api/v1/playlists"), NeverRead)?.status)
        assertEquals(401, client.answer(post("/api/v1/playlists", "Authorization: Bearer nope"), NeverRead)?.status)
        assertEquals(404, client.answer(post("/elsewhere"), NeverRead)?.status)
        val noIp = "POST /api/v1/playlists HTTP/1.1\r\nHost: tv.example.com\r\nContent-Length: 65536"
        assertEquals(400, client.answer(noIp, NeverRead)?.status)
    }

    @Test
    fun `pairing from a web page is refused before its body is read`() {
        assertEquals(403, client.answer(post("/api/v1/pair", "Origin: http://page.example.com"), NeverRead)?.status)
        assertEquals(415, client.answer(post("/api/v1/pair"), NeverRead)?.status)
    }

    @Test
    fun `pairing and requests with a key go on to read the body`() {
        val pin = """{"pin":"000000","name":"x"}"""
        val pairing = "POST /api/v1/pair HTTP/1.1\r\nHost: 192.168.1.20:8090\r\nContent-Type: application/json\r\nContent-Length: ${pin.length}"
        val refused = client.answer(pairing, pin.byteInputStream())
        assertEquals("Wrong PIN, or pairing is not open on the TV", refused?.body?.let { JSONObject(it).getString("error") })
        val status = "GET /api/v1 HTTP/1.1\r\nHost: 192.168.1.20:8090\r\nAuthorization: Bearer ${client.token}"
        assertEquals(200, client.answer(status, NeverRead)?.status)
    }
}
