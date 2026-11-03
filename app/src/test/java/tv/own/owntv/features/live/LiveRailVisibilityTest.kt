package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import tv.own.owntv.core.live.LiveBuiltIns
import tv.own.owntv.core.live.LiveKey

class LiveRailVisibilityTest {

    private fun keys(hasCatchup: Boolean, showAll: Boolean = true, showCatchup: Boolean = true) =
        LiveViewModel.railWithCatchup(hasCatchup, LiveBuiltIns(showAll, showCatchup)).map { it.key }

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
    fun `fallbackKey follows the rail as shown`() {
        val open = LiveRailItem(LiveKey.Folder(2), title = "B")
        assertEquals(LiveKey.All, LiveViewModel.fallbackKey(LiveViewModel.railWithCatchup(false) + open))
        assertEquals(LiveKey.Folder(2), LiveViewModel.fallbackKey(LiveViewModel.railWithCatchup(true, LiveBuiltIns(showAll = false)) + open))
    }
}
