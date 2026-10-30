package tv.own.owntv.features.automation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Channels, settings, cache and logs: the routes whose checks are only on query and body values. */
class MaintenanceRoutesTest {
    private var lastSearch: Triple<String, Long?, Int>? = null
    private var imported: JSONObject? = null
    private var cleared: Set<CacheTarget>? = null
    private var logLines = 0
    private val settings = JSONObject().put("vod_buffer_secs", 10).put("hw_decoding", true).put("proxy_host", "10.0.0.9")

    private val channels = object : ChannelBackend {
        override suspend fun search(query: String, playlistId: Long?, limit: Int): JSONObject {
            lastSearch = Triple(query, playlistId, limit)
            return JSONObject()
        }
        override suspend fun play(id: Long) = when (id) {
            1L -> PlayResult.Started
            2L -> PlayResult.Blocked
            4L -> PlayResult.Private
            5L -> PlayResult.NoProfile
            else -> PlayResult.NotFound
        }
    }
    private val settingsBackend = object : SettingsBackend {
        override suspend fun export() = JSONObject(settings.toString())
        override suspend fun import(changes: JSONObject) {
            imported = changes
            if (changes.optInt("vod_buffer_secs", -1) in 1..60) settings.put("vod_buffer_secs", changes.getInt("vod_buffer_secs"))
        }
    }
    private val logs = object : LogBackend {
        override suspend fun appLog(lines: Int): JSONObject {
            logLines = lines
            return JSONObject().put("lines", JSONArray())
        }
        override suspend fun playbackErrors() = JSONObject()
        override suspend fun clearPlaybackErrors() = Unit
        override suspend fun lastCrash() = JSONObject()
    }
    private val client = ApiTestClient(
        channelRoutes(channels) + settingsRoutes(settingsBackend) + cacheRoutes { cleared = it } + logRoutes(logs),
    )

    @Test
    fun `channel search reads its query with defaults and limits`() {
        client.json("GET", "/api/v1/channels", query = mapOf("q" to " news "))
        assertEquals(Triple("news", null, 50), lastSearch)
        client.json("GET", "/api/v1/channels", query = mapOf("playlist" to "1", "limit" to "500"))
        assertEquals(Triple("", 1L, 500), lastSearch)
        assertEquals(400, client.call("GET", "/api/v1/channels", query = mapOf("limit" to "501")).status)
        assertEquals(400, client.call("GET", "/api/v1/channels", query = mapOf("playlist" to "x")).status)
    }

    @Test
    fun `tuning reports started, blocked, private, no-profile and unknown channels`() {
        assertEquals(202, client.call("POST", "/api/v1/channels/1/play").status)
        val blocked = client.call("POST", "/api/v1/channels/2/play")
        assertEquals(409, blocked.status)
        assertFalse(blocked.body.contains("private"))
        assertEquals(404, client.call("POST", "/api/v1/channels/3/play").status)
        val privateScreen = client.call("POST", "/api/v1/channels/4/play")
        assertEquals(409, privateScreen.status)
        assertTrue(privateScreen.body.contains("private"))
        val noProfile = client.call("POST", "/api/v1/channels/5/play")
        assertEquals(409, noProfile.status)
        assertEquals(ApiReply.NO_PROFILE, JSONObject(noProfile.body).getString("error"))
    }

    @Test
    fun `settings patch says which keys took`() {
        val result = client.json("PATCH", "/api/v1/settings", """{"vod_buffer_secs":20,"hw_decoding":"yes"}""")
        assertEquals("""["vod_buffer_secs"]""", result.getJSONArray("applied").toString())
        assertEquals("""["hw_decoding"]""", result.getJSONArray("notApplied").toString())
        assertEquals(20, client.json("GET", "/api/v1/settings").getInt("vod_buffer_secs"))
        assertEquals(400, client.call("PATCH", "/api/v1/settings", "{}").status)
    }

    @Test
    fun `settings patch with a key outside the allow-list changes nothing and names it`() {
        val response = client.call("PATCH", "/api/v1/settings", """{"vod_buffer_secs":20,"proxy_host":"10.0.0.8","made_up":1}""")
        assertEquals(400, response.status)
        val error = JSONObject(response.body).getString("error")
        assertTrue(error, error.contains("proxy_host") && error.contains("made_up") && !error.contains("vod_buffer_secs"))
        assertNull(imported)
        assertEquals(10, settings.getInt("vod_buffer_secs"))
    }

    @Test
    fun `settings read leaves out keys the API cannot change`() {
        val shown = client.json("GET", "/api/v1/settings")
        assertFalse(shown.has("proxy_host"))
        assertTrue(shown.has("vod_buffer_secs") && shown.has("hw_decoding"))
    }

    @Test
    fun `settings diff compares numbers and lists by value`() {
        val now = JSONObject().put("a", 2.0).put("b", JSONArray(listOf("x", "y"))).put("c", "on")
        val diff = SettingsDiff.of(JSONObject().put("a", 2).put("b", JSONArray(listOf("y", "x"))).put("c", "off"), now)
        assertEquals(2, diff.getJSONArray("applied").length())
        assertEquals("""["c"]""", diff.getJSONArray("notApplied").toString())
    }

    @Test
    fun `cache clear defaults to everything and rejects unknown caches`() {
        client.json("POST", "/api/v1/cache/clear")
        assertEquals(CacheTarget.entries.toSet(), cleared)
        client.json("POST", "/api/v1/cache/clear", """{"targets":["images"]}""")
        assertEquals(setOf(CacheTarget.IMAGES), cleared)
        assertEquals(400, client.call("POST", "/api/v1/cache/clear", """{"targets":["timeshift"]}""").status)
        assertEquals(400, client.call("POST", "/api/v1/cache/clear", """{"targets":[]}""").status)
    }

    @Test
    fun `app log line count is bounded`() {
        client.json("GET", "/api/v1/logs/app")
        assertEquals(500, logLines)
        assertEquals(400, client.call("GET", "/api/v1/logs/app", query = mapOf("lines" to "0")).status)
        assertEquals(400, client.call("GET", "/api/v1/logs/app", query = mapOf("lines" to "5001")).status)
    }
}
