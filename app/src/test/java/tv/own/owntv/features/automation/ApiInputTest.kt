package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Route patterns, typed body fields and the edit parser. */
class ApiInputTest {
    @Test
    fun `patterns match whole numbers, negatives included`() {
        val route = ApiRoute("GET", "guide/{id}/sync", "") { ApiReply.ok(JSONObject()) }
        assertEquals(mapOf("id" to -12L), route.match(listOf("guide", "-12", "sync")))
        assertNull(route.match(listOf("guide", "x", "sync")))
        assertNull(route.match(listOf("guide", "1")))
        assertEquals("/api/v1/guide/{id}/sync", route.describe().getString("path"))
        assertEquals(emptyMap<String, Long>(), ApiRoute("GET", "", "") { ApiReply.ok(JSONObject()) }.match(listOf("")))
    }

    @Test
    fun `typed fields keep the first type error`() {
        val fields = JsonFields(JSONObject("""{"name":5,"on":"yes","n":2.5,"list":["a",1]}"""))
        assertNull(fields.string("name"))
        assertNull(fields.boolean("on"))
        assertTrue(fields.error!!.contains("name"))
        assertNull(JsonFields(JSONObject("""{"n":2.5}""")).int("n"))
        assertNull(JsonFields(JSONObject("""{"list":["a",1]}""")).strings("list"))
        assertEquals(listOf("a", "b"), JsonFields(JSONObject("""{"list":["a","b"]}""")).strings("list"))
    }

    @Test
    fun `edit parser refuses masked addresses and empty credentials`() {
        fun reason(body: String) = (PlaylistPatchRequest.parse(JSONObject(body)) as? ParsedPatch.Invalid)?.reason
        assertTrue(reason("""{"url":"http://x.tv/get.php?username=a&password=***"}""")!!.contains("masked"))
        assertTrue(reason("""{"url":"http://a:***@x.tv/list.m3u"}""")!!.contains("masked"))
        assertTrue(reason("""{"epgUrl":"http://x.tv/epg.xml?token=***"}""")!!.contains("masked"))
        assertTrue(reason("""{"url":"http://x.tv/live/•••/•••/1.ts"}""").orEmpty().contains("masked"))
        assertTrue(reason("""{"password":""}""") != null)
        assertTrue(reason("""{"refresh":"WEEKLY"}""") != null)
        assertTrue(reason("""{"epgUrl":"not a url"}""") != null)
        assertNull(reason("""{"epgUrl":""}"""))
        assertNull(reason("""{"name":"A","default":true,"preferHls":false}"""))
    }

    @Test
    fun `edit parsers refuse an address the API cut down when it showed it`() {
        fun reason(body: String) = (PlaylistPatchRequest.parse(JSONObject(body)) as? ParsedPatch.Invalid)?.reason.orEmpty()
        val cut = "url is the shortened or masked copy the API shows; send the full address"
        assertEquals(cut, reason("""{"url":"http://x.tv:8080/…"}"""))
        assertTrue(reason("""{"epgUrl":"http://x.tv/…"}""").startsWith("epgUrl is the shortened"))
        assertTrue(reason("""{"httpReferer":"https://x.tv/…"}""").startsWith("httpReferer is the shortened"))
        assertEquals(ParsedGuide.Invalid(cut), GuideRequest.parse(JSONObject("""{"url":"http://g.tv/…"}"""), adding = false))
        assertTrue(PlaylistPatchRequest.parse(JSONObject("""{"url":"http://x.tv:8080"}""")) is ParsedPatch.Ok)
    }

    @Test
    fun `adding a playlist refuses an address the API cut down when it showed it`() {
        fun reason(vararg fields: Pair<String, String>): String {
            val body = JSONObject().put("name", "A").put("username", "me").put("password", "pw")
            fields.forEach { (key, value) -> body.put(key, value) }
            return (AddPlaylistRequest.parse(body.toString()) as? ParsedAdd.Invalid)?.reason.orEmpty()
        }
        val cut = "url is the shortened or masked copy the API shows; send the full address"
        assertEquals(cut, reason("type" to "m3u", "url" to "http://x.tv:8080/…"))
        assertEquals(cut, reason("type" to "m3u", "url" to "http://x.tv/get.php?username=me&password=***"))
        assertTrue(reason("type" to "m3u", "url" to "http://x.tv/a.m3u", "epgUrl" to "http://x.tv/…").startsWith("epgUrl is the shortened"))
        assertTrue(reason("type" to "xtream", "server" to "http://…").startsWith("server is the shortened"))
        assertEquals("", reason("type" to "m3u", "url" to "http://x.tv:8080"))
        assertEquals("", reason("type" to "xtream", "server" to "http://x.tv:8080"))
    }
}
