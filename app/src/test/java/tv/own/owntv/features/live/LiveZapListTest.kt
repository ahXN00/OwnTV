package tv.own.owntv.features.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.LiveKey

/**
 * The zap list — what CH+/CH− steps through, and the bookkeeping that keeps it working across a
 * numeric tune that lands outside the current list.
 *
 * Two failures live here and neither is visible in a log, which is why they are pinned: a background
 * rebuild publishing over a list the user has already navigated away from, and CH+/CH− going dead in
 * the seconds between a numeric jump and its rebuild.
 *
 * No player, no database, no view model — the loaders are lambdas, so this is the sequencing on its own.
 */
class LiveZapListTest {

    private fun channel(id: Long) = ChannelEntity(
        id = id, sourceId = 1, categoryId = 10, name = "Ch $id",
        streamUrl = "http://x/$id.ts", remoteId = "$id",
    )

    private class Harness(
        val category: List<ChannelEntity> = emptyList(),
        val window: List<ChannelEntity> = emptyList(),
    ) {
        val scope = CoroutineScope(Dispatchers.Default)
        var playing: Long? = null
        var windowBuilds = 0
        var releaseWindow = false // when true, the rebuild loader blocks until told to finish
        var releaseArming = false // …and this one blocks the ordinary arming load the same way

        val zap = LiveZapList(
            scope = scope,
            playingChannelId = { playing },
            loadForChannel = {
                while (releaseArming) delay(2)
                category
            },
            loadForCategory = { category },
            loadWindowAround = {
                windowBuilds++
                while (releaseWindow) delay(2)
                window
            },
            categoryName = { "Sports" },
        )

        fun await(what: String, condition: () -> Boolean) = runBlocking {
            repeat(200) {
                if (condition()) return@runBlocking
                delay(5)
            }
            throw AssertionError("timed out waiting for $what")
        }

        fun stop() = scope.cancel()
    }

    private val list = (1L..5L).map { ChannelEntity(id = it, sourceId = 1, categoryId = 10, name = "Ch $it", streamUrl = "http://x/$it.ts", remoteId = "$it") }

    @Test
    fun `arming from a browse rail keeps that rail as the context`() {
        val h = Harness()
        h.zap.armFromBrowse(list, title = "Favorites", key = LiveKey.Favorites, categoryId = null)
        assertEquals(list, h.zap.channels.value)
        assertEquals("Favorites", h.zap.title.value)
        assertEquals(LiveKey.Favorites, h.zap.key.value)
        assertTrue(h.zap.canZap.value)
        h.stop()
    }

    @Test
    fun `a single-channel list cannot be zapped`() {
        val h = Harness()
        h.zap.armFromBrowse(list.take(1), title = null, key = null, categoryId = null)
        assertFalse(h.zap.canZap.value)
        h.playing = 1
        assertNull(h.zap.next(1))
        h.stop()
    }

    @Test
    fun `CH plus and minus step through the armed list and wrap around it`() {
        val h = Harness()
        h.zap.armFromBrowse(list, title = null, key = null, categoryId = null)
        h.playing = 3
        assertEquals(4L, h.zap.next(1)?.id)
        assertEquals(2L, h.zap.next(-1)?.id)
        h.playing = 5
        assertEquals(1L, h.zap.next(1)?.id)
        h.playing = 1
        assertEquals(5L, h.zap.next(-1)?.id)
        h.stop()
    }

    @Test
    fun `re-arming the same category while zapping inside it does not reload`() {
        val h = Harness(category = list)
        h.zap.armFor(channel(2))
        h.await("the first load") { h.zap.channels.value.isNotEmpty() }
        // The list already contains this channel and the category matches, so nothing is reloaded —
        // this is what keeps CH+/CH− a pure in-memory step.
        h.zap.armFromBrowse(list.take(3), title = "kept", key = null, categoryId = 10)
        h.zap.armFor(channel(2))
        runBlocking { delay(40) }
        assertEquals("kept", h.zap.title.value)
        h.stop()
    }

