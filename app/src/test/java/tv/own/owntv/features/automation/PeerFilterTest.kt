package tv.own.owntv.features.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PeerFilterTest {
    private fun peer(text: String, onTailscaleInterface: Boolean = false) =
        Peer(InetAddress.getByName(text), onTailscaleInterface)

    @Test
    fun `home network, loopback and link-local callers are read`() {
        listOf("127.0.0.1", "::1", "10.0.0.5", "172.16.0.1", "172.31.255.254", "192.168.1.10", "169.254.10.20", "fd12:3456::1", "fc00::1", "fe80::1")
            .forEach { assertTrue(it, PeerFilter.admits(peer(it), allowTailscale = false)) }
    }

    @Test
    fun `internet and carrier-grade nat callers are closed unread`() {
        listOf("8.8.8.8", "172.32.0.1", "100.101.102.103", "2001:db8::1", "2a00:1450:4001::1")
            .forEach { assertFalse(it, PeerFilter.admits(peer(it), allowTailscale = true)) }
    }

    @Test
    fun `tailscale callers are read only when allowed and on the tailscale interface`() {
        val tailnet = peer("100.101.102.103", onTailscaleInterface = true)
        assertTrue(PeerFilter.admits(tailnet, allowTailscale = true))
        assertFalse(PeerFilter.admits(tailnet, allowTailscale = false))
        assertTrue(PeerFilter.admits(peer("fd7a:115c:a1e0::5", onTailscaleInterface = true), allowTailscale = true))
        assertFalse(PeerFilter.admits(peer("8.8.8.8", onTailscaleInterface = true), allowTailscale = true))
    }

    @Test
    fun `home-range callers that came through a vpn tunnel are closed unread`() {
        listOf("192.168.1.10", "10.0.0.5", "172.16.0.1", "fd12:3456::1", "169.254.10.20").forEach { address ->
            val tunnelled = Peer(InetAddress.getByName(address), onTailscaleInterface = false, onTunnel = true)
            assertFalse(address, PeerFilter.admits(tunnelled, allowTailscale = true))
            assertFalse(address, PeerFilter.admits(tunnelled.copy(onTailscaleInterface = true), allowTailscale = true))
        }
        assertTrue(PeerFilter.admits(peer("100.101.102.103", onTailscaleInterface = true), allowTailscale = true))
    }

    @Test
    fun `tailnet ipv6 is not home network although it sits inside fc00 slash 7`() {
        val tailnet = peer("fd7a:115c:a1e0::5", onTailscaleInterface = true)
        assertFalse(PeerFilter.isLocal(tailnet.address))
        assertFalse(PeerFilter.admits(tailnet, allowTailscale = false))
        assertFalse(PeerFilter.admits(peer("fd7a:115c:a1e0::5"), allowTailscale = true))
        assertFalse(PeerFilter.admits(peer("fd7a:115c:a1e0::5"), allowTailscale = false))
        assertTrue(PeerFilter.admits(peer("fd7a:115c:a1e1::5"), allowTailscale = false))
    }

    @Test
    fun `with tailscale off no tailnet caller is read, on any interface`() {
        listOf("100.64.0.1", "100.101.102.103", "100.127.255.254", "fd7a:115c:a1e0::1", "fd7a:115c:a1e0:ab12:4843:cd96:6258:b240")
            .forEach {
                assertFalse(it, PeerFilter.admits(peer(it, onTailscaleInterface = true), allowTailscale = false))
                assertFalse(it, PeerFilter.admits(peer(it), allowTailscale = false))
            }
    }
}
