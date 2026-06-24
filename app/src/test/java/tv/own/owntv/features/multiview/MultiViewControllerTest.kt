package tv.own.owntv.features.multiview

import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.player.CornerEngine
import tv.own.owntv.player.CornerState
import tv.own.owntv.player.MediaMeta

/**
 * Verifies MultiView orchestration with fake engines — no real decoders. Focuses on the things that matter
 * for correctness and for being light on resources: only the active tile is audible, the device tile cap is
 * honoured, and **every tile is released on exit** (nothing keeps decoding in the background).
 */
class MultiViewControllerTest {

    private class FakeEngine : CornerEngine {
        override val state: StateFlow<CornerState> = MutableStateFlow(CornerState.IDLE)
        override val meta: StateFlow<MediaMeta> = MutableStateFlow(MediaMeta())
        override var currentUrl: String? = null
        var muted = true
        var released = false
        var stops = 0
        override fun play(url: String, meta: MediaMeta, muted: Boolean) { currentUrl = url; this.muted = muted }
        override fun setMuted(muted: Boolean) { this.muted = muted }
        override fun stop() { stops++; currentUrl = null }
        override fun release() { released = true; currentUrl = null }
        override fun setSurface(surface: Surface?) {}
    }

    /** A controller whose engines are fakes we can inspect (one fake per slot, created on demand). */
    private fun controller(maxTiles: Int = 4): Pair<MultiViewController, List<FakeEngine>> {
        val created = mutableListOf<FakeEngine>()
        val c = MultiViewController(maxTiles = maxTiles, engineFactory = { FakeEngine().also { created += it } })
        return c to created
    }

    private fun channel(id: Long, url: String) =
        ChannelEntity(id = id, sourceId = 1, name = "Ch$id", streamUrl = url)

    @Test
    fun enter_startsTilesAndOnlyActiveIsAudible() {
        val (c, engines) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b"), channel(3, "c")))

        assertTrue(c.active.value)
        assertEquals(3, c.tiles.value.size)
        assertEquals(0, c.activeIndex.value)
        // Exactly one engine (the active tile) is unmuted.
        assertEquals(1, engines.count { !it.muted })
        assertFalse("active tile must be audible", engines[0].muted)
    }

    @Test
    fun setActive_movesAudioToTheFocusedTile() {
        val (c, engines) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b")))

        c.setActive(1)

        assertEquals(1, c.activeIndex.value)
        assertTrue("previously-active tile is muted", engines[0].muted)
        assertFalse("newly-focused tile is audible", engines[1].muted)
    }

    @Test
    fun promoteToDominant_setsActiveAndSwitchesLayout() {
        val (c, _) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b")))

        c.promoteToDominant(1)

        assertEquals(1, c.activeIndex.value)
        assertEquals(MultiLayout.DOMINANT, c.layout.value)
    }

    @Test
    fun toggleLayout_flipsGridAndDominant() {
        val (c, _) = controller()
        c.enter(listOf(channel(1, "a")))
        assertEquals(MultiLayout.GRID, c.layout.value)
        c.toggleLayout(); assertEquals(MultiLayout.DOMINANT, c.layout.value)
        c.toggleLayout(); assertEquals(MultiLayout.GRID, c.layout.value)
    }

    @Test
    fun deviceCap_isHonoured_onEnterAndAdd() {
        val (c, _) = controller(maxTiles = 2)
        c.enter(listOf(channel(1, "a"), channel(2, "b"), channel(3, "c"))) // 3 offered, device allows 2
        assertEquals(2, c.tiles.value.size)
        assertFalse(c.canAddTile)
        c.addTile(channel(4, "d")) // must be refused
        assertEquals(2, c.tiles.value.size)
    }

    @Test
    fun exit_releasesEveryTile_soNothingStreamsInBackground() {
        val (c, engines) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b"), channel(3, "c")))

        c.exit()

        assertFalse(c.active.value)
        assertTrue(c.tiles.value.isEmpty())
        assertEquals("all three tile engines must be released", 3, engines.count { it.released })
    }

    @Test
    fun removeTile_freesASlotAndKeepsOneAudible() {
        val (c, _) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b"), channel(3, "c")))

        c.removeTile(1)

        assertEquals(2, c.tiles.value.size)
        assertTrue(c.canAddTile) // back under the cap
        assertTrue(c.active.value)
    }
}
