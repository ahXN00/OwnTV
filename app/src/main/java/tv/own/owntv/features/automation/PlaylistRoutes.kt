package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.features.automation.ApiReply.accepted
import tv.own.owntv.features.automation.ApiReply.notFound
import tv.own.owntv.features.automation.ApiReply.ok

/** What the playlist routes ask of the app. The Android side is [AutomationPlaylists]. */
interface PlaylistBackend {
    /** False while no profile is open on the TV ([AutomationProfile.id]); the writes then answer 409. */
    suspend fun hasProfile(): Boolean
    suspend fun status(): JSONObject
    suspend fun playlists(): JSONObject
    /** Null when there is no playlist with that id; likewise for every id-taking call below. */
    suspend fun playlist(id: Long): JSONObject?
    /** Starts the import in the background; false while another one is still running. */
    fun startImport(request: AddPlaylist): Boolean
    suspend fun update(id: Long, patch: PlaylistPatch): PlaylistUpdate
    suspend fun resync(id: Long, clean: Boolean): Boolean
    suspend fun syncState(id: Long): JSONObject?
    suspend fun cancelSync(id: Long): Boolean
    suspend fun delete(id: Long): Boolean
}

/** How an edit ended: saved, no such playlist, or refused for [Refused.reason] (see [PlaylistMove]). */
sealed interface PlaylistUpdate {
    data object Saved : PlaylistUpdate
    data object NotFound : PlaylistUpdate
    data class Refused(val reason: String) : PlaylistUpdate
}

/** Status, and the playlists' full lifecycle: list, add, read, edit, re-sync, cancel, delete. */
fun playlistRoutes(backend: PlaylistBackend): List<ApiRoute> = listOf(
    ApiRoute("GET", "status", "App version, active profile and the last import's outcome") { ok(backend.status()) },
    ApiRoute("GET", "playlists", "The active profile's playlists with counts, schedule and sync state") { ok(backend.playlists()) },
    ApiRoute(
        "POST", "playlists", "Add a playlist; the import runs in the background (see status.lastImport)",
        """{"type":"m3u|xtream","name","url" (m3u) | "server","username","password" (xtream),"epgUrl"?,"userAgent"?,"refresh"?,"default"?}""",
    ) { call -> withProfile(backend) { add(backend, call) } },
    ApiRoute("GET", "playlists/{id}", "One playlist; passwords are never returned") { call ->
        backend.playlist(call.id())?.let(::ok) ?: notFound("playlist", call.id())
    },
    ApiRoute(
        "PATCH", "playlists/{id}", "Edit a playlist; send only the fields to change",
        PlaylistPatchRequest.FIELDS.joinToString(prefix = "{", postfix = "}") { "\"$it\"?" },
    ) { call -> withProfile(backend) { update(backend, call) } },
    ApiRoute("DELETE", "playlists/{id}", "Delete a playlist and everything synced from it") { call ->
        withProfile(backend) { if (backend.delete(call.id())) accepted("deleting") else notFound("playlist", call.id()) }
    },
    ApiRoute("POST", "playlists/{id}/sync", "Refresh the playlist now; clean also drops items the provider removed", """{"clean"?:false}""") { call ->
        withProfile(backend) {
            val clean = call.json()?.optBoolean("clean", false) ?: return@withProfile ApiReply.badBody()
            if (backend.resync(call.id(), clean)) accepted("syncing") else notFound("playlist", call.id())
        }
    },
    ApiRoute("GET", "playlists/{id}/sync", "Refresh progress: idle, or syncing with item counts") { call ->
        backend.syncState(call.id())?.let(::ok) ?: notFound("playlist", call.id())
    },
    ApiRoute("DELETE", "playlists/{id}/sync", "Cancel a running refresh") { call ->
        withProfile(backend) { if (backend.cancelSync(call.id())) accepted("cancelled") else notFound("playlist", call.id()) }
    },
)

/** A write needs a profile open on the TV, as there is none to write for otherwise ([ApiReply.noProfile]). */
private suspend fun withProfile(backend: PlaylistBackend, write: suspend () -> HttpResponse): HttpResponse =
    if (backend.hasProfile()) write() else ApiReply.noProfile()

private fun add(backend: PlaylistBackend, call: ApiCall): HttpResponse = when (val parsed = AddPlaylistRequest.parse(call.request.body)) {
    is ParsedAdd.Invalid -> ApiReply.invalid(parsed.reason)
    is ParsedAdd.Ok -> if (backend.startImport(parsed.request)) accepted("importing") else ApiReply.error(409, "Another import is still running")
}

private suspend fun update(backend: PlaylistBackend, call: ApiCall): HttpResponse {
    val json = call.json() ?: return ApiReply.badBody()
    return when (val parsed = PlaylistPatchRequest.parse(json)) {
        is ParsedPatch.Invalid -> ApiReply.invalid(parsed.reason)
        is ParsedPatch.Ok -> when (val result = backend.update(call.id(), parsed.patch)) {
            PlaylistUpdate.Saved -> ok(JSONObject().put("status", "saved"))
            PlaylistUpdate.NotFound -> notFound("playlist", call.id())
            is PlaylistUpdate.Refused -> ApiReply.invalid(result.reason)
        }
    }
}