    @Test
    fun `an empty category leaves the browser open`() {
        val h = Harness(category = emptyList())
        var closed = false
        h.zap.armForCategory(10) { closed = true }
        runBlocking { delay(40) }
        assertFalse(closed)
        assertTrue(h.zap.channels.value.isEmpty())
        h.stop()
    }

    @Test
    fun `a numeric tune inside the current list plays at once and rebuilds nothing`() {
        val h = Harness(window = list)
        h.zap.armFromBrowse(list, title = null, key = null, categoryId = null)
        h.playing = 1
        val played = mutableListOf<Long>()
        runBlocking { h.zap.directTune(currentChannelId = 1, tuned = list[3]) { played += it.id } }
        assertEquals(listOf(4L), played)
        runBlocking { delay(40) }
        assertEquals(0, h.windowBuilds)
        h.stop()
    }

    @Test
    fun `a numeric tune outside the list plays at once, then publishes the rebuilt window`() {
        val far = channel(99)
        val rebuilt = listOf(channel(98), far, channel(100))
        val h = Harness(window = rebuilt)
        h.zap.armFromBrowse(list, title = null, key = null, categoryId = null)
        h.playing = 1
        runBlocking { h.zap.directTune(currentChannelId = 1, tuned = far) { h.playing = it.id } }
        h.await("the rebuilt window") { h.zap.channels.value == rebuilt }
        assertEquals(1, h.windowBuilds)
        assertEquals(100L, h.zap.next(1)?.id) // stepping from the rebuilt list now
        h.stop()
    }

    @Test
    fun `CH plus and minus keep working on the old list while the rebuild is still running`() {
        val far = channel(99)
        val h = Harness(window = listOf(far))
        h.zap.armFromBrowse(list, title = null, key = null, categoryId = null)
        h.playing = 1
        h.releaseWindow = true // the rebuild is stuck; the anchor is all the user has
        runBlocking { h.zap.directTune(currentChannelId = 1, tuned = far) { h.playing = it.id } }
        // The anchor remembers index 0 of the old list, so CH+ steps to its second entry…
        val next = h.zap.next(1)
        assertEquals(2L, next?.id)
        // …and a held key chains from there rather than reading the anchor as stale.
        h.playing = next!!.id
        assertEquals(3L, h.zap.next(1)?.id)
        h.releaseWindow = false
        h.stop()
    }

    @Test
    fun `a rebuild cannot publish once the user has navigated away`() {
        val far = channel(99)
        val rebuilt = listOf(channel(98), far)
        val h = Harness(window = rebuilt)
        h.zap.armFromBrowse(list, title = null, key = null, categoryId = null)
        h.playing = 1
        h.releaseWindow = true
        runBlocking { h.zap.directTune(currentChannelId = 1, tuned = far) { h.playing = it.id } }
        // Ordinary navigation elsewhere — exactly what ensurePlaying does before it plays.
        h.zap.cancelPendingRebuild()
        h.releaseWindow = false
        runBlocking { delay(40) }
        assertEquals(list, h.zap.channels.value) // the stale rebuild never landed
        h.stop()
    }

    @Test
    fun `a rebuild cannot publish for a channel that is no longer playing`() {
        val far = channel(99)
        val h = Harness(window = listOf(channel(98), far))
        h.zap.armFromBrowse(list, title = null, key = null, categoryId = null)
        h.playing = 1
        h.releaseWindow = true
        runBlocking { h.zap.directTune(currentChannelId = 1, tuned = far) { h.playing = it.id } }
        h.playing = 3 // the user zapped on before the window finished building
        h.releaseWindow = false
        runBlocking { delay(40) }
        assertEquals(list, h.zap.channels.value)
        h.stop()
    }

