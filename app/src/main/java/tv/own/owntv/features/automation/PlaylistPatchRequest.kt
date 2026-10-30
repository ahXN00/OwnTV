package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.stalker.StalkerClient

/** Changes to a saved playlist, as sent to `PATCH /api/v1/playlists/{id}`. Null keeps the current value. */
data class PlaylistPatch(
    val name: String? = null,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val mac: String? = null,
    val userAgent: String? = null,
    val epgUrl: String? = null,
    val httpReferer: String? = null,
    val refresh: PlaylistRefresh? = null,
    val makeDefault: Boolean? = null,
    val syncLive: Boolean? = null,
    val syncMovies: Boolean? = null,
    val syncSeries: Boolean? = null,
    val preferHls: Boolean? = null,
)

sealed interface ParsedPatch {
    data class Ok(val patch: PlaylistPatch) : ParsedPatch
    data class Invalid(val reason: String) : ParsedPatch
}

/**
 * Reads and checks an edit body. `url` is the M3U address or the Xtream/Stalker server; `mac` is a
 * Stalker playlist's MAC; an empty `userAgent`, `epgUrl` or `httpReferer` clears it; `refresh` takes
 * the same values as when adding. An address the API cut down or masked is refused, so is a blank
 * `username`, `password` or `mac`, since saving one keeps the stored value. A `url` on another server,
 * or a new `username` or `mac`, also needs the login again (see [PlaylistMove]).
 */
object PlaylistPatchRequest {
    val FIELDS = setOf(
        "name", "url", "username", "password", "mac", "userAgent", "epgUrl", "httpReferer", "refresh", "default",
        "syncLive", "syncMovies", "syncSeries", "preferHls",
    )

    fun parse(body: JSONObject): ParsedPatch {
        val unknown = ApiValues.unknownKeys(body, FIELDS)
        if (unknown.isNotEmpty()) return ParsedPatch.Invalid("Unknown fields: ${unknown.joinToString()}; allowed: ${FIELDS.joinToString()}")
        if (body.length() == 0) return ParsedPatch.Invalid("Nothing to change")
        val fields = JsonFields(body)
        val patch = PlaylistPatch(
            name = fields.string("name")?.trim(),
            url = fields.string("url")?.trim(),
            username = fields.string("username")?.trim(),
            password = fields.string("password"),
            mac = fields.string("mac")?.trim(),
            userAgent = fields.string("userAgent")?.trim(),
            epgUrl = fields.string("epgUrl")?.trim(),
            httpReferer = fields.string("httpReferer")?.trim(),
            makeDefault = fields.boolean("default"),
            syncLive = fields.boolean("syncLive"),
            syncMovies = fields.boolean("syncMovies"),
            syncSeries = fields.boolean("syncSeries"),
            preferHls = fields.boolean("preferHls"),
        )
        val refreshRaw = fields.string("refresh")
        fields.error?.let { return ParsedPatch.Invalid(it) }
        val refresh = refreshRaw?.let { ApiValues.playlistRefresh(it) ?: return ParsedPatch.Invalid(refreshError()) }
        return check(patch)?.let { ParsedPatch.Invalid(it) } ?: ParsedPatch.Ok(patch.copy(refresh = refresh))
    }

    fun refreshError() = "refresh must be one of ${ApiValues.playlistRefreshModes()} (MANUAL:<days> for a day count)"

    private fun check(patch: PlaylistPatch): String? =
        ApiValues.overlongUrl("url" to patch.url, "epgUrl" to patch.epgUrl)
            ?: ApiValues.shortenedUrl("url" to patch.url, "epgUrl" to patch.epgUrl, "httpReferer" to patch.httpReferer)
            ?: HeaderValue.problem("userAgent", patch.userAgent) ?: HeaderValue.problem("httpReferer", patch.httpReferer) ?: fieldProblem(patch)

    private fun fieldProblem(patch: PlaylistPatch): String? = when {
        patch.name != null && (patch.name.isEmpty() || patch.name.length > ApiValues.MAX_NAME) ->
            "name must be 1 to ${ApiValues.MAX_NAME} characters"
        patch.url != null && !ApiValues.isHttpUrl(patch.url) -> "url must be an http(s) address"
        patch.epgUrl != null && patch.epgUrl.isNotEmpty() && !ApiValues.isHttpUrl(patch.epgUrl) -> "epgUrl must be an http(s) address or empty"
        patch.mac != null && StalkerClient.canonicalizeMac(patch.mac) == null -> "mac must be 12 letters or digits, such as 00:1A:79:12:34:56"
        else -> patch.username?.let(ApiValues::usernameProblem) ?: patch.password?.let(ApiValues::passwordProblem)
    }
}
