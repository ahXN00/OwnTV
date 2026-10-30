package tv.own.owntv.features.settings

import tv.own.owntv.core.settings.PlaylistRefresh

/** The Edit Playlist form's values, as [SourceActions.update] applies them. Blank keeps the stored value where noted there. */
data class SourceEdit(
    val id: Long,
    val name: String,
    val urlOrServer: String,
    val user: String,
    val pass: String,
    val userAgent: String,
    val epgUrl: String,
    val autoRefresh: PlaylistRefresh,
    val isDefault: Boolean = false,
    val mac: String = "",
    val stalkerSerialNumber: String = "",
    val stalkerDeviceId: String = "",
    val stalkerDeviceId2: String = "",
    val stalkerSignature: String = "",
    val syncLive: Boolean = true,
    val syncMovies: Boolean = true,
    val syncSeries: Boolean = true,
    val preferHls: Boolean = false,
    val httpReferer: String = "",
)
