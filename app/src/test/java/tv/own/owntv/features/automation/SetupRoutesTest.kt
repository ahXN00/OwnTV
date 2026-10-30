package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRoutesTest {
    private val backend = FakeSetup()
    private val client = ApiTestClient(setupRoutes(backend, setOf("", "en-US", "de")))

    @Test
    fun `GET setup says whether setup is done and how many profiles there are`() {
        val json = client.json("GET", "/api/v1/setup")
        assertEquals(false, json.getBoolean("done"))
        assertEquals(1, json.getInt("profiles"))
    }

    @Test
    fun `finish hands the backend the values it read, the language in the wizard's letter case`() {
        val json = client.json("POST", "/api/v1/setup/finish", """{"language":"EN-us","uiZoom":95,"fontSize":110}""")
        assertTrue(json.getBoolean("done"))
        assertEquals(listOf(SetupFinish("en-US", 95, 110)), backend.finished)
    }

    @Test
    fun `finish with an empty body changes no setting and still finishes`() {
        assertEquals(200, client.call("POST", "/api/v1/setup/finish").status)
        assertEquals(listOf(SetupFinish()), backend.finished)
    }

    @Test
    fun `finish once setup is done is refused`() {
        backend.result = SetupFinished.AlreadyDone
        val response = client.call("POST", "/api/v1/setup/finish", "{}")
        assertEquals(409, response.status)
        assertEquals("Setup is already finished", error(response))
    }

    @Test
    fun `finish while no device paired from the setup page is refused`() {
        backend.result = SetupFinished.Closed
        val response = client.call("POST", "/api/v1/setup/finish", "{}")
        assertEquals(409, response.status)
        assertTrue(error(response).contains("Set up by Automation is not open"))
    }

    @Test
    fun `finish with no profile to open is refused and says how to add one`() {
        backend.result = SetupFinished.NoProfile
        val response = client.call("POST", "/api/v1/setup/finish", "{}")
        assertEquals(409, response.status)
        assertTrue(error(response).contains("POST profiles"))
    }

    @Test
    fun `finish refuses bad values before it reaches the backend`() {
        val bodies = listOf(
            """{"language":"xx-YY"}""", """{"language":5}""", """{"uiZoom":45}""", """{"uiZoom":155}""",
            """{"uiZoom":92}""", """{"uiZoom":"90"}""", """{"fontSize":55}""", """{"fontSize":145}""",
            """{"fontSize":101}""", """{"theme":"dark"}""", """{"pin":"1234"}""", "[1]", "not json",
        )
        for (body in bodies) {
            assertEquals(body, 400, client.call("POST", "/api/v1/setup/finish", body).status)
        }
        assertTrue(backend.finished.isEmpty())
    }

    @Test
    fun `the setup routes are listed with the rest`() {
        val paths = client.json("GET", "/api/v1").getJSONArray("endpoints").let { list ->
            (0 until list.length()).map { list.getJSONObject(it).getString("path") }
        }
        assertTrue(paths.containsAll(listOf("/api/v1/setup", "/api/v1/setup/finish")))
    }

    private fun error(response: HttpResponse): String = JSONObject(response.body).getString("error")

    private class FakeSetup : SetupBackend {
        var result: SetupFinished = SetupFinished.Done(JSONObject().put("done", true).put("profileId", 1))
        val finished = mutableListOf<SetupFinish>()

        override suspend fun state(): JSONObject = JSONObject().put("done", false).put("profiles", 1)
        override suspend fun finish(finish: SetupFinish): SetupFinished {
            finished += finish
            return result
        }
    }
}
