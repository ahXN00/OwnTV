package tv.own.owntv.features.automation

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Secrets, PINs and the address rules behind the automation API's access checks. */
object AutomationAccess {
    private val random = SecureRandom()

    /** 256 random bits, URL-safe so it can sit in a header or a shell variable without quoting. */
    fun newSecret(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun newPin(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    /** Only the hash is stored, so a copied preferences file does not hand out working keys. */
    fun hash(secret: String): String =
        MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    fun bearer(headers: Map<String, String>): String? {
        val value = headers["authorization"] ?: return null
        if (!value.startsWith("Bearer ", ignoreCase = true)) return null
        return value.substring(7).trim().takeIf { it.isNotEmpty() }
    }

    /** 10/8, 172.16/12, 192.168/16: the home network a pairing request may come from. */
    fun isPrivateLan(address: InetAddress): Boolean {
        val b = ipv4(address) ?: return false
        return b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168)
    }

    /** Tailscale hands out 100.64.0.0/10 (IPv4) and fd7a:115c:a1e0::/48 (IPv6). */
    fun isTailscale(address: InetAddress): Boolean {
        ipv4(address)?.let { return it[0] == 100 && it[1] in 64..127 }
        val b = address.address
        return address is Inet6Address &&
            (b[0].toInt() and 0xff) == 0xfd && (b[1].toInt() and 0xff) == 0x7a &&
            (b[2].toInt() and 0xff) == 0x11 && (b[3].toInt() and 0xff) == 0x5c &&
            (b[4].toInt() and 0xff) == 0xa1 && (b[5].toInt() and 0xff) == 0xe0
    }

    /** IPv4, including an IPv4 address mapped into IPv6 (::ffff:a.b.c.d). */
    fun isIpv4(address: InetAddress): Boolean = ipv4(address) != null

    /** The address as stored for a paired device; an IPv4-mapped IPv6 address reads as plain IPv4. */
    fun normalize(address: InetAddress): String =
        ipv4(address)?.joinToString(".") ?: address.hostAddress.orEmpty()

    private fun ipv4(address: InetAddress): List<Int>? {
        val b = address.address.map { it.toInt() and 0xff }
        if (address is Inet4Address) return b
        val mapped = b.size == 16 && b.subList(0, 10).all { it == 0 } && b[10] == 0xff && b[11] == 0xff
        return if (mapped) b.subList(12, 16) else null
    }
}
