package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.SocketTimeoutException

class DeadlineInputStreamTest {
    private var now = 0L
    private val timeouts = mutableListOf<Int>()

    private fun deadline(input: InputStream) = DeadlineInputStream(input, 15_000, { timeouts += it }, { now })

    /** Reads a whole request the way the server does, with [onHead] between the head and the body. */
    private fun readRequest(input: BufferedInputStream, onHead: () -> Unit = {}): HttpRequest? {
        val head = AutomationHttp.readHead(input) ?: return null
        onHead()
        return AutomationHttp.readBody(input, head)
    }

    @Test
    fun `each read waits only for what is left of the budget`() {
        val input = deadline("abc".byteInputStream())
        assertEquals('a'.code, input.read())
        now += 9_000
        assertEquals('b'.code, input.read())
        assertEquals(listOf(15_000, 6_000), timeouts)
    }

    @Test
    fun `reads past the budget fail even when bytes are waiting`() {
        val input = deadline("abc".byteInputStream())
        input.read()
        now += 15_000
        assertThrows(SocketTimeoutException::class.java) { input.read() }
        assertThrows(SocketTimeoutException::class.java) { input.read(ByteArray(2), 0, 2) }
    }

    @Test
    fun `a header trickled in a byte at a time stops at the deadline`() {
        val trickle = object : InputStream() {
            override fun read(): Int {
                now += 1_000
                return 'a'.code
            }
        }
        val input = BufferedInputStream(deadline(trickle), 1)
        assertThrows(SocketTimeoutException::class.java) { readRequest(input) }
        assertEquals(15, timeouts.size)
    }

    private fun headed(input: InputStream) = DeadlineInputStream(input, 15_000, { timeouts += it }, { now }, headBudgetMs = 5_000)

    @Test
    fun `a head still arriving after its own budget stops there`() {
        val trickle = object : InputStream() {
            override fun read(): Int {
                now += 1_000
                return 'a'.code
            }
        }
        val input = headed(trickle)
        assertThrows(SocketTimeoutException::class.java) { readRequest(BufferedInputStream(input, 1), input::headDone) }
        assertEquals(listOf(5_000, 4_000, 3_000, 2_000, 1_000), timeouts)
    }

    /** Sends [text] one byte per read, at once for the head and [bodyByteMs] apart for the body. */
    private fun slowBody(text: String, bodyByteMs: Long) = object : InputStream() {
        private val headLength = text.indexOf("\r\n\r\n") + 4
        private var index = 0

        override fun read(): Int {
            if (index >= text.length) return -1
            if (index >= headLength) now += bodyByteMs
            return text[index++].code
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val c = read()
            if (c < 0) return -1
            b[off] = c.toByte()
            return 1
        }
    }

    private val postWithBody = "POST /x HTTP/1.1\r\nContent-Length: 3\r\n\r\nabc"

    @Test
    fun `once the head is in the body may use the rest of the whole budget`() {
        val input = headed(slowBody(postWithBody, bodyByteMs = 3_000))
        val parsed = readRequest(BufferedInputStream(input, 1), input::headDone)
        assertEquals("abc", parsed?.body)
        assertEquals(5_000, timeouts.first())
        assertEquals(listOf(15_000, 12_000, 9_000), timeouts.takeLast(3))
    }

    @Test
    fun `without the head marked done the body is held to the head budget`() {
        val input = headed(slowBody(postWithBody, bodyByteMs = 3_000))
        assertThrows(SocketTimeoutException::class.java) { readRequest(BufferedInputStream(input, 1)) }
    }
}
