package tv.own.owntv.features.shell

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity

class StaleShellItemsViewModelTest {
    private val stale = StaleShellItemsViewModel()
    private val channel = ChannelEntity(id = 5, sourceId = 1, name = "News", streamUrl = "http://tv.example.com/5")
    private val movie = MovieEntity(id = 12, sourceId = 1, name = "Film", streamUrl = "http://tv.example.com/m/12")
    private val otherMovie = MovieEntity(id = 13, sourceId = 1, name = "Other film", streamUrl = "http://tv.example.com/m/13")
    private val show = SeriesEntity(id = 7, sourceId = 1, name = "Show")

    @Test
    fun `the first profile's items are its own`() {
        val items = ShellItems(channel = channel, selectedMovie = movie, playingSeries = show)
        assertEquals(items, stale.current(null, items))
        assertEquals(items, stale.current(1, items))
        assertEquals(items, stale.current(1, items))
    }

    @Test
    fun `after a profile switch the last profile's items are left out`() {
        val held = ShellItems(channel = channel, selectedMovie = movie, playingMovie = movie, selectedSeries = show)
        stale.current(1, held)
        assertEquals(ShellItems(), stale.current(2, held))
        assertEquals(ShellItems(), stale.current(2, held))
    }

    @Test
    fun `an item the new profile picks is reported, and stays so if it picks the old one again`() {
        stale.current(1, ShellItems(selectedMovie = movie, selectedSeries = show))
        stale.current(2, ShellItems(selectedMovie = movie, selectedSeries = show))
        assertEquals(ShellItems(selectedMovie = otherMovie), stale.current(2, ShellItems(selectedMovie = otherMovie, selectedSeries = show)))
        assertEquals(ShellItems(selectedMovie = movie), stale.current(2, ShellItems(selectedMovie = movie, selectedSeries = show)))
    }

    @Test
    fun `an item cleared after the switch is no longer stale`() {
        stale.current(1, ShellItems(channel = channel))
        stale.current(2, ShellItems(channel = channel))
        assertEquals(ShellItems(), stale.current(2, ShellItems()))
        assertEquals(ShellItems(channel = channel), stale.current(2, ShellItems(channel = channel)))
    }
}
