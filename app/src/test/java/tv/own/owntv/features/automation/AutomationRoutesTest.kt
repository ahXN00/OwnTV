package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.PlaylistAutoRefresh

class AutomationRoutesTest {
    private val backend = FakePlaylists()
    private val client = ApiTestClient(playlistRoutes(backend))

    @Test
    fun `everything but pairing needs a key`() {
        assertEquals(401, client.call("GET", "/api/v1/status", key = null).status)
        assertEquals(401, client.call("GET", "/api/v1/playlists", key = "nope").status)
        assertEquals(401, client.call("GET", "/api/v1", key = null).status)
        assertEquals(200, client.call("GET", "/api/v1/status").status)
    }

    @Test
    fun `the root lists every endpoint with and without a trailing slash`() {
        val endpoints = client.json("GET", "/api/v1").getJSONArray("endpoints")
        val paths = (0 until endpoints.length()).map { endpoints.getJSONObject(it).let { e -> "${e.getString("method")} ${e.getString("path")}" } }
        assertTrue(paths.contains("POST /api/v1/pair"))
        assertTrue(paths.contains("PATCH /api/v1/playlists/{id}"))
        assertTrue(paths.contains("DELETE /api/v1/playlists/{id}/sync"))
        assertEquals(200, client.call("GET", "/api/v1/").status)
    }

    @Test
    fun `add starts an import and refuses a second one while it runs`() {
        val body = """{"type":"m3u","name":"Test","url":"http://example.com/list.m3u","refresh":"HOURS_6","default":true}"""
        assertEquals(202, client.call("POST", "/api/v1/playlists", body).status)
        val added = backend.imports.single() as AddPlaylist.M3u
        assertEquals("http://example.com/list.m3u", added.url)
        assertEquals(PlaylistAutoRefresh.HOURS_6, added.refresh.mode)
        assertEquals(true, added.makeDefault)
        backend.busy = true
        assertEquals(409, client.call("POST", "/api/v1/playlists", body).status)
    }

    @Test
    fun `bad add bodies are rejected with a reason`() {
        listOf(
            "not json",
            """{"type":"m3u","url":"http://example.com/a.m3u"}""",
            """{"type":"m3u","name":"A","url":"file:///sdcard/a.m3u"}""",
            """{"type":"m3u","name":"A","url":"http://example.com/a.m3u","refresh":"SOMETIMES"}""",
            """{"type":"xtream","name":"A","server":"http://x.tv"}""",
            """{"type":"stalker","name":"A"}""",
        ).forEach { body ->
            val response = client.call("POST", "/api/v1/playlists", body)
            assertEquals(body, 400, response.status)
            assertTrue(JSONObject(response.body).has("error"))
        }
        assertTrue(backend.imports.isEmpty())
    }

    @Test
    fun `edits reach the backend and bad ones do not`() {
        assertEquals(200, client.call("PATCH", "/api/v1/playlists/7", """{"name":"Renamed","refresh":"HOURS_12","syncMovies":false}""").status)
        val patch = backend.patches.single().second
        assertEquals("Renamed", patch.name)
        assertEquals(PlaylistAutoRefresh.HOURS_12, patch.refresh?.mode)
        assertEquals(false, patch.syncMovies)
        assertEquals(400, client.call("PATCH", "/api/v1/playlists/7", """{"colour":"red"}""").status)
        assertEquals(400, client.call("PATCH", "/api/v1/playlists/7", "{}").status)
        assertEquals(404, client.call("PATCH", "/api/v1/playlists/99", """{"name":"X"}""").status)
        assertEquals(400, client.call("PATCH", "/api/v1/playlists/7", """{"mac":"not-a-mac"}""").status)
        assertEquals(1, backend.patches.size)
    }

    @Test
    fun `an edit the backend refuses answers 400 with its reason`() {
        val response = client.call("PATCH", "/api/v1/playlists/8", """{"url":"http://other.example.com"}""")
        assertEquals(400, response.status)
        assertEquals("send password again", JSONObject(response.body).getString("error"))
    }

