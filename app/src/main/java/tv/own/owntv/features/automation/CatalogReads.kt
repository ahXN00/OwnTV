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

/**
 * What [ProfileCatalog] decides over: a profile's playlists and Customize state, single rows by id, and
 * Core's search. The Android side is [RoomCatalogReads].
 */
interface CatalogReads {
    suspend fun playlists(profileId: Long): List<SourceEntity>

    suspend fun isKids(profileId: Long): Boolean

    /** What the profile changed in Customize for [type]: hidden items and categories, renames. */
    suspend fun customizations(profileId: Long, type: MediaType): SectionCustomizations

    suspend fun category(id: Long): CategoryEntity?

    suspend fun channel(id: Long): ChannelEntity?

    suspend fun movie(id: Long): MovieEntity?

    suspend fun show(id: Long): SeriesEntity?

    suspend fun episode(id: Long): EpisodeEntity?

    /**
     * Core's search ([tv.own.owntv.core.content.SearchReader.search]): [query] among [sources], without the
     * profile's hidden items and categories or what a kids profile may not see, and with its renames.
     */
    suspend fun search(profileId: Long, sources: ActiveProfileSources, query: String, limit: Int): SearchResults
}
