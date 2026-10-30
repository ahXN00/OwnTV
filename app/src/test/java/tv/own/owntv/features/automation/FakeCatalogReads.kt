package tv.own.owntv.features.automation

import tv.own.owntv.core.content.SearchResults
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources

/** In-memory [CatalogReads]: rows by id, playlists and Customize state per profile, a canned search answer. */
class FakeCatalogReads : CatalogReads {
    val playlists = mutableMapOf<Long, List<SourceEntity>>()
    val kids = mutableSetOf<Long>()
    val customized = mutableMapOf<Pair<Long, MediaType>, SectionCustomizations>()
    val categories = mutableMapOf<Long, CategoryEntity>()
    val channels = mutableMapOf<Long, ChannelEntity>()
    val movies = mutableMapOf<Long, MovieEntity>()
    val shows = mutableMapOf<Long, SeriesEntity>()
    val episodes = mutableMapOf<Long, EpisodeEntity>()
    var found = SearchResults()
    var lastSearch: ActiveProfileSources? = null

    override suspend fun playlists(profileId: Long) = playlists[profileId].orEmpty()

    override suspend fun isKids(profileId: Long) = profileId in kids

    override suspend fun customizations(profileId: Long, type: MediaType) =
        customized[profileId to type] ?: SectionCustomizations()

    override suspend fun category(id: Long) = categories[id]

    override suspend fun channel(id: Long) = channels[id]

    override suspend fun movie(id: Long) = movies[id]

    override suspend fun show(id: Long) = shows[id]

    override suspend fun episode(id: Long) = episodes[id]

    override suspend fun search(profileId: Long, sources: ActiveProfileSources, query: String, limit: Int): SearchResults {
        lastSearch = sources
        return found
    }
}
