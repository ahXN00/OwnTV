package tv.own.owntv.features.automation

import java.net.Inet6Address
import java.net.InetAddress

/**
 * Which callers the server reads at all. The listener binds every interface, so on a TV with a
 * public IPv6 address anyone on the internet could open connections and hold the few slots before
 * any key is checked. The API is for the home network, so the server closes any other connection
 * before reading a byte: only loopback, private and link-local IPv4 (10/8, 172.16/12, 192.168/16,
 * 169.254/16), IPv6 unique-local and link-local (fc00::/7, fe80::/10) and, when the user allows
 * Tailscale, tailnet callers that came in on the Tailscale interface ([Peer.viaTailscale]). A home-range
 * caller that came in through a VPN tunnel ([Peer.onTunnel]) is not on the home network and is closed too.
 *
 * Tailscale's own ranges are never "local", although its IPv6 range (fd7a:115c:a1e0::/48) sits
 * inside fc00::/7: otherwise a tailnet caller would get in with Tailscale switched off.
 */
object PeerFilter {
    fun admits(peer: Peer, allowTailscale: Boolean): Boolean =
        (isLocal(peer.address) && !peer.onTunnel) || (allowTailscale && peer.viaTailscale)

    /** Loopback, private or link-local, and not Tailscale: an address only the home network reaches. */
    fun isLocal(address: InetAddress): Boolean =
        !AutomationAccess.isTailscale(address) && (
            address.isLoopbackAddress || address.isLinkLocalAddress ||
                AutomationAccess.isPrivateLan(address) || isUniqueLocal(address)
            )

    private fun isUniqueLocal(address: InetAddress) =
        address is Inet6Address && (address.address[0].toInt() and ULA_MASK) == ULA_PREFIX

    private const val ULA_MASK = 0xfe
    private const val ULA_PREFIX = 0xfc
}