    @Test
    fun `sync, cancel and delete reach the backend by id`() {
        assertEquals(202, client.call("POST", "/api/v1/playlists/7/sync", """{"clean":true}""").status)
        assertEquals(listOf(7L to true), backend.syncs)
        assertEquals(200, client.call("GET", "/api/v1/playlists/7/sync").status)
        assertEquals(202, client.call("DELETE", "/api/v1/playlists/7/sync").status)
        assertEquals(202, client.call("DELETE", "/api/v1/playlists/7").status)
        assertEquals(listOf(7L), backend.deletes)
        assertEquals(404, client.call("DELETE", "/api/v1/playlists/99").status)
    }

    @Test
    fun `with no profile open the writes answer 409 private and reach nothing`() {
        backend.profileOpen = false
        val writes = listOf(
            Triple("POST", "/api/v1/playlists", """{"type":"m3u","name":"Test","url":"http://example.com/list.m3u"}"""),
            Triple("PATCH", "/api/v1/playlists/7", """{"name":"Renamed"}"""),
            Triple("DELETE", "/api/v1/playlists/7", ""),
            Triple("POST", "/api/v1/playlists/7/sync", "{}"),
            Triple("DELETE", "/api/v1/playlists/7/sync", ""),
        )
        for ((method, path, body) in writes) {
            val response = client.call(method, path, body)
            assertEquals("$method $path", 409, response.status)
            assertEquals(ApiReply.NO_PROFILE, JSONObject(response.body).getString("error"))
        }
        assertTrue(ApiReply.NO_PROFILE.contains("private"))
        assertTrue(backend.imports.isEmpty() && backend.patches.isEmpty() && backend.syncs.isEmpty())
        assertTrue(backend.deletes.isEmpty() && backend.cancels.isEmpty())
        assertEquals(200, client.call("GET", "/api/v1/status").status)
        assertEquals(200, client.call("GET", "/api/v1/playlists").status)
    }

    @Test
    fun `unknown paths and methods answer 404 and 405`() {
        assertEquals(200, client.call("GET", "/api/v1/playlists/7").status)
        assertEquals(404, client.call("GET", "/api/v1/playlists/99").status)
        assertEquals(404, client.call("GET", "/api/v1/playlists/abc").status)
        assertEquals(405, client.call("PUT", "/api/v1/playlists/7").status)
        assertEquals(404, client.call("GET", "/api/v1/nothing").status)
        assertEquals(405, client.call("GET", "/api/v1/pair").status)
    }

    private class FakePlaylists : PlaylistBackend {
        val imports = mutableListOf<AddPlaylist>()
        val patches = mutableListOf<Pair<Long, PlaylistPatch>>()
        val syncs = mutableListOf<Pair<Long, Boolean>>()
        val deletes = mutableListOf<Long>()
        val cancels = mutableListOf<Long>()
        var busy = false
        var profileOpen = true

        override suspend fun hasProfile() = profileOpen
        override suspend fun status() = JSONObject().put("app", "OwnTV")
        override suspend fun playlists() = JSONObject()
        override suspend fun playlist(id: Long) = if (id == 7L) JSONObject().put("id", 7) else null
        override fun startImport(request: AddPlaylist): Boolean {
            if (busy) return false
            imports += request
            return true
        }
        override suspend fun update(id: Long, patch: PlaylistPatch): PlaylistUpdate = when (id) {
            7L -> PlaylistUpdate.Saved.also { patches += id to patch }
            8L -> PlaylistUpdate.Refused("send password again")
            else -> PlaylistUpdate.NotFound
        }
        override suspend fun resync(id: Long, clean: Boolean) = (id == 7L).also { if (it) syncs += id to clean }
        override suspend fun syncState(id: Long) = if (id == 7L) JSONObject().put("state", "idle") else null
        override suspend fun cancelSync(id: Long) = (id == 7L).also { if (it) cancels += id }
        override suspend fun delete(id: Long) = (id == 7L).also { if (it) deletes += id }
    }
}