    /**
     * The gap [cancelPendingRebuild] did not cover. Ordinary navigation goes through `ensurePlaying`,
     * which cancels the rebuild first — but picking a category in the in-player browser does not, and
     * it does not change the playing channel either, so both of the rebuild's guards still held: the
     * generation was untouched and the tuned channel was still on screen. The window then landed on
     * top of the category the user had just chosen, seconds after they chose it.
     */
    @Test
    fun `choosing a category beats a direct-tune rebuild still in flight`() {
        val far = channel(99)
        val chosen = listOf(channel(50), channel(51))
        val h = Harness(category = chosen, window = listOf(channel(98), far, channel(100)))
        h.zap.armFromBrowse(list, title = null, key = null, categoryId = null)
        h.playing = 1
        h.releaseWindow = true
        runBlocking { h.zap.directTune(currentChannelId = 1, tuned = far) { h.playing = it.id } }
        // The user opens the category browser and picks a category while the window is still building.
        var closed = false
        h.zap.armForCategory(11) { closed = true }
        h.await("the chosen category") { h.zap.channels.value == chosen }
        h.releaseWindow = false // the older rebuild finishes now
        runBlocking { delay(40) }
        assertTrue(closed)
        assertEquals(chosen, h.zap.channels.value)
        assertEquals("Sports", h.zap.title.value)
        h.stop()
    }

    /** The mirror image: a numeric tune takes ownership of the list, so an arming load that was
     *  already in flight must not publish over the window it builds. */
    @Test
    fun `a slow arming load cannot land on top of a numeric tune`() {
        val far = channel(99)
        val rebuilt = listOf(channel(98), far, channel(100))
        val h = Harness(category = list.take(2), window = rebuilt)
        h.releaseArming = true
        h.zap.armFor(channel(2)) // still loading when the user types a channel number
        h.playing = 1
        runBlocking { h.zap.directTune(currentChannelId = 1, tuned = far) { h.playing = it.id } }
        h.await("the rebuilt window") { h.zap.channels.value == rebuilt }
        h.releaseArming = false
        runBlocking { delay(40) }
        assertEquals(rebuilt, h.zap.channels.value)
        h.stop()
    }

    @Test
    fun `a numeric tune from a list too small to anchor leaves no stale fallback`() {
        val far = channel(99)
        val h = Harness(window = listOf(far))
        h.zap.armFromBrowse(list.take(1), title = null, key = null, categoryId = null)
        h.playing = 1
        h.releaseWindow = true
        runBlocking { h.zap.directTune(currentChannelId = 1, tuned = far) { h.playing = it.id } }
        assertNull(h.zap.next(1)) // one channel is not something to step through
        h.releaseWindow = false
        h.stop()
    }

    @Test
    fun `a relock takes the sealed folder's channels out of the list`() {
        val h = Harness()
        val mixed = list.mapIndexed { i, ch -> if (i % 2 == 0) ch.copy(categoryId = 20) else ch }
        h.zap.armFromBrowse(mixed, title = "Favorites", key = LiveKey.Favorites, categoryId = null)
        h.zap.dropCategories(setOf(20))
        assertEquals(listOf(2L, 4L), h.zap.channels.value.map { it.id })
        h.playing = 2
        assertEquals(4L, h.zap.next(1)?.id)
        assertEquals("Favorites", h.zap.title.value)
        h.stop()
    }

    @Test
    fun `a load that started before a relock publishes without the sealed folder`() {
        val h = Harness(category = list.mapIndexed { i, ch -> if (i < 2) ch.copy(categoryId = 20) else ch })
        h.releaseArming = true
        h.zap.armFor(list[4])
        h.zap.dropCategories(setOf(20))
        h.releaseArming = false
        h.await("the arming load") { h.zap.channels.value.isNotEmpty() }
        assertEquals(listOf(3L, 4L, 5L), h.zap.channels.value.map { it.id })
        h.stop()
    }

    @Test
    fun `a folder the PIN opened again publishes its channels`() {
        val h = Harness()
        h.zap.dropCategories(setOf(10))
        h.zap.dropCategories(emptySet())
        h.zap.armFromBrowse(list, title = null, key = null, categoryId = 10)
        assertEquals(list.map { it.id }, h.zap.channels.value.map { it.id })
        h.stop()
    }

    @Test
    fun `a relock that seals the whole list leaves nothing to zap`() {
        val h = Harness()
        h.zap.armFromBrowse(list, title = null, key = null, categoryId = 10)
        h.zap.dropCategories(setOf(10))
        assertEquals(emptyList<ChannelEntity>(), h.zap.channels.value)
        assertFalse(h.zap.canZap.value)
        h.stop()
    }
}
