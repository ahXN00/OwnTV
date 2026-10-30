package tv.own.owntv.features.automation

import tv.own.owntv.core.network.HttpClient

/**
 * Hides logins before the API hands text out. A paired device may manage playlists, but provider
 * logins stay on the TV. No stored login takes part in what comes back, so it is the same whatever the
 * login: a caller that writes guesses into an address and reads it back learns nothing about one.
 *
 * Every address field the API returns (a playlist's address, guide address and referer, a guide
 * source's address, a channel's logo) shows only where it points ([origin]): `scheme://host[:port]`,
 * with anything after that shown as `/…`. Whoever wrote the address, a caller or the provider, already
 * has it, and a login can sit anywhere in its path (`/playlist/user/pass/m3u_plus`), where no pattern
 * tells it from an ordinary one.
 *
 * Text the TV writes that can quote any playlist, or an address a caller sent (log lines, error-log
 * details, the crash report, a guide's or an import's last error, the player's error), has each address
 * in it cut down the same way and login values outside addresses masked by pattern ([maskTvText]). The
 * deliberate trade-off: a login such text quotes outside an address, in a shape no pattern knows, is
 * shown. Only the API's copy is cut down; the TV's own screens still show the full detail.
 */
object UrlMask {
    private const val HIDDEN = "***"
    /** What core puts in place of an Xtream path login. */
    private const val CORE_HIDDEN = "•••"
    /** What [origin] shows in place of everything in an address after its host and port. */
    private const val CUT = "/…"
    /** What [origin] shows in place of a host it does not show, after the scheme if there is one. */
    private const val CUT_ALL = "…"
    /** Where OkHttp ends an address's authority (host, port and user info): `\` counts as `/`. */
    private val AUTHORITY_END = charArrayOf('/', '\\', '?', '#')
    /**
     * An address in text. The scheme is bounded (no real one comes near 32 characters), so a long run of
     * letters costs a few dozen steps per character instead of a search to the end of the run from each
     * one. It is not anchored to the start of a word: an address glued to a digit or a long word before it
     * is still found, by its last 32 scheme characters, and cut down.
     */
    private val ADDRESS = Regex("""[A-Za-z][A-Za-z0-9+.-]{0,31}://[^\s"'<>]+""")
    /** The scheme an address starts with, bounded like [ADDRESS]'s. */
    private val SCHEME = Regex("""^[A-Za-z][A-Za-z0-9+.-]{0,31}://""")
    /** User info that carries a password: `scheme://user:password@`. */
    private val USER_PASSWORD = Regex("""://[^/?#\s:]*:[^/?#\s]+@""")
    /** A host, bracketed for IPv6, and a port of digits only, at the start of an authority without user info. */
    private val HOST_PORT = Regex("""^(?:\[[0-9A-Fa-f:.]*\]|[A-Za-z0-9._~%-]*)(?::\d{1,5})?""")
    private val SECRET_PARAM =
        Regex("""(\b(?:password|pass|pwd|token|key|mac|auth|sig|signature)=)[^&#\s"'<>]*""", RegexOption.IGNORE_CASE)

    /**
     * [url], an address field, cut down to `scheme://host[:port]`. User info goes, and anything else, such
     * as a path, a query or a port that is not a number, shows as [CUT], so a cut copy can be told from a
     * whole one ([isMasked]).
     *
     * The host shown is the one OkHttp, which fetches playlists and guides, connects to: the text after
     * the last `@` before the first `/`, `\`, `?` or `#`. Text before an `@` is never shown, though. An `@`
     * further on means the text before it may be user info all the same, to a reader that does not stop
     * at `\` or for a login with a `/` in its password (`http://me:12/34@x.tv`), while the text after it
     * is path or query to OkHttp, maybe a password's tail (`?password=p@ss`). Neither is shown: `http://…`.
     * A value without a scheme shows its host only when it is nothing but one, with a dot or in brackets
     * (`x.tv:8080/a`); anything else, such as a free-text referer, shows as `…`.
     */
    fun origin(url: String): String {
        val scheme = SCHEME.find(url)?.value.orEmpty()
        val rest = url.substring(scheme.length)
        val end = rest.indexOfAny(AUTHORITY_END).takeIf { it >= 0 } ?: rest.length
        val authority = rest.substring(0, end)
        val host = HOST_PORT.find(authority.substringAfterLast('@'))?.value.orEmpty()
        return when {
            rest.isEmpty() -> url
            host.isEmpty() || rest.indexOf('@', end) >= 0 || scheme.isEmpty() && !isBareHost(host, authority) -> scheme + CUT_ALL
            else -> scheme + host + if (host.length < authority.length || end < rest.length) CUT else ""
        }
    }

    /** Whether [authority], from a value without a scheme, is [host] alone: a name with a dot, or in brackets. */
    private fun isBareHost(host: String, authority: String): Boolean =
        host == authority && (host.startsWith('[') || '.' in host.substringBefore(':'))

    /**
     * [text], which the TV wrote and which can quote any playlist or an address a caller sent, with each
     * address cut down to where it points ([origin]) and the login patterns applied to the rest.
     */
    fun maskTvText(text: String): String =
        text.replace(ADDRESS) { origin(it.value) }.replace(SECRET_PARAM, "\$1$HIDDEN")

    /**
     * Whether [text] holds a secret by pattern: a value for a login parameter ([SECRET_PARAM]), a
     * `user:password@` password or an Xtream path login, found the way core's [HttpClient.redactUrl] finds
     * it. Not a user name alone, such as `?username=` or `https://user@`, nor a name that merely ends like
     * a login word, such as `metadata=`.
     */
    fun hasSecret(text: String): Boolean =
        SECRET_PARAM.findAll(text).any { it.value.length > it.groupValues[1].length } ||
            USER_PASSWORD.containsMatchIn(text) ||
            ADDRESS.findAll(text).any { HttpClient.redactUrl(it.value).contains(CORE_HIDDEN) }

    /**
     * Whether [url] is a copy with something hidden: cut by [origin] (its `…`) or masked (`***` from TV
     * text, `•••` from the TV's own logs and info overlay). Sent back in an edit, it would replace the real
     * address and the login in it, so edits refuse it. No real address holds any of these marks.
     */
    fun isMasked(url: String): Boolean = url.contains('…') || url.contains(HIDDEN) || url.contains(CORE_HIDDEN)
}
