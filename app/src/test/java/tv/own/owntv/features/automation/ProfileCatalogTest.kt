package tv.own.owntv.features.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.content.SearchResults
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType

class ProfileCatalogTest {
    private val reads = FakeCatalogReads()
    private val catalog = ProfileCatalog(reads)

    private val drama = CategoryEntity(id = 20, sourceId = 1, mediaType = MediaType.MOVIE, name = "Drama")
    private val adult = CategoryEntity(id = 21, sourceId = 1, mediaType = MediaType.MOVIE, name = "Adult")
    private val film = MovieEntity(id = 100, sourceId = 1, name = "Film", streamUrl = "http://tv.example.com/m/100", categoryId = 20)
    private val adultFilm = MovieEntity(id = 101, sourceId = 1, name = "Late film", streamUrl = "http://tv.example.com/m/101", categoryId = 21)
    private val elsewhere = MovieEntity(id = 102, sourceId = 4, name = "Other list", streamUrl = "http://tv.example.com/m/102")
    private val show = SeriesEntity(id = 200, sourceId = 1, name = "Show")
    private val episode = EpisodeEntity(id = 300, seriesId = 200, seasonNumber = 1, episodeNumber = 1, name = "Pilot", streamUrl = "http://tv.example.com/e/300")
    private val news = ChannelEntity(id = 400, sourceId = 1, name = "News", streamUrl = "http://tv.example.com/400")

    init {
        reads.playlists[ADULT] = listOf(playlist(1))
        reads.playlists[KID] = listOf(playlist(1))
        reads.kids += KID
        listOf(drama, adult).forEach { reads.categories[it.id] = it }
        listOf(film, adultFilm, elsewhere).forEach { reads.movies[it.id] = it }
        reads.shows[show.id] = show
        reads.episodes[episode.id] = episode
        reads.channels[news.id] = news
    }

    @Test
    fun `a profile sees its own playlists' items and not another's`() = runBlocking {
        assertEquals(film, catalog.movie(ADULT, film.id))
        assertNull(catalog.movie(ADULT, elsewhere.id))
        assertNull(catalog.movie(OTHER, film.id))
        assertNull(catalog.movie(ADULT, 999))
    }

    @Test
    fun `a playlist with the section switched off shows none of its items`() = runBlocking {
        reads.playlists[ADULT] = listOf(playlist(1, syncMovies = false))
        assertNull(catalog.movie(ADULT, film.id))
        assertEquals(show, catalog.show(ADULT, show.id))
    }

    @Test
    fun `a kids profile does not see adult categories`() = runBlocking {
        assertEquals(adultFilm, catalog.movie(ADULT, adultFilm.id))
        assertNull(catalog.movie(KID, adultFilm.id))
        assertEquals(film, catalog.movie(KID, film.id))
    }

    @Test
    fun `an item hidden in Customize is not found`() = runBlocking {
        reads.customized[ADULT to MediaType.MOVIE] = SectionCustomizations(hiddenItems = mapOf(CustomizeKeys.movie(film) to film.name))
        reads.customized[ADULT to MediaType.LIVE] = SectionCustomizations(hiddenItems = mapOf(CustomizeKeys.channel(news) to news.name))
        assertNull(catalog.movie(ADULT, film.id))
        assertNull(catalog.channel(ADULT, news.id))
        assertEquals(adultFilm, catalog.movie(ADULT, adultFilm.id))
        assertEquals(news, catalog.channel(KID, news.id))
    }

    @Test
    fun `an item in a category hidden in Customize is not found`() = runBlocking {
        reads.customized[ADULT to MediaType.MOVIE] = SectionCustomizations(hiddenCategories = setOf(CustomizeKeys.category(drama)))
        assertNull(catalog.movie(ADULT, film.id))
        assertEquals(film, catalog.movie(KID, film.id))
    }

    @Test
    fun `an episode is found only while its show is`() = runBlocking {
        assertEquals(ShowEpisode(show, episode), catalog.episode(ADULT, episode.id))
        reads.customized[ADULT to MediaType.SERIES] = SectionCustomizations(hiddenItems = mapOf(CustomizeKeys.series(show) to show.name))
        assertNull(catalog.show(ADULT, show.id))
        assertNull(catalog.episode(ADULT, episode.id))
        assertNull(catalog.episode(ADULT, 999))
    }

    @Test
    fun `an episode is hidden with its show's playlist section, category or the kids rule`() = runBlocking {
        val adultShows = CategoryEntity(id = 22, sourceId = 1, mediaType = MediaType.SERIES, name = "Adult")
        val lateShow = SeriesEntity(id = 201, sourceId = 1, name = "Late show", categoryId = 22)
        val lateEpisode = EpisodeEntity(id = 301, seriesId = 201, seasonNumber = 1, episodeNumber = 1, name = "Late pilot", streamUrl = "http://tv.example.com/e/301")
        reads.categories[adultShows.id] = adultShows
        reads.shows[lateShow.id] = lateShow
        reads.episodes[lateEpisode.id] = lateEpisode
        assertEquals(ShowEpisode(lateShow, lateEpisode), catalog.episode(ADULT, lateEpisode.id))
        assertNull(catalog.episode(KID, lateEpisode.id))
        reads.customized[ADULT to MediaType.SERIES] = SectionCustomizations(hiddenCategories = setOf(CustomizeKeys.category(adultShows)))
        assertNull(catalog.episode(ADULT, lateEpisode.id))
        reads.playlists[KID] = listOf(playlist(1, syncSeries = false))
        assertNull(catalog.episode(KID, episode.id))
        assertEquals(ShowEpisode(show, episode), catalog.episode(ADULT, episode.id))
    }

    @Test
    fun `searches go to Core's search with only that section's playlists`() = runBlocking {
        reads.playlists[ADULT] = listOf(playlist(1), playlist(2, syncMovies = false))
        reads.found = SearchResults(movies = listOf(film), series = listOf(show))
        assertEquals(listOf(film), catalog.movies(ADULT, "fi", null, 20))
        assertEquals(listOf(1L), reads.lastSearch?.movieSourceIds)
        assertEquals(emptyList<Long>(), reads.lastSearch?.seriesSourceIds)
        assertEquals(listOf(show), catalog.series(ADULT, "", 2, 20))
        assertEquals(listOf(2L), reads.lastSearch?.seriesSourceIds)
        assertEquals(emptyList<ChannelEntity>(), catalog.channels(ADULT, "", null, 50))
        assertEquals(listOf(1L, 2L), reads.lastSearch?.liveSourceIds)
    }

    private fun playlist(id: Long, syncMovies: Boolean = true, syncSeries: Boolean = true) = SourceEntity(
        id = id,
        name = "List $id",
        type = SourceType.M3U,
        url = "http://lists.example.com/$id.m3u",
        syncMovies = syncMovies,
        syncSeries = syncSeries,
    )

    private companion object {
        const val ADULT = 1L
        const val KID = 2L
        const val OTHER = 3L
    }
}
