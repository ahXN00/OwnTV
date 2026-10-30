package tv.own.owntv.features.automation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ProfileEntity

class ProfileRoutesTest {
    private val backend = FakeProfiles()
    private val client = ApiTestClient(profileRoutes(backend))

    @Test
    fun `GET profiles lists each profile and never a PIN hash`() {
        val response = client.call("GET", "/api/v1/profiles")
        assertEquals(200, response.status)
        val first = JSONObject(response.body).getJSONArray("profiles").getJSONObject(0)
        assertEquals(setOf("id", "name", "kids", "hasPin", "active"), first.keys().asSequence().toSet())
        assertTrue(first.getBoolean("hasPin"))
        assertTrue(!response.body.contains(HASH))
    }

    @Test
    fun `adding a profile hands the backend the trimmed name, kids and avatar`() {
        val json = client.json("POST", "/api/v1/profiles", """{"name":" Family ","kids":true,"avatar":3}""")
        assertEquals("Family", json.getString("name"))
        assertEquals(listOf(NewProfile("Family", kids = true, avatar = 3)), backend.added)
    }

    @Test
    fun `adding a profile with a name already taken is a conflict`() {
        backend.refusal = ProfileAdded.Taken
        val response = client.call("POST", "/api/v1/profiles", """{"name":"Family"}""")
        assertEquals(409, response.status)
        assertTrue(error(response).contains("Family"))
    }

    @Test
    fun `adding a profile once setup is finished is a conflict that says to add it on the TV`() {
        backend.refusal = ProfileAdded.AfterSetup
        val response = client.call("POST", "/api/v1/profiles", """{"name":"Family"}""")
        assertEquals(409, response.status)
        assertEquals("Profiles can only be added on the TV after setup", error(response))
        assertTrue(backend.added.isEmpty())
    }

    @Test
    fun `adding a profile while no device paired from the setup page is a conflict`() {
        backend.refusal = ProfileAdded.SetupClosed
        val response = client.call("POST", "/api/v1/profiles", """{"name":"Family"}""")
        assertEquals(409, response.status)
        assertTrue(error(response).contains("Set up by Automation is not open"))
        assertTrue(backend.added.isEmpty())
    }

    @Test
    fun `adding refuses a PIN, unknown fields and bad values before the backend`() {
        val bodies = listOf(
            """{"name":"Family","pin":"1234"}""", """{"name":"Family","pinHash":"x"}""", "{}", """{"name":"  "}""",
            """{"name":5}""", """{"name":"${"a".repeat(81)}"}""", """{"name":"a\nb"}""", """{"name":"A","kids":"yes"}""",
            """{"name":"A","avatar":10}""", """{"name":"A","avatar":-2}""", """{"name":"A","avatar":1.5}""", "[1]", "not json",
            """{"name":"A​b"}""", """{"name":"‮ylimaf"}""", """{"name":"A﻿"}""",
        )
        for (body in bodies) {
            assertEquals(body, 400, client.call("POST", "/api/v1/profiles", body).status)
        }
        assertTrue(backend.added.isEmpty())
    }

    @Test
    fun `a PIN in the body is refused by name`() {
        val response = client.call("POST", "/api/v1/profiles", """{"name":"Family","pin":"1234"}""")
        assertTrue(error(response).contains("PIN"))
    }

    @Test
    fun `using a profile answers how it went`() {
        val expected = mapOf(
            ProfileUse.CHOSEN to (200 to "chosen"),
            ProfileUse.ACTIVE to (200 to "active"),
            ProfileUse.SWITCHING to (202 to "switching"),
        )
        for ((use, answer) in expected) {
            backend.use = use
            val response = client.call("POST", "/api/v1/profiles/7/use")
            assertEquals(use.name, answer.first, response.status)
            assertEquals(answer.second, JSONObject(response.body).getString("status"))
            assertEquals(7L, JSONObject(response.body).getLong("profileId"))
        }
        assertEquals(listOf(7L, 7L, 7L), backend.used)
    }

    @Test
    fun `choosing a profile during setup while no device paired from the setup page is a conflict`() {
        backend.use = ProfileUse.SETUP_CLOSED
        val response = client.call("POST", "/api/v1/profiles/7/use")
        assertEquals(409, response.status)
        assertTrue(error(response).contains("Set up by Automation is not open"))
    }

    @Test
    fun `using a profile with a PIN is forbidden`() {
        backend.use = ProfileUse.LOCKED
        val response = client.call("POST", "/api/v1/profiles/7/use")
        assertEquals(403, response.status)
        assertTrue(error(response).contains("PIN"))
    }

    @Test
    fun `using a profile while a private screen is open is a conflict that says private`() {
        backend.use = ProfileUse.PRIVATE
        val response = client.call("POST", "/api/v1/profiles/7/use")
        assertEquals(409, response.status)
        assertTrue(error(response).contains("private"))
    }

    @Test
    fun `using an unknown profile is not found`() {
        backend.use = ProfileUse.NOT_FOUND
        val response = client.call("POST", "/api/v1/profiles/9/use")
        assertEquals(404, response.status)
        assertEquals("No profile with id 9", error(response))
    }

    @Test
    fun `the profile routes are listed with the rest`() {
        val list = client.json("GET", "/api/v1").getJSONArray("endpoints")
        val paths = (0 until list.length()).map { list.getJSONObject(it).getString("method") + " " + list.getJSONObject(it).getString("path") }
        assertTrue(paths.containsAll(listOf("GET /api/v1/profiles", "POST /api/v1/profiles", "POST /api/v1/profiles/{id}/use")))
    }

    private fun error(response: HttpResponse): String = JSONObject(response.body).getString("error")

    private class FakeProfiles : ProfileBackend {
        var refusal: ProfileAdded? = null
        var use = ProfileUse.SWITCHING
        val added = mutableListOf<NewProfile>()
        val used = mutableListOf<Long>()

        override suspend fun profiles(): JSONObject {
            val locked = ProfileEntity(id = 1, name = "Parents", avatarColor = 0, pinHash = HASH)
            return JSONObject().put("profiles", JSONArray().put(ProfileJson.of(locked, active = false)))
        }

        override suspend fun add(profile: NewProfile): ProfileAdded {
            refusal?.let { return it }
            added += profile
            val saved = ProfileEntity(id = 2, name = profile.name, avatarColor = 0, isKids = profile.kids)
            return ProfileAdded.Added(ProfileJson.of(saved, active = false))
        }

        override suspend fun use(id: Long): ProfileUse {
            used += id
            return use
        }
    }

    private companion object {
        const val HASH = "pbkdf2:secret-hash"
    }
}
