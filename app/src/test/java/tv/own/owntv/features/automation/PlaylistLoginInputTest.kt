package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Login fields on add and edit: blank ones would keep the stored value, so they are refused. */
class PlaylistLoginInputTest {
    private val blanks = listOf(" ", "\t", " \t\n ")

    private fun patch(field: String, value: String) = PlaylistPatchRequest.parse(JSONObject().put(field, value))

    private fun addXtream(username: String, password: String) = AddPlaylistRequest.parse(
        JSONObject().put("type", "xtream").put("name", "X").put("server", "http://panel.example.com")
            .put("username", username).put("password", password).toString(),
    )

    @Test
    fun `an edit with a blank password, username or mac is refused`() {
        blanks.forEach { blank ->
            assertEquals(ParsedPatch.Invalid("password cannot be blank"), patch("password", blank))
            assertEquals(ParsedPatch.Invalid("username cannot be blank"), patch("username", blank))
            assertTrue(patch("mac", blank) is ParsedPatch.Invalid)
        }
        assertTrue(patch("password", "") is ParsedPatch.Invalid)
        assertTrue(patch("password", " p ") is ParsedPatch.Ok)
    }

    @Test
    fun `adding with a blank password is refused`() {
        blanks.forEach { blank ->
            assertEquals(ParsedAdd.Invalid("password cannot be blank"), addXtream("me", blank))
            assertTrue(addXtream(blank, "pw") is ParsedAdd.Invalid)
        }
        assertTrue(addXtream("me", "pw") is ParsedAdd.Ok)
    }

    @Test
    fun `a username is capped on add and edit`() {
        val longest = "u".repeat(ApiValues.MAX_USERNAME)
        val tooLong = longest + "u"
        val reason = "username must be up to ${ApiValues.MAX_USERNAME} characters"
        assertTrue(patch("username", longest) is ParsedPatch.Ok)
        assertEquals(ParsedPatch.Invalid(reason), patch("username", tooLong))
        assertTrue(addXtream(longest, "pw") is ParsedAdd.Ok)
        assertEquals(ParsedAdd.Invalid(reason), addXtream(tooLong, "pw"))
    }
}
