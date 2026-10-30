package tv.own.owntv.features.automation

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.launcher.LauncherDeepLink

/**
 * Channel search and tuning for the automation API. Tuning goes through the launcher deep link ([AppLauncher]),
 * the same way a Live TV tile on the home screen opens a channel, so the app does exactly what it does there.
 * The active profile only finds and tunes what its own screens show ([ProfileCatalog]). How a channel is
 * shown, and its logo cut down, is [ChannelJson]'s job.
 */
class AutomationChannels(
    private val catalog: ProfileCatalog,
    private val profile: AutomationProfile,
    private val launcher: AppLauncher,
) : ChannelBackend {

    override suspend fun search(query: String, playlistId: Long?, limit: Int): JSONObject {
        val array = JSONArray()
        profile.id()?.let { pid -> catalog.channels(pid, query, playlistId, limit).forEach { array.put(ChannelJson.of(it)) } }
        return JSONObject().put("channels", array)
    }

    override suspend fun play(id: Long): PlayResult {
        val pid = profile.id() ?: return PlayResult.NoProfile
        val channel = catalog.channel(pid, id) ?: return PlayResult.NotFound
        val link = LauncherDeepLink.Live(sourceId = channel.sourceId, itemId = channel.id).toUri()
        Log.d(TAG, "play channelId=$id")
        return launcher.open(link, launcher.playing(channel.name))
    }

    private companion object {
        const val TAG = "OwnTVAutomation"
    }
}
