package tv.own.owntv.features.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class ArchiveGuideQueueTest {

    private fun ch(id: Long) = ChannelEntity(id = id, sourceId = 1, name = "C$id", streamUrl = "http://x/$id", catchup = true)

    private fun ArchiveGuideQueue.drain(): List<Long> = generateSequence { next()?.id }.toList()

    @Test fun `the row seen last goes first`() {
        val queue = ArchiveGuideQueue(max = 24)
        listOf(1L, 2, 3).forEach { queue.add(ch(it)) }
        assertEquals(listOf(3L, 2, 1), queue.drain())
        assertNull(queue.next())
    }

    @Test fun `a channel queued again moves to the front and is asked for once`() {
        val queue = ArchiveGuideQueue(max = 24)
        listOf(1L, 2, 3, 1).forEach { queue.add(ch(it)) }
        assertEquals(listOf(1L, 3, 2), queue.drain())
    }

    @Test fun `past the cap the rows scrolled past longest ago are forgotten`() {
        val queue = ArchiveGuideQueue(max = 3)
        (1L..5).forEach { queue.add(ch(it)) }
        assertEquals(3, queue.size)
        assertEquals(listOf(5L, 4, 3), queue.drain())
    }
}
