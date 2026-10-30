package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.sync.work.EpgSyncState

/**
 * How the API shows a guide source. Its address shows only where it points ([UrlMask.origin]): a
 * provider can put its login anywhere in the path. Its last error is the TV's own text but can quote
 * that address, or a playlist's when a provider reuses the playlist login in its guide address, so
 * each address in it is cut down the same way ([UrlMask.maskTvText]). The TV's own screens still show
 * both whole.
 */
object GuideJson {
    fun of(
        source: EpgSource,
        refresh: EpgRefresh,
        channels: Int,
        programmes: Int,
        sync: EpgSyncState,
    ): JSONObject = JSONObject()
        .put("id", source.id)
        .put("name", source.name)
        .put("url", UrlMask.origin(source.url))
        .put("userAgent", source.userAgent ?: JSONObject.NULL)
        .put("refresh", refresh.serialize())
        .put("lastSyncAt", source.lastSyncAt ?: JSONObject.NULL)
        .put("lastError", source.lastError?.let(UrlMask::maskTvText) ?: JSONObject.NULL)
        .put("channels", channels)
        .put("programmes", programmes)
        .put("sync", sync(sync))

    private fun sync(state: EpgSyncState): JSONObject = when (state) {
        EpgSyncState.Idle -> JSONObject().put("state", "idle")
        is EpgSyncState.Syncing -> JSONObject()
            .put("state", "syncing")
            .put("channels", state.channels)
            .put("programmes", state.programmes)
            .put("previousProgrammes", state.baseProgrammes)
    }
}
