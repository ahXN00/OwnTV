package tv.own.owntv.features.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.DownloadDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.categorylock.lockAwareCount
import tv.own.owntv.core.categorylock.recheckedOn
import tv.own.owntv.core.categorylock.sealedCategoryIds

/** The rail's details lines (Size = Extra wide), as data; the shell turns them into text. */
data class RailDetails(
    val lastChannel: String? = null,
    val resumeMovie: String? = null,
    /** The last watched episode: series name, season and episode number, and whether it is part-watched. */
    val episode: RailEpisode? = null,
    val downloading: Int = 0,
    /** Bytes done over bytes expected across the running downloads, null while no size is known. */
    val downloadProgress: Float? = null,
)

data class RailEpisode(val series: String, val season: Int, val episode: Int, val resume: Boolean)

/**
 * The numbers beside Live TV, Movies, Series and Downloads in the open Stage rail, for the playlists
 * being shown ("All playlists" or the one picked), and the details lines under them. Every query
 * already exists on the DAOs; titles in adult or locked categories stay hidden exactly as in the
 * Continue pill, and the counts leave out what a locked category holds. Both follow a lock closing
 * or opening, not only new history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RailCountsViewModel(
    channelDao: ChannelDao,
    movieDao: MovieDao,
    seriesDao: SeriesDao,
    downloadDao: DownloadDao,
    settings: SettingsRepository,
    sourceDao: SourceDao,
    historyDao: HistoryDao,
    progressDao: ProgressDao,
    profileDao: ProfileDao,
    categoryDao: CategoryDao,
    customize: CustomizationStore,
) : ViewModel() {

    val counts: StateFlow<Map<MainSection, Int>> = activeProfileSources(settings, sourceDao)
        .flatMapLatest { c ->
            if (c.profileId < 0) return@flatMapLatest flowOf(emptyMap())
            // An empty id list would make the IN clause meaningless; -1 matches nothing.
            fun ids(type: MediaType) = c.sourceIdsFor(type).ifEmpty { listOf(-1L) }
            fun count(type: MediaType, all: (List<Long>) -> Flow<Int>, excluding: (List<Long>, List<Long>) -> Flow<Int>) =
                lockAwareCount(customize.sealedCategoryIds(c.profileId, type), { all(ids(type)) }) { excluding(ids(type), it) }
            combine(
                count(MediaType.LIVE, channelDao::countAll, channelDao::countAllExcluding),
                count(MediaType.MOVIE, movieDao::countAll, movieDao::countAllExcluding),
                count(MediaType.SERIES, seriesDao::countAll, seriesDao::countAllExcluding),
                downloadDao.count(c.profileId),
            ) { live, movies, series, downloads ->
                mapOf(
                    MainSection.LIVE_TV to live,
                    MainSection.MOVIES to movies,
                    MainSection.SERIES to series,
                    MainSection.DOWNLOADS to downloads,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val details: StateFlow<RailDetails> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) return@flatMapLatest flowOf(RailDetails())
            suspend fun allowed(categoryId: Long?) =
                AdultCategoryClassifier.allows(pid, categoryId, profileDao, categoryDao) && !customize.sealsCategory(pid, categoryId)
            // Each with its section's customizations, so a rename in Customize shows here too.
            fun lastOf(type: MediaType, section: MediaType) =
                combine(
                    historyDao.observeMostRecentOfType(pid, type).recheckedOn(customize.sealedCategoryIds(pid, section)),
                    customize.observe(pid, section),
                ) { h, cust -> h to cust }
            val live = lastOf(MediaType.LIVE, MediaType.LIVE).map { (h, cust) ->
                h?.let { channelDao.getById(it.itemId) }?.takeIf { allowed(it.categoryId) }?.let { cust.displayName(it) }
            }
            val movie = lastOf(MediaType.MOVIE, MediaType.MOVIE).map { (h, cust) ->
                h?.let { movieDao.getById(it.itemId) }
                    ?.takeIf { allowed(it.categoryId) && (progressDao.get(pid, MediaType.MOVIE, it.id)?.positionMs ?: 0L) > 0 }
                    ?.let { cust.displayName(it) }
            }
            val episode = lastOf(MediaType.EPISODE, MediaType.SERIES).map { (h, cust) ->
                val ep = h?.let { seriesDao.getEpisodeById(it.itemId) } ?: return@map null
                val s = seriesDao.getSeriesById(ep.seriesId)?.takeIf { allowed(it.categoryId) } ?: return@map null
                val pos = progressDao.get(pid, MediaType.EPISODE, ep.id)?.positionMs ?: 0L
                RailEpisode(cust.displayName(s), ep.seasonNumber, ep.episodeNumber, resume = pos > 0)
            }
            val downloads = downloadDao.observeForProfile(pid).map { all -> all.filter { it.status == DownloadStatus.RUNNING } }
            combine(live, movie, episode, downloads) { l, m, e, running ->
                val total = running.sumOf { it.totalBytes }
                RailDetails(
                    lastChannel = l,
                    resumeMovie = m,
                    episode = e,
                    downloading = running.size,
                    downloadProgress = if (total > 0) running.sumOf { it.downloadedBytes }.toFloat() / total else null,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RailDetails())
}
