package tv.own.owntv.features.automation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.player.PlaybackEngine
import tv.own.owntv.player.TrackOption

/**
 * The player half of the automation API. The engines are main-thread only, so every call hops there.
 * The error detail can quote the stream's address, from any playlist or one a caller sent, so it is
 * cut down like a log line, each address to where it points ([UrlMask.maskTvText]); the TV's own
 * screens still show it whole. Remote keys go through [KeyGate]; stop is [PlayerStop].
 */
class AutomationPlayer(
    private val engines: PlayingEngine,
    private val stopper: PlayerStop,
    private val foreground: AutomationForeground,
    private val remote: KeyGate,
) : PlayerBackend {

    override suspend fun nowPlaying(): JSONObject = onMain {
        val playing = engines.current()
        val player = playing.engine
        val meta = player.currentMeta.value
        JSONObject()
            .put("active", playing.active)
            .put("inFront", foreground.activity() != null)
            .put("title", meta.title ?: JSONObject.NULL)
            .put("subtitle", meta.subtitle ?: JSONObject.NULL)
            .put("playing", player.isPlaying.value)
            .put("buffering", player.buffering.value)
            .put("positionMs", player.position.value)
            .put("durationMs", player.duration.value)
            .put("bufferedMs", player.bufferedMs.value)
            .put("volume", player.volume.value)
            .put("video", player.videoRes.value ?: JSONObject.NULL)
            .put("fps", playing.fps?.toDouble() ?: JSONObject.NULL)
            .put("engine", player.engineChip.value ?: JSONObject.NULL)
            .put("error", error(player))
            .put("audioTracks", tracks(player.audioTracks()))
            .put("subtitles", tracks(player.textTracks()))
    }

    override suspend fun press(keys: List<RemoteKey>): KeyPress = remote.press(keys)

    override suspend fun playPause() = onMain { engines.current().engine.togglePlayPause() }

    override suspend fun stop() = onMain { stopper.stop() }

    override suspend fun setVolume(percent: Int) = onMain {
        val player = engines.current().engine
        player.adjustVolume(percent - player.volume.value)
    }

    override suspend fun selectAudio(id: Int): Boolean = onMain {
        val player = engines.current().engine
        if (player.audioTracks().none { it.mpvId == id }) return@onMain false
        player.selectAudio(id)
        true
    }

    override suspend fun selectSubtitle(id: Int?): Boolean = onMain {
        val player = engines.current().engine
        if (id == null) {
            player.disableSubtitles()
            return@onMain true
        }
        if (player.textTracks().none { it.mpvId == id }) return@onMain false
        player.selectSubtitle(id)
        true
    }

    private fun error(player: PlaybackEngine): Any {
        val failure = player.error.value ?: return JSONObject.NULL
        val info = player.errorInfo.value
        return JSONObject()
            .put("kind", failure::class.simpleName)
            .put("reason", info?.reason?.name ?: JSONObject.NULL)
            .put("detail", info?.raw?.let(UrlMask::maskTvText) ?: JSONObject.NULL)
    }

    private fun tracks(options: List<TrackOption>): JSONArray {
        val array = JSONArray()
        options.forEach { track ->
            array.put(
                JSONObject()
                    .put("id", track.mpvId)
                    .put("label", track.label)
                    .put("language", track.lang ?: JSONObject.NULL)
                    .put("codec", track.codec ?: JSONObject.NULL)
                    .put("selected", track.selected),
            )
        }
        return array
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }
}
