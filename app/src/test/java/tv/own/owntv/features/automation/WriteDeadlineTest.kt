package tv.own.owntv.features.automation

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WriteDeadlineTest {
    /** Stands in for a socket whose caller stopped reading: a write blocks until it is closed. */
    private class StuckConnection : Closeable {
        private val closedSignal = CountDownLatch(1)
        val closed: Boolean get() = closedSignal.count == 0L

        override fun close() = closedSignal.countDown()

        fun blockedWrite() {
            if (!closedSignal.await(10, TimeUnit.SECONDS)) error("the write was never cut off")
            throw IOException("Socket closed")
        }
    }

    @Test
    fun `a write that blocks past the budget is cut off by closing the connection`() {
        val connection = StuckConnection()
        val started = System.nanoTime()
        assertThrows(IOException::class.java) {
            runBlocking { writeWithin(connection, budgetMs = 100) { connection.blockedWrite() } }
        }
        assertTrue(connection.closed)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)
    }

    @Test
    fun `a write that finishes in time leaves the connection open`() {
        val connection = StuckConnection()
        val result = runBlocking {
            val written = writeWithin(connection, budgetMs = 100) { "sent" }
            delay(300)
            written
        }
        assertEquals("sent", result)
        assertFalse(connection.closed)
    }
}
