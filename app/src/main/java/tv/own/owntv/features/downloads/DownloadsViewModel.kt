@file:OptIn(ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.DownloadDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer

/** Phase 12 — lists the active profile's downloads and plays completed ones from local storage. */
class DownloadsViewModel(
    private val downloadDao: DownloadDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val categoryDao: tv.own.owntv.core.database.dao.CategoryDao,
    private val profileDao: tv.own.owntv.core.database.dao.ProfileDao,
    private val customize: CustomizationStore,
    private val settings: SettingsRepository,
    private val downloadManager: DownloadManager,
    val player: OwnTVPlayer,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
    private val subtitleController: tv.own.owntv.core.subtitles.SubtitleController,
    private val metadata: tv.own.owntv.core.metadata.MetadataRepository,
) : ViewModel() {

    /**
     * The profile's downloads, minus rows whose movie/series the user has hidden — a hidden title
     * disappears everywhere, Downloads included. The file stays on disk and the row reappears on
     * unhide (Settings → Customize Category). Downloads whose source item no longer exists (deleted
     * playlist / re-sync id churn) are kept — they can't be resolved to a hide key.
     */
    val downloads: StateFlow<List<DownloadEntity>> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) {
                flowOf(emptyList())
            } else {
                combine(
                downloadDao.observeForProfile(pid),
                customize.observe(pid, MediaType.MOVIE),
                customize.observe(pid, MediaType.SERIES),
                profileDao.observeById(pid),
            ) { list, custMovie, custSeries, profile ->
                if (custMovie.hiddenItems.isEmpty() && custSeries.hiddenItems.isEmpty() && profile?.isKids != true) list
                else list.filterNot { isHidden(it, custMovie, custSeries, profile?.isKids == true) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun isHidden(
        d: DownloadEntity,
        custMovie: SectionCustomizations,
        custSeries: SectionCustomizations,
        isKidsProfile: Boolean,
    ): Boolean = when (d.mediaType) {
        MediaType.MOVIE -> movieDao.getById(d.itemId)?.let { movie ->
            CustomizeKeys.movie(movie) in custMovie.hiddenItems ||
                (isKidsProfile && tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(movie.categoryId?.let { categoryDao.getById(it)?.name }))
        } ?: isKidsProfile
        MediaType.EPISODE -> seriesDao.getEpisodeById(d.itemId)
            ?.let { ep -> seriesDao.getSeriesById(ep.seriesId) }
            ?.let { series ->
                CustomizeKeys.series(series) in custSeries.hiddenItems ||
                    (isKidsProfile && tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(series.categoryId?.let { categoryDao.getById(it)?.name }))
            } ?: isKidsProfile
        else -> false
    }

    /** Free/total space on the download volume — recomputed whenever the download list changes. */
    /**
     * Free and total space on the volume the downloads are written to.
     *
     * Keyed on the download **root** as well as on the list, and the root is the one that matters:
     * keyed on the list alone, changing the folder left the bar showing the old volume's numbers
     * until the app was restarted — and on a screen with no downloads yet, the list never changes at
     * all, so it never refreshed. Found on the television the first time the folder was pointed at a
     * USB stick.
     */
    val storage: StateFlow<tv.own.owntv.core.download.DownloadStorageInfo?> =
        combine(downloads, settings.downloadRoot) { _, _ -> Unit }
            .mapLatest { downloadManager.storageInfo() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Where downloads are written. Plan Z moved this preference out of Settings and onto the gear
     * beside this screen's title — it is the one thing on the screen it is about.
     */
    val downloadRoot: StateFlow<String> = settings.downloadRoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setDownloadRoot(path: String) {
        viewModelScope.launch { settings.setDownloadRoot(path) }
    }

    private val _lastPlayedId = MutableStateFlow<Long?>(null)
    val lastPlayedId: StateFlow<Long?> = _lastPlayedId.asStateFlow()

    /** "External player" for downloadable content — the screen must NOT open the fullscreen in-app player
     *  when on (mounting it spins up an mpv instance even though play() branched to the external app).
     *  The list mixes movies and episodes, so this is true when EITHER section is set to play externally;
     *  [play] then re-checks the individual download's own section before branching. */
    val externalPlayerOn: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(settings.externalPlayerMovies, settings.externalPlayerSeries) { m, s -> m || s }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Phase B: always play this download in an external player, regardless of the global toggle. */
    fun playExternal(download: DownloadEntity) {
        val path = download.filePath ?: return
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            if (!isDownloadAllowed(download, pid)) return@launch
            _lastPlayedId.value = download.id
            externalPlayerLauncher.launch(path, download.title)
        }
    }

    /** Play a completed download from its local file. */
    fun play(download: DownloadEntity) {
        val path = download.filePath ?: return
        _lastPlayedId.value = download.id
        viewModelScope.launch {
            // External player (global toggle): share the downloaded file with an external app via its
            // FileProvider URI. Otherwise play it in the built-in player.
            val pid = settings.activeProfileId.first()
            if (!isDownloadAllowed(download, pid)) return@launch
            if (settings.externalPlayerFor(download.mediaType).first()) {
                externalPlayerLauncher.launch(path, download.title)
                return@launch
            }
            player.play(path, title = download.title, isLive = false)
            setSubtitleContext(download, path)
        }
    }

    /**
     * Downloaded movies/episodes get the same external-subtitle experience as streams (subtitle plan
     * §3.3): previously downloaded subs re-list offline (§9), local files attach, and a new search —
     * when online — carries the optional moviehash enhancer via [localPath]. Best-effort: a download
     * whose source item was deleted just plays without a subtitle context, as before.
     */
    private suspend fun isDownloadAllowed(download: DownloadEntity, profileId: Long): Boolean = when (download.mediaType) {
        MediaType.MOVIE -> movieDao.getById(download.itemId)?.let {
            tv.own.owntv.core.content.AdultCategoryClassifier.allows(profileId, it.categoryId, profileDao, categoryDao)
        } ?: (profileDao.getById(profileId)?.let { !it.isKids } ?: false)
        MediaType.EPISODE -> seriesDao.getEpisodeById(download.itemId)
            ?.let { seriesDao.getSeriesById(it.seriesId) }
            ?.let { tv.own.owntv.core.content.AdultCategoryClassifier.allows(profileId, it.categoryId, profileDao, categoryDao) }
            ?: (profileDao.getById(profileId)?.let { !it.isKids } ?: false)
        else -> true
    }

    private suspend fun setSubtitleContext(download: DownloadEntity, localPath: String) {
        val pid = settings.activeProfileId.first()
        runCatching {
            when (download.mediaType) {
                MediaType.MOVIE -> movieDao.getById(download.itemId)?.let { movie ->
                    val tmdbId = runCatching { metadata.resolveMovie(movie)?.tmdbId?.toLong() }.getOrNull()
                    subtitleController.setMovie(pid, movie, tmdbId, localFilePath = localPath)
                } ?: subtitleController.clear()
                MediaType.EPISODE -> {
                    val ep = seriesDao.getEpisodeById(download.itemId)
                    val show = ep?.let { seriesDao.getSeriesById(it.seriesId) }
                    if (ep != null && show != null) {
                        val parentTmdbId = runCatching { metadata.resolveSeries(show)?.tmdbId?.toLong() }.getOrNull()
                        subtitleController.setEpisode(pid, show, ep, parentTmdbId, localFilePath = localPath)
                    } else subtitleController.clear()
                }
                else -> subtitleController.clear()
            }
        }
    }

    fun retry(download: DownloadEntity) = downloadManager.retry(download)
    fun pause(download: DownloadEntity) = downloadManager.pause(download)
    fun resume(download: DownloadEntity) = downloadManager.resume(download)
    fun delete(download: DownloadEntity) = downloadManager.delete(download)
}
