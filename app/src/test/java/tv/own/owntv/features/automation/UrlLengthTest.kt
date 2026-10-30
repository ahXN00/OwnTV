package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlLengthTest {
    /** An http address exactly [length] characters long. */
    private fun address(length: Int): String {
        val start = "http://example.com/"
        return start + "a".repeat(length - start.length)
    }

    private val longest = address(ApiValues.MAX_URL)
    private val tooLong = address(ApiValues.MAX_URL + 1)

    @Test
    fun `adding a playlist takes addresses up to the limit and refuses longer ones`() {
        val m3u = { url: String, epg: String -> JSONObject().put("type", "m3u").put("name", "A").put("url", url).put("epgUrl", epg).toString() }
        assertTrue(AddPlaylistRequest.parse(m3u(longest, longest)) is ParsedAdd.Ok)
        assertEquals(ParsedAdd.Invalid("url must be up to 4096 characters"), AddPlaylistRequest.parse(m3u(tooLong, "")))
        assertEquals(ParsedAdd.Invalid("epgUrl must be up to 4096 characters"), AddPlaylistRequest.parse(m3u(longest, tooLong)))
        val xtream = JSONObject().put("type", "xtream").put("name", "A").put("server", tooLong)
            .put("username", "me").put("password", "pw").toString()
        assertEquals(ParsedAdd.Invalid("server must be up to 4096 characters"), AddPlaylistRequest.parse(xtream))
    }

    @Test
    fun `editing a playlist takes addresses up to the limit and refuses longer ones`() {
        assertTrue(PlaylistPatchRequest.parse(JSONObject().put("url", longest).put("epgUrl", longest)) is ParsedPatch.Ok)
        assertEquals(ParsedPatch.Invalid("url must be up to 4096 characters"), PlaylistPatchRequest.parse(JSONObject().put("url", tooLong)))
        assertEquals(
            ParsedPatch.Invalid("epgUrl must be up to 4096 characters"),
            PlaylistPatchRequest.parse(JSONObject().put("epgUrl", tooLong)),
        )
    }

    @Test
    fun `a guide address up to the limit is taken and a longer one refused`() {
        assertTrue(GuideRequest.parse(JSONObject().put("url", longest), adding = true) is ParsedGuide.Ok)
        assertEquals(ParsedGuide.Invalid("url must be up to 4096 characters"), GuideRequest.parse(JSONObject().put("url", tooLong), adding = true))
        assertEquals(ParsedGuide.Invalid("url must be up to 4096 characters"), GuideRequest.parse(JSONObject().put("url", tooLong), adding = false))
    }
}
