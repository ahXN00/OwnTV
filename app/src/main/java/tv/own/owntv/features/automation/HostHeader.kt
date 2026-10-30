package tv.own.owntv.features.automation

/**
 * Whether an HTTP Host header names the server by IP address: a dotted IPv4 address or a bracketed
 * IPv6 address, either optionally followed by `:port`. Host names are what DNS rebinding needs (a
 * page on attacker.example re-points its own name at the TV, and the browser keeps sending
 * `Host: attacker.example`), so anything that is not an IP literal is refused. The check is plain
 * text matching: it never resolves a name.
 */
object HostHeader {
    fun isIpLiteral(value: String?): Boolean {
        val (address, port) = split(value?.trim().orEmpty()) ?: return false
        if (port != null && !isPort(port)) return false
        return if (address.startsWith("[")) isIpv6(address.removeSurrounding("[", "]")) else isIpv4(address)
    }

    /** The address and the port text after it, or null when a bracket is not closed right. */
    private fun split(host: String): Pair<String, String?>? {
        if (host.startsWith("[")) {
            val end = host.indexOf(']')
            if (end < 0) return null
            val rest = host.substring(end + 1)
            return when {
                rest.isEmpty() -> host to null
                rest.startsWith(":") -> host.substring(0, end + 1) to rest.substring(1)
                else -> null
            }
        }
        val colon = host.lastIndexOf(':')
        return if (colon < 0) host to null else host.substring(0, colon) to host.substring(colon + 1)
    }

    private fun isPort(text: String) = text.length in 1..5 && text.all(::isDigit) && text.toInt() in 1..MAX_PORT

    private fun isIpv4(text: String): Boolean {
        val parts = text.split('.')
        return parts.size == 4 && parts.all { it.length in 1..3 && it.all(::isDigit) && it.toInt() <= MAX_OCTET }
    }

    /** Eight groups of up to four hex digits, one `::` standing for missing groups, an optional IPv4 tail. */
    private fun isIpv6(text: String): Boolean {
        if (!text.all { it == ':' || it == '.' || isHex(it) }) return false
        val halves = text.split("::")
        if (halves.size > 2) return false
        val groups = halves.filter { it.isNotEmpty() }.flatMap { it.split(':') }
        if (groups.any { it.isEmpty() }) return false
        val tail = groups.lastOrNull()?.takeIf { '.' in it }
        if (tail != null && !(text.endsWith(tail) && isIpv4(tail))) return false
        val hexGroups = if (tail == null) groups else groups.dropLast(1)
        if (hexGroups.any { it.length > 4 || '.' in it }) return false
        val words = hexGroups.size + if (tail == null) 0 else 2
        return if (halves.size == 2) words <= 7 else words == 8
    }

    private fun isDigit(c: Char) = c in '0'..'9'

    private fun isHex(c: Char) = isDigit(c) || c in 'a'..'f' || c in 'A'..'F'

    private const val MAX_PORT = 65_535
    private const val MAX_OCTET = 255
}
