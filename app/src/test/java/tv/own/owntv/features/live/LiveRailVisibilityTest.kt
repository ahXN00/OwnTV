package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import tv.own.owntv.core.live.LiveKey

class LiveRailVisibilityTest {

    private fun keys(hasCatchup: Boolean, showAll: Boolean = true, showCatchup: Boolean = true) =
        LiveViewModel.railWithCatchup(hasCatchup, showAll, showCatchup).map { it.key }

    @Test
    fun `defaults without catch-up are today's rail`() {
        val rail = LiveViewModel.railWithCatchup(false)
        assertSame(LiveViewModel.defaultRail, rail)
        assertEquals(listOf(LiveKey.Favorites, LiveKey.History, LiveKey.All), rail.map { it.key })
    }

    @Test
    fun `defaults with catch-up insert Catchup before All`() {
        assertEquals(
            listOf(LiveKey.Favorites, LiveKey.History, LiveKey.Catchup, LiveKey.All),
            keys(true),
        )
    }

    @Test
    fun `showAll false drops All only`() {
        assertEquals(listOf(LiveKey.Favorites, LiveKey.History), keys(false, showAll = false))
        assertEquals(
            listOf(LiveKey.Favorites, LiveKey.History, LiveKey.Catchup),
            keys(true, showAll = false),
        )
    }

    @Test
    fun `showCatchup false drops Catchup only even when hasCatchup`() {
        assertEquals(
            listOf(LiveKey.Favorites, LiveKey.History, LiveKey.All),
            keys(true, showCatchup = false),
        )
    }

    @Test
    fun `fallbackKey returns All when present`() {
        assertEquals(LiveKey.All, LiveViewModel.fallbackKey(LiveViewModel.railWithCatchup(false)))
    }

    @Test
    fun `fallbackKey skips a sealed folder then takes the first open folder or custom`() {
        val sealed = LiveRailItem(LiveKey.Folder(1), title = "A", sealed = true)
        val open = LiveRailItem(LiveKey.Folder(2), title = "B")
        val custom = LiveRailItem(LiveKey.Custom("c"), title = "C")
        val rail = listOf(
            LiveRailItem(LiveKey.Favorites),
            sealed,
            open,
            custom,
        )
        assertEquals(LiveKey.Folder(2), LiveViewModel.fallbackKey(rail))
        assertEquals(LiveKey.Custom("c"), LiveViewModel.fallbackKey(listOf(sealed, custom)))
    }

    @Test
    fun `fallbackKey with no folders returns Favorites`() {
        val rail = listOf(LiveRailItem(LiveKey.Favorites), LiveRailItem(LiveKey.History))
        assertEquals(LiveKey.Favorites, LiveViewModel.fallbackKey(rail))
    }

    @Test
    fun `hiddenSelectionFallback`() {
        val rail = LiveViewModel.railWithCatchup(true, showAll = false, showCatchup = false)
        val folder = LiveRailItem(LiveKey.Folder(1), title = "A")
        assertEquals(LiveKey.Favorites, LiveViewModel.hiddenSelectionFallback(LiveKey.All, rail, false, true))
        assertEquals(LiveKey.Favorites, LiveViewModel.hiddenSelectionFallback(LiveKey.Catchup, rail, true, false))
        assertNull(LiveViewModel.hiddenSelectionFallback(LiveKey.All, LiveViewModel.railWithCatchup(false), true, true))
        assertNull(LiveViewModel.hiddenSelectionFallback(LiveKey.Favorites, rail, false, false))
        assertNull(LiveViewModel.hiddenSelectionFallback(folder.key, listOf(folder), false, false))
    }
}
