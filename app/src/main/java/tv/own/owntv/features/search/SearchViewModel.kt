@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.search

import tv.own.owntv.core.epg.displayLogoUrl
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.database.entity.playStreamUrl
import tv.own.owntv.core.content.SearchIntent
import tv.own.owntv.core.content.SearchReader
import tv.own.owntv.core.content.SearchResults
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer

/**
 * Phase 11 — cross-section search over a profile's channels, movies and series.
 *
 * The searching itself is core's [SearchReader]: the FTS expression, the hidden items and
 * categories, the kids filter and the Customize renames are the same rules on a phone as on a
 * television, so they are not written twice. What stays here is the screen's own behaviour —
 * debouncing, the recents list, favourites, and how a result is played.
 */
class SearchViewModel(
    private val categoryDao: CategoryDao,
    private val historyDao: HistoryDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val favoriteDao: FavoriteDao,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val searchReader: SearchReader,
    val player: OwnTVPlayer,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
) : ViewModel() {

    /** Global "External player" toggle — the screen must NOT open the fullscreen in-app player when on. */
    val externalPlayerOn: kotlinx.coroutines.flow.StateFlow<Boolean> = settings.externalPlayerMovies
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Observe the active profile's sources reactively so adding/removing a playlist refreshes Search
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    // Per-section Off flags split the id sets so an Off section never surfaces in results.
    private val ctx: StateFlow<ActiveProfileSources> = activeProfileSources(settings, sourceDao)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveProfileSources(-1L, emptyList()))

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val results: StateFlow<SearchResults> = _query
        .map { it.trim() }
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.length < 2) {
                flowOf(SearchResults())
            } else {
                flowOf(search(q))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) {
        _query.value = q
        if (q.isNotBlank()) _intent.value = null // typing overrides an active launcher intent
    }

    // --- Batch 5: empty-state launcher (recent search terms + Continue / Unwatched / Channels) ---

    /** Persisted recent search terms (most-recent first). */
    val recentSearches: StateFlow<List<String>> = settings.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearRecentSearches() { viewModelScope.launch { settings.clearRecentSearches() } }

    /** Save the current query into recents — called when the user actually opens a result. */
    fun rememberCurrentQuery() { viewModelScope.launch { settings.addRecentSearch(_query.value) } }

    private val _intent = MutableStateFlow<SearchIntent?>(null)
    val intent: StateFlow<SearchIntent?> = _intent.asStateFlow()

    /** Selecting an intent clears any typed query so the curated list shows; null returns to the launcher. */
    fun setIntent(i: SearchIntent?) {
        _intent.value = i
        if (i != null) _query.value = ""
    }

    /** Curated results for the active empty-state intent (bounded; reuses favourites/history queries). */
    val curatedResults: StateFlow<SearchResults> = combine(_intent, ctx) { i, c -> i to c }
        .flatMapLatest { (i, c) ->
            if (i == null) flowOf(SearchResults())
            else flowOf(searchReader.curated(c.profileId, c, i))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    private suspend fun search(q: String): SearchResults {
        val pid = currentProfileId() ?: return SearchResults()
        return searchReader.search(pid, ctx.value, q)
    }

    /** Live channels this profile has favourited — so a search result can show a star and toggle it. */
    val favoriteChannelIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.LIVE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Long-press a channel result to add/remove it from Favorites (no need to open Live TV first). */
    fun toggleFavoriteChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = ctx.value.profileId
            if (pid < 0) return@launch
            if (favoriteChannelIds.value.contains(channel.id)) {
                userDataWriter.removeFavorite(pid, MediaType.LIVE, channel.id)
            } else {
                favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }
        }
    }

    /** The non-playback half of tuning a channel result: watch history + the recent-searches entry.
     *  The shell calls this and then hands playback to LiveViewModel's shared path (F05), so a channel
     *  opened from Search gets the same engine routing as one opened from Live TV. */
    fun noteChannelPlayed(channel: ChannelEntity) {
        record(MediaType.LIVE, channel.id)
        rememberCurrentQuery()
    }

    // A second live-start path used to live here as a "standalone host" fallback: mpv straight away, no
    // ExoPlayer-first ladder, no per-channel engine pin, no learned quirks. It was unreachable in the app
    // (the shell always supplies the callback) but it was a standing invitation for the two paths to
    // drift, so SearchScreen now REQUIRES the shared callback and this copy is gone.

    fun playMovie(movie: MovieEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, movie.categoryId, profileDao, categoryDao)) return@launch
            val source = sourceDao.getById(movie.sourceId)
            // Stalker: resolve the stored cmd before EITHER branch — the external player must never
            // receive a raw portal cmd (plan §7), and neither should the in-app engines.
            val url = if (streamUrlResolver.needsResolve(source)) {
                runCatching { streamUrlResolver.resolve(source!!, movie.streamUrl, vod = true) }
                    .onFailure { Log.w(TAG, "stalker resolve failed movieId=${movie.id}", it) }
                    .getOrNull() ?: return@launch
            } else {
                movie.streamUrl
            }
            // Global external-player toggle: same chokepoint behavior as MovieViewModel.play().
            // #115 — a protected item cannot go to an external player: no standard intent extra
            // carries a licence URL, so the other app would open it and fail on the first segment.
            // Play it here instead, where the licence request can actually be made.
            if (settings.externalPlayerMovies.first() && movie.drmConfig == null) {
                externalPlayerLauncher.launch(
                    url = url,
                    title = movie.name,
                    userAgent = source?.userAgent,
                    httpHeaders = movie.httpHeaders,
                )
                return@launch
            }
            player.play(
                url, title = movie.name, year = movie.year?.toString(), isLive = false,
                userAgent = source?.userAgent,
                httpHeaders = movie.httpHeaders,
                drmConfig = movie.drmConfig,
                // P6 — same stable engine-pin identity MovieViewModel uses, so a pin made in one
                // screen applies in the other.
                contentKey = tv.own.owntv.core.player.enginePinKey(movie.sourceId, "MOVIE", movie.remoteId),
            )
        }
        record(MediaType.MOVIE, movie.id)
        rememberCurrentQuery()
    }

    private fun record(type: MediaType, itemId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            runCatching {
                historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = type, itemId = itemId))
            }.onFailure { t ->
                Log.w(TAG, "record history failed profile=$pid type=$type itemId=$itemId", t)
            }
        }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    private companion object {
        const val TAG = "OwnTVHome"
    }
}
