package tv.own.owntv.features.automation

import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources

/**
 * The profile's playlists as a search of one section sees them: those that keep [MediaType] items (such as
 * [SourceEntity.syncMovies]), the way a home-screen tile resolves one, narrowed to the playlist the caller
 * names. Each keeps only that section switched on, so Core's search reads that section alone.
 */
object SectionSources {
    fun of(profileId: Long, playlists: List<SourceEntity>, type: MediaType, playlistId: Long?): ActiveProfileSources {
        val keeping = ActiveProfileSources(profileId, playlists).sourceIdsFor(type)
        val chosen = playlists.filter { it.id in keeping && (playlistId == null || it.id == playlistId) }
        return ActiveProfileSources(profileId, chosen.map { it.only(type) })
    }

    private fun SourceEntity.only(type: MediaType): SourceEntity = copy(
        syncLive = type == MediaType.LIVE,
        syncMovies = type == MediaType.MOVIE,
        syncSeries = type == MediaType.SERIES || type == MediaType.EPISODE,
    )
}
