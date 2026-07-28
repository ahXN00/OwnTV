package tv.own.owntv.core.parser

import android.os.SystemClock
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.HttpClient
import java.io.InputStream
import java.net.URLEncoder

// --- Parsed Xtream models ---
data class XtCategory(val id: String, val name: String)
data class XtLiveStream(
    val streamId: String, val name: String, val icon: String?, val epgChannelId: String?,
    val categoryId: String?, val num: Int?,
    /** `tv_archive` = 1 → catch-up available; `tv_archive_duration` = days of archive kept. */
    val archive: Boolean = false, val archiveDays: Int = 0,
)
data class XtVod(
    val streamId: String, val name: String, val icon: String?, val rating: Double?, val plot: String?,
    val categoryId: String?, val containerExt: String?, val added: Long?,
)
data class XtSeries(
    val seriesId: String, val name: String, val cover: String?, val plot: String?,
    val rating: Double?, val categoryId: String?, val year: Int?,
    val added: Long? = null, val lastModified: Long? = null,
)
data class XtEpisode(
    val id: String, val seasonNumber: Int, val episodeNumber: Int, val title: String, val containerExt: String?,
)
data class XtSeriesInfo(val episodes: List<XtEpisode>)
data class XtEpgEntry(val title: String, val description: String?, val startMs: Long, val stopMs: Long)

/**
 * Xtream Codes `player_api.php` client. Category lists are small and collected to a list; the large
 * stream lists are streamed object-by-object with [android.util.JsonReader] and pushed to a callback,
 * so a 340k-channel response never sits fully in memory.
 */
class XtreamClient(private val http: HttpClient) {

    // --- Categories ---
    suspend fun liveCategories(s: SourceEntity, onProgress: ((Long, Long?) -> Unit)? = null) = categories(s, "get_live_categories", onProgress)
    suspend fun vodCategories(s: SourceEntity, onProgress: ((Long, Long?) -> Unit)? = null) = categories(s, "get_vod_categories", onProgress)
    suspend fun seriesCategories(s: SourceEntity, onProgress: ((Long, Long?) -> Unit)? = null) = categories(s, "get_series_categories", onProgress)

    private suspend fun categories(s: SourceEntity, action: String, onProgress: ((Long, Long?) -> Unit)? = null): List<XtCategory> {
        val out = ArrayList<XtCategory>()
        http.get(api(s, action), s.userAgent, onProgress, maxAttempts = CATEGORY_MAX_ATTEMPTS) { input ->
            streamObjects(input) { m ->
                val id = m["category_id"] ?: return@streamObjects
                out.add(XtCategory(id, m["category_name"] ?: id))
            }
        }
        return out
    }

    // --- Streams (callback-streamed) ---
    // Each returns true if the full list parsed cleanly, false if the server truncated it mid-stream
    // (issue #15) — the sync uses that to fall back to per-category fetching. [categoryId] filters the
    // request server-side (`&category_id=X`), keeping payloads small enough to dodge the truncation.
    suspend fun streamLive(
        s: SourceEntity,
        categoryId: String? = null,
        onItem: suspend (XtLiveStream) -> Unit,
        onProgress: ((Long, Long?) -> Unit)? = null,
    ): Boolean =
        streamLive(
            s = s,
            categoryId = categoryId,
            transform = { streamId, name, icon, epgChannelId, itemCategoryId, num, archive, archiveDays ->
                XtLiveStream(streamId, name, icon, epgChannelId, itemCategoryId, num, archive, archiveDays)
            },
            onItem = onItem,
            onProgress = onProgress,
        )

    suspend fun <T : Any> streamLive(
        s: SourceEntity,
        categoryId: String? = null,
        transform: (
            streamId: String,
            name: String,
            icon: String?,
            epgChannelId: String?,
            categoryId: String?,
            num: Int?,
            archive: Boolean,
            archiveDays: Int,
        ) -> T?,
        onItem: suspend (T) -> Unit,
        onProgress: ((Long, Long?) -> Unit)? = null,
    ): Boolean {
        return http.get(api(s, "get_live_streams", categoryParam(categoryId)), s.userAgent, onProgress) { input ->
            streamItems("get_live_streams", input, { reader -> readLiveStreamAs(reader, transform) }, onItem)
        }
    }

