package tv.own.owntv.features.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.features.automation.UiItem
import tv.own.owntv.features.automation.UiItemType
import tv.own.owntv.features.automation.UiPlayer

class ShellUiItemsTest {
    private val channel = ChannelEntity(id = 5, sourceId = 1, name = "News", streamUrl = "http://tv.example.com/5")
    private val movie = MovieEntity(id = 12, sourceId = 1, name = "Film", streamUrl = "http://tv.example.com/m/12")
    private val otherMovie = MovieEntity(id = 13, sourceId = 1, name = "Other film", streamUrl = "http://tv.example.com/m/13")
    private val show = SeriesEntity(id = 7, sourceId = 1, name = "Show")
    private val otherShow = SeriesEntity(id = 8, sourceId = 1, name = "Other show")
    private val episode = EpisodeEntity(id = 70, seriesId = 7, seasonNumber = 1, episodeNumber = 2, name = "Pilot", streamUrl = "http://tv.example.com/e/70")

    @Test
    fun `every player mode has its API name`() {
        assertEquals(UiPlayer.NONE, ShellUiItems.player(PlayerMode.NONE))
        assertEquals(UiPlayer.FULLSCREEN, ShellUiItems.player(PlayerMode.FULLSCREEN))
        assertEquals(UiPlayer.MINI, ShellUiItems.player(PlayerMode.MINI))
        assertEquals(UiPlayer.AUDIO, ShellUiItems.player(PlayerMode.AUDIO))
    }

    @Test
    fun `browsing reports the section's focused item and nothing playing`() {
        val items = ShellItems(channel = channel, selectedMovie = movie)
        val live = ShellUiItems.snapshot(MainSection.LIVE_TV, PlayerMode.NONE, null, items)
        assertEquals(MainSection.LIVE_TV, live.section)
        assertEquals(UiItem(UiItemType.CHANNEL, 5, "News"), live.focused)
        assertNull(live.playing)
        assertEquals(UiItem(UiItemType.MOVIE, 12, "Film"), ShellUiItems.snapshot(MainSection.MOVIES, PlayerMode.NONE, null, items).focused)
        assertNull(ShellUiItems.snapshot(MainSection.HOME, PlayerMode.NONE, null, items).focused)
    }

    @Test
    fun `in series the opened show's focused episode wins over the show`() {
        val browsing = ShellItems(selectedSeries = otherShow)
        assertEquals(UiItem(UiItemType.SERIES, 8, "Other show"), focused(MainSection.SERIES, browsing))
        val opened = ShellItems(selectedSeries = otherShow, openedSeries = show)
        assertEquals(UiItem(UiItemType.SERIES, 7, "Show"), focused(MainSection.SERIES, opened))
        val onEpisode = opened.copy(selectedEpisode = episode)
        assertEquals(UiItem(UiItemType.EPISODE, 70, "Pilot"), focused(MainSection.SERIES, onEpisode))
        val staleEpisode = ShellItems(openedSeries = otherShow, selectedEpisode = episode)
        assertEquals(UiItem(UiItemType.SERIES, 8, "Other show"), focused(MainSection.SERIES, staleEpisode))
    }

    @Test
    fun `the playing item follows the section that started the player`() {
        val items = ShellItems(channel = channel, selectedMovie = otherMovie, playingMovie = movie, playingSeries = show)
        assertEquals(UiItem(UiItemType.CHANNEL, 5, "News"), playing(MainSection.LIVE_TV, items))
        assertEquals(UiItem(UiItemType.CHANNEL, 5, "News"), playing(MainSection.EPG, items))
        assertEquals(UiItem(UiItemType.MOVIE, 12, "Film"), playing(MainSection.MOVIES, items))
        assertEquals(UiItem(UiItemType.SERIES, 7, "Show"), playing(MainSection.SERIES, items))
        assertNull(playing(MainSection.DOWNLOADS, items))
        assertNull(playing(null, items))
    }

    @Test
    fun `full screen hides the focus and a docked player still reports what plays`() {
        val items = ShellItems(selectedMovie = otherMovie, playingMovie = movie)
        val full = ShellUiItems.snapshot(MainSection.MOVIES, PlayerMode.FULLSCREEN, MainSection.MOVIES, items)
        assertNull(full.focused)
        assertEquals(UiPlayer.FULLSCREEN, full.player)
        val mini = ShellUiItems.snapshot(MainSection.MOVIES, PlayerMode.MINI, MainSection.MOVIES, items)
        assertEquals(UiItem(UiItemType.MOVIE, 13, "Other film"), mini.focused)
        assertEquals(UiItem(UiItemType.MOVIE, 12, "Film"), mini.playing)
    }

    private fun focused(section: MainSection, items: ShellItems) = ShellUiItems.snapshot(section, PlayerMode.NONE, null, items).focused

    private fun playing(zapSource: MainSection?, items: ShellItems) =
        ShellUiItems.snapshot(MainSection.HOME, PlayerMode.FULLSCREEN, zapSource, items).playing
}
