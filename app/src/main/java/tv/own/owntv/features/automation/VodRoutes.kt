package tv.own.owntv.features.automation

import org.json.JSONObject

/** What the movie and series routes ask of the app. The Android side is [AutomationVod]. */
interface VodBackend {
    /** The active profile's movies whose name holds [query] (all when empty). */
    suspend fun movies(query: String, playlistId: Long?, limit: Int): JSONObject

    /** The active profile's shows whose name holds [query] (all when empty). */
    suspend fun series(query: String, playlistId: Long?, limit: Int): JSONObject

    /** The show's episodes, or null when the active profile has no show [seriesId]. */
    suspend fun episodes(seriesId: Long): JSONObject?

    suspend fun playMovie(id: Long): PlayResult

    suspend fun playEpisode(id: Long): PlayResult

    /** Opens the show's page on the TV; null when the active profile has no show [id]. */
    suspend fun openSeries(id: Long): NavResult?
}

/** Finding movies and shows, playing one full screen, and opening a show's page on the TV. */
fun vodRoutes(backend: VodBackend): List<ApiRoute> = listOf(
    ApiRoute("GET", "movies", "Search movies by name: ${SEARCH.hint}") { call ->
        SEARCH.answer(call, backend::movies)
    },
    ApiRoute("POST", "movies/{id}/play", "Play this movie on the TV, full screen") { call ->
        playReply(backend.playMovie(call.id()), "movie", call.id())
    },
    ApiRoute("GET", "series", "Search series by name: ${SEARCH.hint}") { call ->
        SEARCH.answer(call, backend::series)
    },
    ApiRoute("GET", "series/{id}/episodes", "This show's episodes, by season") { call ->
        backend.episodes(call.id())?.let(ApiReply::ok) ?: ApiReply.notFound("series", call.id())
    },
    ApiRoute("POST", "series/{id}/open", "Open this show's page on the TV") { call ->
        when (backend.openSeries(call.id())) {
            NavResult.SENT -> ApiReply.accepted("opening")
            NavResult.BLOCKED -> ApiReply.blocked()
            NavResult.PRIVATE -> ApiReply.privateScreen()
            NavResult.NO_PROFILE -> ApiReply.noProfile()
            null -> ApiReply.notFound("series", call.id())
        }
    },
    ApiRoute("POST", "episodes/{id}/play", "Play this episode on the TV, full screen") { call ->
        playReply(backend.playEpisode(call.id()), "episode", call.id())
    },
)

private fun playReply(result: PlayResult, what: String, id: Long): HttpResponse = when (result) {
    PlayResult.Started -> ApiReply.accepted("playing")
    PlayResult.NotFound -> ApiReply.notFound(what, id)
    PlayResult.Blocked -> ApiReply.blocked()
    PlayResult.Private -> ApiReply.privateScreen()
    PlayResult.NoProfile -> ApiReply.noProfile()
}

private val SEARCH = SearchParams(defaultLimit = 20, maxLimit = 50)