    suspend fun streamVod(
        s: SourceEntity,
        categoryId: String? = null,
        onItem: suspend (XtVod) -> Unit,
        onProgress: ((Long, Long?) -> Unit)? = null,
    ): Boolean =
        streamVod(
            s = s,
            categoryId = categoryId,
            transform = { streamId, name, icon, rating, plot, itemCategoryId, containerExt, added ->
                XtVod(streamId, name, icon, rating, plot, itemCategoryId, containerExt, added)
            },
            onItem = onItem,
            onProgress = onProgress,
        )

    suspend fun <T : Any> streamVod(
        s: SourceEntity,
        categoryId: String? = null,
        transform: (
            streamId: String,
            name: String,
            icon: String?,
            rating: Double?,
            plot: String?,
            categoryId: String?,
            containerExt: String?,
            added: Long?,
        ) -> T?,
        onItem: suspend (T) -> Unit,
        onProgress: ((Long, Long?) -> Unit)? = null,
    ): Boolean {
        return http.get(api(s, "get_vod_streams", categoryParam(categoryId)), s.userAgent, onProgress) { input ->
            streamItems("get_vod_streams", input, { reader -> readVodAs(reader, transform) }, onItem)
        }
    }

    suspend fun streamSeries(
        s: SourceEntity,
        categoryId: String? = null,
        onItem: suspend (XtSeries) -> Unit,
        onProgress: ((Long, Long?) -> Unit)? = null,
    ): Boolean =
        streamSeries(
            s = s,
            categoryId = categoryId,
            transform = { seriesId, name, cover, plot, rating, itemCategoryId, year, added, lastModified ->
                XtSeries(seriesId, name, cover, plot, rating, itemCategoryId, year, added, lastModified)
            },
            onItem = onItem,
            onProgress = onProgress,
        )

    suspend fun <T : Any> streamSeries(
        s: SourceEntity,
        categoryId: String? = null,
        transform: (
            seriesId: String,
            name: String,
            cover: String?,
            plot: String?,
            rating: Double?,
            categoryId: String?,
            year: Int?,
            added: Long?,
            lastModified: Long?,
        ) -> T?,
        onItem: suspend (T) -> Unit,
        onProgress: ((Long, Long?) -> Unit)? = null,
    ): Boolean {
        return http.get(api(s, "get_series", categoryParam(categoryId)), s.userAgent, onProgress) { input ->
            streamItems("get_series", input, { reader -> readSeriesAs(reader, transform) }, onItem)
        }
    }

    /**
     * Fetches seasons/episodes for a series (lazy, on open). Panels vary in how they shape the `episodes`
     * field — usually an OBJECT mapping season-number → array of episode objects, but some return a flat
     * ARRAY of episodes (season taken from each episode's own `season` field). Both are handled, so series
     * that showed no episodes on stricter panels now populate.
     */
    suspend fun getSeriesInfo(s: SourceEntity, seriesId: String): XtSeriesInfo {
        val episodes = ArrayList<XtEpisode>()
        http.get(api(s, "get_series_info", "&series_id=$seriesId"), s.userAgent) { input ->
            JsonReader(input.reader(Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return@use }
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "episodes") {
                        when (reader.peek()) {
                            JsonToken.BEGIN_OBJECT -> readEpisodesObject(reader, episodes) // { "1": [ep,…], … }
                            JsonToken.BEGIN_ARRAY -> readEpisodesArray(reader, episodes)    // [ ep, ep, … ]
                            else -> reader.skipValue()
                        }
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
        return XtSeriesInfo(episodes)
    }

    /** `episodes` as `{ season → [episodes] }`. */
    private fun readEpisodesObject(reader: JsonReader, out: MutableList<XtEpisode>) {
        reader.beginObject()
        while (reader.hasNext()) {
            val season = reader.nextName().toIntOrNull() ?: 0
            if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); continue }
            reader.beginArray()
            while (reader.hasNext()) readEpisode(reader, out, season)
            reader.endArray()
        }
        reader.endObject()
    }

    /** `episodes` as a flat `[episodes]` — season comes from each episode's own field. */
    private fun readEpisodesArray(reader: JsonReader, out: MutableList<XtEpisode>) {
        reader.beginArray()
        while (reader.hasNext()) readEpisode(reader, out, 0)
        reader.endArray()
    }

