package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** User agents and referers are sent as HTTP headers, so the add, edit and guide parsers all check them. */
class HeaderValueTest {
    private val tooLong = "a".repeat(HeaderValue.MAX_LENGTH + 1)
    private val bad = listOf(tooLong, "VLC\r\nX-Injected: 1", "VLC\nnext", "VLC\u0000", "VLC\tx", "VLC\u007f")

    @Test
    fun `normal values and absent ones pass`() {
        assertNull(HeaderValue.problem("userAgent", null))
        assertNull(HeaderValue.problem("userAgent", ""))
        assertNull(HeaderValue.problem("userAgent", "a".repeat(HeaderValue.MAX_LENGTH)))
        assertNull(HeaderValue.problem("userAgent", "Mozilla/5.0 (Linux; Android 11) VLC/3.0.20 é"))
    }

    @Test
    fun `long values and control characters are refused with the field name`() {
        bad.forEach { assertTrue(it, HeaderValue.problem("httpReferer", it).orEmpty().startsWith("httpReferer ")) }
    }

    @Test
    fun `add, edit and guide bodies refuse bad header values`() {
        (bad + "VLC/3.0").forEach { value ->
            val add = JSONObject().put("type", "m3u").put("name", "A").put("url", "http://x.tv/a.m3u").put("userAgent", value)
            assertEquals(value, value in bad, AddPlaylistRequest.parse(add.toString()) is ParsedAdd.Invalid)
            listOf("userAgent", "httpReferer").forEach { field ->
                assertEquals(value, value in bad, PlaylistPatchRequest.parse(JSONObject().put(field, value)) is ParsedPatch.Invalid)
            }
            val guide = JSONObject().put("url", "http://x.tv/epg.xml").put("userAgent", value)
            assertEquals(value, value in bad, GuideRequest.parse(guide, adding = true) is ParsedGuide.Invalid)
        }
    }
}
