package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import java.net.URI

/** Checks for values sent to the API, shared by every area so they are read the same way everywhere. */
object ApiValues {
    const val MAX_NAME = 80

    /** The longest address the API takes, the usual limit for a URL. */
    const val MAX_URL = 4096

    /** The longest user name the API takes, far more than any provider hands out. */
    const val MAX_USERNAME = 256

    /** Why the first of [urls] (field name to value) is too long, or null when none is. */
    fun overlongUrl(vararg urls: Pair<String, String?>): String? =
        urls.firstOrNull { (_, value) -> (value?.length ?: 0) > MAX_URL }?.let { "${it.first} must be up to $MAX_URL characters" }

    /**
     * Why the first of [urls] (field name to value) is a copy the API cut down or masked when it showed
     * it ([UrlMask.isMasked]), or null when none is. Saved, it would replace the real address and the
     * login in it.
     */
    fun shortenedUrl(vararg urls: Pair<String, String?>): String? =
        urls.firstOrNull { (_, value) -> value != null && UrlMask.isMasked(value) }
            ?.let { "${it.first} is the shortened or masked copy the API shows; send the full address" }

    /** Why [username] cannot be saved, or null when it can. Blank is refused, as saving it keeps the stored one. */
    fun usernameProblem(username: String): String? = when {
        username.isBlank() -> "username cannot be blank"
        username.length > MAX_USERNAME -> "username must be up to $MAX_USERNAME characters"
        else -> null
    }

    /**
     * Why [password] cannot be saved, or null when it can. Blank is refused: saving it keeps the stored
     * password (SourceActions.update), so an edit that moved the playlist would carry that one along.
     */
    fun passwordProblem(password: String): String? = "password cannot be blank".takeIf { password.isBlank() }

    fun isHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
    }

    /** A playlist schedule such as "OFF", "HOURS_6" or "MANUAL:14"; null when the mode is unknown. */
    fun playlistRefresh(raw: String): PlaylistRefresh? =
        if (PlaylistAutoRefresh.entries.any { it.name == raw.trim().substringBefore(':') }) PlaylistRefresh.parse(raw) else null

    /** A guide schedule such as "OFF", "HOURS_12" or "MANUAL:3"; null when the mode is unknown. */
    fun epgRefresh(raw: String): EpgRefresh? =
        if (EpgAutoRefresh.entries.any { it.name == raw.trim().substringBefore(':') }) EpgRefresh.parse(raw) else null

    fun playlistRefreshModes(): String = PlaylistAutoRefresh.entries.joinToString { it.name }

    fun epgRefreshModes(): String = EpgAutoRefresh.entries.joinToString { it.name }

    /** Keys in [json] that are not in [allowed]. */
    fun unknownKeys(json: JSONObject, allowed: Set<String>): List<String> =
        json.keys().asSequence().filter { it !in allowed }.toList()
}
