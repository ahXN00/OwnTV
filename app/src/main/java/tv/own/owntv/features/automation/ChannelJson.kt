package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * How the API shows a channel. Logo addresses come from the provider and can carry its login anywhere
 * in their path, so a logo shows only where it points ([UrlMask.origin]), the same whatever login any
 * playlist has stored.
 */
object ChannelJson {
    fun of(channel: ChannelEntity): JSONObject = JSONObject()
        .put("id", channel.id)
        .put("name", channel.name)
        .put("number", channel.number ?: JSONObject.NULL)
        .put("playlistId", channel.sourceId)
        .put("categoryId", channel.categoryId ?: JSONObject.NULL)
        .put("logoUrl", channel.logoUrl?.let(UrlMask::origin) ?: JSONObject.NULL)
        .put("hasGuide", !channel.epgChannelId.isNullOrEmpty())
        .put("catchup", channel.catchup)
}
