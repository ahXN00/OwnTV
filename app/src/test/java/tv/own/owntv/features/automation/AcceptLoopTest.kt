package tv.own.owntv.features.automation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AcceptLoopTest {
    private val loopback = InetAddress.getLoopbackAddress()

    @Test
    fun `a hand-off that throws closes that client and the loop goes on`() {
        val server = ServerSocket(0, 50, loopback)
        val handOffs = AtomicInteger()
        val handed = LinkedBlockingQueue<Socket>()
        runLoop(server, { client ->
            handed.put(client)
            if (handOffs.incrementAndGet() == 1) throw IllegalStateException("first client")
        }) {
            val callers = List(2) { Socket(loopback, server.localPort) }
            val first = handed.poll(5, TimeUnit.SECONDS)
            val second = handed.poll(5, TimeUnit.SECONDS)
            assertNotNull(second)
            // Clients are handed off one at a time, so the first is closed by the time the second arrives.
            assertTrue(first.isClosed)
            assertFalse(second.isClosed)
            (callers + second).forEach { it.close() }
        }
    }

    @Test
    fun `an accept failure while the server is open is skipped`() {
        listOf(SecurityException("refused"), SocketException("Too many open files")).forEach { failure ->
            val server = FailingOnce(loopback, failure, closeFirst = false)
            val handed = LinkedBlockingQueue<Socket>()
            runLoop(server, { handed.put(it) }) {
                val caller = Socket(loopback, server.localPort)
                val client = handed.poll(5, TimeUnit.SECONDS)
                assertNotNull(failure.javaClass.name, client)
                listOf(caller, client).forEach { it.close() }
            }
        }
    }

    @Test
    fun `a failure once the server is stopping ends the loop`() = runBlocking {
        listOf(SecurityException("refused"), SocketException("Socket closed")).forEach { failure ->
            val server = FailingOnce(loopback, failure, closeFirst = true)
            withTimeout(5_000) { AcceptLoop.run(server) { it.close() } }
            assertEquals(1, server.calls)
        }
    }

    /** Runs the loop on [server] while [check] runs, then closes [server] and waits for the loop to end. */
    private fun runLoop(server: ServerSocket, handOff: (Socket) -> Unit, check: () -> Unit) = runBlocking {
        val loop = launch(Dispatchers.IO) { AcceptLoop.run(server, handOff) }
        try {
            check()
        } finally {
            // Also on a failed check: an open server would leave the loop blocked in accept forever.
            server.close()
        }
        withTimeout(5_000) { loop.join() }
    }

    /** Throws [failure] from its first accept, after closing itself when [closeFirst]. */
    private class FailingOnce(
        address: InetAddress,
        private val failure: Throwable,
        private val closeFirst: Boolean,
    ) : ServerSocket(0, 50, address) {
        @Volatile var calls = 0

        override fun accept(): Socket {
            calls++
            if (calls > 1) return super.accept()
            if (closeFirst) close()
            throw failure
        }
    }
}
