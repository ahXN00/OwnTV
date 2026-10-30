package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestGuardTest {
    private val client = ApiTestClient(emptyList())
    private val script = ApiTestClient.SCRIPT_HEADERS

    @Test
    fun `requests that name the tv by host name are refused on every route`() {
        assertEquals(200, client.call("GET", "/api/v1").status)
        assertEquals(400, client.call("GET", "/api/v1", headers = script + ("host" to "rebind.example:8090")).status)
        assertEquals(400, client.call("GET", "/api/v1", headers = script - "host").status)
        assertEquals(400, client.call("GET", "/elsewhere", headers = script + ("host" to "localhost")).status)
        assertEquals(400, client.pairWith(script + ("host" to "rebind.example")).status)
        assertEquals(200, client.call("GET", "/api/v1", headers = script + ("host" to "[fd12::5]:8090")).status)
    }

    @Test
    fun `pairing refuses web pages and bodies not sent as json`() {
        assertEquals(403, client.pairWith(script + ("origin" to "http://page.example")).status)
        assertEquals(403, client.pairWith(script + ("origin" to "null")).status)
        assertEquals(415, client.pairWith(script + ("content-type" to "text/plain")).status)
        assertEquals(415, client.pairWith(script - "content-type").status)
        assertEquals(200, client.pairWith(script + ("content-type" to "Application/JSON")).status)
    }
}
