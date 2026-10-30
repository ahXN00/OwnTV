package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.features.automation.ApiReply.ok

/** What the log routes ask of the app. The Android side is [AutomationLogs]. */
interface LogBackend {
    /** The last [lines] lines of this app's own log. */
    suspend fun appLog(lines: Int): JSONObject
    suspend fun playbackErrors(): JSONObject
    suspend fun clearPlaybackErrors()
    suspend fun lastCrash(): JSONObject
}

/** Diagnostics: the app log, the playback error log the app keeps, and the last crash report. */
fun logRoutes(backend: LogBackend): List<ApiRoute> = listOf(
    ApiRoute("GET", "logs/app", "This app's own log, newest last: ?lines=1..$MAX_LINES (default $DEFAULT_LINES). May hold stream addresses.") { call ->
        val lines = call.query("lines")?.let { raw ->
            raw.toIntOrNull()?.takeIf { it in 1..MAX_LINES } ?: return@ApiRoute ApiReply.invalid("lines must be 1 to $MAX_LINES")
        } ?: DEFAULT_LINES
        ok(backend.appLog(lines))
    },
    ApiRoute("GET", "logs/playback", "Playback errors and stream reports, newest first") { ok(backend.playbackErrors()) },
    ApiRoute("DELETE", "logs/playback", "Clear the playback error log") { backend.clearPlaybackErrors(); ApiReply.accepted("cleared") },
    ApiRoute("GET", "logs/crash", "The last crash report, or null") { ok(backend.lastCrash()) },
)

private const val DEFAULT_LINES = 500
private const val MAX_LINES = 5000
