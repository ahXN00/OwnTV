package tv.own.owntv.features.automation

import android.view.KeyEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyGateTest {
    private val screens = SensitiveScreens()
    private val time = VirtualTime()
    private var inFront = true
    private val sent = mutableListOf<Int>()

    /** What the TV does with a delivered key: by default it just takes it. */
    private var deliver: (Int) -> Unit = { sent += it }

    /** Like the real senders: asks the gate, then delivers, which takes a moment, only if it may. */
    private val sender = KeySender { keyCode, allowed ->
        val gate = allowed()
        if (gate == KeyPress.PRESSED) {
            yield()
            deliver(keyCode)
        }
        gate
    }
    private val gate = KeyGate({ inFront }, screens, sender, clock = { time.now }, pause = { time.advance(it) })

    @Test
    fun `keys reach the sender in order, a gap apart`() = runBlocking {
        assertEquals(KeyPress.PRESSED, gate.press(listOf(RemoteKey.DOWN, RemoteKey.OK)))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER), sent)
        assertEquals(150L, time.now)
    }

    @Test
    fun `nothing is sent while OwnTV is not in front`() = runBlocking {
        inFront = false
        assertEquals(KeyPress.NOT_IN_FRONT, gate.press(listOf(RemoteKey.OK)))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `nothing is sent while a private screen is open`() = runBlocking {
        screens.enter()
        assertEquals(KeyPress.PAUSED, gate.press(listOf(RemoteKey.OK)))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a key that opens a private screen stops the rest`() = runBlocking {
        deliver = { sent += it; screens.enter() }
        assertEquals(KeyPress.PAUSED, gate.press(listOf(RemoteKey.OK, RemoteKey.DOWN)))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_CENTER), sent)
    }

    @Test
    fun `the next call waits out the gap and sees the screen the last key opened`() = runBlocking {
        deliver = { sent += it; time.at(time.now + 5) { screens.enter() } }
        assertEquals(KeyPress.PRESSED, gate.press(listOf(RemoteKey.OK)))
        assertEquals(KeyPress.PAUSED, gate.press(listOf(RemoteKey.DOWN)))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_CENTER), sent)
        assertEquals(150L, time.now)
    }

    @Test
    fun `a call long after the last key does not wait`() = runBlocking {
        gate.press(listOf(RemoteKey.OK))
        time.advance(400)
        gate.press(listOf(RemoteKey.DOWN))
        assertEquals(400L, time.now)
    }

    @Test
    fun `calls take turns instead of mixing their keys`() = runBlocking {
        val first = async { gate.press(listOf(RemoteKey.OK, RemoteKey.OK)) }
        val second = async { gate.press(listOf(RemoteKey.DOWN)) }
        assertEquals(KeyPress.PRESSED, first.await())
        assertEquals(KeyPress.PRESSED, second.await())
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_DOWN), sent)
    }

    @Test
    fun `a focused window that is not OwnTV's refuses the key`() = runBlocking {
        deliver = { throw SecurityException("not ours") }
        assertEquals(KeyPress.NOT_IN_FRONT, gate.press(listOf(RemoteKey.OK, RemoteKey.DOWN)))
    }

    @Test
    fun `a call still waiting for its turn is refused, and its keys never land later`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val slow = slowGate { keyCode, _ -> release.await(); sent += keyCode; KeyPress.PRESSED }
        val first = async { slow.press(listOf(RemoteKey.OK)) }
        yield()
        assertEquals(KeyPress.BUSY, slow.press(listOf(RemoteKey.DOWN)))
        release.complete(Unit)
        assertEquals(KeyPress.PRESSED, first.await())
        assertEquals(KeyPress.PRESSED, slow.press(listOf(RemoteKey.UP)))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP), sent)
    }

    @Test
    fun `a call cancelled while its keys run gives the turn back`() = runBlocking {
        var hang = true
        val slow = slowGate { keyCode, _ -> if (hang) awaitCancellation(); sent += keyCode; KeyPress.PRESSED }
        val first = async { slow.press(listOf(RemoteKey.OK)) }
        yield()
        first.cancelAndJoin()
        hang = false
        assertEquals(KeyPress.PRESSED, slow.press(listOf(RemoteKey.DOWN)))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_DOWN), sent)
    }

    /** A gate whose calls give up waiting for their turn after 50 ms of real time. */
    private fun slowGate(sender: KeySender) =
        KeyGate({ inFront }, screens, sender, clock = { time.now }, pause = { time.advance(it) }, turnWaitMs = 50)

    /** A clock that only moves when told, running what was due by then. */
    private class VirtualTime {
        var now = 0L
            private set
        private val due = mutableListOf<Pair<Long, () -> Unit>>()

        fun at(time: Long, action: () -> Unit) {
            due += time to action
        }

        fun advance(ms: Long) {
            now += ms
            val ready = due.filter { it.first <= now }
            due -= ready.toSet()
            ready.forEach { it.second() }
        }
    }
}
