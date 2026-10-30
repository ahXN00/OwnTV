package tv.own.owntv.features.automation

import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.util.CrashRecorder
import tv.own.owntv.player.PlaybackErrorLog

/**
 * The app's logs for the automation API. An app may read its own logcat lines without any permission.
 * Lines, error details and the crash report can quote any playlist's address, or one a caller sent, so
 * each address in them is cut down to where it points and the rest masked by pattern
 * ([UrlMask.maskTvText]). Only this copy is cut down; the TV's own Error log screen still shows the full
 * detail.
 */
class AutomationLogs(private val context: Context) : LogBackend {

    override suspend fun appLog(lines: Int): JSONObject = withContext(Dispatchers.IO) {
        val process = ProcessBuilder("logcat", "-d", "-t", lines.toString(), "--pid=${Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readLines() }
        process.waitFor()
        JSONObject().put("lines", JSONArray(text.map(UrlMask::maskTvText)))
    }

    override suspend fun playbackErrors(): JSONObject = withContext(Dispatchers.IO) {
        val array = JSONArray()
        PlaybackErrorLog.read(context).forEach { entry ->
            array.put(
                JSONObject()
                    .put("atMs", entry.atMs)
                    .put("kind", entry.kind.name)
                    .put("engine", entry.engine)
                    .put("live", entry.live)
                    .put("reason", entry.reason?.name ?: entry.legacyReason ?: JSONObject.NULL)
                    .put("codec", entry.codec ?: JSONObject.NULL)
                    .put("resolution", entry.resolution ?: JSONObject.NULL)
                    .put("decoder", entry.decoderName ?: JSONObject.NULL)
                    .put("hardwareDecoder", entry.decoderHardware)
                    .put("detail", entry.raw?.let(UrlMask::maskTvText) ?: JSONObject.NULL),
            )
        }
        JSONObject().put("entries", array)
    }

    override suspend fun clearPlaybackErrors() = withContext(Dispatchers.IO) { PlaybackErrorLog.clear(context) }

    override suspend fun lastCrash(): JSONObject = withContext(Dispatchers.IO) {
        JSONObject().put("crash", CrashRecorder.read(context)?.let(UrlMask::maskTvText) ?: JSONObject.NULL)
    }
}
