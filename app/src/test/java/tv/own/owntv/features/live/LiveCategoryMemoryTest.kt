package tv.own.owntv.features.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.live.LiveKey

/** What Live TV saves as the remembered category, and how the restore of it ends. Real time, short waits. */
class LiveCategoryMemoryTest {

    private val news = LiveRailItem(LiveKey.Folder(1), title = "News")
    private val sport = LiveRailItem(LiveKey.Folder(2), title = "Sport")
    private val rail = LiveViewModel.defaultRail + news + sport

    private fun memoryTest(block: suspend CoroutineScope.(LiveCategoryMemory, MutableList<LiveKey>) -> Unit) = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val saved = mutableListOf<LiveKey>()
        try {
            block(scope, LiveCategoryMemory(scope) { saved += it }, saved)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a pick is saved once the focus settles, only the last of a quick run`() = memoryTest { memory, saved ->
        memory.picked(LiveKey.Favorites)
        memory.picked(news.key)
        delay(LiveCategoryMemory.SAVE_DEBOUNCE_MS - 400)
        assertEquals(emptyList<LiveKey>(), saved)
        delay(800)
        assertEquals(listOf(news.key), saved)
    }

    @Test
    fun `a restore shows the remembered category and saves nothing`() = memoryTest { memory, saved ->
        val shown = mutableListOf<LiveKey>()
        memory.restore({ "FOLDER:2" }, { rail }, { shown += it })
        delay(LiveCategoryMemory.SAVE_DEBOUNCE_MS + 400)
        assertEquals(listOf(sport.key), shown)
        assertEquals(emptyList<LiveKey>(), saved)
    }

    @Test
    fun `the restore ends with the first complete list even when the category is gone`() = memoryTest { memory, _ ->
        val shown = mutableListOf<LiveKey>()
        var railReads = 0
        memory.restore({ "FOLDER:9" }, { railReads++; rail }, { shown += it })
        delay(200)
        assertEquals(1, railReads)
        assertEquals(emptyList<LiveKey>(), shown)
    }

    @Test
    fun `a locked category is not restored`() = memoryTest { memory, _ ->
        val shown = mutableListOf<LiveKey>()
        memory.restore({ "FOLDER:1" }, { rail.map { if (it == news) it.copy(locked = true, sealed = true) else it } }, { shown += it })
        delay(200)
        assertEquals(emptyList<LiveKey>(), shown)
    }

    @Test
    fun `with the setting off nothing is restored`() = memoryTest { memory, _ ->
        val shown = mutableListOf<LiveKey>()
        memory.restore({ null }, { rail }, { shown += it })
        delay(200)
        assertEquals(emptyList<LiveKey>(), shown)
    }

    @Test
    fun `a pick or a startup choice made while the list loads wins over the restore`() = memoryTest { memory, _ ->
        val shown = mutableListOf<LiveKey>()
        val loaded = CompletableDeferred<List<LiveRailItem>>()
        memory.restore({ "FOLDER:2" }, { loaded.await() }, { shown += it })
        memory.placed() // the startup put Favorites on screen
        loaded.complete(rail)
        delay(200)
        assertEquals(emptyList<LiveKey>(), shown)

        val again = CompletableDeferred<List<LiveRailItem>>()
        memory.restore({ "FOLDER:2" }, { again.await() }, { shown += it })
        memory.picked(news.key)
        again.complete(rail)
        delay(200)
        assertEquals(emptyList<LiveKey>(), shown)
    }

    @Test
    fun `the TV Guide follows Live TV only while the switch is on`() {
        assertNull(LiveCategoryMemory.guideOpening(false, "FOLDER:2", rail))
        assertEquals(sport.key, LiveCategoryMemory.guideOpening(true, "FOLDER:2", rail))
        // Nothing it can show (gone, or nothing remembered): All channels.
        assertEquals(LiveKey.All, LiveCategoryMemory.guideOpening(true, "FOLDER:9", rail))
        assertEquals(LiveKey.All, LiveCategoryMemory.guideOpening(true, null, rail))
    }
}
