@file:OptIn(ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.downloads

import tv.own.owntv.core.storage.missingFiles
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.ItemCategory
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.sealsItem
import tv.own.owntv.core.customize.sealsItemIn
import tv.own.owntv.core.database.dao.DownloadDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.categorylock.PlayingFileCategory
import tv.own.owntv.features.categorylock.anyLockSealed
import tv.own.owntv.player.OwnTVPlayer

/** Phase 12 — lists the active profile's downloads and plays completed ones from local storage. */
class DownloadsViewModel(
    /** Only to check whether finished files are still there. */
    appContext: android.content.Context,
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
    tracker: tv.own.owntv.core.download.DownloadActivityTracker,
    private val playingFiles: PlayingFileCategory,
) : ViewModel() {

    /** What playing does to a running download on a one-stream playlist (the Downloads screen's tool). */
    val downloadWatchChoice: StateFlow<SettingsRepository.DownloadWatchChoice> = settings.downloadWatchChoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DownloadWatchChoice.ASK)

    fun setDownloadWatchChoice(choice: SettingsRepository.DownloadWatchChoice) {
        viewModelScope.launch { settings.setDownloadWatchChoice(choice) }
    }

    /** The one transfer running now, with its speed (G3), for the focused row's "12.4 MB/s · 3 min left". */
    val active: StateFlow<tv.own.owntv.core.download.DownloadActivityTracker.ActiveDownload?> = tracker.active

    /**
     * The profile's downloads, minus rows whose movie/series the user has hidden — a hidden title
     * disappears everywhere, Downloads included. The file stays on disk and the row reappears on
     * unhide (Settings → Customize Category). A title in a locked category stays out the same way
     * until the PIN opens that category. Downloads whose source item no longer exists (deleted
     * playlist / re-sync id churn) are kept — they can't be resolved to a hide key — except while the
     * profile has a lock that is not open: which category held them is unknown, so they stay out too.
     */
    val downloads: StateFlow<List<DownloadEntity>> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) {
                flowOf(emptyList())
            } else {
                combine(
                downloadDao.observeForProfile(pid),
                customize.observeVisible(pid, MediaType.MOVIE),
                customize.observeVisible(pid, MediaType.SERIES),
                profileDao.observeById(pid),
                customize.anyLockSealed(pid),
            ) { list, custMovie, custSeries, profile, anySealed ->
                val noSeals = !anySealed && custMovie.sealedCategoryIds.isEmpty() && custSeries.sealedCategoryIds.isEmpty()
                if (custMovie.hiddenItems.isEmpty() && custSeries.hiddenItems.isEmpty() && noSeals && profile?.isKids != true) list
                else list.filterNot { isHidden(it, custMovie, custSeries, profile?.isKids == true, anySealed) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun isHidden(
        d: DownloadEntity,
        custMovie: SectionCustomizations,
        custSeries: SectionCustomizations,
        isKidsProfile: Boolean,
        anySealed: Boolean,
    ): Boolean = when (d.mediaType) {
        MediaType.MOVIE -> movieDao.getById(d.itemId)?.let { movie ->
            CustomizeKeys.movie(movie) in custMovie.hiddenItems || custMovie.sealsItemIn(movie.categoryId) ||
                (isKidsProfile && AdultCategoryClassifier.isAdult(movie.categoryId?.let { categoryDao.getById(it)?.name }))
        } ?: (isKidsProfile || custMovie.sealsItem(ItemCategory.Unresolved, anySealed))
        MediaType.EPISODE -> seriesDao.getEpisodeById(d.itemId)
            ?.let { ep -> seriesDao.getSeriesById(ep.seriesId) }
            ?.let { series ->
                CustomizeKeys.series(series) in custSeries.hiddenItems || custSeries.sealsItemIn(series.categoryId) ||
                    (isKidsProfile && AdultCategoryClassifier.isAdult(series.categoryId?.let { categoryDao.getById(it)?.name }))
            } ?: (isKidsProfile || custSeries.sealsItem(ItemCategory.Unresolved, anySealed))
        else -> false
    }

    /**
     * What a row shows besides the stored title: the show's name for an episode, 16:9 art, and the
     * parts of "2026 · 1 h 38 min · 1080p" / "Season 1 · Episode 3". Looked up once per download —
     * the list re-emits on every progress tick — and absent for a title its playlist no longer has.
     */
    /** Paths of finished downloads whose file is gone (drive formatted, swapped or unplugged). */
    val missingFiles: StateFlow<Set<String>> = downloads
        .map { rows -> rows// A paused or failed one has a partial file that can go missing too (a formatted stick).
        .filter { it.status == tv.own.owntv.core.model.DownloadStatus.COMPLETED || (it.status in RESUMABLE && it.downloadedBytes > 0) }.map { it.filePath } }
        .missingFiles(appContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val details: StateFlow<Map<Long, DownloadDetails>> = run {
        val cache = HashMap<Long, DownloadDetails?>()
        downloads.mapLatest { list ->
            list.mapNotNull { d ->
                val found = if (cache.containsKey(d.id)) cache[d.id] else lookUp(d).also { cache[d.id] = it }
                found?.let { d.id to it }
            }.toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    }

    private suspend fun lookUp(d: DownloadEntity): DownloadDetails? = when (d.mediaType) {
        MediaType.MOVIE -> movieDao.getById(d.itemId)?.let { m ->
            DownloadDetails(
                title = m.name, artUrl = m.backdropUrl ?: m.posterUrl, year = m.year ?: m.parsedYear, runtimeSecs = m.durationSecs,
                quality = tv.own.owntv.features.shell.components.cinematicQualityBadges(m.qualityRank, null).firstOrNull(),
            )
        }
        MediaType.EPISODE -> seriesDao.getEpisodeById(d.itemId)?.let { ep ->
            val show = seriesDao.getSeriesById(ep.seriesId)
            DownloadDetails(
                title = show?.name ?: d.title, artUrl = show?.backdropUrl ?: show?.posterUrl ?: d.posterUrl,
                runtimeSecs = ep.durationSecs, season = ep.seasonNumber, episode = ep.episodeNumber,
            )
        }
        else -> null
    }

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
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
            playingFiles.started(path, categoryOf(download))
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
            AdultCategoryClassifier.allows(profileId, it.categoryId, profileDao, categoryDao) && !customize.sealsCategory(profileId, it.categoryId)
        } ?: allowsUnresolved(profileId)
        MediaType.EPISODE -> seriesDao.getEpisodeById(download.itemId)
            ?.let { seriesDao.getSeriesById(it.seriesId) }
            ?.let { AdultCategoryClassifier.allows(profileId, it.categoryId, profileDao, categoryDao) && !customize.sealsCategory(profileId, it.categoryId) }
            ?: allowsUnresolved(profileId)
        else -> true
    }

    /** A download whose film or show is gone: not on a kids profile, nor while a lock is closed (its category is unknown). */
    private suspend fun allowsUnresolved(profileId: Long): Boolean =
        profileDao.getById(profileId)?.isKids == false && !customize.sealsItem(profileId, ItemCategory.Unresolved)

    /** The category of [download]'s film or show; null when its playlist no longer has it. */
    private suspend fun categoryOf(download: DownloadEntity): Long? = when (download.mediaType) {
        MediaType.MOVIE -> movieDao.getById(download.itemId)?.categoryId
        MediaType.EPISODE -> seriesDao.getEpisodeById(download.itemId)?.let { seriesDao.getSeriesById(it.seriesId) }?.categoryId
        else -> null
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

/** See [DownloadsViewModel.details]. */
data class DownloadDetails(
    val title: String,
    val artUrl: String?,
    val year: Int? = null,
    val runtimeSecs: Int? = null,
    val quality: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

/** Stopped downloads that keep a partial file on the drive. */
private val RESUMABLE = setOf(tv.own.owntv.core.model.DownloadStatus.PAUSED, tv.own.owntv.core.model.DownloadStatus.FAILED)
