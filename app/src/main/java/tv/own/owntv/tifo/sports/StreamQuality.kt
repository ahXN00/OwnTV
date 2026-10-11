package tv.own.owntv.tifo.sports

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.live.ProviderTags

/**
 * A channel's picture: its resolution ("1080p", "FHD") and frame rate. [measured] when it was read
 * from the stream itself during a preview, otherwise guessed from the quality mark in its name.
 */
data class StreamQuality(val resolution: String?, val fps: Int?, val measured: Boolean)

/**
 * What the channels looked like the last time they played. A playlist only says a channel's
 * quality in its name, if at all: the real resolution and frame rate are known once the stream has
 * played, so each preview's are kept here and shown on the channel from then on.
 *
 * Keyed by playlist and the provider's own channel id, which survive a playlist refresh.
 */
class StreamQualityStore private constructor(private val prefs: SharedPreferences) {

    private val _version = MutableStateFlow(0)

    /** Bumps whenever a channel's quality is stored, so the screen redraws its tags. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** The measured quality if the channel has played, the one in its name otherwise. */
    fun qualityOf(channel: ChannelEntity): StreamQuality? =
        measured(channel) ?: StreamQualityNames.fromName(channel.name)

    private fun measured(channel: ChannelEntity): StreamQuality? {
        val raw = prefs.getString(keyOf(channel), null) ?: return null
        val parts = raw.split('|')
        val resolution = parts.getOrNull(0)?.takeIf { it.isNotEmpty() }
        val fps = parts.getOrNull(1)?.toIntOrNull()
        if (resolution == null && fps == null) return null
        return StreamQuality(resolution, fps, measured = true)
    }

    /** Keeps what the preview engine reported for [channel]: its chips "1080p", "50 FPS", … */
    fun record(channel: ChannelEntity, chips: List<String>) {
        val resolution = chips.firstOrNull { RESOLUTION_CHIP.matches(it) }
        val fps = chips.firstNotNullOfOrNull { FPS_CHIP.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        if (resolution == null && fps == null) return
        val old = measured(channel)
        val value = "${resolution ?: old?.resolution.orEmpty()}|${fps ?: old?.fps ?: ""}|${System.currentTimeMillis()}"
        val key = keyOf(channel)
        if (prefs.getString(key, null)?.substringBeforeLast('|') == value.substringBeforeLast('|')) return
        prefs.edit().putString(key, value).apply()
        prune()
        _version.value += 1
    }

    /** Forgets the channels played longest ago once there are more than [MAX_CHANNELS]. */
    private fun prune() {
        val all = prefs.all
        if (all.size <= MAX_CHANNELS) return
        val oldest = all.entries
            .sortedBy { (it.value as? String)?.substringAfterLast('|')?.toLongOrNull() ?: 0L }
            .take(all.size - MAX_CHANNELS)
        prefs.edit().apply { oldest.forEach { remove(it.key) } }.apply()
    }

    private fun keyOf(channel: ChannelEntity): String =
        "${channel.sourceId}:${channel.remoteId ?: channel.name}"

    companion object {
        private const val PREFS = "tifo_stream_quality"
        private const val MAX_CHANNELS = 3000
        private val RESOLUTION_CHIP = Regex("""(\d{3,4}p|4K)""")
        private val FPS_CHIP = Regex("""(\d{2,3}) FPS""")

        @Volatile private var instance: StreamQualityStore? = null

        fun get(context: Context): StreamQualityStore = instance ?: synchronized(this) {
            instance ?: StreamQualityStore(
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}

/** The quality marks providers put in channel names: "ᴴᴰ", "FHD", "4K", "1080p", "50fps". */
object StreamQualityNames {
    private val RESOLUTION = Regex("""(?i)(?<![\p{L}\d])(8K|4K|UHD|FHD|HD|SD|2160p|1080[pi]|720p|576[pi]|480p)(?![\p{L}\d])""")
    private val FPS = Regex("""(?i)(?<!\d)(25|30|50|60)\s?fps(?![\p{L}\d])""")

    fun fromName(name: String): StreamQuality? {
        val parsed = ProviderTags.parse(name)
        val texts = parsed.tags + parsed.name
        val resolution = texts.firstNotNullOfOrNull { RESOLUTION.find(it)?.groupValues?.get(1) }?.let(::label)
        val fps = texts.firstNotNullOfOrNull { FPS.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        if (resolution == null && fps == null) return null
        return StreamQuality(resolution, fps, measured = false)
    }

    private fun label(mark: String): String = when (val upper = mark.uppercase()) {
        "2160P" -> "4K"
        "1080I" -> "1080i"
        "576I" -> "576i"
        else -> if (upper.endsWith("P")) upper.lowercase() else upper
    }
}
