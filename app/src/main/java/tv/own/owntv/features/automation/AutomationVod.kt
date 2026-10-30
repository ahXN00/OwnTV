package tv.own.owntv.features.automation

import android.util.Log
import org.json.JSONObject
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.core.repository.SeriesRepository

/**
 * Movie and series search, playing, and opening a show, for the automation API. Playing goes through the
 * launcher deep link ([AppLauncher]), the way a home-screen tile does, so resume and the episode queue
 * work as they do there; a show opens through the shell ([NavGate]) as from Home. The active profile only
 * finds and plays what its own screens show ([ProfileCatalog]). How an item is shown, poster cut down, is
 * [VodJson]'s job.
 */
class AutomationVod(
    private val catalog: ProfileCatalog,
    private val seriesDao: SeriesDao,
    private val seriesRepository: SeriesRepository,
    private val profile: AutomationProfile,
    private val launcher: AppLauncher,
    private val gate: NavGate,
) : VodBackend {

    override suspend fun movies(query: String, playlistId: Long?, limit: Int): JSONObject {
        val pid = profile.id() ?: return VodJson.movies(emptyList())
        return VodJson.movies(catalog.movies(pid, query, playlistId, limit))
    }

    override suspend fun series(query: String, playlistId: Long?, limit: Int): JSONObject {
        val pid = profile.id() ?: return VodJson.series(emptyList())
        return VodJson.series(catalog.series(pid, query, playlistId, limit))
    }

    /** Fetches the show's episodes from an Xtream or Stalker provider first when due, as opening the show does. */
    override suspend fun episodes(seriesId: Long): JSONObject? {
        val pid = profile.id() ?: return null
        val show = catalog.show(pid, seriesId) ?: return null
        seriesRepository.loadEpisodes(show)
        return VodJson.episodes(show.id, seriesDao.episodesBySeriesOnce(show.id))
    }

    override suspend fun playMovie(id: Long): PlayResult {
        val pid = profile.id() ?: return PlayResult.NoProfile
        val movie = catalog.movie(pid, id) ?: return PlayResult.NotFound
        val link = LauncherDeepLink.Movie(sourceId = movie.sourceId, itemId = movie.id).toUri()
        Log.d(TAG, "play movieId=$id")
        return launcher.open(link, launcher.playing(movie.name))
    }

    override suspend fun playEpisode(id: Long): PlayResult {
        val pid = profile.id() ?: return PlayResult.NoProfile
        val (show, episode) = catalog.episode(pid, id) ?: return PlayResult.NotFound
        val link = LauncherDeepLink.Episode(seriesSourceId = show.sourceId, seriesItemId = show.id, episodeItemId = episode.id).toUri()
        Log.d(TAG, "play episodeId=$id")
        return launcher.open(link, launcher.playing(show.name))
    }

    override suspend fun openSeries(id: Long): NavResult? {
        val pid = profile.id() ?: return NavResult.NO_PROFILE
        val show = catalog.show(pid, id) ?: return null
        Log.d(TAG, "open seriesId=$id")
        return gate.openSeries(show.id, pid, launcher.opened(show.name))
    }

    private companion object {
        const val TAG = "OwnTVAutomation"
    }
}
