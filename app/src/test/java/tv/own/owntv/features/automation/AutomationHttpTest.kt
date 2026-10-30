package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.SequenceInputStream

class AutomationHttpTest {

    private fun read(raw: String): HttpRequest? {
        val input = BufferedInputStream(raw.byteInputStream(Charsets.UTF_8))
        return AutomationHttp.readHead(input)?.let { AutomationHttp.readBody(input, it) }
    }

    @Test
    fun `parses method, path, query, headers and body`() {
        val body = """{"clean":true}"""
        val request = read("POST /api/v1/playlists/7/sync?x=a%20b HTTP/1.1\r\nAuthorization: Bearer k\r\nContent-Length: ${body.length}\r\n\r\n$body")!!
        assertEquals("POST", request.method)
        assertEquals("/api/v1/playlists/7/sync", request.path)
        assertEquals(mapOf("x" to "a b"), request.query)
        assertEquals("Bearer k", request.headers["authorization"])
        assertEquals(body, request.body)
    }

    @Test
    fun `the head is read without touching the body`() {
        val head = "POST /api/v1/playlists?x=1 HTTP/1.1\r\nContent-Length: 65536\r\n\r\n"
        val input = BufferedInputStream(SequenceInputStream(head.byteInputStream(), NeverRead))
        val parsed = AutomationHttp.readHead(input)
        assertEquals(HttpHead("POST", "/api/v1/playlists", mapOf("x" to "1"), mapOf("content-length" to "65536"), 65536), parsed)
    }

    @Test
    fun `request without a body reads an empty one`() {
        assertEquals("", read("GET /api/v1/status HTTP/1.1\r\nHost: tv\r\n\r\n")!!.body)
    }

    @Test
    fun `malformed or oversized requests are refused`() {
        assertNull(read("GET /\r\n\r\n"))
        assertNull(read("GET / HTTP/1.1\r\nHost: tv\r\n"))
        assertNull(read("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n"))
        assertNull(read("POST / HTTP/1.1\r\nContent-Length: ${AutomationHttp.MAX_BODY_BYTES + 1}\r\n\r\n"))
        assertNull(read("POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\nshort"))
        assertNull(read("GET / HTTP/1.1\r\nX: ${"a".repeat(AutomationHttp.MAX_HEAD_BYTES)}\r\n\r\n"))
    }

    @Test
    fun `response carries status, json type and exact length`() {
        val out = ByteArrayOutputStream()
        AutomationHttp.writeResponse(out, HttpResponse(202, """{"status":"é"}"""))
        val text = out.toString(Charsets.UTF_8.name())
        assertTrue(text.startsWith("HTTP/1.1 202 Accepted\r\n"))
        assertTrue(text.contains("Content-Length: 15\r\n"))
        assertTrue(text.endsWith("""{"status":"é"}"""))
    }
}