    /** One episode object — tolerant of string/number/null fields and an in-object `season`. */
    private fun readEpisode(reader: JsonReader, out: MutableList<XtEpisode>, seasonFallback: Int) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return }
        var id: String? = null
        var epNum = 0
        var title = ""
        var ext: String? = null
        var season = seasonFallback
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextStringOrNull()
                "episode_num" -> epNum = reader.nextIntOrNull() ?: epNum
                "title" -> title = reader.nextStringOrNull() ?: title
                "container_extension" -> ext = reader.nextStringOrNull()
                "season" -> reader.nextIntOrNull()?.let { if (it > 0) season = it }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        id?.let { out.add(XtEpisode(it, season, epNum, title.ifBlank { "Episode $epNum" }, ext)) }
    }

    /** Reads a string, coercing numbers and tolerating JSON null. */
    private fun JsonReader.nextStringOrNull(): String? =
        if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()

    /** Reads an int from a number or a numeric string, tolerating null/other. */
    private fun JsonReader.nextIntOrNull(): Int? = when (peek()) {
        JsonToken.NUMBER -> nextInt()
        JsonToken.STRING -> nextString().trim().toIntOrNull()
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    /** Reads a long from a number or a numeric string, tolerating null/other. */
    private fun JsonReader.nextLongOrNull(): Long? = when (peek()) {
        JsonToken.NUMBER -> nextLong()
        JsonToken.STRING -> nextString().trim().toLongOrNull()
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    /** Reads a double from a number or a numeric string, tolerating null/other. */
    private fun JsonReader.nextDoubleOrNull(): Double? = when (peek()) {
        JsonToken.NUMBER -> nextDouble()
        JsonToken.STRING -> nextString().trim().toDoubleOrNull()
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    /**
     * Short EPG (now + a few upcoming programmes) for a single live channel via `get_short_epg`.
     * Titles/descriptions are base64-encoded; timestamps are unix seconds. Returns entries sorted by
     * start time (empty if the panel has no guide for this channel).
     */
    suspend fun getShortEpg(s: SourceEntity, streamId: String, limit: Int = 6): List<XtEpgEntry> {
        val out = ArrayList<XtEpgEntry>()
        http.get(api(s, "get_short_epg", "&stream_id=$streamId&limit=$limit"), s.userAgent) { input ->
            JsonReader(input.reader(Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return@use }
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "epg_listings" && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        readEpgListings(reader, out)
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
        return out.sortedBy { it.startMs }
    }

    private fun readEpgListings(reader: JsonReader, out: MutableList<XtEpgEntry>) {
        reader.beginArray()
        while (reader.hasNext()) {
            var title = ""
            var desc: String? = null
            var startTs = 0L
            var stopTs = 0L
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (reader.peek() == JsonToken.NULL) { reader.nextNull(); continue }
                when (name) {
                    "title" -> title = decodeBase64(reader.nextString())
                    "description" -> desc = decodeBase64(reader.nextString()).takeIf { it.isNotBlank() }
                    "start_timestamp" -> startTs = reader.nextString().toLongOrNull() ?: 0
                    "stop_timestamp" -> stopTs = reader.nextString().toLongOrNull() ?: 0
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (startTs > 0 && stopTs > startTs) {
                out.add(XtEpgEntry(title.ifBlank { "—" }, desc, startTs * 1000, stopTs * 1000))
            }
        }
        reader.endArray()
    }

    private fun decodeBase64(s: String): String =
        runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim() }.getOrDefault(s)

    // --- Stream URL builders ---
    // Live uses the raw MPEG-TS endpoint (.ts) — the universal Xtream live format. The .m3u8/HLS
    // wrapper isn't served by every panel (mpegts-only providers 404 on it, so channels won't load),
    // whereas every panel serves .ts and mpv plays it natively.
    fun liveUrl(s: SourceEntity, streamId: String) = "${base(s)}/live/${s.username}/${s.password}/$streamId.ts"

    /**
     * Catch-up (timeshift) URL for a past programme:
     * `…/timeshift/user/pass/{durationMinutes}/{yyyy-MM-dd:HH-mm}/{streamId}.ts`. The start is formatted
     * in [tz] (UTC by default — EPG timestamps are UTC; some panels expect server-local, hence the knob).
     */
    fun timeshiftUrl(s: SourceEntity, streamId: String, startMs: Long, durationMinutes: Int, tz: java.util.TimeZone = java.util.TimeZone.getTimeZone("UTC")): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US).apply { timeZone = tz }
        return "${base(s)}/timeshift/${s.username}/${s.password}/$durationMinutes/${fmt.format(java.util.Date(startMs))}/$streamId.ts"
    }
    fun movieUrl(s: SourceEntity, streamId: String, ext: String?) =
        "${base(s)}/movie/${s.username}/${s.password}/$streamId.${ext ?: "mp4"}"
    fun seriesEpisodeUrl(s: SourceEntity, episodeId: String, ext: String?) =
        "${base(s)}/series/${s.username}/${s.password}/$episodeId.${ext ?: "mp4"}"

    /** Full XMLTV guide for the whole account (all channels) — the bulk EPG used by the guide grid. */
    /**
     * Account expiry from the bare `player_api.php` call (`user_info.exp_date`, epoch seconds) —
     * null when the panel reports none/unlimited or the payload is malformed. Small response; used
     * for the Manage-sources "Expires …" line, never during sync.
     */
    suspend fun accountExpiryMs(s: SourceEntity): Long? {
        val u = URLEncoder.encode(s.username.orEmpty(), "UTF-8")
        val p = URLEncoder.encode(s.password.orEmpty(), "UTF-8")
        return http.get("${base(s)}/player_api.php?username=$u&password=$p", s.userAgent) { input ->
            var expSec: Long? = null
            JsonReader(java.io.InputStreamReader(input, Charsets.UTF_8)).use { r ->
                if (r.peek() != JsonToken.BEGIN_OBJECT) return@use
                r.beginObject()
                while (r.hasNext()) {
                    if (r.nextName() == "user_info" && r.peek() == JsonToken.BEGIN_OBJECT) {
                        r.beginObject()
                        while (r.hasNext()) {
                            if (r.nextName() == "exp_date") {
                                expSec = when (r.peek()) {
                                    JsonToken.STRING -> r.nextString().toLongOrNull()
                                    JsonToken.NUMBER -> r.nextLong()
                                    else -> { r.skipValue(); null }
                                }
                            } else {
                                r.skipValue()
                            }
                        }
                        r.endObject()
                    } else {
                        r.skipValue()
                    }
                }
                r.endObject()
            }
            expSec?.takeIf { it > 0 }?.times(1000)
        }
    }

    fun xmltvUrl(s: SourceEntity): String {
        val u = URLEncoder.encode(s.username.orEmpty(), "UTF-8")
        val p = URLEncoder.encode(s.password.orEmpty(), "UTF-8")
        return "${base(s)}/xmltv.php?username=$u&password=$p"
    }

    // --- helpers ---
    private fun base(s: SourceEntity) = s.url.trimEnd('/')

    private fun api(s: SourceEntity, action: String, extra: String = ""): String {
        val u = URLEncoder.encode(s.username.orEmpty(), "UTF-8")
        val p = URLEncoder.encode(s.password.orEmpty(), "UTF-8")
        return "${base(s)}/player_api.php?username=$u&password=$p&action=$action$extra"
    }

    /** `&category_id=X` query suffix (server-side filter), or "" when fetching everything. */
    private fun categoryParam(categoryId: String?): String =
        categoryId?.takeIf { it.isNotBlank() }?.let { "&category_id=$it" } ?: ""

    /** Category lists are small, so keeping the map reader here keeps that path simple. */
    private suspend fun streamObjects(input: InputStream, onObject: suspend (Map<String, String?>) -> Unit): Boolean =
        streamItems("objects", input, ::readObject, onObject)

    private suspend fun <T> streamItems(
        label: String,
        input: InputStream,
        readItem: (JsonReader) -> T?,
        onItem: suspend (T) -> Unit,
    ): Boolean =
        // Per-item timing costs millions of elapsedRealtime() syscalls on a 170k+ catalog, so the
        // detailed metrics only run when the tag is debuggable (`setprop log.tag.XtreamClient DEBUG`).
        if (DEBUG) {
            streamArray(label, input) { reader, metrics ->
                val parseStart = SystemClock.elapsedRealtime()
                val item = readItem(reader)
                metrics.parseOrReadMs += SystemClock.elapsedRealtime() - parseStart
                if (item != null) {
                    val callbackStart = SystemClock.elapsedRealtime()
                    onItem(item)
                    metrics.callbackMs += SystemClock.elapsedRealtime() - callbackStart
                }
            }
        } else {
            streamArray(label, input) { reader, _ ->
                readItem(reader)?.let { onItem(it) }
            }
        }

    /**
     * Streams a top-level JSON array. Returns true if the array parsed to its end, false if the server
     * truncated it mid-stream (issue #15). A failure before any item is read is fatal and rethrown.
     */
    private suspend fun streamArray(label: String, input: InputStream, readItem: suspend (JsonReader, StreamMetrics) -> Unit): Boolean {
        val ctx = currentCoroutineContext()
        val startedAt = SystemClock.elapsedRealtime()
        var lastLogAt = startedAt
        var lastParseOrReadMs = 0L
        var lastCallbackMs = 0L
        var count = 0
        val metrics = StreamMetrics()
        if (DEBUG) Log.d(TAG, "streamArray start label=$label")
        try {
            JsonReader(input.reader(Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                    // Some servers return {} or an error object instead of an array.
                    reader.skipValue()
                    Log.d(TAG, "streamArray non-array label=$label totalMs=${SystemClock.elapsedRealtime() - startedAt}")
                    return true
                }
                reader.beginArray()
                while (reader.hasNext()) {
                    ctx.ensureActive()
                    readItem(reader, metrics)
                    count++
                    if (DEBUG && count % STREAM_LOG_ITEM_STEP == 0) {
                        val now = SystemClock.elapsedRealtime()
                        val parseOrReadDelta = metrics.parseOrReadMs - lastParseOrReadMs
                        val callbackDelta = metrics.callbackMs - lastCallbackMs
                        Log.d(
                            TAG,
                            "streamArray parsed count=$count label=$label deltaMs=${now - lastLogAt} " +
                                "parseOrReadMs=$parseOrReadDelta callbackMs=$callbackDelta " +
                                "totalParseOrReadMs=${metrics.parseOrReadMs} totalCallbackMs=${metrics.callbackMs} " +
                                "totalMs=${now - startedAt}",
                        )
                        lastLogAt = now
                        lastParseOrReadMs = metrics.parseOrReadMs
                        lastCallbackMs = metrics.callbackMs
                    }
                }
                reader.endArray()
            }
            Log.i(TAG, "streamArray end count=$count label=$label totalMs=${SystemClock.elapsedRealtime() - startedAt}")
            return true
        } catch (c: kotlin.coroutines.cancellation.CancellationException) {
            throw c
        } catch (e: Exception) {
            // Truncated mid-stream (JsonReader reports "Unterminated string …"). Keep everything parsed
            // so far; only a failure before ANY item is read is fatal.
            if (count == 0) throw e
            Log.w(
                TAG,
                "Stream truncated after $count items label=$label — partial list kept " +
                    "parseOrReadMs=${metrics.parseOrReadMs} callbackMs=${metrics.callbackMs} " +
                    "totalMs=${SystemClock.elapsedRealtime() - startedAt}",
                e,
            )
            return false
        }
    }

    private class StreamMetrics {
        var parseOrReadMs = 0L
        var callbackMs = 0L
    }

    private companion object {
        const val TAG = "XtreamClient"
        const val STREAM_LOG_ITEM_STEP = 10_000
        /** Detailed per-item parse metrics — off in normal runs, enabled via `setprop log.tag.XtreamClient DEBUG`. */
        val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
        /** Category lists are tiny; a retry on a connect/DNS blip saves the whole sync phase. */
        const val CATEGORY_MAX_ATTEMPTS = 3
    }

    private fun <T : Any> readLiveStreamAs(
        reader: JsonReader,
        transform: (
            streamId: String,
            name: String,
            icon: String?,
            epgChannelId: String?,
            categoryId: String?,
            num: Int?,
            archive: Boolean,
            archiveDays: Int,
        ) -> T?,
    ): T? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var streamId: String? = null
        var name = ""
        var icon: String? = null
        var epgChannelId: String? = null
        var categoryId: String? = null
        var num: Int? = null
        var archive = false
        var archiveDays = 0

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "stream_id" -> streamId = reader.nextScalarStringOrNull()
                "name" -> name = reader.nextScalarStringOrNull().orEmpty()
                "stream_icon" -> icon = reader.nextScalarStringOrNull()
                "epg_channel_id" -> epgChannelId = reader.nextScalarStringOrNull()?.takeIf { it.isNotBlank() }
                "category_id" -> categoryId = reader.nextScalarStringOrNull()
                "num" -> num = reader.nextIntOrNull()
                "tv_archive" -> archive = (reader.nextIntOrNull() ?: 0) > 0
                "tv_archive_duration" -> archiveDays = reader.nextIntOrNull() ?: 0
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return streamId?.let { transform(it, name, icon, epgChannelId, categoryId, num, archive, archiveDays) }
    }

    private fun <T : Any> readVodAs(
        reader: JsonReader,
        transform: (
            streamId: String,
            name: String,
            icon: String?,
            rating: Double?,
            plot: String?,
            categoryId: String?,
            containerExt: String?,
            added: Long?,
        ) -> T?,
    ): T? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var streamId: String? = null
        var name = ""
        var icon: String? = null
        var rating: Double? = null
        var plot: String? = null
        var description: String? = null
        var categoryId: String? = null
        var containerExt: String? = null
        var added: Long? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "stream_id" -> streamId = reader.nextScalarStringOrNull()
                "name" -> name = reader.nextScalarStringOrNull().orEmpty()
                "stream_icon" -> icon = reader.nextScalarStringOrNull()
                "rating" -> rating = reader.nextDoubleOrNull()
                "plot" -> plot = reader.nextScalarStringOrNull()
                "description" -> description = reader.nextScalarStringOrNull()
                "category_id" -> categoryId = reader.nextScalarStringOrNull()
                "container_extension" -> containerExt = reader.nextScalarStringOrNull()
                "added" -> added = reader.nextLongOrNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val cleanPlot = plot?.takeIf { it.isNotBlank() } ?: description?.takeIf { it.isNotBlank() }
        return streamId?.let { transform(it, name, icon, rating, cleanPlot, categoryId, containerExt, added) }
    }

    private fun <T : Any> readSeriesAs(
        reader: JsonReader,
        transform: (
            seriesId: String,
            name: String,
            cover: String?,
            plot: String?,
            rating: Double?,
            categoryId: String?,
            year: Int?,
            added: Long?,
            lastModified: Long?,
        ) -> T?,
    ): T? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var seriesId: String? = null
        var name = ""
        var cover: String? = null
        var plot: String? = null
        var rating: Double? = null
        var categoryId: String? = null
        var year: Int? = null
        var added: Long? = null
        var lastModified: Long? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "series_id" -> seriesId = reader.nextScalarStringOrNull()
                "name" -> name = reader.nextScalarStringOrNull().orEmpty()
                "cover" -> cover = reader.nextScalarStringOrNull()
                "plot" -> plot = reader.nextScalarStringOrNull()
                "rating" -> rating = reader.nextDoubleOrNull()
                "category_id" -> categoryId = reader.nextScalarStringOrNull()
                "year" -> year = reader.nextIntOrNull()
                "added" -> added = reader.nextScalarStringOrNull()?.toLongOrNull()
                "last_modified" -> lastModified = reader.nextScalarStringOrNull()?.toLongOrNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return seriesId?.let {
            transform(
                it,
                name,
                cover,
                plot,
                rating,
                categoryId,
                year,
                added,
                lastModified,
            )
        }
    }

    private fun readObject(reader: JsonReader): Map<String, String?> {
        val map = HashMap<String, String?>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (reader.peek()) {
                JsonToken.NULL -> {
                    reader.nextNull()
                    map[name] = null
                }
                JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> reader.skipValue()
                else -> map[name] = reader.nextString()
            }
        }
        reader.endObject()
        return map
    }

    private fun JsonReader.nextScalarStringOrNull(): String? = when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            null
        }
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> {
            skipValue()
            null
        }
    }
}
