package tv.own.owntv.features.automation

import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity

/**
 * How the API shows movies, shows and episodes. Never their stream addresses; posters come from the
 * provider and can carry its login anywhere in their path, so a poster shows only where it points
 * ([UrlMask.origin]), like a channel's logo.
 */
object VodJson {
    fun movies(movies: List<MovieEntity>): JSONObject = JSONObject().put("movies", JSONArray(movies.map(::movie)))

    fun series(shows: List<SeriesEntity>): JSONObject = JSONObject().put("series", JSONArray(shows.map(::show)))

    fun episodes(seriesId: Long, episodes: List<EpisodeEntity>): JSONObject = JSONObject()
        .put("seriesId", seriesId)
        .put("episodes", JSONArray(episodes.map(::episode)))

    fun movie(movie: MovieEntity): JSONObject = item(movie.id, movie.name, movie.year ?: movie.parsedYear, movie.sourceId, movie.posterUrl)

    fun show(show: SeriesEntity): JSONObject = item(show.id, show.name, show.year ?: show.parsedYear, show.sourceId, show.posterUrl)

    fun episode(episode: EpisodeEntity): JSONObject = JSONObject()
        .put("id", episode.id)
        .put("season", episode.seasonNumber)
        .put("episode", episode.episodeNumber)
        .put("title", episode.name)

    private fun item(id: Long, title: String, year: Int?, playlistId: Long, poster: String?): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("year", year ?: JSONObject.NULL)
        .put("playlistId", playlistId)
        .put("poster", poster?.takeIf { it.isNotBlank() }?.let(UrlMask::origin) ?: JSONObject.NULL)
}
