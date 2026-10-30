package tv.own.owntv.features.automation

import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.content.SearchResults
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources

/** An episode with the show it belongs to. */
data class ShowEpisode(val show: SeriesEntity, val episode: EpisodeEntity)

/**
 * The channels, movies and shows a profile may see through the API, by the rules of the app's own search,
 * so the API never lists, plays or opens what the TV hides. An item must be in one of the profile's
 * playlists that keeps its section, not hidden in Customize, and not in a category that Customize hides
 * or a kids profile may not see. Lists come from Core's search ([CatalogReads.search]), which applies
 * those rules and the profile's renames; a single item is checked here with the same keys
 * ([CustomizeKeys]) and category rule ([AdultCategoryClassifier.hiddenCategoryIds]).
 */
class ProfileCatalog(private val reads: CatalogReads) {

    suspend fun channels(profileId: Long, query: String, playlistId: Long?, limit: Int): List<ChannelEntity> =
        search(profileId, MediaType.LIVE, query, playlistId, limit).channels.map { it.channel }

    suspend fun movies(profileId: Long, query: String, playlistId: Long?, limit: Int): List<MovieEntity> =
        search(profileId, MediaType.MOVIE, query, playlistId, limit).movies

    suspend fun series(profileId: Long, query: String, playlistId: Long?, limit: Int): List<SeriesEntity> =
        search(profileId, MediaType.SERIES, query, playlistId, limit).series

    /** Channel [id], or null when [profileId] may not see it. */
    suspend fun channel(profileId: Long, id: Long): ChannelEntity? = reads.channel(id)?.takeIf {
        sees(profileId, MediaType.LIVE, it.sourceId, CustomizeKeys.channel(it), it.categoryId)
    }

    /** Movie [id], or null when [profileId] may not see it. */
    suspend fun movie(profileId: Long, id: Long): MovieEntity? = reads.movie(id)?.takeIf {
        sees(profileId, MediaType.MOVIE, it.sourceId, CustomizeKeys.movie(it), it.categoryId)
    }

    /** Show [id], or null when [profileId] may not see it. */
    suspend fun show(profileId: Long, id: Long): SeriesEntity? = reads.show(id)?.takeIf {
        sees(profileId, MediaType.SERIES, it.sourceId, CustomizeKeys.series(it), it.categoryId)
    }

    /** Episode [id] with its show, or null when [profileId] may not see the show. */
    suspend fun episode(profileId: Long, id: Long): ShowEpisode? {
        val episode = reads.episode(id) ?: return null
        val show = show(profileId, episode.seriesId) ?: return null
        return ShowEpisode(show, episode)
    }

    /** Core's search of [type] alone ([SectionSources]) in the profile's playlists, or the one named. */
    private suspend fun search(profileId: Long, type: MediaType, query: String, playlistId: Long?, limit: Int): SearchResults =
        reads.search(profileId, SectionSources.of(profileId, reads.playlists(profileId), type, playlistId), query, limit)

    private suspend fun sees(profileId: Long, type: MediaType, sourceId: Long, key: String, categoryId: Long?): Boolean {
        if (sourceId !in ActiveProfileSources(profileId, reads.playlists(profileId)).sourceIdsFor(type)) return false
        val customized = reads.customizations(profileId, type)
        if (key in customized.hiddenItems) return false
        val category = categoryId?.let { reads.category(it) } ?: return true
        return AdultCategoryClassifier.hiddenCategoryIds(listOf(category), customized.hiddenCategories, reads.isKids(profileId)).isEmpty()
    }
}
