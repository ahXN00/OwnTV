@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.live

import tv.own.owntv.core.epg.displayLogoUrl
import android.content.Context
import androidx.compose.runtime.Immutable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.paging.filter
import androidx.paging.map
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.epg.CatchupUrl
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.formatSystemTime

/** Nivel del overlay de canales en el player: lista de canales o lista de categorías. */
enum class ChannelOverlayLevel { CHANNELS, CATEGORIES }

/** Layer-2 rail selection for Live TV. */
sealed interface LiveKey {
    data object Favorites : LiveKey
    data object History : LiveKey
    data object All : LiveKey
    data class Folder(val id: Long) : LiveKey
}

// Persistence for the "remember last category" toggles. The same rail model backs Live TV, Movies and
// Series, so all three view models share one encoding (stored per section in SettingsRepository).
fun LiveKey.serialize(): String = when (this) {
    LiveKey.Favorites -> "FAV"
    LiveKey.History -> "HIST"
    LiveKey.All -> "ALL"
    is LiveKey.Folder -> "FOLDER:$id"
}

fun parseLiveKey(s: String): LiveKey? = when {
    s == "FAV" -> LiveKey.Favorites
    s == "HIST" -> LiveKey.History
    s == "ALL" -> LiveKey.All
    s.startsWith("FOLDER:") -> s.removePrefix("FOLDER:").toLongOrNull()?.let { LiveKey.Folder(it) }
    else -> null
}

/** A rail entry. Favorites/History carry an [icon] rendered inline before the title. */
@Immutable
data class LiveRailItem(val key: LiveKey, val title: String, val icon: OwnTVIcon? = null)

/** Now-playing + up-next EPG for the focused channel (null entries when the guide is unavailable). */
@Immutable
data class EpgNowNext(
    val now: XtEpgEntry?,
    val next: XtEpgEntry?,
    val upcoming: List<XtEpgEntry> = emptyList(),
    val previous: XtEpgEntry? = null,
    /** Whole days of stored guide coverage for this channel (latest stop − earliest start).
     *  Null when unknown/short-EPG only. Drives the "EPG · Nd" hint in the preview metadata. */
    val coverageDays: Int? = null,
)

