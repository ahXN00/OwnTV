package tv.own.owntv.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HostPool
import tv.own.owntv.core.network.HostPreflight
import tv.own.owntv.core.network.HostSelector
import tv.own.owntv.core.parser.XtreamClient

class VodFilmCapTest {

    private val storedUrl = "http://a.example.top/movie/u/p/1.mkv"

    private val source = SourceEntity(
        id = 1,
        name = "X",
        type = SourceType.XTREAM,
        url = "http://a.example.top",
        username = "u",
        password = "p",
        maxConnections = 1,
        backupUrls = HostPool.encode(listOf("http://b.example.top")),
        failoverEnabled = true,
    )

    @Test
    fun `AC25 dead server switches after the film cap not before`() = runTest {
        val rig = Rig(reconnects = 10, aDown = true)
        rig.watch(backgroundScope)
        runCurrent()

        advanceTimeBy(19_000)
        runCurrent()
        assertEquals(0, rig.port.stopped)
        assertTrue(rig.replays.isEmpty())

        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(rig.port.stopped >= 1)
        assertTrue(rig.gateCalls >= 1)
        assertTrue(rig.preflights.first().contains("a.example"))
        assertEquals(1, rig.replays.size)
        assertTrue(rig.replays.single().startsWith("http://b.example.top"))
        assertEquals(0, rig.port.failed)
    }

    @Test
    fun `AC26 live server replays once then the cap fails the film`() = runTest {
        val rig = Rig(reconnects = 1, aDown = false)
        rig.watch(backgroundScope)
        runCurrent()

        advanceTimeBy(20_001)
        runCurrent()
        assertEquals(1, rig.replays.size)
        assertTrue(rig.replays.single().startsWith("http://a.example.top"))
        assertTrue(rig.replays.none { it.startsWith("http://b.example.top") })
        assertEquals(0, rig.port.failed)

        advanceTimeBy(20_001)
        runCurrent()
        assertEquals(1, rig.port.failed)
        assertTrue(rig.replays.none { it.startsWith("http://b.example.top") })
    }

    @Test
    fun `AC27 player error before the cap uses the error path and never stops`() = runTest {
        val rig = Rig(reconnects = 10, aDown = false)
        rig.watch(backgroundScope)
        runCurrent()

        advanceTimeBy(10_000)
        runCurrent()
        rig.port.error.value = "timeout"
        runCurrent()

        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(0, rig.port.stopped)
        assertTrue(rig.preflights.isNotEmpty())
        assertTrue(rig.preflights.first().contains("a.example"))
        assertTrue(rig.replays.isEmpty())
        assertEquals(0, rig.port.failed)
    }

    @Test
    fun `gap b refused gate shows the film error`() = runTest {
        val rig = Rig(
            reconnects = 10,
            aDown = true,
            gateResult = ReleaseGate.Result.Refused(ReleaseGate.Refusal.RECORDING),
        )
        rig.watch(backgroundScope)
        runCurrent()
        advanceTimeBy(20_001)
        runCurrent()
        assertEquals(1, rig.port.failed)
        assertTrue(rig.replays.isEmpty())
    }

    @Test
    fun `AC28 no backups never starts a film cap`() = runTest {
        val rig = Rig(reconnects = 10, aDown = true, watched = source.copy(backupUrls = null))
        rig.watch(backgroundScope)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, rig.port.stopped)
        assertEquals(0, rig.gateCalls)
        assertTrue(rig.replays.isEmpty())
    }

    private class FakePort : VodPlaybackPort {
        override val error = MutableStateFlow<Any?>(null)
        override val videoRes = MutableStateFlow<String?>(null)
        override val buffering = MutableStateFlow(true)
        override val position = MutableStateFlow(0L)
        override val currentMediaContentKey: String? = "k"
        override val lastErrorLine: String? = null
        var stopped = 0
        var failed = 0

        override fun stop() {
            stopped++
        }

        override fun fail() {
            failed++
        }
    }

    private inner class Rig(
        reconnects: Int,
        aDown: Boolean,
        private val watched: SourceEntity = source,
        gateResult: ReleaseGate.Result = ReleaseGate.Result.Released(mpvDestroyed = false, quiet = true),
    ) {
        val port = FakePort()
        val preflights = mutableListOf<String>()
        val replays = mutableListOf<String>()
        var gateCalls = 0
        private val ok = HostPreflight.Result.Ok(10, XtreamClient.XtAccountDetails())
        private val failover = VodServerFailover(
            player = port,
            hosts = HostSelector { _, _ -> },
            gate = {
                gateCalls++
                gateResult
            },
            preflight = { _, h ->
                preflights += h
                if (aDown && h.contains("a.example")) HostPreflight.Result.Down("refused") else ok
            },
            notices = ServerNotices(),
            isOnline = { true },
            awaitOnline = {},
            reconnects = { reconnects },
            log = {},
        )

        fun watch(scope: kotlinx.coroutines.CoroutineScope) {
            failover.watch(
                scope,
                watched,
                "film",
                replay = { url, _, _ -> replays += url },
                storedUrl = storedUrl,
                isOurs = { true },
            )
        }
    }
}
