package tv.own.owntv.features.automation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodRoutesTest {
    private val backend = FakeVod()
    private val client = ApiTestClient(vodRoutes(backend))

    @Test
    fun `movie and series searches read their query with defaults and limits`() {
        for (path in listOf("/api/v1/movies", "/api/v1/series")) {
            client.json("GET", path, query = mapOf("q" to " news "))
            assertEquals(path, Search("news", null, 20), backend.lastSearch)
            client.json("GET", path, query = mapOf("playlist" to "3", "limit" to "50"))
            assertEquals(path, Search("", 3, 50), backend.lastSearch)
        }
    }

    @Test
    fun `a search refuses a limit out of range and a playlist that is not an id`() {
        for (path in listOf("/api/v1/movies", "/api/v1/series")) {
            for (limit in listOf("0", "51", "x")) {
                val response = client.call("GET", path, query = mapOf("limit" to limit))
                assertEquals(400, response.status)
                assertEquals("limit must be 1 to 50", error(response))
            }
            val response = client.call("GET", path, query = mapOf("playlist" to "mine"))
            assertEquals(400, response.status)
            assertEquals("playlist must be a playlist id", error(response))
        }
    }

    @Test
    fun `searches answer with what the backend finds`() {
        assertEquals(1, client.json("GET", "/api/v1/movies").getJSONArray("movies").length())
        assertEquals(1, client.json("GET", "/api/v1/series").getJSONArray("series").length())
    }

    @Test
    fun `episodes of a known show, and 404 for an unknown one`() {
        val json = client.json("GET", "/api/v1/series/7/episodes")
        assertEquals(7L, json.getLong("seriesId"))
        val unknown = client.call("GET", "/api/v1/series/8/episodes")
        assertEquals(404, unknown.status)
        assertEquals("No series with id 8", error(unknown))
    }

    @Test
    fun `playing a movie or an episode reports playing, blocked, private and unknown`() {
        for ((path, what) in listOf("movies" to "movie", "episodes" to "episode")) {
            val started = client.call("POST", "/api/v1/$path/1/play")
            assertEquals(202, started.status)
            assertEquals("playing", JSONObject(started.body).getString("status"))
            val blocked = client.call("POST", "/api/v1/$path/2/play")
            assertEquals(409, blocked.status)
            assertFalse(error(blocked).contains("private"))
            val unknown = client.call("POST", "/api/v1/$path/3/play")
            assertEquals(404, unknown.status)
            assertEquals("No $what with id 3", error(unknown))
            val privateScreen = client.call("POST", "/api/v1/$path/4/play")
            assertEquals(409, privateScreen.status)
            assertTrue(error(privateScreen).contains("private"))
            val noProfile = client.call("POST", "/api/v1/$path/5/play")
            assertEquals(409, noProfile.status)
            assertEquals(ApiReply.NO_PROFILE, error(noProfile))
        }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), backend.movies)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), backend.episodes)
    }

    @Test
    fun `opening a show reports opening, blocked, private, no profile and unknown`() {
        val sent = client.call("POST", "/api/v1/series/1/open")
        assertEquals(202, sent.status)
        assertEquals("opening", JSONObject(sent.body).getString("status"))

        val blocked = client.call("POST", "/api/v1/series/2/open")
        assertEquals(409, blocked.status)
        assertFalse(error(blocked).contains("private"))

        val privateScreen = client.call("POST", "/api/v1/series/4/open")
        assertEquals(409, privateScreen.status)
        assertTrue(error(privateScreen).contains("private"))

        val noProfile = client.call("POST", "/api/v1/series/5/open")
        assertEquals(409, noProfile.status)
        assertEquals(ApiReply.NO_PROFILE, error(noProfile))

        val unknown = client.call("POST", "/api/v1/series/3/open")
        assertEquals(404, unknown.status)
        assertEquals("No series with id 3", error(unknown))
    }

    private fun error(response: HttpResponse): String = JSONObject(response.body).getString("error")

    private data class Search(val query: String, val playlistId: Long?, val limit: Int)

    /** Id 1 works, 2 is blocked, 3 is unknown, 4 is refused over a private screen, 5 with no profile open. */
    private class FakeVod : VodBackend {
        var lastSearch: Search? = null
        val movies = mutableListOf<Long>()
        val episodes = mutableListOf<Long>()

        override suspend fun movies(query: String, playlistId: Long?, limit: Int): JSONObject {
            lastSearch = Search(query, playlistId, limit)
            return JSONObject().put("movies", JSONArray().put(JSONObject().put("id", 1)))
        }

        override suspend fun series(query: String, playlistId: Long?, limit: Int): JSONObject {
            lastSearch = Search(query, playlistId, limit)
            return JSONObject().put("series", JSONArray().put(JSONObject().put("id", 1)))
        }

        override suspend fun episodes(seriesId: Long): JSONObject? =
            if (seriesId == 7L) JSONObject().put("seriesId", 7).put("episodes", JSONArray()) else null

        override suspend fun playMovie(id: Long): PlayResult = play(id).also { movies += id }

        override suspend fun playEpisode(id: Long): PlayResult = play(id).also { episodes += id }

        override suspend fun openSeries(id: Long): NavResult? = when (id) {
            1L -> NavResult.SENT
            2L -> NavResult.BLOCKED
            4L -> NavResult.PRIVATE
            5L -> NavResult.NO_PROFILE
            else -> null
        }

        private fun play(id: Long): PlayResult = when (id) {
            1L -> PlayResult.Started
            2L -> PlayResult.Blocked
            4L -> PlayResult.Private
            5L -> PlayResult.NoProfile
            else -> PlayResult.NotFound
        }
    }
}
