package tv.own.owntv.features.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostHeaderTest {
    @Test
    fun `ip literals with or without a port are accepted`() {
        listOf("192.168.1.20", "192.168.1.20:8090", "10.0.0.1:1", "[::1]", "[::1]:8090", "[fd7a:115c:a1e0::5]:8090", "[fe80::1]", "[::ffff:192.168.1.20]", "[1:2:3:4:5:6:7:8]", "[::]")
            .forEach { assertTrue(it, HostHeader.isIpLiteral(it)) }
    }

    @Test
    fun `host names and missing hosts are refused`() {
        listOf(null, "", "localhost", "localhost:8090", "tv.example", "tv.example:8090", "192.168.1.20.nip.io", "shield", "1.2.3.4.example")
            .forEach { assertFalse(it.toString(), HostHeader.isIpLiteral(it)) }
    }

    @Test
    fun `malformed addresses and ports are refused`() {
        listOf("256.1.1.1", "1.2.3", "1.2.3.4.5", "192.168.1.20:", "192.168.1.20:0", "192.168.1.20:65536", "192.168.1.20:80:80", "192.168.1.20:http")
            .forEach { assertFalse(it, HostHeader.isIpLiteral(it)) }
        listOf("::1", "[::1", "[::1]x", "[:::]", "[1::2::3]", "[12345::1]", "[1:2:3:4:5:6:7]", "[1.2.3.4::]", "[fe80::1%25eth0]", "[g::1]")
            .forEach { assertFalse(it, HostHeader.isIpLiteral(it)) }
    }
}
