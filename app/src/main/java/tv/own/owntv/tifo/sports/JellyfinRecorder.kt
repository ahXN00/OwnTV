package tv.own.owntv.tifo.sports

import android.content.Context
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** The user's Jellyfin server, which records games to its own disk when it is set. */
data class JellyfinConfig(val url: String, val apiKey: String) {
    val isSet: Boolean get() = url.isNotBlank() && apiKey.isNotBlank()
}

/** Where [JellyfinConfig] is kept: TifoTV's own preferences, never core's settings. */
class JellyfinSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): JellyfinConfig = JellyfinConfig(prefs.getString(KEY_URL, "").orEmpty(), prefs.getString(KEY_API_KEY, "").orEmpty())

    fun save(config: JellyfinConfig) {
        prefs.edit().putString(KEY_URL, config.url).putString(KEY_API_KEY, config.apiKey).apply()
    }

    private companion object {
        const val PREFS = "tifo_jellyfin"
        const val KEY_URL = "url"
        const val KEY_API_KEY = "api_key"
    }
}

/** A recording Jellyfin has scheduled. */
data class JellyfinTimer(val id: String, val channelName: String, val startMs: Long, val endMs: Long)

/** Why Jellyfin could not record a game, for the message the screen shows. */
enum class JellyfinFailure { UNREACHABLE, NO_CHANNEL, NO_PROGRAMME }

