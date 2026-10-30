package tv.own.owntv.features.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class ConnectionLimiterTest {
    private val limiter = ConnectionLimiter(perCaller = 2)

    private fun ip(text: String) = InetAddress.getByName(text)

    @Test
    fun `one address gets at most its share`() {
        assertTrue(limiter.tryAcquire(ip("192.168.1.10")))
        assertTrue(limiter.tryAcquire(ip("192.168.1.10")))
        assertFalse(limiter.tryAcquire(ip("192.168.1.10")))
        assertTrue(limiter.tryAcquire(ip("192.168.1.11")))
    }

    @Test
    fun `a released slot can be taken again`() {
        repeat(2) { limiter.tryAcquire(ip("192.168.1.10")) }
        limiter.release(ip("192.168.1.10"))
        assertTrue(limiter.tryAcquire(ip("192.168.1.10")))
        assertFalse(limiter.tryAcquire(ip("192.168.1.10")))
    }

    @Test
    fun `releasing more than was taken does not add slots`() {
        limiter.release(ip("192.168.1.10"))
        limiter.tryAcquire(ip("192.168.1.10"))
        limiter.release(ip("192.168.1.10"))
        limiter.release(ip("192.168.1.10"))
        assertTrue(limiter.tryAcquire(ip("192.168.1.10")))
        assertTrue(limiter.tryAcquire(ip("192.168.1.10")))
        assertFalse(limiter.tryAcquire(ip("192.168.1.10")))
    }

    @Test
    fun `ipv6 callers count by their slash 64, so new addresses buy no slots`() {
        assertTrue(limiter.tryAcquire(ip("fd12:3456:789a:1::10")))
        assertTrue(limiter.tryAcquire(ip("fd12:3456:789a:1:aaaa:bbbb:cccc:dddd")))
        assertFalse(limiter.tryAcquire(ip("fd12:3456:789a:1::11")))
        assertTrue(limiter.tryAcquire(ip("fe80::1")))
        assertTrue(limiter.tryAcquire(ip("fe80::2")))
        assertFalse(limiter.tryAcquire(ip("fe80::3")))
        assertTrue(limiter.tryAcquire(ip("fd12:3456:789a:2::10")))
        limiter.release(ip("fd12:3456:789a:1::99"))
        assertTrue(limiter.tryAcquire(ip("fd12:3456:789a:1::12")))
    }

    @Test
    fun `tailnet ipv6 addresses count one by one, and mapped ipv4 as ipv4`() {
        repeat(2) { assertTrue(limiter.tryAcquire(ip("fd7a:115c:a1e0::1"))) }
        assertFalse(limiter.tryAcquire(ip("fd7a:115c:a1e0::1")))
        assertTrue(limiter.tryAcquire(ip("fd7a:115c:a1e0::2")))
        val mapped = InetAddress.getByAddress(ByteArray(10) + byteArrayOf(-1, -1, -64, -88, 1, 10))
        repeat(2) { assertTrue(limiter.tryAcquire(mapped)) }
        assertFalse(limiter.tryAcquire(ip("192.168.1.10")))
    }
}
