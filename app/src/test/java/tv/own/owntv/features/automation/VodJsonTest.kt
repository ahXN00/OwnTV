package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity

class VodJsonTest {
    private fun movie(posterUrl: String? = null, year: Int? = null, parsedYear: Int? = null) = MovieEntity(
        id = 12,
        sourceId = 3,
        name = "Film",
        posterUrl = posterUrl,
        year = year,
        parsedYear = parsedYear,
        streamUrl = "http://tv.example.com/movie/me/s3cret/12.mp4",
    )

    @Test
    fun `a movie shows its id, title, year, playlist and poster and never its stream`() {
        val json = VodJson.movie(movie(posterUrl = "http://img.example.com/p.jpg", year = 2020))
        assertEquals(setOf("id", "title", "year", "playlistId", "poster"), keys(json))
        assertEquals(12L, json.getLong("id"))
        assertEquals("Film", json.getString("title"))
        assertEquals(2020, json.getInt("year"))
        assertEquals(3L, json.getLong("playlistId"))
        assertFalse(json.toString().contains("s3cret"))
    }

    @Test
    fun `the year falls back to the one read from the name, else null`() {
        assertEquals(2019, VodJson.movie(movie(parsedYear = 2019)).getInt("year"))
        assertEquals(2020, VodJson.movie(movie(year = 2020, parsedYear = 2019)).getInt("year"))
        assertTrue(VodJson.movie(movie()).isNull("year"))
    }

    @Test
    fun `a poster shows only where it points, wherever the login sits in it`() {
        assertEquals("http://img.example.com/…", poster("http://img.example.com/me/s3cret/p.jpg"))
        assertEquals("http://img.example.com/…", poster("http://img.example.com/p.jpg?token=s3cret"))
        assertEquals("http://img.example.com:8080/…", poster("http://me:pw@img.example.com:8080/p.jpg"))
        assertTrue(VodJson.movie(movie(posterUrl = null)).isNull("poster"))
        assertTrue(VodJson.movie(movie(posterUrl = "  ")).isNull("poster"))
    }

    @Test
    fun `shows are listed under series with the movie item shape`() {
        val show = SeriesEntity(id = 7, sourceId = 3, name = "Show", posterUrl = "https://img.example.com/s.jpg?k=1", parsedYear = 2001)
        val item = VodJson.series(listOf(show)).getJSONArray("series").getJSONObject(0)
        assertEquals(setOf("id", "title", "year", "playlistId", "poster"), keys(item))
        assertEquals("Show", item.getString("title"))
        assertEquals(2001, item.getInt("year"))
        assertEquals("https://img.example.com/…", item.getString("poster"))
        assertEquals(1, VodJson.movies(listOf(movie())).getJSONArray("movies").length())
    }

    @Test
    fun `episodes show season, number, title and id under their show`() {
        val episode = EpisodeEntity(id = 70, seriesId = 7, seasonNumber = 1, episodeNumber = 2, name = "Pilot", streamUrl = "http://tv.example.com/s3cret/70.mkv")
        val json = VodJson.episodes(7, listOf(episode))
        assertEquals(setOf("seriesId", "episodes"), keys(json))
        assertEquals(7L, json.getLong("seriesId"))
        val item = json.getJSONArray("episodes").getJSONObject(0)
        assertEquals(setOf("id", "season", "episode", "title"), keys(item))
        assertEquals(70L, item.getLong("id"))
        assertEquals(1, item.getInt("season"))
        assertEquals(2, item.getInt("episode"))
        assertEquals("Pilot", item.getString("title"))
        assertFalse(json.toString().contains("s3cret"))
    }

    private fun poster(url: String): String = VodJson.movie(movie(posterUrl = url)).getString("poster")

    private fun keys(json: JSONObject): Set<String> = json.keys().asSequence().toSet()
}
