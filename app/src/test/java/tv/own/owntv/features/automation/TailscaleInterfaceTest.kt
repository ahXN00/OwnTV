package tv.own.owntv.features.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class TailscaleInterfaceTest {
    private fun ip(text: String) = InetAddress.getByName(text)

    @Test
    fun `the tailscale interface counts by name`() {
        assertTrue(TailscaleInterface.isTailscale("tailscale0", listOf(ip("100.101.102.103"))))
        assertTrue(TailscaleInterface.isTailscale("tailscale0", emptyList()))
    }

    @Test
    fun `a vpn tunnel counts only when it holds a tailscale address`() {
        assertTrue(TailscaleInterface.isTailscale("tun0", listOf(ip("100.101.102.103"))))
        assertTrue(TailscaleInterface.isTailscale("tun1", listOf(ip("fe80::1"), ip("fd7a:115c:a1e0::5"))))
        assertFalse(TailscaleInterface.isTailscale("tun0", listOf(ip("10.8.0.2"))))
        assertFalse(TailscaleInterface.isTailscale("tun0", emptyList()))
    }

    @Test
    fun `other interfaces never count, even with a carrier-grade nat address`() {
        assertFalse(TailscaleInterface.isTailscale("wlan0", listOf(ip("100.101.102.103"))))
        assertFalse(TailscaleInterface.isTailscale("eth0", listOf(ip("192.168.1.20"))))
    }

    @Test
    fun `tailscale's interface and every vpn tunnel are tunnels, other interfaces are not`() {
        listOf("tailscale0", "tun0", "tun1").forEach { assertTrue(it, TailscaleInterface.isTunnel(it)) }
        listOf("wlan0", "eth0", "lo", "rmnet0").forEach { assertFalse(it, TailscaleInterface.isTunnel(it)) }
    }

    @Test
    fun `android's built-in ipsec and ppp vpns are tunnels, wifi direct is not`() {
        listOf("ipsec0", "ipsec1", "ppp0").forEach { assertTrue(it, TailscaleInterface.isTunnel(it)) }
        listOf("p2p0", "p2p-wlan0-0").forEach { assertFalse(it, TailscaleInterface.isTunnel(it)) }
        assertFalse(TailscaleInterface.isTailscale("ppp0", listOf(ip("100.101.102.103"))))
    }

    @Test
    fun `a peer is a tailnet caller only with both the address and the interface`() {
        assertTrue(Peer(ip("100.101.102.103"), onTailscaleInterface = true).viaTailscale)
        assertFalse(Peer(ip("100.101.102.103")).viaTailscale)
        assertFalse(Peer(ip("192.168.1.10"), onTailscaleInterface = true).viaTailscale)
    }

    @Test
    fun `the ipv6 tailnet range counts on a vpn tunnel and for a peer`() {
        assertTrue(TailscaleInterface.isTailscale("tun0", listOf(ip("fd7a:115c:a1e0:ab12:4843:cd96:6258:b240"))))
        assertFalse(TailscaleInterface.isTailscale("tun0", listOf(ip("fd7a:115c:a1e1::5"), ip("fd12:3456::1"))))
        assertTrue(Peer(ip("fd7a:115c:a1e0::5"), onTailscaleInterface = true).viaTailscale)
        assertFalse(Peer(ip("fd7a:115c:a1e0::5")).viaTailscale)
        assertFalse(Peer(ip("fd12:3456::1"), onTailscaleInterface = true).viaTailscale)
    }
}
