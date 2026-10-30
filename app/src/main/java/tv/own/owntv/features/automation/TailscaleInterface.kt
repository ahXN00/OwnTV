package tv.own.owntv.features.automation

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections

/**
 * Whether a connection came in through Tailscale, judged by the network interface that owns the
 * connection's local address instead of by the caller's address, which carrier-grade NAT can share.
 *
 * On Linux Tailscale names its interface `tailscale0`. On Android, and so on Android TV, the
 * Tailscale app is a VPN app, and Android names every VPN interface `tun0`, `tun1` and so on, so the
 * name alone does not say which app owns it. A `tun` interface therefore counts only when it also
 * carries a Tailscale address (100.64.0.0/10 or fd7a:115c:a1e0::/48), which Tailscale gives the
 * device itself. Another VPN app that numbered its tunnel from 100.64.0.0/10 would pass too; the
 * Java network API cannot tell which app owns an interface.
 *
 * The same lookup says whether a connection came through any tunnel ([isTunnel]), so a home-range
 * caller that a VPN or a subnet router carried in is not taken for the home network. It goes by the
 * interface that owns the address the caller connected to, the only one the Java network API shows.
 * Besides VPN apps' `tun` interfaces, Android's built-in VPNs make `ipsec` ones (IKEv2) and `ppp` ones
 * (legacy PPTP and L2TP). A `ppp` interface can also be a wired PPPoE link; it counts as a tunnel all
 * the same, which errs toward refusing a caller rather than taking it for the home network.
 */
object TailscaleInterface {
    /** What the interface a connection arrived on says about it. */
    data class Arrival(val onTunnel: Boolean, val onTailscale: Boolean)

    /** True for an interface called [name], holding [addresses], that Tailscale runs. */
    fun isTailscale(name: String, addresses: List<InetAddress>): Boolean =
        name.startsWith(TAILSCALE_PREFIX) || (name.startsWith(VPN_PREFIX) && addresses.any(AutomationAccess::isTailscale))

    /** True for a VPN tunnel: Tailscale's interface, a VPN app's `tun` one or one of Android's built-in VPNs. */
    fun isTunnel(name: String): Boolean =
        name.startsWith(TAILSCALE_PREFIX) || TUNNEL_PREFIXES.any(name::startsWith)

    /**
     * What the interface that owns [local], the address a connection arrived at, says about it. When
     * that interface cannot be found, the connection counts as a tunnel and not as Tailscale, so it is
     * refused rather than taken for the home network.
     */
    fun arrival(local: InetAddress): Arrival {
        val network = try {
            NetworkInterface.getByInetAddress(local)
        } catch (e: SocketException) {
            null
        } ?: return Arrival(onTunnel = true, onTailscale = false)
        return Arrival(isTunnel(network.name), isTailscale(network.name, Collections.list(network.inetAddresses)))
    }

    private const val TAILSCALE_PREFIX = "tailscale"
    private const val VPN_PREFIX = "tun"
    /** Tunnels whoever runs them: VPN apps' and Android's built-in IKEv2, PPTP and L2TP VPNs. */
    private val TUNNEL_PREFIXES = listOf(VPN_PREFIX, "ipsec", "ppp")
}
