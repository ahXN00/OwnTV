package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HostPool

class VodServerFailoverTest {

    private val source = SourceEntity(
        id = 1,
        name = "X",
        type = SourceType.XTREAM,
        url = "http://a.example:8080",
        backupUrls = "http://b.example:80",
        failoverEnabled = true,
    )
    private val primary = "http://a.example:8080"
    private val movie = "$primary/movie/u/p/1.mkv"
    private val episode = "http://a.example:8080/series/u/p/2.mp4"
    private val backup = "http://b.example:80"

    @Test
    fun `movie and episode move onto the backup keeping path`() {
        assertEquals("$backup/movie/u/p/1.mkv", VodServerFailover.replayUrl(movie, source, backup))
        assertEquals("$backup/series/u/p/2.mp4", VodServerFailover.replayUrl(episode, source, backup))
    }

    @Test
    fun `primary server returns the stored url`() {
        assertEquals(movie, VodServerFailover.replayUrl(movie, source, "http://a.example:8080"))
        assertEquals(episode, VodServerFailover.replayUrl(episode, source, "http://a.example:8080"))
    }

    @Test
    fun `canonical of a replay is the stored url`() {
        assertEquals(movie, HostPool.canonical(VodServerFailover.replayUrl(movie, source, backup), source))
        assertEquals(episode, HostPool.canonical(VodServerFailover.replayUrl(episode, source, backup), source))
    }

    @Test
    fun `queued episodes rewrite onto the in-use server and still match by canonical`() {
        val switched = source.copy(activeUrl = backup)
        val queued = listOf(episode, "$primary/series/u/p/3.mp4", "$primary/series/u/p/4.mp4")
        queued.forEach { stored ->
            val playing = HostPool.forRequest(stored, switched)
            assertEquals("http://b.example" + stored.removePrefix(primary), playing)
            assertEquals(stored, HostPool.canonical(playing, switched))
        }
    }

    @Test
    fun `movie on the backup still matches the stored stream url`() {
        val switched = source.copy(activeUrl = backup)
        val playing = HostPool.forRequest(movie, switched)
        assertEquals(movie, HostPool.canonical(playing, switched))
    }

    @Test
    fun `source with no backups leaves urls unchanged`() {
        val alone = source.copy(backupUrls = null)
        assertEquals(movie, HostPool.forRequest(movie, alone))
        assertEquals(episode, HostPool.forRequest(episode, alone))
        assertEquals("$backup/movie/u/p/1.mkv", HostPool.canonical("$backup/movie/u/p/1.mkv", alone))
        assertEquals("$backup/series/u/p/2.mp4", HostPool.canonical("$backup/series/u/p/2.mp4", alone))
    }
}
