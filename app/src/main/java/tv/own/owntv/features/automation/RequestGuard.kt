package tv.own.owntv.features.automation

import tv.own.owntv.features.automation.ApiReply.error

/**
 * Header checks that keep web pages out of the API. A page open in a browser on the home network
 * can send requests to the TV: simple POSTs need no CORS preflight, and with DNS rebinding (the
 * page points its own host name at the TV) it can even read the answers. The API's clients are
 * scripts that address the TV by IP, so every request must carry an IP literal as its Host
 * ([HostHeader]). Pairing, the one route without a key, also refuses requests with an Origin header
 * (only browsers send one) and bodies not declared as JSON, which a page cannot declare without a
 * preflight the server never approves.
 */
object RequestGuard {
    /** The refusal for a request whose Host is not an IP address, or null when it is one. */
    fun host(headers: Map<String, String>): HttpResponse? =
        if (HostHeader.isIpLiteral(headers["host"])) null else error(400, "Address the TV by its IP address, not a host name")

    /** The refusal for a pairing request a web page could have sent, or null when it looks like a script's. */
    fun pairing(headers: Map<String, String>): HttpResponse? = when {
        "origin" in headers -> error(403, "Pairing from a web page is not allowed")
        mediaType(headers["content-type"]) != JSON -> error(415, "Send the pairing request as application/json")
        else -> null
    }

    private fun mediaType(value: String?) = value?.substringBefore(';')?.trim()?.lowercase()

    private const val JSON = "application/json"
}
