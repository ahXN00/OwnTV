package tv.own.owntv.features.automation

import org.json.JSONObject

/** What the channel routes ask of the app. The Android side is [AutomationChannels]. */
interface ChannelBackend {
    /** The active profile's live channels whose name holds [query] (all when empty). */
    suspend fun search(query: String, playlistId: Long?, limit: Int): JSONObject
    suspend fun play(id: Long): PlayResult
}

sealed interface PlayResult {
    data object Started : PlayResult
    data object NotFound : PlayResult
    /** OwnTV is in the background and Android will not let it come forward on its own. */
    data object Blocked : PlayResult
    /** A private screen ([SensitiveScreens]) is open on the TV. */
    data object Private : PlayResult
    /** No profile is open on the TV ([AutomationProfile.id]), so there is none the API may act for. */
    data object NoProfile : PlayResult
}

/** Finding live channels and tuning one, which opens the full-screen player on the TV. */
fun channelRoutes(backend: ChannelBackend): List<ApiRoute> = listOf(
    ApiRoute("GET", "channels", "Search live channels by name: ${SEARCH.hint}") { call ->
        SEARCH.answer(call, backend::search)
    },
    ApiRoute("POST", "channels/{id}/play", "Tune the TV to this channel, full screen") { call ->
        when (backend.play(call.id())) {
            PlayResult.Started -> ApiReply.accepted("tuning")
            PlayResult.NotFound -> ApiReply.notFound("channel", call.id())
            PlayResult.Blocked -> ApiReply.blocked()
            PlayResult.Private -> ApiReply.privateScreen()
            PlayResult.NoProfile -> ApiReply.noProfile()
        }
    },
)

private val SEARCH = SearchParams(defaultLimit = 50, maxLimit = 500)

