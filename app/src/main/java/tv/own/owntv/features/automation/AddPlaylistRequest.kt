package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.core.settings.PlaylistRefresh

/** A playlist to add, as sent to `POST /api/v1/playlists`. */
sealed interface AddPlaylist {
    val name: String
    val epgUrl: String
    val userAgent: String
    val refresh: PlaylistRefresh
    val makeDefault: Boolean

    data class M3u(
        override val name: String,
        val url: String,
        override val epgUrl: String = "",
        override val userAgent: String = "",
        override val refresh: PlaylistRefresh = PlaylistRefresh.OFF,
        override val makeDefault: Boolean = false,
    ) : AddPlaylist

    data class Xtream(
        override val name: String,
        val server: String,
        val username: String,
        val password: String,
        override val epgUrl: String = "",
        override val userAgent: String = "",
        override val refresh: PlaylistRefresh = PlaylistRefresh.OFF,
        override val makeDefault: Boolean = false,
    ) : AddPlaylist
}

sealed interface ParsedAdd {
    data class Ok(val request: AddPlaylist) : ParsedAdd
    data class Invalid(val reason: String) : ParsedAdd
}

/**
 * Reads and checks an add-playlist body. Fields mirror the Add Playlist form: `type` ("m3u" or
 * "xtream"), `name`, `url` or `server`/`username`/`password`, and optional `epgUrl`, `userAgent`,
 * `refresh` (a stored refresh value such as "OFF", "HOURS_6" or "MANUAL:1") and `default`. A blank
 * password, a user name over [ApiValues.MAX_USERNAME] characters or an address that is the copy the API
 * cut down when it showed it ([ApiValues.shortenedUrl]) is refused.
 */
object AddPlaylistRequest {
    fun parse(body: String): ParsedAdd {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return ParsedAdd.Invalid("Body must be a JSON object")
        val urls = arrayOf("url" to json.optString("url"), "server" to json.optString("server"), "epgUrl" to json.optString("epgUrl"))
        (ApiValues.overlongUrl(*urls) ?: ApiValues.shortenedUrl(*urls))?.let { return ParsedAdd.Invalid(it) }
        val name = json.optString("name").trim()
        if (name.isEmpty() || name.length > ApiValues.MAX_NAME) return ParsedAdd.Invalid("name is required (up to ${ApiValues.MAX_NAME} characters)")
        val epgUrl = json.optString("epgUrl").trim()
        if (epgUrl.isNotEmpty() && !ApiValues.isHttpUrl(epgUrl)) return ParsedAdd.Invalid("epgUrl must be an http(s) address")
        val userAgent = json.optString("userAgent").trim()
        HeaderValue.problem("userAgent", userAgent)?.let { return ParsedAdd.Invalid(it) }
        val refresh = ApiValues.playlistRefresh(json.optString("refresh", "OFF"))
            ?: return ParsedAdd.Invalid(PlaylistPatchRequest.refreshError())
        val makeDefault = json.optBoolean("default", false)
        return when (json.optString("type").lowercase()) {
            "m3u" -> {
                val url = json.optString("url").trim()
                if (!ApiValues.isHttpUrl(url)) return ParsedAdd.Invalid("url must be an http(s) address")
                ParsedAdd.Ok(AddPlaylist.M3u(name, url, epgUrl, userAgent, refresh, makeDefault))
            }
            "xtream" -> {
                val server = json.optString("server").trim()
                val username = json.optString("username").trim()
                val password = json.optString("password")
                if (!ApiValues.isHttpUrl(server)) return ParsedAdd.Invalid("server must be an http(s) address")
                if (username.isEmpty() || password.isEmpty()) return ParsedAdd.Invalid("username and password are required")
                (ApiValues.usernameProblem(username) ?: ApiValues.passwordProblem(password))?.let { return ParsedAdd.Invalid(it) }
                ParsedAdd.Ok(AddPlaylist.Xtream(name, server, username, password, epgUrl, userAgent, refresh, makeDefault))
            }
            else -> ParsedAdd.Invalid("type must be \"m3u\" or \"xtream\"")
        }
    }
}
