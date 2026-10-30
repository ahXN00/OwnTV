package tv.own.owntv.features.automation

import kotlinx.coroutines.flow.first
import tv.own.owntv.core.content.SearchReader
import tv.own.owntv.core.content.SearchResults
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources

/** [CatalogReads] from the database, the Customize store and Core's [SearchReader]. */
class RoomCatalogReads(
    private val profile: AutomationProfile,
    private val profileDao: ProfileDao,
    private val customize: CustomizationStore,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val reader: SearchReader,
) : CatalogReads {
    override suspend fun playlists(profileId: Long): List<SourceEntity> = profile.playlists(profileId)

    override suspend fun isKids(profileId: Long): Boolean = profileDao.getById(profileId)?.isKids == true

    override suspend fun customizations(profileId: Long, type: MediaType): SectionCustomizations =
        customize.observe(profileId, type).first()

    override suspend fun category(id: Long): CategoryEntity? = categoryDao.getById(id)

    override suspend fun channel(id: Long): ChannelEntity? = channelDao.getById(id)

    override suspend fun movie(id: Long): MovieEntity? = movieDao.getById(id)

    override suspend fun show(id: Long): SeriesEntity? = seriesDao.getSeriesById(id)

    override suspend fun episode(id: Long): EpisodeEntity? = seriesDao.getEpisodeById(id)

    override suspend fun search(profileId: Long, sources: ActiveProfileSources, query: String, limit: Int): SearchResults =
        reader.search(profileId, sources, query, limit)
}
