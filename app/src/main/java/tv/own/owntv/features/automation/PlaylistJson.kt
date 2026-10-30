package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.work.CatalogSyncState

/**
 * How the API shows a playlist. Secrets stay on the TV: a password or MAC is reported only as set or
 * not, and each address (the playlist's, its guide's and its referer) only as far as where it points
 * ([UrlMask.origin]), the same whatever login is stored. `hasPassword` also counts a login written into
 * the address itself, such as `user:pass@`, `password=` or an Xtream path.
 */
object PlaylistJson {
    fun of(
        source: SourceEntity,
        counts: SyncCounts,
        isDefault: Boolean,
        refresh: PlaylistRefresh,
        sync: CatalogSyncState,
    ): JSONObject {
        return JSONObject()
            .put("id", source.id)
            .put("name", source.name)
            .put("type", source.type.name.lowercase())
            .put("url", UrlMask.origin(source.url))
            .put("username", source.username ?: JSONObject.NULL)
            .put("hasPassword", !source.password.isNullOrEmpty() || UrlMask.hasSecret(source.url))
            .put("hasMac", source.type == SourceType.STALKER && !source.mac.isNullOrEmpty())
            .put("userAgent", source.userAgent ?: JSONObject.NULL)
            .put("httpReferer", source.httpReferer?.let(UrlMask::origin) ?: JSONObject.NULL)
            .put("epgUrl", source.epgUrl?.let(UrlMask::origin) ?: JSONObject.NULL)
            .put("default", isDefault)
            .put("refresh", refresh.serialize())
            .put("syncLive", source.syncLive)
            .put("syncMovies", source.syncMovies)
            .put("syncSeries", source.syncSeries)
            .put("preferHls", source.preferHls)
            .put("lastSyncAt", source.lastSyncAt ?: JSONObject.NULL)
            .put("channels", counts.channels)
            .put("movies", counts.movies)
            .put("series", counts.series)
            .put("sync", sync(sync))
    }

    fun sync(state: CatalogSyncState): JSONObject = when (state) {
        CatalogSyncState.Idle -> JSONObject().put("state", "idle")
        is CatalogSyncState.Syncing -> JSONObject()
            .put("state", "syncing")
            .put("processed", state.totalProcessed)
            .put("previousTotal", state.baseItemCount)
            .put("live", state.liveProcessed)
            .put("movies", state.moviesProcessed)
            .put("series", state.seriesProcessed)
    }
}