class LiveViewModel(
    private val appContext: Context,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val xtreamClient: XtreamClient,
    private val customize: CustomizationStore,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
    private val epgSourceStore: tv.own.owntv.core.epg.EpgSourceStore,
    val player: OwnTVPlayer,
    val previewEngine: tv.own.owntv.player.LivePreviewEngine,
    private val forceMpvStore: tv.own.owntv.core.player.ForceMpvStore,
    private val contentOrderDao: ContentOrderDao,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
) : ViewModel() {

    data class ChannelMoveState(val items: List<ChannelEntity>, val activeIndex: Int, val contextKey: String)
    private val _moveState = MutableStateFlow<ChannelMoveState?>(null)
    val moveState: StateFlow<ChannelMoveState?> = _moveState.asStateFlow()

    val livePreviewEnabled: StateFlow<Boolean> = settings.livePreviewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Channels pinned to mpv ("compatibility mode") — opened straight on mpv, bypassing ExoPlayer. Eagerly
     *  collected so the routing decision in [ensurePlaying] always sees the current set. Keyed by
     *  [enginePinKey], with legacy stream-URL entries still honoured (P6). */
    val forceMpvUrls: StateFlow<Set<String>> = forceMpvStore.urls
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** List ordering for this section (Playlist order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortLive
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.PLAYLIST)

    fun toggleSort() {
        viewModelScope.launch {
            settings.setSortLive(
                if (sortMode.value == SettingsRepository.SortMode.PLAYLIST) SettingsRepository.SortMode.ALPHA
                else SettingsRepository.SortMode.PLAYLIST,
            )
        }
    }

    private val livePreviewAudio: StateFlow<Boolean> = settings.livePreviewAudio
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Settings → "Channel numbers": shows the provider number in the lists and the player, and enables
     *  typing one to tune. Off hides every number without touching the stored data. Eager so a playback
     *  path can read [StateFlow.value] synchronously when building its [tv.own.owntv.player.MediaMeta]. */
    val showChannelNumbers: StateFlow<Boolean> = settings.directTune
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** The "#123" the player shows under the channel name, in the top bar and on the channel card.
     *  Gating it here rather than at each call site means every playback path — Exo, mpv, Stalker —
     *  is covered by the one check, and a null simply omits the line as it did before numbers existed. */
    private fun channelNumberLabel(channel: ChannelEntity): String? =
        channel.number?.takeIf { showChannelNumbers.value }?.let { "#$it" }

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)

    // Observe the active profile's sources REACTIVELY so adding/removing a playlist refreshes Live TV
    // immediately (it used to be read once at startup, so a new playlist showed nothing until restart).
    // sourceUaMap is a lightweight side-product: sourceId → userAgent, used for synchronous play() calls
    // (playPreview, ensurePlaying) that can't do a DB lookup on the call site.
    private var sourceUaMap: Map<Long, String?> = emptyMap()
    // Full sources by id — lets the synchronous play() paths tell a Stalker source (needs play-time
    // create_link resolution) from M3U/Xtream (final URL already stored) without a DB round-trip.
    private var sourceById: Map<Long, tv.own.owntv.core.database.entity.SourceEntity> = emptyMap()
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps ->
            sourceUaMap = aps.sources.associate { it.id to it.userAgent }
            sourceById = aps.sources.associateBy { it.id }
            Ctx(aps.profileId, aps.liveSourceIds)
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    private val folderContextKeys: StateFlow<Map<Long, String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyMap())
            else categoryDao.observe(c.sourceIds, MediaType.LIVE).map { cats ->
                cats.associateBy({ it.id }, { CustomizeKeys.category(it) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Contexts that actually have manual-order rows (C3): only those folders pay the
     *  unindexable content_order join-sort; everything else stays on the plain indexed query. */
    private val orderedContexts: StateFlow<Set<String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptySet())
            else contentOrderDao.observeContextKeys(c.profileId, MediaType.LIVE).map { it.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _previewChannel = MutableStateFlow<ChannelEntity?>(null)
    val previewChannel: StateFlow<ChannelEntity?> = _previewChannel.asStateFlow()

    private data class CachedEpg(val at: Long, val data: EpgNowNext)
    private val epgCache = HashMap<Long, CachedEpg>()

    /** Bumped when a channel's EPG mapping changes so [nowNext] reloads for the same focused channel. */
    private val epgRefresh = MutableStateFlow(0)

    /** Now/next for the focused channel — fetched (debounced) from the Xtream `get_short_epg` API. */
    val nowNext: StateFlow<EpgNowNext?> = combine(_previewChannel, epgRefresh) { ch, tick -> ch to tick }
        .debounce(350)
        .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second }
        .mapLatest { (ch, _) -> ch?.let { loadEpg(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The focused channel's REAL category name — resolved from its `categoryId`, NOT from whatever rail
     * item the user is currently browsing (Favorites / History / All are browse contexts, not the
     * channel's actual category). Null when the channel has no category or it can't be resolved.
     * Drives the category chip + genre-dot in the preview pane's metadata row.
     */
    val previewCategoryName: StateFlow<String?> = _previewChannel
        .mapLatest { ch -> ch?.categoryId?.let { id -> categoryDao.getById(id)?.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    /** This profile's hide/rename/reorder customizations for Live TV. */
    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.LIVE)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /**
     * Category DB ids of the profile's hidden categories. Hiding a category used to only drop its rail
     * folder — its channels still showed in "All Channels", search and recently-watched (so hiding the
     * adult groups left them all visible under ALL). Resolving the hidden category keys to ids here lets
     * those lists filter the channels out, so hiding a group hides its channels everywhere.
     */
    private val hiddenCategoryIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(emptySet())
            } else {
                combine(categoryDao.observe(c.sourceIds, MediaType.LIVE), custom) { cats, cust ->
                    if (cust.hiddenCategories.isEmpty()) emptySet()
                    else cats.filter { CustomizeKeys.category(it) in cust.hiddenCategories }.map { it.id }.toSet()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Customizations + resolved hidden-category ids, bundled so the list pipeline takes one flow. */
    private data class CustState(val cust: SectionCustomizations, val hiddenCats: Set<Long>)
    private val custResolved: StateFlow<CustState> = combine(custom, hiddenCategoryIds) { c, h -> CustState(c, h) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustState(SectionCustomizations(), emptySet()))

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else combine(categoryDao.observe(c.sourceIds, MediaType.LIVE), custom, sortMode) { cats, cust, sort ->
                // A–Z also sorts the category folders; manually moved categories stay pinned first.
                val folders = cats.applyCustomizations(cust, alphaRest = sort == SettingsRepository.SortMode.ALPHA)
                defaultRail + folders.map { (cat, name) ->
                    LiveRailItem(LiveKey.Folder(cat.id), name)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultRail)

    val channels: Flow<PagingData<ChannelEntity>> = combine(
        _selected,
        ctx,
        _search.map { it.trim() }.debounce(300).distinctUntilChanged(),
        sortMode,
        custResolved,
    ) { key, c, query, sort, cs -> Args(key, c, query, sort, cs) }
        // Rebuild the pager when a folder gains/loses manual order (C3): the fast-path plain
        // PagingSource doesn't observe content_order, so the switch must recreate it.
        .combine(orderedContexts) { args, _ -> args }
        .flatMapLatest { (key, c, query, sort, cs) ->
            // Customizations are applied to each fresh PagingData inside the pager chain — a PagingData
            // that the UI already collected must never be re-transformed (Paging forbids re-collection,
            // which is why hiding a channel used to crash). A customization change re-creates the pager.
            Pager(PagingConfig(pageSize = 80, prefetchDistance = 40, initialLoadSize = 120, maxSize = 400)) {
                pagingSource(key, c, query, sort)
            }.flow.map { paging ->
                val cust = cs.cust
                val hiddenCats = cs.hiddenCats
                if (cust.hiddenItems.isEmpty() && cust.itemNames.isEmpty() && hiddenCats.isEmpty()) paging
                else paging
                    .filter { ch -> isChannelVisible(ch, cust, hiddenCats) }
                    .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
            }
        }
        .cachedIn(viewModelScope)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode, val cs: CustState)

    /** Hide the focused channel from all lists (undo via Settings → Customize → Hidden channels). */
    fun hideChannel(channel: ChannelEntity) {
        if (_previewChannel.value?.id == channel.id) stopPreview()
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setItemHidden(pid, MediaType.LIVE, CustomizeKeys.channel(channel), channel.name, true)
        }
    }

    /** Rename the channel for this profile (blank restores the provider's name). */
    fun renameChannel(channel: ChannelEntity, newName: String?) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.renameItem(pid, MediaType.LIVE, CustomizeKeys.channel(channel), newName)
        }
    }

    /** Manually map a channel to an EPG channel id (null clears the override → auto-match). */
    fun setEpgMatch(channel: ChannelEntity, epgChannelId: String?) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(channel), epgChannelId)
            // The matched id may have no stored programmes yet (bulk sync only keeps ids in use) —
            // top it up from the cached XMLTV, then drop the channel's stale now/next and re-fetch,
            // so the details pane reflects the new match immediately instead of after a restart.
            val id = epgChannelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (id != null) runCatching { epgRepository.storeProgrammesForIdsFromCache(setOf(id)) }
            epgCache.remove(channel.id)
            epgRefresh.value++
        }
    }

    /** The current manual EPG id for a channel, or null if auto-matched. */
    fun currentEpgMatch(channel: ChannelEntity): String? = custom.value.epgMatches[CustomizeKeys.channel(channel)]

    /** Distinct EPG channels for the "Match EPG" picker (across the profile's playlists + EPG feeds),
     *  ranked so guide channels resembling [channelName] come first instead of a plain A-Z list. */
    suspend fun availableEpgChannels(channelName: String, query: String): List<tv.own.owntv.core.database.entity.EpgChannelEntity> {
        if (currentProfileId() == null) return emptyList()
        val ids = ctx.value.sourceIds + epgSourceStore.getAll().map { it.id }
        if (ids.isEmpty()) return emptyList()
        // Fetch the whole (filtered) candidate set, not just the first 300 alphabetically — the best
        // name match may sit far down the alphabet. Rank off-main, then cap for the dialog list.
        val all = epgDao.listEpgChannels(ids, query.trim().lowercase(), EPG_PICKER_SCAN_LIMIT)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            tv.own.owntv.core.epg.EpgMatcher.rankForPicker(channelName, all, { it.displayName }, { it.epgChannelId }).take(EPG_PICKER_RESULT_LIMIT)
        }
    }

    val count: StateFlow<Int> = combine(_selected, ctx, hiddenCategoryIds) { key, c, hidden -> Triple(key, c, hidden) }
        .flatMapLatest { (key, c, hidden) -> countFlow(key, c, hidden).throttleLatest() } // C2: cap live COUNT re-runs during bulk sync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.LIVE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val recentlyWatched: StateFlow<List<ChannelEntity>> = ctx
        .flatMapLatest { channelDao.recentlyWatched(it.profileId, 20) }
        .combine(custResolved) { list, cs ->
            list.filter {
                CustomizeKeys.channel(it) !in cs.cust.hiddenItems &&
                    (it.categoryId == null || it.categoryId !in cs.hiddenCats)
            }
                .map { ch -> cs.cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(key: LiveKey) {
        _selected.value = key
    }

    init {
        // Persist the selected category (debounced — the rail fires select() on focus as you scroll).
        viewModelScope.launch {
            _selected.drop(1).debounce(800).distinctUntilChanged().collect { settings.setLastLiveCategory(it.serialize()) }
        }
        // Restore it once at startup — but only while still on the default (don't yank a user who already
        // navigated). A saved folder is honoured only once it actually exists in this profile's rail.
        // Gated by "Remember last category — Live TV" (Settings → Browsing & lists), on by default.
        viewModelScope.launch {
            if (!settings.rememberCategoryLive.first()) return@launch
            val saved = parseLiveKey(settings.lastLiveCategory.first()) ?: return@launch
            if (saved is LiveKey.Folder) {
                val ok = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                    railItems.first { list -> list.any { it.key == saved } }
                } != null
                if (ok && _selected.value == LiveKey.All) _selected.value = saved
            } else if (_selected.value == LiveKey.All) {
                _selected.value = saved
            }
        }
        // Persist the last focused/interacted channel (debounced), and restore it once at startup so opening
        // Live TV lands focus back on it. Restore leaves the preview disarmed (no auto-preview on launch).
        // The restore is gated by the "Remember last item — Live TV" setting so users who want each category
        // to start at the top don't also get yanked to a saved channel on re-entry. The category restore
        // above is a separate toggle ("Remember last category — Live TV").
        viewModelScope.launch {
            _previewChannel.drop(1).filterNotNull().map { it.id }.debounce(800).distinctUntilChanged()
                .collect { settings.setLastLiveChannelId(it) }
        }
        viewModelScope.launch {
            if (!settings.rememberLastLive.first()) return@launch
            val savedId = settings.lastLiveChannelId.first()
            if (savedId > 0 && _previewChannel.value == null) {
                ctx.first { it.profileId >= 0 }
                channelDao.getById(savedId)?.let { if (_previewChannel.value == null) _previewChannel.value = it }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _search.value = query
    }

    fun onChannelFocused(channel: ChannelEntity) {
        _previewArmed.value = true // a real user focus — the in-pane preview may now play
        _previewChannel.value = channel
    }

    // The in-pane preview only plays once the user has actually focused a channel — so restoring the last
    // focused channel on startup positions focus & the details pane WITHOUT auto-previewing on launch (#6).
    private val _previewArmed = MutableStateFlow(false)
    val previewArmed: StateFlow<Boolean> = _previewArmed.asStateFlow()


    /** In-pane preview playback (no history) — triggered by the UI after the focus settles. Runs on the
     *  lightweight ExoPlayer engine (fast HLS start), not mpv; the full/fullscreen player stays on mpv. */
    // The Stalker `cmd` whose (freshly-resolved) URL is currently loaded in previewEngine. Stalker
    // resolves cmd→URL per play, so the engine's currentUrl is the resolved link, not the cmd — this
    // tracks the cmd identity so re-focus/promote can tell "same channel" without re-resolving.
    private var stalkerPreviewCmd: String? = null
    private var stalkerPreviewJob: Job? = null

    // C-3 (§5.4.1): the live-reconnect URL provider installed on both engines while a Stalker live
    // channel plays, so a mid-session stream-death re-resolves a fresh create_link instead of looping
    // on the expired URL. Null for M3U/Xtream (their URLs are stable). The cmd it resolves is tracked
    // here so the provider always re-resolves the CURRENT channel even after a zap.
    @Volatile private var stalkerReconnectCmd: String? = null
    private val stalkerReconnectProvider = tv.own.owntv.core.stalker.ReconnectUrlProvider {
        val cmd = stalkerReconnectCmd ?: return@ReconnectUrlProvider null
        val channel = _previewChannel.value ?: return@ReconnectUrlProvider null
        val source = sourceById[channel.sourceId] ?: sourceDao.getById(channel.sourceId)
        if (!streamUrlResolver.needsResolve(source)) return@ReconnectUrlProvider null
        runCatching { streamUrlResolver.resolve(source!!, cmd) }
            .onFailure { Log.w(ENGINE_TAG, "stalker reconnect re-resolve failed '${channel.name}'", it) }
            .getOrNull()
    }

    /** Install the reconnect provider on both engines for a Stalker [cmd], or clear it (null). */
    private fun setStalkerReconnect(cmd: String?) {
        stalkerReconnectCmd = cmd
        val provider = if (cmd != null) stalkerReconnectProvider else null
        previewEngine.reconnectUrlProvider = provider
        player.reconnectUrlProvider = provider
    }

    fun playPreview(channel: ChannelEntity) {
        // Don't touch the engine while it's promoted to full-screen. Clicking OK before the in-pane preview's
        // focus-delay fires would otherwise let this late preview call re-mute the now-full-screen stream
        // (preview audio is off) — so full-screen would play with no sound. ensurePlaying() sets liveOnExo
        // the instant OK is pressed, before this can run.
        if (_liveOnExo.value) return
        val source = sourceById[channel.sourceId]
        if (streamUrlResolver.needsResolve(source)) { playPreviewStalker(channel, source!!); return }
        // Already previewing this channel (e.g. re-focus)? Just re-apply the preview mute, no reload.
        if (previewEngine.currentUrl == channel.streamUrl &&
            previewEngine.state.value != tv.own.owntv.player.LivePreviewEngine.State.ERROR
        ) {
            previewEngine.setMuted(!livePreviewAudio.value)
            return
        }
        stalkerPreviewCmd = null
        setStalkerReconnect(null) // non-Stalker: URLs are stable, replay on reconnect
        previewEngine.play(
            channel.streamUrl, muted = !livePreviewAudio.value,
            meta = tv.own.owntv.player.MediaMeta(title = channel.name, subtitle = channelNumberLabel(channel), logoUrl = channel.displayLogoUrl),
            userAgent = sourceUaMap[channel.sourceId],
        )
    }

    /** Stalker preview: same "already-previewing → just re-mute" shortcut keyed by the cmd, else
     *  resolve the cmd to a real URL (create_link) and load it. Async because resolution is a network call. */
    private fun playPreviewStalker(channel: ChannelEntity, source: tv.own.owntv.core.database.entity.SourceEntity) {
        if (stalkerPreviewCmd == channel.streamUrl &&
            previewEngine.state.value != tv.own.owntv.player.LivePreviewEngine.State.ERROR
        ) {
            previewEngine.setMuted(!livePreviewAudio.value)
            return
        }
        stalkerPreviewJob?.cancel()
        stalkerPreviewJob = viewModelScope.launch {
            val url = runCatching { streamUrlResolver.resolve(source, channel.streamUrl) }
                .onFailure { Log.w(ENGINE_TAG, "stalker preview resolve failed '${channel.name}'", it) }
                .getOrNull() ?: return@launch
            if (_liveOnExo.value) return@launch // promoted to fullscreen while resolving
            stalkerPreviewCmd = channel.streamUrl
            setStalkerReconnect(channel.streamUrl) // C-3: re-resolve on reconnect if the URL expires
            previewEngine.play(
                url, muted = !livePreviewAudio.value,
                meta = tv.own.owntv.player.MediaMeta(title = channel.name, subtitle = channelNumberLabel(channel), logoUrl = channel.displayLogoUrl),
                userAgent = source.userAgent,
            )
        }
    }

    // The ordered channel list the player zaps within (CH+/CH-, D-pad up/down) and shows in the
    // left-hand overlay. It is ALWAYS the playing channel's own provider category — never the rail
    // the user happened to launch from. Opening a channel from History or All Channels used to leave
    // the player stuck browsing that rail; now tuning re-arms the list from `channel.categoryId`.
    private var zapList: List<ChannelEntity> = emptyList()
    private val _canZap = MutableStateFlow(false)
    val canZap: StateFlow<Boolean> = _canZap.asStateFlow()
    // The opened channel list, exposed so the in-player channel-list overlay can show & jump within it.
    private val _zapChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val zapChannels: StateFlow<List<ChannelEntity>> = _zapChannels.asStateFlow()
    /** Heading for the left overlay — the playing channel's category name (or "All Channels"). */
    private val _zapListTitle = MutableStateFlow("Channels")
    val zapListTitle: StateFlow<String> = _zapListTitle.asStateFlow()
    /** The category [zapList] was built from; null means the uncategorized → All fallback. */
    private var zapCategoryId: Long? = null
    private var zapArmed = false

    // --- Category browser (second Left press shows all categories) ---
    private val _showCategoryBrowser = MutableStateFlow(false)
    val showCategoryBrowser: StateFlow<Boolean> = _showCategoryBrowser.asStateFlow()

    /** Categories for the category browser (with customizations applied). */
    val browserCategories: StateFlow<List<Pair<CategoryEntity, String>>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyList())
            else combine(categoryDao.observe(c.sourceIds, MediaType.LIVE), custom) { cats, cust ->
                cats.applyCustomizations(cust)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun showCategories() { _showCategoryBrowser.value = true }
    fun hideCategoryBrowser() { _showCategoryBrowser.value = false }

    /** Load channels for an arbitrary category into the zap list. */
    fun loadChannelsForCategory(categoryId: Long) {
        zapListJob?.cancel()
        zapListJob = viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val ctxKey = folderContextKeys.value[categoryId] ?: ""
            val list = channelDao.snapshotByCategoryManual(categoryId, pid, ctxKey, ZAP_LIST_LIMIT)
            zapCategoryId = categoryId
            zapArmed = true
            _zapChannels.value = list
            _zapListTitle.value = categoryDao.getById(categoryId)?.name ?: "Channels"
            _showCategoryBrowser.value = false
        }
    }
    private var zapListJob: Job? = null

    /** Rebuild [zapList] from [channel]'s own provider category. No-op while zapping inside the same
     *  category (the list is already right), so CH+/CH- stays a pure in-memory step. */
    private fun armZapList(channel: ChannelEntity) {
        if (zapArmed && channel.categoryId == zapCategoryId && zapList.any { it.id == channel.id }) return
        zapListJob?.cancel()
        zapListJob = viewModelScope.launch {
            val catId = channel.categoryId
            val pid = currentProfileId()
            val raw = withContext(Dispatchers.IO) {
                if (catId != null && pid != null) {
                    channelDao.snapshotByCategoryManual(catId, pid, folderContextKeys.value[catId] ?: "", ZAP_LIST_LIMIT)
                } else {
                    // No category on this row (hand-made M3U entries) — fall back to All Channels.
                    channelDao.snapshotAll(ctx.value.sourceIds.ifEmpty { listOf(-1L) }, ZAP_LIST_LIMIT)
                }
            }
            // Same hide/rename treatment the browsing lists get, so the overlay matches what the user sees.
            val cust = custom.value
            val hiddenCats = hiddenCategoryIds.value
            val list = raw
                .filter { CustomizeKeys.channel(it) !in cust.hiddenItems && (it.categoryId == null || it.categoryId !in hiddenCats) }
                .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
            zapCategoryId = catId
            zapArmed = true
            zapList = list
            _zapChannels.value = list
            _canZap.value = list.size > 1
            _zapListTitle.value = (catId?.let { categoryDao.getById(it)?.name })?.takeIf { it.isNotBlank() } ?: "All Channels"
        }
    }

    /** The profile's recently-watched channels, for the right-hand in-player history overlay. */
    suspend fun historyChannels(limit: Int = HISTORY_LIST_LIMIT): List<ChannelEntity> {
        val pid = currentProfileId() ?: return emptyList()
        val cust = custom.value
        val hiddenCats = hiddenCategoryIds.value
        return channelDao.recentlyWatched(pid, limit).first()
            .filter { CustomizeKeys.channel(it) !in cust.hiddenItems && (it.categoryId == null || it.categoryId !in hiddenCats) }
            .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
    }

    /** Bumped every time we start a new rebuild OR cancel one. The background rebuild coroutine
     *  captures this at start; before publishing its result it verifies the captured generation
     *  still equals [zapRebuildGeneration]. Older builds therefore cannot overwrite the live
     *  fields after a newer navigation, a newer numeric tune, or a CH+/- fallback. */
    private var zapRebuildGeneration: Long = 0L
    private var zapRebuildJob: Job? = null

    /** Fallback CH+/CH- anchor for the window during which the tuned channel is NOT yet in
     *  [zapList]. Set right before [playChannel] for a numeric tune so the user can still navigate
     *  via CH+/- while the bounded window rebuilds in the background. Cleared when:
     *   - the rebuild publishes successfully (the new list contains the tuned channel),
     *   - normal navigation replaces the playing channel (via [cancelPendingZapRebuild]),
     *   - CH+/- resolves through the saved anchor (fallback consumption).
     *  The list reference + index pair is stored together so a concurrent `zapList` replacement
     *  can't silently redirect CH+/- to an unrelated channel. */
    private data class PendingDirectTuneZapContext(
        val targetChannelId: Long,
        val previousList: List<ChannelEntity>,
        val previousIndex: Int,
    )
    private var pendingDirectTuneZapContext: PendingDirectTuneZapContext? = null

    /** True when full-screen is running on the **ExoPlayer** engine (a promoted preview) rather than mpv.
     *  The shell renders the ExoPlayer surface instead of mpv's when this is set. */
    private val _liveOnExo = MutableStateFlow(false)
    val liveOnExo: StateFlow<Boolean> = _liveOnExo.asStateFlow()

    // Apply the "Preview audio" toggle to a preview that is ALREADY playing — it used to take effect only
    // on the next tune, so turning it off left the current channel audible until focus moved. Never while
    // promoted to full-screen (_liveOnExo): that stream is meant to be heard.
    // Declared HERE, below _liveOnExo/previewEngine: viewModelScope is Main.immediate, so this collector
    // starts inline during construction and must not touch a property initialized further down the class.
    init {
        viewModelScope.launch {
            livePreviewAudio.collect { on ->
                if (!_liveOnExo.value && previewEngine.currentUrl != null) previewEngine.setMuted(!on)
            }
        }
    }

    /** Called when anything OTHER than a promoted live channel takes over full-screen (a movie/episode,
     *  catch-up, an EPG/search channel — all play on mpv). Clears the ExoPlayer flag so the shell renders
     *  mpv's surface (not the leftover live channel) and stops the preview so it doesn't hold a connection. */
    /** Back out of full-screen to the Live screen: we're no longer full-screen on ExoPlayer, so the preview
     *  pane may re-take the engine (and re-apply the preview mute) on the next focus. Keeps the stream
     *  playing (no stop) — just clears the flag so [playPreview] works again. */
    fun onFullscreenExited() {
        _liveOnExo.value = false
        // With the in-pane preview enabled, the preview pane re-takes the ExoPlayer engine on the next
        // focus and re-applies the preview mute — so we can leave it running here. But when live preview
        // is OFF, nothing ever re-takes it, and the engine would keep decoding the (unmuted) channel's
        // audio in the background after exit. Stop it so leaving fullscreen actually silences the stream.
        if (!livePreviewEnabled.value) {
            exoOutcomeJob?.cancel()
            stalkerPreviewJob?.cancel()
            stalkerPreviewCmd = null
            setStalkerReconnect(null)
            previewEngine.stop()
        }
    }

    fun clearLiveOnExo() {
        exoOutcomeJob?.cancel()
        stalkerPreviewJob?.cancel()
        stalkerPreviewCmd = null
        setStalkerReconnect(null) // catch-up/VOD-style mpv takes over — no live-reconnect re-resolve
        _liveOnExo.value = false
        previewEngine.stop()
    }

    /** The most-recently-watched live channel for the active profile (for "resume last channel"). Waits
     *  for the profile to be known, then reads the newest watch-history row. Null if there is none. */
    suspend fun lastWatchedLiveChannel(): ChannelEntity? {
        val pid = ctx.first { it.profileId >= 0 }.profileId
        return channelDao.recentlyWatched(pid, 1).first().firstOrNull()
    }

    /** Open a channel fullscreen. [list] is the rail the user launched from and is deliberately IGNORED:
     *  the player always zaps/browses within the channel's own provider category ([armZapList]). */
    @Suppress("UNUSED_PARAMETER")
    fun watchFullscreen(channel: ChannelEntity, list: List<ChannelEntity>) {
        ensurePlaying(channel)
    }

    /** Tune a channel picked in the Guide. Same as [ensurePlaying] except history is written straight
     *  away: the Guide is a deliberate one-shot pick (you chose a channel, or "Watch channel" in a
     *  programme dialog), not the zap-through-a-category flow the history debounce exists to filter,
     *  and a live channel opened from the Guide was not reliably landing in History. */
    fun watchFromGuide(channel: ChannelEntity) {
        ensurePlaying(channel)
        recordLiveHistory(channel, immediate = true)
    }

    /** Zap to the neighbouring channel ([delta] = +1 down / -1 up). Two-axis resolution:
     *
     *  1. If the currently playing channel is in the live [zapList], apply the existing wrapped
     *     delta on that list (normal navigation). [ensurePlaying] cancels any pending rebuild.
     *  2. Otherwise (the common case right after an out-of-window numeric tune, before its
     *     bounded zap list has finished rebuilding), fall back to [pendingDirectTuneZapContext]'s
     *     saved list + index so CH+/- is responsive while the rebuild is still running. The saved
     *     list is paired with its index so a concurrent `zapList` replacement can't redirect the
     *     delta to an unrelated channel. [ensurePlaying] handles cancellation of the rebuild.
     */
    fun zap(delta: Int) {
        val list = zapList
        val currentId = _previewChannel.value?.id
        val i = if (currentId != null) list.indexOfFirst { it.id == currentId } else -1
        if (i >= 0) {
            // Path 1: normal navigation on the live list.
            val nextIdx = tv.own.owntv.player.wrappedZapIndex(i, delta, list.size) ?: return
            ensurePlaying(list[nextIdx])
            return
        }
        // Path 2: fallback via the saved pending context. The context's targetChannelId is the
        // channel we tuned to; if that no longer matches the playing channel (e.g. a newer numeric
        // tune or CH+/- already moved us), the anchor is stale — drop it and no-op.
        val ctx = pendingDirectTuneZapContext ?: return
        if (ctx.targetChannelId != currentId) {
            pendingDirectTuneZapContext = null
            return
        }
        val prev = ctx.previousList
        val nextIdx = tv.own.owntv.player.wrappedZapIndex(ctx.previousIndex, delta, prev.size) ?: run {
            pendingDirectTuneZapContext = null
            return
        }
        val next = prev[nextIdx]
        ensurePlaying(next)
    }

    /**
     * Direct-tune: resolve a provider channel number to a channel and tune it.
     *
     * Two-stage lookup: the playing channel's source first, then other active Live sources only when
     * the current source has zero visible matches. Duplicate numbers are resolved via zap-context
     * tiebreaker. Hidden channels/categories are excluded.
     *
     * After resolution, playback starts IMMEDIATELY (no awaiting the bounded zap-list rebuild).
     * The rebuild runs in [viewModelScope] on [Dispatchers.IO]; publication is guarded by both
     * the captured generation and the currently playing channel, so a stale or cancelled rebuild
     * can never overwrite [zapList], [_zapChannels], or [_canZap]. Until the rebuild publishes,
     * CH+/- falls back to the saved previous-list index recorded at tune time.
     */
    suspend fun tuneByNumber(number: Int): tv.own.owntv.player.DirectTuneResult {
        try {
            val currentChannel = _previewChannel.value ?: return tv.own.owntv.player.DirectTuneResult.NotFound(number)
            val snapshotSourceIds = ctx.value.sourceIds
            // Snapshot the zap list at lookup START so the resolver's zap-context tiebreaker is
            // stable for the duration of the IO query. After the lookup completes and context
            // validity is verified we re-read zapList: a previous background rebuild may have
            // published during the IO window, and the new tune must use the freshest view of the
            // list for anchor selection and the "already present, skip rebuild" check. Using the
            // stale snapshot there would lose the fallback anchor or incorrectly skip rebuilding.
            val snapshotZapList = zapList

            // DB queries on IO; playback on Main (ExoPlayer/mpv require main thread).
            val resolved = withContext(Dispatchers.IO) {
                // Resolve hidden categories for sources not in the active set (source may have been removed).
                val activeHiddenCats = hiddenCategoryIds.value.toMutableSet()
                if (currentChannel.sourceId !in snapshotSourceIds) {
                    val cats = categoryDao.observe(listOf(currentChannel.sourceId), MediaType.LIVE).first()
                    val cust = custom.value
                    if (cust.hiddenCategories.isNotEmpty()) {
                        cats.filter { CustomizeKeys.category(it) in cust.hiddenCategories }.forEach { activeHiddenCats += it.id }
                    }
                }
                val currentCustom = custom.value

                // Stage 1: query the currently playing source.
                val currentSourceCandidates = channelDao.findByNumber(
                    listOf(currentChannel.sourceId), number,
                ).filter { isChannelVisible(it, currentCustom, activeHiddenCats) }

                if (currentSourceCandidates.isNotEmpty()) {
                    val resolvedId = tv.own.owntv.player.resolveDirectTuneCandidate(
                        currentSourceCandidates.map { it.id },
                        snapshotZapList.map { it.id }.toSet(),
                    )
                    val r = resolvedId?.let { id -> currentSourceCandidates.first { it.id == id } }
                        ?: return@withContext ChannelNumberLookupResult.Ambiguous(currentSourceCandidates.size)
                    val customName = currentCustom.itemNames[CustomizeKeys.channel(r)]
                    return@withContext ChannelNumberLookupResult.Found(customName?.let { r.copy(name = it) } ?: r)
                }

                // Stage 2: fallback to other active Live sources.
                val fallbackSourceIds = snapshotSourceIds.filter { it != currentChannel.sourceId }
                if (fallbackSourceIds.isEmpty()) return@withContext ChannelNumberLookupResult.NotFound

                val fallbackCandidates = channelDao.findByNumber(fallbackSourceIds, number)
                    .filter { isChannelVisible(it, currentCustom, activeHiddenCats) }
                if (fallbackCandidates.isEmpty()) return@withContext ChannelNumberLookupResult.NotFound

                val r = tv.own.owntv.player.resolveDirectTuneCandidate(
                    fallbackCandidates.map { it.id },
                    snapshotZapList.map { it.id }.toSet(),
                )?.let { id -> fallbackCandidates.first { it.id == id } }
                    ?: return@withContext ChannelNumberLookupResult.Ambiguous(fallbackCandidates.size)
                val customName = currentCustom.itemNames[CustomizeKeys.channel(r)]
                ChannelNumberLookupResult.Found(customName?.let { r.copy(name = it) } ?: r)
            }

            // Dispatch the lookup outcome.
            val tuned = when (val lookup = resolved) {
                is ChannelNumberLookupResult.Found -> lookup.channel
                is ChannelNumberLookupResult.Ambiguous ->
                    return tv.own.owntv.player.DirectTuneResult.Ambiguous(number, lookup.matchCount)
                ChannelNumberLookupResult.NotFound ->
                    return tv.own.owntv.player.DirectTuneResult.NotFound(number)
            }

            // Verify context hasn't changed during lookup. If the playing channel or active source
            // set moved, the resolved channel is stale — return Cancelled and do nothing.
            if (_previewChannel.value?.id != currentChannel.id ||
                ctx.value.sourceIds != snapshotSourceIds
            ) return tv.own.owntv.player.DirectTuneResult.Cancelled

            // If the tuned channel is already playing, skip playback + rebuild to avoid a stream
            // restart. Still return Found so the HUD shows normal success feedback.
            if (tuned.id == currentChannel.id) {
                return tv.own.owntv.player.DirectTuneResult.Found(
                    tv.own.owntv.player.DirectTuneChannelInfo(
                        tuned.number, tuned.name, tuned.logoUrl, restarted = false,
                    ),
                )
            }

            // Re-read zapList NOW: a previous background rebuild may have published during the IO
            // window above. Compute anchor data before any state mutation.
            val currentZapList = zapList

            val alreadyInLiveList = currentZapList.any { it.id == tuned.id }

            val inherited = pendingDirectTuneZapContext
                ?.takeIf { it.targetChannelId == currentChannel.id }

            val anchorList = inherited?.previousList ?: currentZapList
            val anchorIndex = inherited?.previousIndex
                ?: currentZapList.indexOfFirst { it.id == currentChannel.id }

            val hasValidAnchor =
                anchorList.size >= 2 &&
                    anchorIndex in anchorList.indices

            if (alreadyInLiveList) {
                zapRebuildJob?.cancel()
                zapRebuildJob = null
                zapRebuildGeneration++
                pendingDirectTuneZapContext = null
            }

            // Playback starts IMMEDIATELY — do not await the rebuild.
            playChannel(tuned)

            // Launch the background rebuild only when the target is outside the current list.
            if (!alreadyInLiveList) {
                zapRebuildJob?.cancel()
                zapRebuildGeneration++
                val myGeneration = zapRebuildGeneration

                if (hasValidAnchor) {
                    pendingDirectTuneZapContext = PendingDirectTuneZapContext(
                        targetChannelId = tuned.id,
                        previousList = anchorList,
                        previousIndex = anchorIndex,
                    )
                } else {
                    pendingDirectTuneZapContext = null
                }

                zapRebuildJob = viewModelScope.launch {
                    try {
                        val rebuilt = try {
                            buildZapList(tuned)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "tuneByNumber: zap rebuild failed", e)
                            return@launch
                        }
                        if (myGeneration != zapRebuildGeneration) return@launch
                        if (_previewChannel.value?.id != tuned.id) return@launch
                        replaceZapList(rebuilt)
                        pendingDirectTuneZapContext = null
                    } finally {
                        if (myGeneration == zapRebuildGeneration) {
                            zapRebuildJob = null
                        }
                    }
                }
            }

            return tv.own.owntv.player.DirectTuneResult.Found(
                tv.own.owntv.player.DirectTuneChannelInfo(
                    tuned.number, tuned.name, tuned.logoUrl, restarted = true,
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "tuneByNumber($number) failed", e)
            return tv.own.owntv.player.DirectTuneResult.Failed(number)
        }
    }

    /** Leaf outcome of the IO database lookup inside [tuneByNumber]. */
    private sealed interface ChannelNumberLookupResult {
        data class Found(val channel: ChannelEntity) : ChannelNumberLookupResult
        data class Ambiguous(val matchCount: Int) : ChannelNumberLookupResult
        data object NotFound : ChannelNumberLookupResult
    }

    private fun isChannelVisible(ch: ChannelEntity, cust: SectionCustomizations, hiddenCats: Set<Long>): Boolean =
        CustomizeKeys.channel(ch) !in cust.hiddenItems &&
            (ch.categoryId == null || ch.categoryId !in hiddenCats)

    /** Rebuild the zap list so CH+/- and the channel-list overlay work after jumping outside the
     *  original list window. Loads a bounded provider-order window centred on the tuned channel
     *  (half before, half after), applying hidden-channel/category filtering and custom names.
     *  Pure builder: returns the local list without mutating any shared state, so the caller can
     *  guard publication by generation/target and discard stale or cancelled results. */
    private suspend fun buildZapList(channel: ChannelEntity): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val cust = custom.value
        val hiddenCats = hiddenCategoryIds.value
        val half = ZAP_WINDOW_HALF
        val afterRaw: List<ChannelEntity>
        val beforeRaw: List<ChannelEntity>
        if (channel.categoryId != null) {
            afterRaw = channelDao.channelsAfterCategory(channel.categoryId, channel.sortOrder, channel.id, half)
            beforeRaw = channelDao.channelsBeforeCategory(channel.categoryId, channel.sortOrder, channel.id, half)
        } else {
            afterRaw = channelDao.channelsAfterSource(channel.sourceId, channel.sortOrder, channel.id, half)
            beforeRaw = channelDao.channelsBeforeSource(channel.sourceId, channel.sortOrder, channel.id, half)
        }
        // beforeRaw is in reverse order; combine: before(reversed) + tuned + after
        val raw = beforeRaw.asReversed() + channel + afterRaw
        raw
            .filter { isChannelVisible(it, cust, hiddenCats) }
            .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
    }

    /** Single main-thread publication point for the three shared zap-list fields. Caller must have
     *  already verified generation + target before calling. */
    private fun replaceZapList(list: List<ChannelEntity>) {
        zapList = list
        _zapChannels.value = list
        _canZap.value = list.size > 1
    }

    /** Cancel any in-flight background zap-list rebuild and discard its pending fallback. Normal
     *  navigation (CH+/-, channel-list, Guide, ensurePlayingById) calls this before playing the
     *  new channel so an obsolete rebuild never publishes after the user has moved elsewhere.
     *  Direct-tune deliberately skips this — it manages the rebuild itself so playback is
     *  immediate and the new list still finishes in the background. */
    private fun cancelPendingZapRebuild() {
        zapRebuildJob?.cancel()
        zapRebuildJob = null
        zapRebuildGeneration++
        pendingDirectTuneZapContext = null
    }

    /** "External player" is on for Live TV — the screen must NOT open the fullscreen in-app player
     *  (mounting it spins up an engine even though the channel went to the external app). */
    val externalPlayerOn: StateFlow<Boolean> = settings.externalPlayerLive
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Hand [channel] to an external app (VLC, MX Player). Used by the Live TV long-press menu, and by
     *  [playChannel] when Live TV's external-player default is on. Stalker channels store a portal
     *  cmd rather than a URL, so it's resolved first — an external app can't mint one. */
    fun playExternal(channel: ChannelEntity) {
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) { sourceDao.getById(channel.sourceId) }
            val url = if (streamUrlResolver.needsResolve(source)) {
                withContext(Dispatchers.IO) {
                    runCatching { streamUrlResolver.resolve(source!!, channel.streamUrl) }
                        .onFailure { Log.w(TAG, "stalker resolve failed channelId=${channel.id}", it) }
                        .getOrNull()
                } ?: return@launch
            } else {
                channel.streamUrl
            }
            externalPlayerLauncher.launch(url, channel.name)
            recordLiveHistory(channel, immediate = true)
        }
    }

    /** Go full-screen on [channel]. Cancels any pending direct-tune zap rebuild first, so normal
     *  navigation always wins over an in-flight rebuild. ExoPlayer is the **primary** live engine
     *  (instant for HLS, and it plays the channels mpv struggles to open): promote the running
     *  preview if it's already this channel, else (re)start ExoPlayer on it. We fall back to the
     *  full **mpv** player ONLY if ExoPlayer **errors** (a stream it can't open) — never just
     *  because it's still loading (clicking OK before the preview is ready used to drop to mpv and
     *  stick on a black screen for HLS). */
    fun ensurePlaying(channel: ChannelEntity) {
        cancelPendingZapRebuild()
        playChannel(channel)
    }

    /** Internal playback: the canonical ExoPlayer / mpv / Stalker / history side-effects for a
     *  channel. Direct-tune's background rebuild path calls this without [cancelPendingZapRebuild]
     *  so the in-flight rebuild it owns isn't killed by its own play. */
    private fun playChannel(channel: ChannelEntity) {
        // Live TV set to play externally: hand the channel over instead of tuning an in-app engine.
        // History is still recorded, so the channel shows up in History/Recently watched either way.
        if (externalPlayerOn.value) { playExternal(channel); return }
        _previewChannel.value = channel
        armZapList(channel) // zap/browse within THIS channel's category, whatever rail we came from
        timeshiftJob?.cancel(); tickJob?.cancel(); _timeshiftOffsetSec.value = null // normal live = not timeshifted
        _catchupActive.value = false // tuning live ends any archive playback the HUD was showing
        // Self-learning routing: a channel the user pinned to mpv skips ExoPlayer entirely (no artifacts/silent
        // first), straight to the engine that plays it. Everyone else gets the fast ExoPlayer-first path.
        val pinned = isPinnedToMpv(channel)
        // A new tune clears any "user chose ExoPlayer" override — that choice is per channel.
        if (exoChosenByUser != channel.streamUrl) exoChosenByUser = null
        android.util.Log.i(ENGINE_TAG, "tune '${channel.name}' -> ${if (pinned) "mpv (pinned)" else "exoplayer"}")
        if (pinned) startOnMpv(channel) else startOnExo(channel)
        recordLiveHistory(channel)
    }

    /** P6 — the stable "compatibility mode" pin key for [channel], or null when the row has no
     *  provider id (some hand-made M3U entries), where the stream URL stays the key. */
    private fun mpvPinKey(channel: ChannelEntity): String? =
        tv.own.owntv.core.player.enginePinKey(channel.sourceId, "LIVE", channel.remoteId)

    /** Whether [channel] is pinned to mpv, honouring pins written by older builds under the stream
     *  URL. A legacy hit is rewritten under the stable key so it survives the next Stalker resolve. */
    private fun isPinnedToMpv(channel: ChannelEntity): Boolean {
        val pins = forceMpvUrls.value
        val key = mpvPinKey(channel)
        if (key != null && key in pins) return true
        if (channel.streamUrl !in pins) return false
        if (key != null) viewModelScope.launch { forceMpvStore.migrateKey(channel.streamUrl, key) }
        return true
    }

    private fun startOnExo(channel: ChannelEntity) {
        _liveOnExo.value = true
        player.stop() // free mpv (decoder/connection) if a previous full-screen used it
        val source = sourceById[channel.sourceId]
        if (streamUrlResolver.needsResolve(source)) { startOnExoStalker(channel, source!!); return }
        if (previewEngine.currentUrl == channel.streamUrl) {
            previewEngine.setMuted(false) // promote — instant if already PLAYING, otherwise keeps loading
        } else {
            // In-player zap to a DIFFERENT channel (CH+/-, D-pad, channel-list overlay): if we're leaving a
            // UHD channel, fully release its 4K decoder before the reuse/rebuild (no-op for SD/HD). Matches
            // the Back/exit path — so the Hisense 4K-decoder leak is avoided however you leave the channel.
            previewEngine.releaseDecoderForUhd()
            stalkerPreviewCmd = null
            setStalkerReconnect(null) // non-Stalker: URLs are stable, replay on reconnect
            previewEngine.play(
                channel.streamUrl, muted = false,
                meta = tv.own.owntv.player.MediaMeta(title = channel.name, subtitle = channelNumberLabel(channel), logoUrl = channel.displayLogoUrl),
                userAgent = sourceUaMap[channel.sourceId],
            )
        }
        watchExoOutcome(channel)
    }

    /** Fullscreen a Stalker channel on ExoPlayer: promote the preview if it already holds this cmd,
     *  else resolve the cmd (create_link) and load the fresh URL. */
    private fun startOnExoStalker(channel: ChannelEntity, source: tv.own.owntv.core.database.entity.SourceEntity) {
        if (stalkerPreviewCmd == channel.streamUrl) {
            previewEngine.setMuted(false) // promote the already-loaded preview
            setStalkerReconnect(channel.streamUrl) // C-3: re-resolve on reconnect if the URL expires
            watchExoOutcome(channel)
            return
        }
        stalkerPreviewJob?.cancel()
        stalkerPreviewJob = viewModelScope.launch {
            previewEngine.releaseDecoderForUhd()
            val url = runCatching { streamUrlResolver.resolve(source, channel.streamUrl) }
                .onFailure { Log.w(ENGINE_TAG, "stalker fullscreen resolve failed '${channel.name}'", it) }
                .getOrNull() ?: return@launch // portal/auth failure — nothing playable to hand the engine
            if (_previewChannel.value?.streamUrl != channel.streamUrl) return@launch // zapped away while resolving
            stalkerPreviewCmd = channel.streamUrl
            setStalkerReconnect(channel.streamUrl) // C-3: re-resolve on reconnect if the URL expires
            previewEngine.play(
                url, muted = false,
                meta = tv.own.owntv.player.MediaMeta(title = channel.name, subtitle = channelNumberLabel(channel), logoUrl = channel.displayLogoUrl),
                userAgent = source.userAgent,
            )
            watchExoOutcome(channel)
        }
    }

    /** Start [channel] on the full mpv engine (pinned "compatibility" channel, or an ExoPlayer fallback). */
    private fun startOnMpv(channel: ChannelEntity) { viewModelScope.launch { fallbackToMpv(channel) } }

    /** HUD "compatibility mode" toggle: pin/unpin the current channel to mpv and swap engines live. */
    fun toggleForceMpv() {
        val channel = _previewChannel.value ?: return
        // A catch-up archive is loaded, not the live stream: re-tuning here would replace the programme
        // the user is watching with the channel's CURRENT one. The HUD already hides this toggle during
        // catch-up (it offers the VOD engine toggle instead) — this is the belt-and-braces guard.
        if (_catchupActive.value) return
        // Base the swap on the ACTUAL running engine, not the pin: after an auto-fallback to mpv the channel
        // runs on mpv while still unpinned, and the old pin-based logic then did nothing on click. Keying off
        // _liveOnExo makes every click flip the live engine, with the pin following the choice.
        val goToMpv = _liveOnExo.value // on Exo now → switch to mpv; on mpv now → switch to Exo
        android.util.Log.i(ENGINE_TAG, "engine toggle '${channel.name}' -> ${if (goToMpv) "mpv" else "exoplayer"} (currentlyOnExo=${_liveOnExo.value})")
        viewModelScope.launch {
            // Pin to mpv when choosing mpv; unpin when choosing Exo. Write the stable key, and clear
            // any legacy URL-keyed entry too so an un-pin can't leave the old one behind (P6).
            val key = mpvPinKey(channel)
            // Choosing ExoPlayer here is an explicit override: suppress the auto-fallback for this
            // channel until the next tune, so it can't be undone a second later by watchExoOutcome.
            exoChosenByUser = if (goToMpv) null else channel.streamUrl
            forceMpvStore.set(key ?: channel.streamUrl, goToMpv)
            if (key != null) forceMpvStore.set(channel.streamUrl, false)
            if (goToMpv) {
                fallbackToMpv(channel) // ExoPlayer → mpv now
            } else {                    // mpv → ExoPlayer now
                player.stop()
                delay(500) // let mpv's decoder/surface release before ExoPlayer takes over
                if (_previewChannel.value?.streamUrl == channel.streamUrl) startOnExo(channel)
            }
        }
    }

    fun ensurePlayingById(channelId: Long) {
        viewModelScope.launch {
            val channel = channelDao.getById(channelId) ?: return@launch
            ensurePlaying(channel)
        }
    }

    /** [zapChannels] is the caller's rail and is deliberately IGNORED — the zap/overlay list always comes
     *  from the channel's own category ([armZapList]), which also fixes the stale-list case in #55. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun ensurePlayingByIdAsync(channelId: Long, zapChannels: List<ChannelEntity> = emptyList()): Boolean {
        val channel = channelDao.getById(channelId) ?: return false
        ensurePlaying(channel)
        return true
    }

    /** One-shot: hand [channel] to mpv if ExoPlayer can't play it fully — either it **errors** opening, or it
     *  plays but ExoPlayer can decode **none of its audio** (e.g. an AC3/E-AC3/DTS movie file added via M3U,
     *  on a device without that decoder — it'd play silently). mpv (FFmpeg) decodes everything. */
    private var exoOutcomeJob: Job? = null

    /** Stream URL of the channel the user explicitly switched BACK to ExoPlayer with the HUD toggle.
     *  While it is set, the auto-fallback below stays out of the way for that channel — otherwise the
     *  same condition that bounced it to mpv fires again immediately and the manual choice looks
     *  ignored ("I switch to Exo and it jumps straight back to MPV"). Cleared on the next tune. */
    private var exoChosenByUser: String? = null

    private fun watchExoOutcome(channel: ChannelEntity) {
        exoOutcomeJob?.cancel()
        if (exoChosenByUser == channel.streamUrl) {
            android.util.Log.i(ENGINE_TAG, "auto-fallback disabled for '${channel.name}' — user chose ExoPlayer")
            return
        }
        exoOutcomeJob = viewModelScope.launch {
            // Runs alongside the terminal-state wait below: audio/position can be progressing fine (so
            // ExoPlayer never reaches ERROR) while a video track never renders a single frame — the "audio
            // plays, no picture" case. One-shot per tune; mpv's own outcome (success or its own error state)
            // takes it from there, same as the ERROR branch below.
            launch {
                previewEngine.noVideoDetected.first { it }
                if (isStill(channel)) fallbackToMpv(channel)
            }
            val terminal = previewEngine.state.first {
                it == tv.own.owntv.player.LivePreviewEngine.State.PLAYING ||
                    it == tv.own.owntv.player.LivePreviewEngine.State.ERROR
            }
            if (!isStill(channel)) return@launch
            if (terminal == tv.own.owntv.player.LivePreviewEngine.State.ERROR) { fallbackToMpv(channel); return@launch }
            // PLAYING: give the track list a moment to settle, then route silent (undecodable-audio) streams to mpv.
            delay(300)
            if (isStill(channel) && previewEngine.audioUnsupported.value) fallbackToMpv(channel)
        }
    }

    private fun isStill(channel: ChannelEntity) =
        _liveOnExo.value && _previewChannel.value?.streamUrl == channel.streamUrl

    private suspend fun fallbackToMpv(channel: ChannelEntity) {
        android.util.Log.i(ENGINE_TAG, "starting mpv for '${channel.name}'")
        _liveOnExo.value = false            // shell flips to mpv's surface
        stalkerPreviewCmd = null
        previewEngine.stop()
        delay(500)                          // let ExoPlayer's decoder release before mpv inits
        if (_previewChannel.value?.streamUrl == channel.streamUrl) {
            val source = sourceById[channel.sourceId] ?: sourceDao.getById(channel.sourceId)
            // Stalker stores the portal cmd — resolve it to a real URL (create_link) before mpv plays.
            val isStalker = streamUrlResolver.needsResolve(source)
            val url = if (isStalker) {
                runCatching { streamUrlResolver.resolve(source!!, channel.streamUrl) }
                    .onFailure { Log.w(ENGINE_TAG, "stalker mpv resolve failed '${channel.name}'", it) }
                    .getOrNull() ?: return
            } else {
                channel.streamUrl
            }
            if (_previewChannel.value?.streamUrl != channel.streamUrl) return // zapped away while resolving
            // C-3: mpv is now the active engine — install/clear the reconnect provider to match.
            setStalkerReconnect(if (isStalker) channel.streamUrl else null)
            player.play(url, title = channel.name, subtitle = channelNumberLabel(channel), logoUrl = channel.displayLogoUrl, isLive = true, muted = false, userAgent = source?.userAgent)
        }
    }

    private var historyJob: Job? = null

    /**
     * Record [channel] in the profile's watch history.
     *
     * Normally deferred by [HISTORY_DEBOUNCE_MS] and cancelled by the next tune, so zapping through a
     * category doesn't fill History with channels the user only passed over. [immediate] skips that
     * wait for entry points where the tune is unambiguously deliberate and no zapping follows — the
     * Guide's "Watch channel", and handing a channel to an external player (where the user leaves the
     * app immediately and the delayed write would have nothing to protect against anyway).
     */
    private fun recordLiveHistory(channel: ChannelEntity, immediate: Boolean = false) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            if (!immediate) delay(HISTORY_DEBOUNCE_MS)
            val pid = currentProfileId() ?: return@launch
            Log.d(TAG, "ensurePlaying history profile=$pid channelId=${channel.id}")
            runCatching {
                historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }.onFailure { t ->
                Log.w(TAG, "ensurePlaying history record failed channelId=${channel.id} profile=$pid", t)
            }
            runCatching { launcherIntegrationRepository.refreshRecentLive(pid) }
        }
    }

    // ---- Catch-up from Live TV: pick a recent programme to replay from the archive (#proposal) ----

    /** Recent (already-aired) programmes for a catch-up channel, newest first — drives the Live TV
     *  catch-up picker. Bounded to the EPG we retain (≈ 2 days) and the channel's archive window. */
    suspend fun catchupProgrammes(ch: ChannelEntity): List<tv.own.owntv.core.database.entity.EpgProgrammeEntity> = withContext(Dispatchers.IO) {
        if (!ch.catchup) return@withContext emptyList()
        val epgKey = (custom.value.epgMatches[CustomizeKeys.channel(ch)] ?: ch.epgChannelId)
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
        val now = System.currentTimeMillis()
        val windowMs = (ch.catchupDays.coerceAtLeast(1) * 24L * 60 * 60 * 1000).coerceAtMost(CATCHUP_LOOKBACK_CAP_MS)
        val ids = ctx.value.sourceIds + epgSourceStore.getAll().map { it.id }
        epgDao.programmesForChannel(ids, epgKey, now - windowMs, now + 60 * 60 * 1000)
            .filter { it.startMs <= now }          // already started → catch-up applies
            .sortedByDescending { it.startMs }      // most recent first
            .take(80)
    }

    /** Full description for a programme picked in the catch-up dialog. The list query drops it to stay
     *  under the CursorWindow limit, so the detail popup fetches it on demand (same as the Guide). */
    suspend fun programmeDescription(programmeId: Long): String? =
        withContext(Dispatchers.IO) { runCatching { epgDao.programmeDescription(programmeId) }.getOrNull() }

    /** True while a catch-up **archive programme** is what's on screen, rather than the live stream.
     *  The HUD keys off this: an archive is VOD-style playback, so it must offer the mpv/ExoPlayer VOD
     *  engine toggle (which reloads the SAME archive URL at the same position) instead of Live TV's
     *  compatibility toggle — the latter re-tunes the live stream and drops you onto the current
     *  programme, which is exactly the bug this flag exists to prevent. Live rewind/timeshift is NOT
     *  catch-up: that one is still the live channel and keeps the live controls. */
    private val _catchupActive = MutableStateFlow(false)
    val catchupActive: StateFlow<Boolean> = _catchupActive.asStateFlow()

    /** Which player takes a catch-up archive — read by the UI so "Watch from start" can route itself. */
    val catchupPlayer: StateFlow<SettingsRepository.CatchupPlayer> = settings.catchupPlayer
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.CatchupPlayer.INTERNAL)

    /** The archive URL for [programme] on [ch], or null when it can't be built. */
    private suspend fun catchupUrl(ch: ChannelEntity, programme: tv.own.owntv.core.database.entity.EpgProgrammeEntity): String? =
        withContext(Dispatchers.IO) {
            val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
            // Stalker archive URLs are minted per-play via create_link (Phase E §5.6); the others
            // are pure string templates handled by CatchupUrl.
            if (source.type == SourceType.STALKER) {
                ch.remoteId?.let { rid ->
                    runCatching { streamUrlResolver.resolveCatchup(source, rid, programme.startMs, programme.stopMs) }
                        .onFailure { Log.w(TAG, "Stalker catch-up resolve failed channelId=${ch.id}", it) }
                        .getOrNull()
                }
            } else {
                CatchupUrl.forSource(ch, programme, source, settings.resolveCatchupTimeZone(), xtreamClient)
            }
        }

    /** Hand an archive programme to an external app (VLC, MX Player). No HUD, resume or engine toggle
     *  once it leaves, but external players cope with mid-GOP archive segments some providers serve. */
    fun playCatchupExternal(ch: ChannelEntity, programme: tv.own.owntv.core.database.entity.EpgProgrammeEntity) {
        viewModelScope.launch {
            val url = catchupUrl(ch, programme) ?: return@launch
            Log.i(ENGINE_TAG, "catch-up external '${ch.name}' prog='${programme.title}'")
            externalPlayerLauncher.launch(url, "${ch.name} · ${programme.title}")
            recordLiveHistory(ch, immediate = true)
        }
    }

    /** Replay a past programme from the channel's archive (seekable, like the Guide's "Watch from start"). */
    fun playCatchupProgramme(ch: ChannelEntity, programme: tv.own.owntv.core.database.entity.EpgProgrammeEntity) {
        viewModelScope.launch {
            val url = catchupUrl(ch, programme) ?: return@launch
            val sourceUa = withContext(Dispatchers.IO) { sourceDao.getById(ch.sourceId)?.userAgent }
            // The archive URL shape decides whether ExoPlayer can take it at all (progressive .ts vs
            // .m3u8 vs an extension-less panel endpoint), so log it redacted — it's the first thing
            // needed when the HUD's engine toggle can't get a catch-up programme playing.
            Log.i(ENGINE_TAG, "catch-up '${ch.name}' prog='${programme.title}' -> ${tv.own.owntv.core.network.HttpClient.redactUrl(url)}")
            _previewChannel.value = ch
            _timeshiftOffsetSec.value = null // guide archive isn't the live-rewind timeshift
            _catchupActive.value = true      // HUD: VOD engine toggle, not the live compatibility toggle
            clearLiveOnExo() // catch-up is a VOD-style archive on mpv, not the live ExoPlayer channel
            // isLive=false → seekable archive; preferSoftware → tolerate mid-GOP archive segments.
            player.play(url, title = ch.name, subtitle = programme.title, logoUrl = ch.displayLogoUrl, isLive = false, preferSoftware = true, userAgent = sourceUa)
        }
    }

    // ---- Live rewind / timeshift -------------------------------------------------------------------
    // Watch a catch-up-capable live channel a few minutes behind the live edge (a missed goal, etc.) using
    // the provider's rolling archive (Xtream timeshift / M3U catchup), then jump back to live. The archive
    // is a VOD-style stream on mpv (preferSoftware, mid-GOP tolerant); "Go to live" returns to ExoPlayer.
    private val _timeshiftOffsetSec = MutableStateFlow<Int?>(null) // null = at the live edge; >0 = N s behind
    val timeshiftOffsetSec: StateFlow<Int?> = _timeshiftOffsetSec.asStateFlow()

    /** True when the channel on screen records an archive — the HUD then offers "Rewind" on live. */
    val canRewindLive: StateFlow<Boolean> =
        _previewChannel.map { it?.catchup == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var timeshiftJob: Job? = null
    private var tickJob: Job? = null
    private var timeshiftStartWall = 0L // wall-clock time of the loaded archive's start (for the live counter)
    private val rewindStepSec = 30

    /** 30 s buttons. */
    fun rewindLive() = scrubLive(rewindStepSec)
    fun forwardLive() = scrubLive(-rewindStepSec)

    /** Move [deltaSec] further back (+) or toward live (−) into the archive (also drives the timeline
     *  scrubber). Coalesced so holding a key scrubs freely and loads the archive once at the final point;
     *  reaching the live edge returns to the real-time stream. */
    fun scrubLive(deltaSec: Int) {
        val ch = _previewChannel.value ?: return
        if (!ch.catchup) return
        val maxBack = (ch.catchupDays.takeIf { it > 0 } ?: 7) * 24 * 3600
        val next = ((_timeshiftOffsetSec.value ?: 0) + deltaSec).coerceIn(0, maxBack)
        if (next == 0) { goToLive(); return }
        _timeshiftOffsetSec.value = next
        scheduleTimeshiftLoad(ch, next)
    }

    /** Jump back to the real-time live edge (back on the fast ExoPlayer engine). */
    fun goToLive() {
        timeshiftJob?.cancel(); tickJob?.cancel()
        _timeshiftOffsetSec.value = null
        _previewChannel.value?.let { ensurePlaying(it) }
    }

    private fun scheduleTimeshiftLoad(ch: ChannelEntity, offsetSec: Int) {
        timeshiftJob?.cancel(); tickJob?.cancel()
        timeshiftJob = viewModelScope.launch {
            delay(350) // coalesce rapid rewind/forward presses into one archive load
            val nowMs = System.currentTimeMillis()
            val startMs = nowMs - offsetSec * 1000L
            val tz = withContext(Dispatchers.IO) { settings.resolveCatchupTimeZone() }
            val (url, sourceUa) = withContext(Dispatchers.IO) {
                val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
                buildLiveTimeshiftUrl(ch, source, startMs, offsetSec, tz)?.let { it to source.userAgent }
            } ?: return@launch
            if (_timeshiftOffsetSec.value == null) return@launch // user jumped back to live meanwhile
            // Show the clock time being watched (handy for the user; no credentials in logs).
            val localLabel = formatSystemTime(appContext, startMs)
            _previewChannel.value = ch
            clearLiveOnExo() // archive plays as a VOD-style mpv stream, not the live ExoPlayer channel
            player.play(url, title = ch.name, subtitle = "Rewind · $localLabel", logoUrl = ch.displayLogoUrl, isLive = false, preferSoftware = true, userAgent = sourceUa)
            timeshiftStartWall = startMs
            startOffsetTick()
        }
    }

    /** Tick the "behind live" counter down as the archive plays forward (offset = realNow − watched time =
     *  realNow − (archive start + playback position)). Pausing makes it grow (you fall further behind). */
    private fun startOffsetTick() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                if (_timeshiftOffsetSec.value == null) break
                val behindSec = ((System.currentTimeMillis() - (timeshiftStartWall + player.position.value)) / 1000)
                _timeshiftOffsetSec.value = behindSec.toInt().coerceAtLeast(0)
            }
        }
    }

    private suspend fun buildLiveTimeshiftUrl(ch: ChannelEntity, source: tv.own.owntv.core.database.entity.SourceEntity, startMs: Long, offsetSec: Int, tz: java.util.TimeZone): String? {
        val durationMin = (offsetSec / 60 + 5).coerceAtLeast(1) // rewound window + buffer to play up to live
        return when (source.type) {
            SourceType.XTREAM -> ch.remoteId?.let { xtreamClient.timeshiftUrl(source, it, startMs, durationMin, tz) }
            SourceType.M3U -> CatchupUrl.forM3u(ch.streamUrl, null, ch.catchupSource, startMs, startMs + durationMin * 60_000L)
            // Stalker rewind = the same per-play archive create_link as catch-up (Phase E §5.6).
            SourceType.STALKER -> ch.remoteId?.let { rid ->
                runCatching { streamUrlResolver.resolveCatchup(source, rid, startMs, startMs + durationMin * 60_000L) }
                    .onFailure { Log.w(TAG, "Stalker rewind resolve failed channelId=${ch.id}", it) }
                    .getOrNull()
            }
            else -> null
        }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteIds.value.contains(channel.id)) {
                favoriteDao.remove(pid, MediaType.LIVE, channel.id)
            } else {
                favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }
        }
    }

    fun stopPreview() {
        setStalkerReconnect(null) // tearing down — no reconnect re-resolve should fire
        previewEngine.stop()
        player.stop()
        _previewChannel.value = null
    }

    /** Now/next for [ch], cached ~5 min. Prefers a manual EPG match / stored bulk guide, then falls
     *  back to Xtream's short EPG API. */
    private suspend fun loadEpg(ch: ChannelEntity): EpgNowNext? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        epgCache[ch.id]?.takeIf { now - it.at < 5 * 60_000 }?.let { return@withContext it.data }

        // 1) Bulk guide via the effective EPG id (manual match overrides the channel's own id).
        val epgKey = (custom.value.epgMatches[CustomizeKeys.channel(ch)] ?: ch.epgChannelId)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (epgKey != null) {
            val nowProg = epgDao.nowPlaying(epgKey, now)
            val future = epgDao.upcoming(epgKey, now, 6).first().filter { it.startMs > (nowProg?.startMs ?: 0) }
            val nextProg = future.firstOrNull()
            if (nowProg != null || nextProg != null) {
                val prevProg = epgDao.previousProgramme(epgKey, nowProg?.startMs ?: now)
                // Days of stored guide — accurate for bulk-guide channels (the short-EPG API path
                // leaves this null, since its ~8 entries only span a few hours).
                val days = runCatching { epgDao.coverageDays(epgKey) }.getOrNull()?.takeIf { it > 0 }
                val result = EpgNowNext(nowProg?.toXt(), nextProg?.toXt(), future.drop(1).take(4).map { it.toXt() }, previous = prevProg?.toXt(), coverageDays = days)
                epgCache[ch.id] = CachedEpg(now, result)
                return@withContext result
            }
        }

        // 2) Provider short-EPG API fallback (Xtream get_short_epg / Stalker get_short_epg, Phase E §5.5).
        val streamId = ch.remoteId ?: return@withContext null
        val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
        val entries = when (source.type) {
            SourceType.XTREAM -> runCatching { xtreamClient.getShortEpg(source, streamId, limit = 8) }
                .getOrNull().orEmpty()
            SourceType.STALKER -> runCatching {
                streamUrlResolver.shortEpg(source, streamId)
                    .map { XtEpgEntry(title = it.title, description = it.description, startMs = it.startMs, stopMs = it.stopMs) }
            }.getOrNull().orEmpty()
            else -> return@withContext null
        }
        // A gap in the provider's own guide data around "now" (nothing covers this instant) must leave
        // current null — picking the next entry that simply hasn't ended yet would mislabel an upcoming
        // programme as live (issue #68). "Next"/"Later" are computed independently below, so a genuine
        // gap correctly shows no "Now" while the upcoming programme still appears as "Next".
        val current = entries.firstOrNull { it.startMs <= now && it.stopMs > now }
        val future = entries.filter { it.startMs > (current?.startMs ?: now) }.sortedBy { it.startMs }
        // Short-EPG responses sometimes include the just-finished programme — surface it as "Before".
        val previous = entries.filter { it.stopMs <= (current?.startMs ?: now) }.maxByOrNull { it.stopMs }
        val result = EpgNowNext(current, future.firstOrNull(), future.drop(1).take(4), previous = previous)
        epgCache[ch.id] = CachedEpg(now, result)
        result
    }

    /**
     * The programme currently airing on each of [channels] (channel id → title), looked up in ONE batch
     * against the stored bulk guide — same query the Home "On Now" rail uses. This powers the small
     * "current programme" subtitle under each channel row in the Live list and the in-player channel
     * overlay. Only the stored guide is consulted (no per-channel short-EPG API calls): a channel with no
     * guide simply has no entry here, and the row shows no second line. Returns only channels that
     * actually have something airing right now.
     */
    suspend fun nowPlayingFor(channels: List<ChannelEntity>): Map<Long, String> = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext emptyMap()
        val now = System.currentTimeMillis()
        val epgIds = epgSourceStore.getAll().map { it.id }
        val sourceIds = (channels.map { it.sourceId } + epgIds).distinct()
        // Manual EPG-match overrides take precedence over the channel's own epgChannelId, mirroring loadEpg.
        val channelKeys = channels.mapNotNull { ch ->
            val key = (custom.value.epgMatches[CustomizeKeys.channel(ch)] ?: ch.epgChannelId)
                ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (key != null) ch.id to key else null
        }
        if (channelKeys.isEmpty()) return@withContext emptyMap()
        val rowsByKey = channelKeys
            .map { it.second }.distinct()
            .chunked(400)
            .flatMap { keys -> epgDao.programmeSummariesForChannels(sourceIds, keys, now, now + 1) }
            .groupBy { it.epgChannelId }
        val result = HashMap<Long, String>()
        for ((channelId, epgKey) in channelKeys) {
            rowsByKey[epgKey]
                ?.firstOrNull { now in it.startMs until it.stopMs }
                ?.let { result[channelId] = it.title }
        }
        result
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    private fun tv.own.owntv.core.database.entity.EpgProgrammeEntity.toXt() =
        XtEpgEntry(title = title, description = description, startMs = startMs, stopMs = stopMs)

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, ChannelEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        val playlist = sort == SettingsRepository.SortMode.PLAYLIST
        return if (query.isBlank()) {
            when (key) {
                LiveKey.All -> if (playlist) channelDao.pagingAllOriginal(ids) else channelDao.pagingAll(ids)
                LiveKey.Favorites -> channelDao.pagingFavoritesManual(c.profileId, ContentOrderEntity.FAV_CONTEXT, ids)
                LiveKey.History -> channelDao.pagingHistory(c.profileId, ids)
                is LiveKey.Folder -> {
                    val ctxKey = folderContextKeys.value[key.id] ?: ""
                    // C3 fast path: no manual order in this folder → the plain indexed query has
                    // the identical (sortOrder, name) order without the join-sort.
                    if (ctxKey !in orderedContexts.value) channelDao.pagingByCategory(key.id)
                    else channelDao.pagingByCategoryManual(key.id, c.profileId, ctxKey)
                }
            }
        } else {
            when (key) {
                LiveKey.All -> channelDao.searchAll(query, ids)
                LiveKey.Favorites -> channelDao.searchFavorites(query, c.profileId, ids)
                LiveKey.History -> channelDao.searchHistory(query, c.profileId, ids)
                is LiveKey.Folder -> channelDao.searchInCategory(query, key.id)
            }
        }
    }

    fun enterMoveMode(channel: ChannelEntity, key: LiveKey) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val contextKey = when (key) {
                is LiveKey.Folder -> folderContextKeys.value[key.id] ?: return@launch
                LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                else -> return@launch
            }
            val items = when (key) {
                is LiveKey.Folder -> channelDao.snapshotByCategoryManual(key.id, pid, contextKey, 5000)
                LiveKey.Favorites -> channelDao.snapshotFavoritesManual(pid, contextKey, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                LiveKey.History, LiveKey.All -> return@launch
            }
            val idx = items.indexOfFirst { it.id == channel.id }
            if (idx < 0) return@launch
            _moveState.value = ChannelMoveState(items, idx, contextKey)
            settings.setSortLive(SettingsRepository.SortMode.PLAYLIST)
        }
    }

    fun moveUp() {
        val s = _moveState.value ?: return
        if (s.activeIndex == 0) return
        val list = s.items.toMutableList()
        val i = s.activeIndex
        list[i - 1] = s.items[i]; list[i] = s.items[i - 1]
        _moveState.value = s.copy(items = list, activeIndex = i - 1)
    }

    fun moveDown() {
        val s = _moveState.value ?: return
        if (s.activeIndex == s.items.size - 1) return
        val list = s.items.toMutableList()
        val i = s.activeIndex
        list[i + 1] = s.items[i]; list[i] = s.items[i + 1]
        _moveState.value = s.copy(items = list, activeIndex = i + 1)
    }

    fun commitMove() {
        val s = _moveState.value ?: return
        _moveState.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            contentOrderDao.replaceContext(
                profileId = pid,
                type = MediaType.LIVE,
                contextKey = s.contextKey,
                rows = s.items.mapIndexed { i, ch ->
                    ContentOrderEntity(profileId = pid, mediaType = MediaType.LIVE, contextKey = s.contextKey, itemId = ch.id, position = i)
                },
            )
        }
    }

    fun cancelMove() { _moveState.value = null }

    fun removeFromHistory(channelId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            historyDao.remove(pid, MediaType.LIVE, channelId)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx, hiddenCats: Set<Long>): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All -> if (hiddenCats.isEmpty()) channelDao.countAll(ids) else channelDao.countAllExcluding(ids, hiddenCats.toList())
            LiveKey.Favorites -> channelDao.countFavorites(c.profileId, ids)
            LiveKey.History -> channelDao.countHistory(c.profileId, ids)
            is LiveKey.Folder -> channelDao.countByCategory(key.id)
        }
    }

    private companion object {
        const val ENGINE_TAG = "LiveEngine"
        /** How long a channel must stay tuned before it counts as watched — see [recordLiveHistory]. */
        const val HISTORY_DEBOUNCE_MS = 5_000L
        const val TAG = "OwnTVHome"
        // Match EPG picker: how many guide channels to scan for name-ranking vs. show in the dialog.
        const val EPG_PICKER_SCAN_LIMIT = 20_000
        const val EPG_PICKER_RESULT_LIMIT = 300
        // In-player channel lists: one category is normally far smaller, but cap the uncategorized
        // → All Channels fallback so a huge playlist can't be pulled into memory on every tune.
        const val ZAP_LIST_LIMIT = 2_000
        const val HISTORY_LIST_LIMIT = 30
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, "Favorites", OwnTVIcon.FAVORITE),
            LiveRailItem(LiveKey.History, "History", OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All, "All Channels"),
        )
        const val CATCHUP_LOOKBACK_CAP_MS = 48L * 60 * 60 * 1000 // bounded by the EPG we retain (~2 days)
        const val ZAP_WINDOW_HALF = 50 // channels loaded on each side of the tuned channel for CH+/-
    }
}
