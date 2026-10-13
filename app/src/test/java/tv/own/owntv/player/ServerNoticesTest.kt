package tv.own.owntv.player

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.network.HostPool

/** Host failover's notices: one toast per switch, and "All N servers failed" until something plays. */
class ServerNoticesTest {

    @Test
    fun `a switch is posted once and clears an earlier exhausted round`() = runBlocking {
        val n = ServerNotices()
        n.exhausted(21)
        assertEquals(21, n.exhausted.value)
        val got = async { n.switched.first() }
        yield()
        n.switched(3, 21, "pro.example.top")
        assertEquals(ServerNotices.Switched(3, 21, "pro.example.top"), got.await())
        assertNull(n.exhausted.value)
    }

    @Test
    fun `switchingBusy emits on busy`() = runBlocking {
        val n = ServerNotices()
        val got = async { n.busy.first() }
        yield()
        n.switchingBusy()
        assertEquals(Unit, got.await())
    }

    @Test
    fun `noNetwork emits once`() = runBlocking {
        val n = ServerNotices()
        val got = async { n.noNetwork.first() }
        yield()
        n.noNetwork()
        assertEquals(Unit, got.await())
    }

    @Test
    fun `timeshiftOff emits once`() = runBlocking {
        val n = ServerNotices()
        val got = async { n.timeshiftOff.first() }
        yield()
        n.timeshiftOff()
        assertEquals(Unit, got.await())
    }

    @Test
    fun `clear drops the exhausted state`() {
        val n = ServerNotices()
        n.exhausted(4)
        n.clear()
        assertNull(n.exhausted.value)
    }

    @Test
    fun `live toast formats the switched server`() = runBlocking {
        val n = ServerNotices()
        val got = async { n.switched.first() }
        yield()
        n.switched(2, 3, HostPool.display("http://backup.example.top/"))
        val e = got.await()
        assertEquals(
            "Switched to server 2 of 3 · backup.example.top",
            String.format("Switched to server %1\$d of %2\$d · %3\$s", e.position, e.total, e.server),
        )
    }
}
