package tv.own.owntv.features.automation

import java.net.InetAddress

/**
 * Where a connection came from: the caller's address, whether the connection arrived through the
 * Tailscale network interface, and whether it arrived through any VPN tunnel, Tailscale's included
 * ([TailscaleInterface]). The address alone cannot say "Tailscale": carrier-grade NAT hands out the
 * same 100.64.0.0/10 range, so a caller from that range counts as a tailnet device only when the
 * connection also came in on Tailscale's interface. Nor can it say "home network": a VPN or a
 * Tailscale subnet router can carry another network's 10/8 or 192.168/16 callers in through a tunnel.
 */
data class Peer(
    val address: InetAddress,
    val onTailscaleInterface: Boolean = false,
    val onTunnel: Boolean = onTailscaleInterface,
) {
    /** A Tailscale address that arrived on the Tailscale interface: the only kind of tailnet caller to trust. */
    val viaTailscale: Boolean get() = onTailscaleInterface && AutomationAccess.isTailscale(address)
}
