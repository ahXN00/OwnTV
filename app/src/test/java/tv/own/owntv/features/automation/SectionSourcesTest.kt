package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType

class SectionSourcesTest {
    private val all = playlist(1)
    private val liveOnly = playlist(2, syncMovies = false, syncSeries = false)
    private val seriesOnly = playlist(3, syncLive = false, syncMovies = false)
    private val playlists = listOf(all, liveOnly, seriesOnly)

    @Test
    fun `without a playlist every playlist that keeps the section is searched`() {
        assertEquals(listOf(1L, 2L), SectionSources.of(9, playlists, MediaType.LIVE, null).sourceIds)
        assertEquals(listOf(1L), SectionSources.of(9, playlists, MediaType.MOVIE, null).sourceIds)
        assertEquals(listOf(1L, 3L), SectionSources.of(9, playlists, MediaType.SERIES, null).sourceIds)
    }

    @Test
    fun `a named playlist narrows the search, and one that does not keep the section finds nothing`() {
        assertEquals(listOf(3L), SectionSources.of(9, playlists, MediaType.SERIES, 3).sourceIds)
        assertEquals(emptyList<Long>(), SectionSources.of(9, playlists, MediaType.MOVIE, 3).sourceIds)
        assertEquals(emptyList<Long>(), SectionSources.of(9, playlists, MediaType.MOVIE, 7).sourceIds)
    }

    @Test
    fun `only the searched section stays on, so the other sections are not read`() {
        val movies = SectionSources.of(9, playlists, MediaType.MOVIE, null)
        assertEquals(9L, movies.profileId)
        assertEquals(listOf(1L), movies.movieSourceIds)
        assertEquals(emptyList<Long>(), movies.liveSourceIds)
        assertEquals(emptyList<Long>(), movies.seriesSourceIds)
        val live = SectionSources.of(9, playlists, MediaType.LIVE, null)
        assertEquals(listOf(1L, 2L), live.liveSourceIds)
        assertEquals(emptyList<Long>(), live.movieSourceIds + live.seriesSourceIds)
    }

    private fun playlist(id: Long, syncLive: Boolean = true, syncMovies: Boolean = true, syncSeries: Boolean = true) = SourceEntity(
        id = id,
        name = "List $id",
        type = SourceType.M3U,
        url = "http://lists.example.com/$id.m3u",
        syncLive = syncLive,
        syncMovies = syncMovies,
        syncSeries = syncSeries,
    )
}
