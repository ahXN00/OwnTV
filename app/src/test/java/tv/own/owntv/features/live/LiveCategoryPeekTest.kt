package tv.own.owntv.features.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.LiveKey

/**
 * The channel browser's right-hand list. Loads are released by hand on an unconfined scope, so the
 * order in which they finish is chosen by the test, not by timing.
 */
class LiveCategoryPeekTest {

    private fun channel(id: Long, category: Long = 10) = ChannelEntity(
        id = id, sourceId = 1, categoryId = category, name = "Ch $id",
        streamUrl = "http://x/$id.ts", remoteId = "$id",
    )

    private val zap = listOf(channel(1), channel(2))
    private val news = LiveKey.Folder(20)
    private val sport = LiveKey.Folder(30)
    private fun peek() = LiveCategoryPeek(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `opens on the CH± list`() {
        val p = peek()
        p.start(zap, LiveKey.Folder(10))
        assertEquals(zap, p.channels.value)
        assertEquals(LiveKey.Folder(10), p.key.value)
    }

    @Test
    fun `a category shows once loaded`() {
        val p = peek()
        p.start(zap, null)
        val gate = CompletableDeferred<List<ChannelEntity>>()
        p.load(news) { gate.await() }
        assertEquals(zap, p.channels.value) // nothing changes before the load lands
        gate.complete(listOf(channel(5, 20)))
        assertEquals(listOf(5L), p.channels.value.map { it.id })
        assertEquals(news, p.key.value)
    }

    @Test
    fun `a newer pick wins even when the older load lands last`() {
        val p = peek()
        p.start(zap, null)
        val slow = CompletableDeferred<List<ChannelEntity>>()
        // An older load that ignores cancellation, as a database read already under way would.
        p.load(news) { withContext(NonCancellable) { slow.await() } }
        p.load(sport) { listOf(channel(7, 30)) }
        slow.complete(listOf(channel(5, 20)))
        assertEquals(listOf(7L), p.channels.value.map { it.id })
        assertEquals(sport, p.key.value)
    }

    @Test
    fun `an empty category leaves the list as it was`() {
        val p = peek()
        p.start(zap, null)
        p.load(news) { emptyList() }
        assertEquals(zap, p.channels.value)
        assertEquals(null, p.key.value)
    }

    @Test
    fun `each category remembers its focused channel, else the playing one`() {
        val p = peek()
        p.start(zap, null)
        assertEquals(2L, p.entryChannel(playingId = 2))
        p.rememberFocus(1)
        p.load(news) { listOf(channel(5, 20), channel(6, 20)) }
        assertEquals(2L, p.entryChannel(playingId = 2))
        p.rememberFocus(6)
        p.start(zap, null)
        assertEquals(1L, p.entryChannel(playingId = 2))
        p.load(news) { listOf(channel(5, 20), channel(6, 20)) }
        assertEquals(6L, p.entryChannel(playingId = 2))
    }

    @Test
    fun `a shown folder locked while open goes back to the CH± list`() {
        val p = peek()
        p.start(zap, LiveKey.Folder(10))
        p.load(news) { listOf(channel(5, 20)) }
        p.dropCategories(setOf(20))
        assertEquals(zap, p.channels.value)
        assertEquals(LiveKey.Folder(10), p.key.value)
    }

    @Test
    fun `a mixed list loses only the locked folder's channels`() {
        val p = peek()
        p.start(zap, null)
        p.load(LiveKey.Favorites) { listOf(channel(5, 20), channel(6, 30)) }
        p.dropCategories(setOf(20))
        assertEquals(listOf(6L), p.channels.value.map { it.id })
        assertEquals(LiveKey.Favorites, p.key.value)
    }
}
