package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class AutomationGateTest {
    private var now = 1_000_000L
    private var settings = AutomationSettings(enabled = true)
    private val pairing = PairingWindow(clock = { now })
    private val gate = AutomationGate(
        settings = { settings },
        addDevice = { settings = settings.copy(devices = settings.devices + it) },
        pairing = pairing,
        limiter = FailureLimiter(maxFailures = 3, windowMs = 60_000, clock = { now }),
        clock = { now },
    )
    private val first = Peer(InetAddress.getByName("192.168.1.10"))
    private val second = Peer(InetAddress.getByName("192.168.1.11"))
    private val tailnet = Peer(InetAddress.getByName("100.101.102.103"), onTailscaleInterface = true)
    private val tailnetV6 = Peer(InetAddress.getByName("fd7a:115c:a1e0::5"), onTailscaleInterface = true)
    private val carrierNat = Peer(InetAddress.getByName("100.101.102.103"))
    private val internet = Peer(InetAddress.getByName("8.8.8.8"))

    private fun bearer(token: String) = mapOf("authorization" to "Bearer $token")

    private fun pairFirst(): String {
        pairing.open("123456")
        return (gate.pair("123456", "Laptop", first) as AutomationGate.PairResult.Paired).token
    }

    @Test
    fun `pairing stores only the hash and the address`() {
        val token = pairFirst()
        val device = settings.devices.single()
        assertEquals("192.168.1.10", device.address)
        assertEquals(AutomationAccess.hash(token), device.secretHash)
        assertFalse(device.secretHash.contains(token))
        assertNull(pairing.state.value)
    }

    @Test
    fun `pairing needs the open window and the right pin`() {
        assertEquals(AutomationGate.PairResult.WrongPin, gate.pair("123456", "x", first))
        pairing.open("123456")
        assertEquals(AutomationGate.PairResult.WrongPin, gate.pair("000000", "x", first))
        now += 3 * 60_000L
        assertEquals(AutomationGate.PairResult.WrongPin, gate.pair("123456", "x", first))
        assertTrue(settings.devices.isEmpty())
    }

    @Test
    fun `pairing from outside the home network is refused`() {
        pairing.open("123456")
        assertEquals(AutomationGate.PairResult.NotFromHome, gate.pair("123456", "x", internet))
        assertEquals(AutomationGate.PairResult.NotFromHome, gate.pair("123456", "x", tailnet))
        settings = settings.copy(allowTailscale = true)
        assertEquals(AutomationGate.PairResult.NotFromHome, gate.pair("123456", "x", carrierNat))
        assertTrue(gate.pair("123456", "x", tailnet) is AutomationGate.PairResult.Paired)
    }

    @Test
    fun `a home address that came through a vpn tunnel is neither home nor tailscale`() {
        settings = settings.copy(allowTailscale = true)
        val token = pairFirst()
        val tunnelled = first.copy(onTunnel = true)
        assertEquals(AutomationGate.Access.WrongAddress, gate.authorize(bearer(token), tunnelled))
        assertEquals(AutomationGate.Access.WrongAddress, gate.authorize(bearer(token), tunnelled.copy(onTailscaleInterface = true)))
        pairing.open("123456")
        assertEquals(AutomationGate.PairResult.NotFromHome, gate.pair("123456", "x", second.copy(onTunnel = true)))
    }

    @Test
    fun `wrong pins close the window and then lock the address out`() {
        pairing.open("123456")
        repeat(3) { gate.pair("000000", "x", first) }
        pairing.open("123456")
        assertEquals(AutomationGate.PairResult.Blocked, gate.pair("123456", "x", first))
        now += 61_000
        assertTrue(gate.pair("123456", "x", first) is AutomationGate.PairResult.Paired)
    }

    @Test
    fun `a key works only from its own address or tailscale when allowed`() {
        val token = pairFirst()
        assertTrue(gate.authorize(bearer(token), first) is AutomationGate.Access.Granted)
        assertEquals(AutomationGate.Access.WrongAddress, gate.authorize(bearer(token), second))
        assertEquals(AutomationGate.Access.WrongAddress, gate.authorize(bearer(token), tailnet))
        settings = settings.copy(allowTailscale = true)
        assertTrue(gate.authorize(bearer(token), tailnet) is AutomationGate.Access.Granted)
        assertEquals(AutomationGate.Access.WrongAddress, gate.authorize(bearer(token), carrierNat))
    }

    @Test
    fun `a device paired over tailscale stops working when tailscale is switched off`() {
        listOf(tailnet, tailnetV6).forEach { peer ->
            settings = settings.copy(allowTailscale = true)
            pairing.open("123456")
            val token = (gate.pair("123456", "Phone", peer) as AutomationGate.PairResult.Paired).token
            assertTrue(gate.authorize(bearer(token), peer) is AutomationGate.Access.Granted)
            settings = settings.copy(allowTailscale = false)
            assertEquals(AutomationGate.Access.WrongAddress, gate.authorize(bearer(token), peer))
        }
    }

    @Test
    fun `a tailnet address counts only through the tailscale interface, even the one stored`() {
        settings = settings.copy(allowTailscale = true)
        pairing.open("123456")
        val token = (gate.pair("123456", "Phone", tailnet) as AutomationGate.PairResult.Paired).token
        assertEquals("100.101.102.103", settings.devices.single().address)
        assertEquals(AutomationGate.Access.WrongAddress, gate.authorize(bearer(token), carrierNat))
    }

    @Test
    fun `pairing over tailnet ipv6 needs tailscale allowed`() {
        pairing.open("123456")
        assertEquals(AutomationGate.PairResult.NotFromHome, gate.pair("123456", "x", tailnetV6))
        assertEquals(AutomationGate.PairResult.NotFromHome, gate.pair("123456", "x", Peer(tailnetV6.address)))
        settings = settings.copy(allowTailscale = true)
        assertEquals(AutomationGate.PairResult.NotFromHome, gate.pair("123456", "x", Peer(tailnetV6.address)))
        assertTrue(gate.pair("123456", "x", tailnetV6) is AutomationGate.PairResult.Paired)
    }

    @Test
    fun `wrong pins while no window is open do not count`() {
        repeat(5) { assertEquals(AutomationGate.PairResult.WrongPin, gate.pair("000000", "x", first)) }
        pairing.open("123456")
        assertTrue(gate.pair("123456", "x", first) is AutomationGate.PairResult.Paired)
    }

    @Test
    fun `unknown keys lock the address out but the right key still works`() {
        val token = pairFirst()
        assertEquals(AutomationGate.Access.NoKey, gate.authorize(emptyMap(), first))
        repeat(3) { assertEquals(AutomationGate.Access.WrongKey, gate.authorize(bearer("guess$it"), first)) }
        assertEquals(AutomationGate.Access.Blocked, gate.authorize(bearer("guess"), first))
        assertEquals(AutomationGate.Access.Blocked, gate.authorize(emptyMap(), first))
        assertTrue(gate.authorize(bearer(token), first) is AutomationGate.Access.Granted)
    }

    @Test
    fun `a removed device's key stops working`() {
        val token = pairFirst()
        settings = settings.copy(devices = emptyList())
        assertEquals(AutomationGate.Access.WrongKey, gate.authorize(bearer(token), first))
    }

    @Test
    fun `address rules`() {
        assertTrue(AutomationAccess.isPrivateLan(InetAddress.getByName("10.0.0.5")))
        assertTrue(AutomationAccess.isPrivateLan(InetAddress.getByName("172.20.1.1")))
        assertFalse(AutomationAccess.isPrivateLan(InetAddress.getByName("172.32.1.1")))
        assertFalse(AutomationAccess.isPrivateLan(tailnet.address))
        assertTrue(AutomationAccess.isTailscale(tailnet.address))
        assertFalse(AutomationAccess.isTailscale(InetAddress.getByName("100.128.0.1")))
        assertTrue(AutomationAccess.isTailscale(InetAddress.getByName("fd7a:115c:a1e0::1")))
        assertEquals("192.168.1.10", AutomationAccess.normalize(InetAddress.getByAddress(ByteArray(10) + byteArrayOf(-1, -1, -64, -88, 1, 10))))
    }
}
