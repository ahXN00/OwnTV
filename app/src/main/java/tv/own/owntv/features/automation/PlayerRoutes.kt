package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.features.automation.ApiReply.invalid
import tv.own.owntv.features.automation.ApiReply.ok

/** What the player routes ask of the app. The Android side is [AutomationPlayer]. */
interface PlayerBackend {
    suspend fun nowPlaying(): JSONObject
    /** Presses [keys] in order and stops at the first one that cannot be pressed. */
    suspend fun press(keys: List<RemoteKey>): KeyPress
    suspend fun playPause()
    suspend fun stop()
    suspend fun setVolume(percent: Int)
    /** False when no track of the playing item has that id. */
    suspend fun selectAudio(id: Int): Boolean
    /** [id] null turns subtitles off. False when no subtitle of the playing item has that id. */
    suspend fun selectSubtitle(id: Int?): Boolean
}

/** What is playing, and the remote control: keys, play/pause, stop, volume, audio and subtitle tracks. */
fun playerRoutes(backend: PlayerBackend): List<ApiRoute> = listOf(
    ApiRoute("GET", "player", "What is playing: title, state, position, video, tracks, and whether OwnTV is in front") { ok(backend.nowPlaying()) },
    ApiRoute(
        "POST", "player/keys", "Press remote buttons on OwnTV, in order. OwnTV must be the app in front; refused while a private screen is open on the TV (logins, PINs, profiles, network, backups or pairing)",
        """{"keys":[${RemoteKey.entries.joinToString { "\"${it.name}\"" }}]} (digits also as "0".."9")""",
    ) { call -> press(backend, call) },
    ApiRoute("POST", "player/play-pause", "Pause or resume") { backend.playPause(); ApiReply.accepted("ok") },
    ApiRoute("POST", "player/stop", "Stop playback and leave full screen") { backend.stop(); ApiReply.accepted("ok") },
    ApiRoute("POST", "player/volume", "Player volume", """{"percent":0..100}""") { call ->
        val percent = call.json()?.let { JsonFields(it).int("percent") }?.takeIf { it in 0..100 }
            ?: return@ApiRoute invalid("percent must be a whole number from 0 to 100")
        backend.setVolume(percent)
        ApiReply.accepted("ok")
    },
    ApiRoute("POST", "player/audio", "Pick an audio track by its id from GET player", """{"id"}""") { call ->
        val id = call.json()?.let { JsonFields(it).int("id") } ?: return@ApiRoute invalid("id must be a track id from GET player")
        if (backend.selectAudio(id)) ApiReply.accepted("ok") else ApiReply.error(404, "No audio track with id $id")
    },
    ApiRoute("POST", "player/subtitles", "Pick a subtitle by its id from GET player, or null for off", """{"id":<id>|null}""") { call ->
        val json = call.json() ?: return@ApiRoute ApiReply.badBody()
        if (!json.has("id")) return@ApiRoute invalid("id is required: a subtitle id from GET player, or null for off")
        val id = if (json.isNull("id")) null else JsonFields(json).int("id") ?: return@ApiRoute invalid("id must be a subtitle id or null")
        if (backend.selectSubtitle(id)) ApiReply.accepted("ok") else ApiReply.error(404, "No subtitle with id $id")
    },
)

private suspend fun press(backend: PlayerBackend, call: ApiCall): HttpResponse {
    val names = call.json()?.let { JsonFields(it).strings("keys") } ?: return invalid("keys must be a list of button names")
    if (names.isEmpty() || names.size > RemoteKey.MAX_PER_CALL) return invalid("Send 1 to ${RemoteKey.MAX_PER_CALL} keys")
    val keys = names.map { RemoteKey.parse(it) ?: return invalid("Unknown key \"$it\"; see GET ${AutomationRoutes.PREFIX}") }
    return when (backend.press(keys)) {
        KeyPress.PRESSED -> ApiReply.accepted("pressed")
        KeyPress.NOT_IN_FRONT -> ApiReply.error(409, "OwnTV is not in front on the TV")
        KeyPress.PAUSED -> ApiReply.error(409, KEYS_PAUSED)
        KeyPress.BUSY -> ApiReply.error(409, "Another key request is still running; try again")
    }
}

/** Why keys are refused while [SensitiveScreens] has a screen open. */
private const val KEYS_PAUSED = "Remote buttons are paused while a private screen (logins, PINs, profiles, network, backups or pairing) is open on the TV"
