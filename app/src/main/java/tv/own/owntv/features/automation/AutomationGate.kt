package tv.own.owntv.features.automation

import java.util.UUID

/**
 * Who may talk to the automation API. A device pairs once — from the home network (or Tailscale,
 * when allowed), with the PIN the television shows — and gets a key. After that each request needs
 * that key AND must come from the address the device paired from, or from Tailscale when allowed.
 * "From Tailscale" means a tailnet address that came in on the Tailscale interface ([Peer.viaTailscale]),
 * and a device paired over Tailscale works only that way, only while Tailscale is allowed. A home
 * address that came in through a VPN tunnel ([Peer.onTunnel]) is neither home nor Tailscale.
 *
 * Wrong keys, and wrong PINs while a pairing window is open, count toward a per-address lockout.
 * The key is checked before the lockout, so a paired device keeps working when something else
 * behind the same address (a web page, a container) runs up failures; and a PIN sent while no
 * window is open cannot be a guess at anything, so it does not count.
 */
class AutomationGate(
    private val settings: () -> AutomationSettings,
    private val addDevice: (PairedDevice) -> Unit,
    private val pairing: PairingWindow,
    private val limiter: FailureLimiter = FailureLimiter(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface PairResult {
        data class Paired(val token: String, val device: PairedDevice) : PairResult
        data object WrongPin : PairResult
        data object NotFromHome : PairResult
        data object Blocked : PairResult
    }

    sealed interface Access {
        data class Granted(val device: PairedDevice) : Access
        data object NoKey : Access
        data object WrongKey : Access
        data object WrongAddress : Access
        data object Blocked : Access
    }

    fun pair(pin: String, name: String, peer: Peer): PairResult {
        val address = AutomationAccess.normalize(peer.address)
        if (limiter.isBlocked(address)) return PairResult.Blocked
        if (!fromHomeNetwork(peer) && !viaTailscale(peer)) return PairResult.NotFromHome
        when (pairing.tryPin(pin)) {
            PairingWindow.PinCheck.ACCEPTED -> limiter.clear(address)
            PairingWindow.PinCheck.WRONG -> {
                limiter.recordFailure(address)
                return PairResult.WrongPin
            }
            PairingWindow.PinCheck.NOT_OPEN -> return PairResult.WrongPin
        }
        val token = AutomationAccess.newSecret()
        val device = PairedDevice(
            id = UUID.randomUUID().toString(),
            name = cleanName(name).ifEmpty { address },
            address = address,
            secretHash = AutomationAccess.hash(token),
            pairedAt = clock(),
        )
        addDevice(device)
        return PairResult.Paired(token, device)
    }

    fun authorize(headers: Map<String, String>, peer: Peer): Access {
        val address = AutomationAccess.normalize(peer.address)
        val token = AutomationAccess.bearer(headers)
        val device = token?.let(::deviceWith)
        if (device == null) {
            if (limiter.isBlocked(address)) return Access.Blocked
            if (token == null) return Access.NoKey
            limiter.recordFailure(address)
            return Access.WrongKey
        }
        if (!fromHomeAddress(device, peer, address) && !viaTailscale(peer)) return Access.WrongAddress
        return Access.Granted(device)
    }

    /**
     * The device's own address on the home network. A Tailscale address never counts here, even the
     * one the device paired from: a device paired over Tailscale must stop working once Tailscale is
     * switched off, and carrier-grade NAT hands out the same 100.64.0.0/10 addresses to strangers.
     */
    private fun fromHomeAddress(device: PairedDevice, peer: Peer, address: String) =
        device.address == address && !AutomationAccess.isTailscale(peer.address) && !peer.onTunnel

    /** A private address that did not come through a VPN tunnel, which can carry another network's. */
    private fun fromHomeNetwork(peer: Peer) = AutomationAccess.isPrivateLan(peer.address) && !peer.onTunnel

    private fun deviceWith(token: String): PairedDevice? {
        val hash = AutomationAccess.hash(token)
        return settings().devices.firstOrNull { AutomationAccess.constantTimeEquals(it.secretHash, hash) }
    }

    private fun viaTailscale(peer: Peer) = settings().allowTailscale && peer.viaTailscale

    private fun cleanName(raw: String) = raw.filter { !it.isISOControl() }.trim().take(MAX_NAME)

    private companion object {
        const val MAX_NAME = 40
    }
}
