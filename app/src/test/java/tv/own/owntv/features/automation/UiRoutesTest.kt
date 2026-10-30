package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiRoutesTest {
    private val backend = FakeUi()
    private val client = ApiTestClient(uiRoutes(backend))

    @Test
    fun `GET ui answers with what the backend shows`() {
        backend.state = JSONObject().put("inFront", false).put("screenOn", true)
        val json = client.json("GET", "/api/v1/ui")
        assertFalse(json.getBoolean("inFront"))
        assertTrue(json.getBoolean("screenOn"))
    }

    @Test
    fun `navigate opens a section in any letter case and names it back`() {
        val response = client.call("POST", "/api/v1/ui/navigate", """{"to":" Movies "}""")
        assertEquals(response.body, 202, response.status)
        assertEquals("movies", JSONObject(response.body).getString("to"))
        assertEquals(listOf(NavTarget.MOVIES), backend.sent)
    }

    @Test
    fun `navigate refuses a missing, blank, unknown or settings section and a body that is not an object`() {
        val bodies = listOf("{}", """{"to":""}""", """{"to":"  "}""", """{"to":"kitchen"}""", """{"to":"Settings"}""", """{"to":5}""", "[1]", "not json")
        for (body in bodies) {
            assertEquals(body, 400, client.call("POST", "/api/v1/ui/navigate", body).status)
        }
        assertTrue(backend.sent.isEmpty())
    }

    @Test
    fun `settings is refused by name`() {
        val response = client.call("POST", "/api/v1/ui/navigate", """{"to":"settings"}""")
        assertTrue(error(response).contains("Settings"))
    }

    @Test
    fun `a blocked app, a private screen and no open profile are 409 and only the last two say private`() {
        backend.result = NavResult.BLOCKED
        val blocked = client.call("POST", "/api/v1/ui/navigate", """{"to":"home"}""")
        assertEquals(409, blocked.status)
        assertFalse(error(blocked).contains("private"))

        backend.result = NavResult.PRIVATE
        val privateScreen = client.call("POST", "/api/v1/ui/navigate", """{"to":"home"}""")
        assertEquals(409, privateScreen.status)
        assertTrue(error(privateScreen).contains("private"))
        assertTrue(error(privateScreen).contains("PINs"))

        backend.result = NavResult.NO_PROFILE
        val noProfile = client.call("POST", "/api/v1/ui/navigate", """{"to":"home"}""")
        assertEquals(409, noProfile.status)
        assertEquals("No profile is open on the TV; pick one there first (private)", error(noProfile))
    }

    private fun error(response: HttpResponse): String = JSONObject(response.body).getString("error")

    private class FakeUi : UiBackend {
        var state = JSONObject()
        var result = NavResult.SENT
        val sent = mutableListOf<NavTarget>()

        override suspend fun state() = state
        override suspend fun navigate(target: NavTarget): NavResult {
            sent += target
            return result
        }
    }
}
