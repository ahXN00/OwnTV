@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.transformLatest
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.ui.stage.PlaylistMark
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.content.SearchIntent
import tv.own.owntv.core.content.SearchKind
import tv.own.owntv.core.content.SearchReader
import tv.own.owntv.core.content.SearchResults
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Cross-section search over a profile's channels, movies and series.
 *
 * The searching itself is core's [SearchReader]: the FTS expression, the hidden items and
 * categories, the kids filter and the Customize renames are the same rules on a phone as on a
 * television, so they are not written twice. What stays here is the screen's own behaviour —
 * debouncing and the recents list. A result is never played here: OK goes to it.
 */
class SearchViewModel(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val settings: SettingsRepository,
    private val searchReader: SearchReader,
    private val metadata: MetadataRepository,
) : ViewModel() {

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
        if (q.trim() != _query.value.trim()) lastFocusKey = null
        _query.value = q
        if (q.isNotBlank()) _intent.value = null // typing overrides an active launcher intent
    }

    // --- Empty-state launcher (recent search terms + Continue / Unwatched / Channels) ---

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

    // --- Stage page ---

    /** The filter buttons switched on; none = everything. Kept across new search text; Back clears it. */
    private val _kinds = MutableStateFlow<Set<SearchKind>>(emptySet())
    val kinds: StateFlow<Set<SearchKind>> = _kinds.asStateFlow()
    fun toggleKind(k: SearchKind) { _kinds.value = if (k in _kinds.value) _kinds.value - k else _kinds.value + k }
    fun showOnly(k: SearchKind) { _kinds.value = setOf(k) }
    fun clearKinds() { _kinds.value = emptySet() }

    /** The row a "Go to" left from ("c12", "m34", "s56"), so Back to Search lands on it again. */
    var lastFocusKey: String? = null

    /** Which playlist a row comes from, as on the browse screens; empty with one playlist. */
    val playlistMarks: StateFlow<Map<Long, PlaylistMark>> = ctx
        .map { aps -> if (aps.sources.size < 2) emptyMap() else aps.sources.withIndex().associate { (i, s) -> s.id to PlaylistMark.of(s.name, i) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Category names for the movie and series rows ("2005 · ★ 6.2 · Disney+ Movies"). */
    val categoryNames: StateFlow<Map<Long, String>> = combine(results, curatedResults) { a, b ->
        (a.movies.mapNotNull { it.categoryId } + a.series.mapNotNull { it.categoryId } +
            b.movies.mapNotNull { it.categoryId } + b.series.mapNotNull { it.categoryId }).toSet()
    }
        .distinctUntilChanged()
        .mapLatest { ids -> ids.mapNotNull { id -> categoryDao.getById(id)?.let { id to it.name } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val metadataMode: StateFlow<tv.own.owntv.core.metadata.MetadataMode> = settings.metadataMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)

    /** The focused movie or series, for the poster panel's TMDB details (backdrop, genres, cast). */
    private val _focusedVod = MutableStateFlow<Any?>(null)
    fun focusVod(item: Any?) { _focusedVod.value = item }

    /** The focused title's key ("m34" / "s56") and its TMDB details, after the same focus debounce the
     *  browse screens use, so passing over rows looks nothing up. */
    val focusedMeta: StateFlow<Pair<String, MetadataCacheEntity?>?> = _focusedVod
        .transformLatest { item ->
            val key = when (item) { is MovieEntity -> "m${item.id}"; is SeriesEntity -> "s${item.id}"; else -> null }
            if (key == null) { emit(null); return@transformLatest }
            delay(MetadataRepository.FOCUS_DEBOUNCE_MS)
            val cache = runCatching {
                when (item) { is MovieEntity -> metadata.resolveMovie(item); is SeriesEntity -> metadata.resolveSeries(item); else -> null }
            }.getOrNull()
            emit(key to cache)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }
}