class JellyfinException(val failure: JellyfinFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/**
 * Schedules recordings on the user's Jellyfin server through its Live TV API, the same calls
 * Jellyfin's own web client makes: find the channel by name (Jellyfin reads the same playlist),
 * find the guide programme airing at the game's start, take Jellyfin's timer defaults for it (the
 * user's padding there) and create the timer.
 */
class JellyfinRecorder(private val http: OkHttpClient) {

    /** Jellyfin's live channels by normalised name, per server, so a game doesn't list them all again. */
    private var channelCache: Pair<JellyfinConfig, Map<String, String>>? = null

    /** The server's name, or a failure: what "Connect" shows. */
    suspend fun test(config: JellyfinConfig): String = withContext(Dispatchers.IO) {
        val info = JSONObject(get(config, "System/Info") { })
        info.optString("ServerName").ifBlank { config.url }
    }

    suspend fun timers(config: JellyfinConfig): List<JellyfinTimer> = withContext(Dispatchers.IO) {
        parseTimers(get(config, "LiveTv/Timers") { })
    }

    /** Records the programme airing at [atMs] on the channel named [channelName]. */
    suspend fun schedule(config: JellyfinConfig, channelName: String, atMs: Long) = withContext(Dispatchers.IO) {
        val channelId = channelId(config, channelName) ?: throw JellyfinException(JellyfinFailure.NO_CHANNEL)
        val at = Instant.ofEpochMilli(atMs).toString()
        val programmes = get(config, "LiveTv/Programs") {
            addQueryParameter("channelIds", channelId)
            addQueryParameter("maxStartDate", at)
            addQueryParameter("minEndDate", at)
            addQueryParameter("limit", "5")
        }
        val programmeId = JSONObject(programmes).optJSONArray("Items")?.optJSONObject(0)?.optString("Id")
            ?.takeIf { it.isNotBlank() } ?: throw JellyfinException(JellyfinFailure.NO_PROGRAMME)
        val defaults = get(config, "LiveTv/Timers/Defaults") { addQueryParameter("programId", programmeId) }
        post(config, "LiveTv/Timers", defaults)
    }

    suspend fun cancel(config: JellyfinConfig, timerId: String) = withContext(Dispatchers.IO) {
        execute(config, request(config, "LiveTv/Timers/$timerId") { }.delete().build())
    }

    private fun channelId(config: JellyfinConfig, channelName: String): String? {
        val channels = channelCache?.takeIf { it.first == config }?.second
            ?: parseChannels(
                get(config, "LiveTv/Channels") {
                    addQueryParameter("enableImages", "false")
                    addQueryParameter("enableUserData", "false")
                    addQueryParameter("addCurrentProgram", "false")
                },
            ).also { channelCache = config to it }
        return findChannel(channels, channelName)
    }

    private fun get(config: JellyfinConfig, path: String, query: HttpUrl.Builder.() -> Unit): String =
        execute(config, request(config, path, query).get().build())

    private fun post(config: JellyfinConfig, path: String, json: String): String =
        execute(config, request(config, path) { }.post(json.toRequestBody(JSON)).build())

    private fun request(config: JellyfinConfig, path: String, query: HttpUrl.Builder.() -> Unit): Request.Builder {
        val base = baseUrl(config.url) ?: throw JellyfinException(JellyfinFailure.UNREACHABLE)
        val url = base.newBuilder().addPathSegments(path).apply(query).build()
        return Request.Builder().url(url).header("Authorization", authorization(config.apiKey))
    }

    private fun execute(config: JellyfinConfig, request: Request): String = try {
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw JellyfinException(JellyfinFailure.UNREACHABLE)
            response.body.string()
        }
    } catch (e: JellyfinException) {
        throw e
    } catch (e: Exception) {
        throw JellyfinException(JellyfinFailure.UNREACHABLE, e)
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        /** "192.168.1.20:8096" is taken as http://192.168.1.20:8096/. */
        internal fun baseUrl(raw: String): HttpUrl? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            return "$withScheme/".toHttpUrlOrNull()
        }

        internal fun authorization(apiKey: String): String =
            "MediaBrowser Client=\"TifoTV\", Device=\"Android TV\", DeviceId=\"tifotv\", Version=\"1\", Token=\"" + apiKey.trim() + "\""

        internal fun parseChannels(json: String): Map<String, String> {
            val items = JSONObject(json).optJSONArray("Items") ?: return emptyMap()
            return buildMap {
                for (item in items.objects()) {
                    val name = item.optString("Name")
                    val id = item.optString("Id")
                    if (name.isNotBlank() && id.isNotBlank()) putIfAbsent(channelKey(name), id)
                }
            }
        }

        /** The same channel by name: exactly, else the one whose name holds the other's. */
        internal fun findChannel(channels: Map<String, String>, channelName: String): String? {
            val key = channelKey(channelName)
            if (key.isEmpty()) return null
            channels[key]?.let { return it }
            return channels.entries
                .filter { (name, _) -> name.contains(key) || key.contains(name) }
                .minByOrNull { (name, _) -> kotlin.math.abs(name.length - key.length) }
                ?.value
        }

        internal fun parseTimers(json: String): List<JellyfinTimer> {
            val items = JSONObject(json).optJSONArray("Items") ?: return emptyList()
            return items.objects().mapNotNull { t ->
                val id = t.optString("Id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val start = parseEspnDate(t.optString("StartDate")) ?: return@mapNotNull null
                val end = parseEspnDate(t.optString("EndDate")) ?: start
                if (t.optString("Status") == "Cancelled") return@mapNotNull null
                JellyfinTimer(id, t.optString("ChannelName"), start, end)
            }
        }

        /** Whether [timer] records the programme starting at [startMs] on [channelName]. */
        internal fun covers(timer: JellyfinTimer, channelName: String, startMs: Long): Boolean {
            val at = startMs + 60_000L
            val a = channelKey(timer.channelName)
            val b = channelKey(channelName)
            val sameChannel = a.isNotEmpty() && (a == b || a.contains(b) || b.contains(a))
            return sameChannel && timer.startMs <= at && timer.endMs >= at
        }

        private fun channelKey(name: String): String =
            SportsMatching.normalize(name).replace(NON_ALNUM, " ").trim().replace(SPACES, " ")

        private val NON_ALNUM = Regex("[^\\p{L}\\p{N}+]+")
        private val SPACES = Regex(" +")

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
