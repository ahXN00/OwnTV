package tv.own.owntv.features.automation

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.BuildConfig
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.repository.SourceTestResult
import tv.own.owntv.core.repository.SourceTester
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.ImportFinalizer
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.features.settings.SourceActions
import tv.own.owntv.features.settings.SourceEdit
import tv.own.owntv.features.settings.runSemiAutoEpgSync

/**
 * The automation API's view of the playlists: the sources of the profile it acts for ([PlaylistTarget]), add /
 * edit / re-sync / cancel / delete, and the state of the last import. An import runs in [scope], so it finishes even though the HTTP
 * call that started it returned at once; its outcome is read back through `GET /status`.
 */
class AutomationPlaylists(
    private val newImporter: () -> SourceImporter,
    private val sourceActions: SourceActions,
    private val profile: PlaylistTarget,
    private val settings: SettingsRepository,
    private val importFinalizer: ImportFinalizer,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val epgRepository: EpgRepository,
    private val epgSourceStore: EpgSourceStore,
    private val sourceTester: SourceTester,
    private val scope: CoroutineScope,
) : PlaylistBackend {
    private var importJob: Job? = null
    @Volatile private var lastImport: JSONObject = JSONObject().put("state", "idle")

    override suspend fun hasProfile(): Boolean = profile.id() != null

    override suspend fun status(): JSONObject = JSONObject()
        .put("app", "OwnTV")
        .put("version", BuildConfig.VERSION_NAME)
        .put("profileId", profile.id() ?: JSONObject.NULL)
        .put("lastImport", lastImport)

    override suspend fun playlists(): JSONObject {
        val pid = profile.id() ?: return JSONObject().put("playlists", JSONArray())
        val array = JSONArray()
        profile.playlists(pid).forEach { array.put(json(it)) }
        return JSONObject().put("playlists", array)
    }

    override suspend fun playlist(id: Long): JSONObject? = owned(id)?.let { json(it) }

    override suspend fun update(id: Long, patch: PlaylistPatch): PlaylistUpdate {
        val source = owned(id) ?: return PlaylistUpdate.NotFound
        PlaylistMove.refusal(source, patch)?.let { return PlaylistUpdate.Refused(it) }
        Log.d(TAG, "update sourceId=$id fields=${patchedFields(patch)}")
        sourceActions.update(edit(source, patch))
        return PlaylistUpdate.Saved
    }

    override suspend fun syncState(id: Long): JSONObject? =
        owned(id)?.let { PlaylistJson.sync(catalogSyncScheduler.observeSync(id).first()) }

    override suspend fun cancelSync(id: Long): Boolean {
        owned(id) ?: return false
        catalogSyncScheduler.cancelSync(id)
        return true
    }

    @Synchronized
    override fun startImport(request: AddPlaylist): Boolean {
        if (importJob?.isActive == true) return false
        lastImport = JSONObject().put("state", "running").put("name", request.name)
        importJob = scope.launch { runImport(request) }
        return true
    }

    override suspend fun resync(id: Long, clean: Boolean): Boolean {
        val source = owned(id) ?: return false
        Log.d(TAG, "resync sourceId=$id clean=$clean")
        sourceActions.resync(source, clean)
        return true
    }

    override suspend fun delete(id: Long): Boolean {
        val source = owned(id) ?: return false
        Log.d(TAG, "delete sourceId=$id")
        scope.launch { sourceActions.delete(source) }
        return true
    }

    private suspend fun runImport(request: AddPlaylist) {
        try {
            val pid = profile.id() ?: return report(request, "failed", ApiReply.NO_PROFILE)
            if (request is AddPlaylist.Xtream) loginProblem(request)?.let { return report(request, "failed", it) }
            val importer = newImporter()
            importer.useProfile(pid)
            when (request) {
                is AddPlaylist.M3u -> importer.m3u(
                    request.name, request.url, request.userAgent, request.epgUrl, request.refresh,
                    makeDefault = request.makeDefault,
                )
                is AddPlaylist.Xtream -> importer.xtream(
                    request.name, request.server, request.username, request.password,
                    userAgent = request.userAgent, epgUrl = request.epgUrl, autoRefresh = request.refresh,
                    makeDefault = request.makeDefault,
                )
            }
            when (val state = importer.state.value) {
                is SourceImporter.ImportState.Success -> {
                    lastImport = JSONObject().put("state", "done").put("name", request.name)
                        .put("sourceId", state.source?.id ?: JSONObject.NULL)
                        .put("channels", state.counts?.channels ?: 0)
                        .put("movies", state.counts?.movies ?: 0)
                        .put("series", state.counts?.series ?: 0)
                    runCatching { sourceActions.refreshTvHome() }
                    // No one is at the TV to answer "Sync the guide now?", so the answer is yes.
                    state.source?.let { runSemiAutoEpgSync(it, epgRepository, epgSourceStore) {} }
                }
                is SourceImporter.ImportState.Failed -> report(request, "failed", state.failure.toString())
                else -> report(request, "failed", "Import ended in state ${state::class.simpleName}")
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w(TAG, "automation import failed", e)
            report(request, "failed", e.message ?: e::class.java.simpleName)
        }
    }

    /** A playlist of the open profile; another profile's ids, or any with no profile open, are treated as unknown. */
    private suspend fun owned(id: Long): SourceEntity? {
        val pid = profile.id() ?: return null
        return profile.playlists(pid).firstOrNull { it.id == id }
    }

    private suspend fun json(source: SourceEntity): JSONObject = PlaylistJson.of(
        source,
        importFinalizer.contentCounts(source.id),
        isDefault = settings.defaultSourceId.first() == source.id,
        refresh = settings.playlistAutoRefresh.first()[source.id] ?: PlaylistRefresh.OFF,
        sync = catalogSyncScheduler.observeSync(source.id).first(),
    )

    /**
     * The full Edit Playlist form: the patch over what is stored, so untouched fields stay as they are.
     * On a move to another server the Stalker device identity stays behind with the old login (see [PlaylistMove]).
     */
    private suspend fun edit(source: SourceEntity, patch: PlaylistPatch): SourceEdit {
        val moved = patch.url != null && PlaylistMove.changesServer(source.url, patch.url)
        fun identity(stored: String?) = if (moved) "" else stored.orEmpty()
        return SourceEdit(
            id = source.id,
            name = patch.name ?: source.name,
            urlOrServer = patch.url ?: source.url,
            user = patch.username ?: source.username.orEmpty(),
            // Blank keeps the stored password (see SourceActions.update).
            pass = patch.password.orEmpty(),
            userAgent = patch.userAgent ?: source.userAgent.orEmpty(),
            epgUrl = patch.epgUrl ?: source.epgUrl.orEmpty(),
            autoRefresh = patch.refresh ?: settings.playlistAutoRefresh.first()[source.id] ?: PlaylistRefresh.OFF,
            isDefault = patch.makeDefault ?: (settings.defaultSourceId.first() == source.id),
            mac = patch.mac ?: source.mac.orEmpty(),
            stalkerSerialNumber = identity(source.stalkerSerialNumber),
            stalkerDeviceId = identity(source.stalkerDeviceId),
            stalkerDeviceId2 = identity(source.stalkerDeviceId2),
            stalkerSignature = identity(source.stalkerSignature),
            syncLive = patch.syncLive ?: source.syncLive,
            syncMovies = patch.syncMovies ?: source.syncMovies,
            syncSeries = patch.syncSeries ?: source.syncSeries,
            preferHls = patch.preferHls ?: source.preferHls,
            httpReferer = patch.httpReferer ?: source.httpReferer.orEmpty(),
        )
    }

    /** Names only, never values: the patch can carry a password. */
    private fun patchedFields(patch: PlaylistPatch): String = listOfNotNull(
        patch.name?.let { "name" }, patch.url?.let { "url" }, patch.username?.let { "username" },
        patch.password?.let { "password" }, patch.mac?.let { "mac" }, patch.userAgent?.let { "userAgent" }, patch.epgUrl?.let { "epgUrl" },
        patch.httpReferer?.let { "httpReferer" }, patch.refresh?.let { "refresh" }, patch.makeDefault?.let { "default" },
        patch.syncLive?.let { "syncLive" }, patch.syncMovies?.let { "syncMovies" }, patch.syncSeries?.let { "syncSeries" },
        patch.preferHls?.let { "preferHls" },
    ).joinToString()

    /**
     * The Add screen saves an Xtream playlist whatever its login (Test is a separate button), but an API
     * caller has no screen to notice an empty playlist, so the login is checked first and a refused one
     * is not saved. Null when the panel accepts it.
     */
    private suspend fun loginProblem(request: AddPlaylist.Xtream): String? {
        val probe = SourceEntity(
            name = request.name, type = SourceType.XTREAM, url = request.server,
            username = request.username, password = request.password, userAgent = request.userAgent,
        )
        return when (val result = sourceTester.test(probe)) {
            is SourceTestResult.Ok -> null
            SourceTestResult.AuthFailed -> "The server refused the username or password"
            is SourceTestResult.Expired -> "The subscription has expired"
            is SourceTestResult.Unreachable -> "Could not reach the server"
        }
    }

    private fun report(request: AddPlaylist, state: String, error: String) {
        lastImport = JSONObject().put("state", state).put("name", request.name).put("error", UrlMask.maskTvText(error))
    }

    private companion object {
        const val TAG = "OwnTVAutomation"
    }
}
