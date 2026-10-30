package tv.own.owntv.features.automation

import java.net.InetAddress

/**
 * Open connections per caller. The server has a limited number of slots, so without a cap per
 * caller one machine could take all of them and shut every other device out until its requests
 * time out. Callers with no open connection are forgotten, so the table never outgrows the slots.
 *
 * An IPv4 caller is its address. An IPv6 caller is its /64: a host picks its own addresses inside
 * its network's /64 (SLAAC and privacy addresses), so counting single IPv6 addresses would hand one
 * machine as many slots as it cares to make addresses. Tailscale addresses are the exception and
 * count one by one: WireGuard only accepts a tailnet node's packets from the address the tailnet gave
 * it, and every node shares the same /64, so grouping them would make the whole tailnet one caller.
 */
class ConnectionLimiter(private val perCaller: Int) {
    private val open = HashMap<String, Int>()

    /** Takes a slot for [address], or returns false when its caller already holds [perCaller]. */
    @Synchronized
    fun tryAcquire(address: InetAddress): Boolean {
        val caller = callerOf(address)
        val count = open[caller] ?: 0
        if (count >= perCaller) return false
        open[caller] = count + 1
        return true
    }

    /** Gives back a slot taken by [tryAcquire]. */
    @Synchronized
    fun release(address: InetAddress) {
        val caller = callerOf(address)
        val count = open[caller] ?: return
        if (count <= 1) open.remove(caller) else open[caller] = count - 1
    }

    private fun callerOf(address: InetAddress): String {
        if (AutomationAccess.isIpv4(address) || AutomationAccess.isTailscale(address)) return AutomationAccess.normalize(address)
        return address.address.take(PREFIX_BYTES).joinToString("", postfix = "/64") { "%02x".format(it) }
    }

    private companion object {
        /** 64 bits: the network half of an IPv6 address. */
        const val PREFIX_BYTES = 8
    }
}
