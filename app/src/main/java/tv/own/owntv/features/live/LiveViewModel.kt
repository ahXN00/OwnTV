@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.live

import tv.own.owntv.core.epg.displayLogoUrl
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.paging.filter
import androidx.paging.map
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.features.customize.MoveTarget
import tv.own.owntv.core.epg.CatchupUrl
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.customize.applyCustomizationsWithCustoms
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.database.entity.playStreamUrl
import tv.own.owntv.core.database.entity.resolveStreamUrl
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.LiveBuffer
import tv.own.owntv.core.settings.LiveLatency
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.player.LiveLadder
import tv.own.owntv.player.LiveProgramme
import tv.own.owntv.player.LiveStreamQuirks
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.core.live.LiveTimeshift
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.isChannelVisible
import tv.own.owntv.core.live.liveCountFlow
import tv.own.owntv.core.live.livePagingSource
import tv.own.owntv.core.live.parseLiveKey
import tv.own.owntv.core.live.serialize
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.live.LiveEpgReader
import tv.own.owntv.core.live.LiveArchiveUrls

/** A rail entry. Favorites/History carry an [icon] rendered inline before the title. */
@Immutable
data class LiveRailItem(
    val key: LiveKey,
    val title: String? = null,
    val icon: OwnTVIcon? = null,
    val providerName: String? = null,
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
    private val customCategoryDao: CustomCategoryDao,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
) : ViewModel() {

    data class ChannelMoveState(val items: List<ChannelEntity>, val activeIndex: Int, val contextKey: String)
    private val _moveState = MutableStateFlow<ChannelMoveState?>(null)
    val moveState: StateFlow<ChannelMoveState?> = _moveState.asStateFlow()

    data class CategoryMoveState(val items: List<String>, val keys: List<String>, val activeIndex: Int, val targetKey: String)
    private val _categoryMoveState = MutableStateFlow<CategoryMoveState?>(null)
    val categoryMoveState: StateFlow<CategoryMoveState?> = _categoryMoveState.asStateFlow()

    val livePreviewEnabled: StateFlow<Boolean> = settings.livePreviewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Channels pinned to mpv ("compatibility mode") — opened straight on mpv, bypassing ExoPlayer. Eagerly
     *  collected so the routing decision in [ensurePlaying] always sees the current set. Keyed by
     *  [enginePinKey], with legacy stream-URL entries still honoured (P6). */
    val forceMpvUrls: StateFlow<Set<String>> = forceMpvStore.urls
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Channels pinned the other way — to ExoPlayer — by the same toggle. Only reachable once the global
     *  setting starts channels on mpv, which is why this list did not exist before it. */
    private val forceExoUrls: StateFlow<Set<String>> = forceMpvStore.exoUrls
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Live TV's global engine order. Eagerly collected for the same reason as the pins: the routing
     *  decision in [playChannel] is synchronous and must never read a stale default. */
    val liveEnginePreference: StateFlow<tv.own.owntv.core.player.EnginePreference> = settings.liveEnginePreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.player.EnginePreference.EXO_FIRST)

    /** Settings → "Give up after": the whole-tune budget in ms, or [LiveLadder.NO_BUDGET] for Never.
     *  Eagerly collected for the same reason as the engine preference — the tune reads it synchronously. */
    private val ladderBudgetMs: StateFlow<Long> = settings.liveTuneTimeoutSecs
        .map { it * 1000L }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LiveLadder.DEFAULT_BUDGET_SECS * 1000L)

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

    private data class Ctx(
        val profileId: Long,
        val sourceIds: List<Long>,
        val sourceNames: Map<Long, String>,
    )

    // Observe the active profile's sources REACTIVELY so adding/removing a playlist refreshes Live TV
    // immediately (it used to be read once at startup, so a new playlist showed nothing until restart).
    // sourceUaMap is a lightweight side-product: sourceId → userAgent, used for synchronous play() calls
    // (playPreview, ensurePlaying) that can't do a DB lookup on the call site.
    private var sourceUaMap: Map<Long, String?> = emptyMap()
    // Full sources by id — lets the synchronous play() paths tell a Stalker source (needs play-time
    // create_link resolution) from M3U/Xtream (final URL already stored) without a DB round-trip.
    private var sourceById: Map<Long, tv.own.owntv.core.database.entity.SourceEntity> = emptyMap()

    /** This playlist's "Pre-buffer" override in seconds, or null to follow the global
     *  setting. Per-playlist because the periodic-rebuffer problem it solves belongs to a provider. */
    private fun prerollFor(sourceId: Long?): Int? =
        sourceId?.let { sourceById[it]?.livePrerollSecs }?.takeIf { it >= 0 }

    /** This playlist's Live TV engine override, or null to follow the global setting. Same reasoning as
     *  [prerollFor]: which engine copes is a property of the provider's stream format, not of the user. */
    private fun enginePreferenceFor(sourceId: Long?): tv.own.owntv.core.player.EnginePreference? =
        sourceId?.let { sourceById[it]?.liveEnginePreference }
            ?.let { name -> tv.own.owntv.core.player.EnginePreference.entries.firstOrNull { it.name == name } }

    /** This playlist's Live latency override, or null to follow the global setting. Resolved through the
     *  same [LiveBuffer.effectiveSeconds] the global path uses, so a CUSTOM value is clamped identically
     *  and Balanced still means "engine defaults" — for this playlist, rather than for all of them. */
    private fun liveBufferFor(sourceId: Long?): LiveBuffer.Override? {
        val source = sourceId?.let { sourceById[it] } ?: return null
        val mode = source.liveLatencyMode ?: return null
        return LiveBuffer.Override(LiveBuffer.effectiveSeconds(LiveLatency.fromName(mode), source.liveLatencyCustomSecs))
    }
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps ->
            sourceUaMap = aps.sources.associate { it.id to it.userAgent }
            sourceById = aps.sources.associateBy { it.id }
            val liveIds = aps.liveSourceIds
            Ctx(
                aps.profileId,
                liveIds,
                aps.sources.filter { it.id in liveIds }.associate { it.id to it.name },
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList(), emptyMap()))

    /** Empty for zero/one active Live source so single-playlist layouts stay unchanged. */
    val providerNames: StateFlow<Map<Long, String>> = ctx
        .map { c -> c.sourceNames.takeIf { it.size > 1 } ?: emptyMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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

    /** Every guide read this screen makes, and the now/next cache that used to live here — see
     *  [LiveEpgReader]. The shift it applies is passed in at each call, so this view model stays the
     *  single place that knows a customization changed. */
    private val epgReader = LiveEpgReader(epgDao, epgSourceStore, sourceDao, xtreamClient, streamUrlResolver)

    /** Catch-up and live-rewind archive URL construction — see [LiveArchiveUrls]. */
    private val archiveUrls = LiveArchiveUrls(sourceDao, xtreamClient, streamUrlResolver, settings)

    /** Bumped when a channel's EPG mapping changes so [nowNext] reloads for the same focused channel. */
    private val epgRefresh = MutableStateFlow(0)

    // ---- Live rewind / timeshift -------------------------------------------------------------------
    // Watch a catch-up-capable live channel a few minutes behind the live edge (a missed goal, etc.) using
    // the provider's rolling archive (Xtream timeshift / M3U catchup), then jump back to live. The archive
    // is a VOD-style stream on mpv (isArchive, mid-GOP tolerant); "Go to live" returns to ExoPlayer.
    //
    // How far back the user is, when to reload and the counters that run while an archive plays all live
    // in [LiveTimeshift]. This view model keeps only what opening a stream needs.
    private val timeshift = LiveTimeshift(
        scope = viewModelScope,
        playback = object : LiveTimeshift.Playback {
            override val positionMs: Long get() = player.position.value
            override val hasError: Boolean get() = player.error.value != null
            override val hasActiveStream: Boolean get() = player.hasActiveStream
        },
        loadArchive = ::loadArchiveStream,
        onLiveEdge = { goToLive() },
    )

    /** "Watching" clock: the wall-clock instant actually on screen. During archive playback the HUD
     *  clock alone is misleading — it reads 10:00 while the picture is yesterday at 13:00. Null
     *  whenever the picture IS the present (live edge, or a movie/episode). */
    val watchingWallMs: StateFlow<Long?> = timeshift.watchingWallMs


    /** Now/next for the focused channel — fetched (debounced) from the Xtream `get_short_epg` API. */
    val nowNext: StateFlow<EpgNowNext?> = combine(_previewChannel, epgRefresh) { ch, tick -> ch to tick }
        .debounce(350)
        .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second }
        .mapLatest { (ch, _) -> ch?.let { epgReader.nowNext(it, custom.value, epgOffset.value) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * "PLAYING / THEN" — what was on air at the moment being replayed. Null unless an archive is on
     * screen, so the HUD simply omits the card the rest of the time.
     *
     * Keyed on the watched instant rounded to the minute, not the raw value: [watchingWallMs] ticks
     * every second, and the answer can only change when a programme boundary is crossed. Without that
     * rounding this would hit the guide database once a second for the whole of a replay.
     */
    val archiveNowNext: StateFlow<EpgNowNext?> = combine(
        _previewChannel,
        watchingWallMs.map { ms -> ms?.let { it / 60_000L } }.distinctUntilChanged(),
        epgRefresh,
    ) { ch, minute, _ -> Triple(ch, minute, Unit) }
        .mapLatest { (ch, minute, _) ->
            if (ch == null || minute == null) null
            else epgReader.nowNextAt(ch, minute * 60_000L, custom.value, epgOffset.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The playing channel's guide entries, for the player's live timeline: they become the programme
     * boundary ticks drawn on the bar and the name the scrub bubble reads out. Keyed on the channel
     * alone — scrubbing must not re-query the guide, the whole two-hour window is already here.
     */
    val timelineProgrammes: StateFlow<List<LiveProgramme>> =
        combine(_previewChannel, epgRefresh) { ch, tick -> ch to tick }
            .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second }
            .mapLatest { (ch, _) ->
                ch?.let { catchupProgrammes(it).map { p -> LiveProgramme(p.startMs, p.stopMs, p.title) } }.orEmpty()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Global guide shift (minutes); a per-channel override in [custom] wins over it. Changing it
     *  drops the now/next cache so the details pane reflects the new offset straight away. */
    private val epgOffset: StateFlow<Int> = settings.epgOffsetMinutes
        .onEach { epgReader.clearCache(); epgRefresh.value++ }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** The user's custom combined categories with live member counts — the "Move to…" dialog's list. */
    val moveTargets: StateFlow<List<MoveTarget>> = combine(ctx, custom) { c, cust -> c to cust }
        .flatMapLatest { (c, cust) ->
            if (c.profileId < 0 || cust.customCategories.isEmpty()) flowOf(emptyList())
            else customCategoryDao.observeCountsByContexts(
                c.profileId,
                MediaType.LIVE,
                cust.customCategories.map { it.id },
                c.sourceIds.ifEmpty { listOf(-1L) },
            ).map { counts ->
                cust.customCategories.map { cc ->
                    MoveTarget(
                        id = cc.id,
                        displayName = cust.categoryNames[cc.id] ?: cc.name,
                        count = counts.firstOrNull { it.contextKey == cc.id }?.count ?: 0,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The stable key of a provider folder ([null] when the folder vanished) — the Move dialog's origin. */
    fun folderKey(id: Long): String? = folderContextKeys.value[id]

    /** Creates a custom category (issue #87) — the Move dialog's "＋ New category…" flow. */
    fun createCustomCategory(name: String) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.createCustomCategory(pid, MediaType.LIVE, name)
        }
    }

    /**
     * Moves (or copies, [keepInOrigin]) one channel into a custom category (issue #87). The item is
     * appended at the category's tail (maxPosition + 1). Without [keepInOrigin] the item leaves its
     * origin: a favorite row is deleted, a custom-category membership row is deleted, and a provider
     * folder is marked in movedFromOrigin — the pager chain then drops it from that folder while
     * keeping it in All / search / recent.
     */
    fun moveToCategory(itemKey: String, itemId: Long, originKey: String, targetId: String, keepInOrigin: Boolean) {
        if (targetId == originKey) return
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customCategoryDao.appendItem(pid, MediaType.LIVE, targetId, itemId)
            if (!keepInOrigin) {
                when {
                    originKey == ContentOrderEntity.FAV_CONTEXT -> favoriteDao.remove(pid, MediaType.LIVE, itemId)
                    CustomizeKeys.isCustom(originKey) -> customCategoryDao.deleteItem(pid, MediaType.LIVE, originKey, itemId)
                    else -> customize.setItemMovedFromOrigin(pid, MediaType.LIVE, itemKey, originKey, moved = true)
                }
            }
        }
    }

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
                combine(categoryDao.observe(c.sourceIds, MediaType.LIVE), custom, profileDao.observeById(c.profileId)) { cats, cust, profile ->
                    tv.own.owntv.core.content.AdultCategoryClassifier.hiddenCategoryIds(
                        cats,
                        cust.hiddenCategories,
                        profile?.isKids == true,
                    )
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
            else combine(
                categoryDao.observe(c.sourceIds, MediaType.LIVE),
                custom,
                sortMode,
                // Catch-up sits between History and All, but ONLY when the provider actually advertises
                // an archive — otherwise every user without catch-up gets a folder that can never fill.
                channelDao.observeCatchupCount(c.sourceIds.ifEmpty { listOf(-1L) }).distinctUntilChanged(),
                profileDao.observeById(c.profileId),
            ) { cats, cust, sort, catchupCount, profile ->
                // A–Z also sorts the category folders (custom categories included); manually moved
                // categories stay pinned first. Custom categories ride the SAME customization keys,
                // so renames/hides/reorders apply to them with no extra code (#87).
                val kids = profile?.isKids == true
                val visibleCats = if (kids) cats.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) } else cats
                val visibleCustoms = if (kids) cust.customCategories.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) } else cust.customCategories
                val folders = visibleCats.applyCustomizationsWithCustoms(cust, visibleCustoms, alphaRest = sort == SettingsRepository.SortMode.ALPHA)
                val multiSourceNames = c.sourceNames.takeIf { it.size > 1 }.orEmpty()
                val categoriesById = visibleCats.associateBy { it.id }
                railWithCatchup(catchupCount > 0) + folders.map { e ->
                    LiveRailItem(
                        key = e.categoryId?.let { LiveKey.Folder(it) } ?: LiveKey.Custom(e.customId!!),
                        title = e.displayName,
                        providerName = e.categoryId
                            ?.let(categoriesById::get)
                            ?.sourceId
                            ?.let(multiSourceNames::get),
                    )
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
                val movedFrom = cust.movedFromOrigin
                if (cust.hiddenItems.isEmpty() && cust.itemNames.isEmpty() && hiddenCats.isEmpty() && movedFrom.isEmpty()) paging
                else paging
                    .filter { ch ->
                        CustomizeKeys.channel(ch) !in cust.hiddenItems &&
                        (ch.categoryId == null || ch.categoryId !in hiddenCats) &&
                            // Moved-out items leave ONLY their origin folder (they stay in All/search).
                            (movedFrom[CustomizeKeys.channel(ch)]?.let { origin ->
                                key !is LiveKey.Folder || origin != folderContextKeys.value[key.id]
                            } ?: true)
                    }
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
            epgReader.invalidate(channel.id)
            epgRefresh.value++
        }
    }

    /** Hide a category by its rail key (undo via Settings → Customize). */
    fun hideCategory(key: LiveKey) {
        if (key !is LiveKey.Folder && key !is LiveKey.Custom) return
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val customizationKey = when (key) {
                is LiveKey.Folder -> categoryDao.getById(key.id)?.let { CustomizeKeys.category(it) }
                is LiveKey.Custom -> key.id
            } ?: return@launch
            customize.setCategoryHidden(pid, MediaType.LIVE, customizationKey, true)
        }
    }

    fun enterCategoryMoveMode(key: LiveKey) {
        if (key !is LiveKey.Folder && key !is LiveKey.Custom) return
        viewModelScope.launch {
            val c = ctx.first { it.profileId >= 0 }
            val pid = c.profileId
            val type = MediaType.LIVE
            val cats = categoryDao.observe(c.sourceIds, type).first()
            val cust = customize.observe(pid, type).first()
            val sort = sortMode.value
            val profile = profileDao.getById(pid)

            val kids = profile?.isKids == true
            val visibleCats = if (kids) cats.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) } else cats
            val visibleCustoms = if (kids) cust.customCategories.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) } else cust.customCategories

            val targetKey = when (key) {
                is LiveKey.Folder -> categoryDao.getById(key.id)?.let { CustomizeKeys.category(it) }
                is LiveKey.Custom -> key.id
            } ?: return@launch

            // Filter hidden categories so reordering exactly mirrors applyCustomizationsWithCustoms.
            val filteredCats = if (cust.hiddenCategories.isEmpty()) visibleCats else visibleCats.filter { CustomizeKeys.category(it) !in cust.hiddenCategories }
            val filteredCustoms = visibleCustoms.filter { it.id !in cust.hiddenCategories }

            val orderIndex = cust.categoryOrder.withIndex().associate { (i, k) -> k to i }
            val entries = filteredCustoms.map { cc ->
                cc.id to (cust.categoryNames[cc.id] ?: cc.name)
            } + filteredCats.map { cat ->
                val k = CustomizeKeys.category(cat)
                k to (cust.categoryNames[k] ?: cat.name)
            }

            val alpha = sort == SettingsRepository.SortMode.ALPHA
            val (pinned, rest) = entries.partition { it.first in orderIndex }
            val sortedList = pinned.sortedBy { orderIndex.getValue(it.first) } +
                (if (alpha) rest.sortedBy { it.second.lowercase() } else rest)

            val fullKeys = sortedList.map { it.first }
            val entryIdx = fullKeys.indexOf(targetKey)
            if (entryIdx < 0) return@launch

            _categoryMoveState.value = CategoryMoveState(
                items = sortedList.map { it.second },
                keys = fullKeys,
                activeIndex = entryIdx,
                targetKey = targetKey,
            )
        }
    }

    fun moveCategoryUp() {
        val s = _categoryMoveState.value ?: return
        if (s.activeIndex <= 0) return
        val items = s.items.toMutableList()
        val keys = s.keys.toMutableList()
        val i = s.activeIndex
        items[i - 1] = s.items[i]; items[i] = s.items[i - 1]
        keys[i - 1] = s.keys[i]; keys[i] = s.keys[i - 1]
        _categoryMoveState.value = s.copy(items = items, keys = keys, activeIndex = i - 1)
    }

    fun moveCategoryDown() {
        val s = _categoryMoveState.value ?: return
        if (s.activeIndex >= s.items.lastIndex) return
        val items = s.items.toMutableList()
        val keys = s.keys.toMutableList()
        val i = s.activeIndex
        items[i + 1] = s.items[i]; items[i] = s.items[i + 1]
        keys[i + 1] = s.keys[i]; keys[i] = s.keys[i + 1]
        _categoryMoveState.value = s.copy(items = items, keys = keys, activeIndex = i + 1)
    }

    fun commitCategoryMove() {
        val s = _categoryMoveState.value ?: return
        _categoryMoveState.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setCategoryOrder(pid, MediaType.LIVE, s.keys)
        }
    }

    fun cancelCategoryMove() {
        _categoryMoveState.value = null
    }

    /** The current manual EPG id for a channel, or null if auto-matched. */
    fun currentEpgMatch(channel: ChannelEntity): String? = custom.value.epgMatches[CustomizeKeys.channel(channel)]

    /** Shift this channel's guide by [minutes] (null → follow the global EPG offset). */
    fun setEpgShift(channel: ChannelEntity, minutes: Int?) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val key = CustomizeKeys.channel(channel)
            customize.setEpgShift(pid, MediaType.LIVE, key, minutes)
            // The guide read takes the shift off `custom` — let the DataStore edit reach it before refreshing.
            kotlinx.coroutines.withTimeoutOrNull(1_000) { custom.first { it.epgShifts[key]?.toIntOrNull() == minutes } }
            epgReader.invalidate(channel.id) // the cached now/next was built on the old shift
            epgRefresh.value++
        }
    }

    /** The channel's own guide shift in minutes, or null when it follows the global offset. */
    fun currentEpgShift(channel: ChannelEntity): Int? =
        tv.own.owntv.core.epg.EpgShift.overrideFor(custom.value, channel)

    /** The global guide shift, shown as the per-channel dialog's "follow global" default. */
    fun globalEpgShift(): Int = epgOffset.value

    /** Distinct EPG channels for the "Match EPG" picker (across the profile's playlists + EPG feeds),
     *  ranked so guide channels resembling [channelName] come first instead of a plain A-Z list. */
    suspend fun availableEpgChannels(channelName: String, query: String): List<tv.own.owntv.core.database.entity.EpgChannelEntity> {
        if (currentProfileId() == null) return emptyList()
        return epgReader.availableEpgChannels(channelName, query, ctx.value.sourceIds)
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
            // A saved Folder/Custom is honoured only while it still exists in this profile's rail —
            // a deleted custom category or a re-synced-away folder must not resurrect on restart.
            if (saved is LiveKey.Folder || saved is LiveKey.Custom) {
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

    // True while the in-pane preview is suppressed because the provider allows one stream at a time and
    // that stream is already in use (full-screen playback). The pane says so instead of sitting silently
    // dead — otherwise "no preview" looks like a broken channel (F31).
    private val _previewBlockedSingleSession = MutableStateFlow(false)
    val previewBlockedSingleSession: StateFlow<Boolean> = _previewBlockedSingleSession.asStateFlow()

    fun playPreview(channel: ChannelEntity) {
        if (channel.categoryId != null && channel.categoryId in hiddenCategoryIds.value) return
        // Don't touch the engine while it's promoted to full-screen. Clicking OK before the in-pane preview's
        // focus-delay fires would otherwise let this late preview call re-mute the now-full-screen stream
        // (preview audio is off) — so full-screen would play with no sound. ensurePlaying() sets liveOnExo
        // the instant OK is pressed, before this can run.
        if (_liveOnExo.value) return
        val source = sourceById[channel.sourceId]
        if (streamUrlResolver.needsResolve(source)) { playPreviewStalker(channel, source!!); return }
        val targetUrl = tuneUrl(channel, source)
        // A one-session panel counts the muted preview as the account's single stream, so previewing while
        // mpv is playing full-screen locks the user's own playback out. Browsing stays silent there.
        if (player.hasActiveStream && LiveStreamQuirks.isSingleSession(targetUrl)) {
            _previewBlockedSingleSession.value = true
            return
        }
        _previewBlockedSingleSession.value = false
        // Already previewing this channel (e.g. re-focus)? Just re-apply the preview mute, no reload.
        if (previewEngine.currentUrl == targetUrl &&
            previewEngine.state.value != tv.own.owntv.player.LivePreviewEngine.State.ERROR
        ) {
            previewEngine.setMuted(!livePreviewAudio.value)
            return
        }
        stalkerPreviewCmd = null
        setStalkerReconnect(null) // non-Stalker: URLs are stable, replay on reconnect
        previewEngine.play(
            targetUrl, muted = !livePreviewAudio.value,
            meta = tv.own.owntv.player.MediaMeta(title = channel.name, subtitle = channelNumberLabel(channel), logoUrl = channel.displayLogoUrl, contentKey = mpvPinKey(channel)),
            userAgent = sourceUaMap[channel.sourceId],
            prerollSecsOverride = prerollFor(channel.sourceId),
            liveBufferOverride = liveBufferFor(channel.sourceId),
            httpHeaders = channel.httpHeaders,
            drmConfig = channel.drmConfig,
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
            if (player.hasActiveStream && LiveStreamQuirks.isSingleSession(url)) { // see playPreview
                _previewBlockedSingleSession.value = true
                return@launch
            }
            _previewBlockedSingleSession.value = false
            stalkerPreviewCmd = channel.streamUrl
            setStalkerReconnect(channel.streamUrl) // C-3: re-resolve on reconnect if the URL expires
            previewEngine.play(
                url, muted = !livePreviewAudio.value,
                meta = tv.own.owntv.player.MediaMeta(title = channel.name, subtitle = channelNumberLabel(channel), logoUrl = channel.displayLogoUrl, contentKey = mpvPinKey(channel)),
                userAgent = source.userAgent,
                prerollSecsOverride = prerollFor(channel.sourceId),
                liveBufferOverride = liveBufferFor(channel.sourceId),
                httpHeaders = channel.httpHeaders,
                drmConfig = channel.drmConfig,
            )
        }
    }

    // The ordered channel list the player zaps within (CH+/CH-, D-pad up/down) and shows in the
    // left-hand overlay. This is playback CONTEXT: the Favorites/History/All/folder/custom rail that
    // launched the channel, or a provider category explicitly selected in the in-player browser.
    // `channel.categoryId` remains metadata and is used only when a caller has no browse context.
    //
    // Which list is armed, where CH± lands and which background rebuild may publish all live in
    // [LiveZapList]. What stays here is the querying: the loaders below, because the hide/rename rules
    // they apply are shared with the browse lists and belong beside them.
    private val zapList = LiveZapList(
        scope = viewModelScope,
        playingChannelId = { _previewChannel.value?.id },
        loadForChannel = ::channelsAroundChannel,
        loadForCategory = ::channelsInCategory,
        loadWindowAround = ::buildZapList,
        categoryName = { id -> categoryDao.getById(id)?.name },
    )
    val canZap: StateFlow<Boolean> = zapList.canZap
    val zapChannels: StateFlow<List<ChannelEntity>> = zapList.channels
    val zapListTitle: StateFlow<String?> = zapList.title
    val zapListKey: StateFlow<LiveKey?> = zapList.key

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
    fun loadChannelsForCategory(categoryId: Long) =
        zapList.armForCategory(categoryId) { _showCategoryBrowser.value = false }

    /** One provider category, in its manual order — the in-player category browser's pick. */
    private suspend fun channelsInCategory(categoryId: Long): List<ChannelEntity> {
        val pid = currentProfileId() ?: return emptyList()
        val ctxKey = folderContextKeys.value[categoryId] ?: ""
        return channelDao.snapshotByCategoryManual(categoryId, pid, ctxKey, ZAP_LIST_LIMIT)
    }

    /** [channel]'s own provider category, with the same hide/rename treatment the browsing lists get,
     *  so the in-player overlay matches what the user sees everywhere else. */
    private suspend fun channelsAroundChannel(channel: ChannelEntity): List<ChannelEntity> {
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
        val cust = custom.value
        val hiddenCats = hiddenCategoryIds.value
        return raw
            .filter { CustomizeKeys.channel(it) !in cust.hiddenItems && (it.categoryId == null || it.categoryId !in hiddenCats) }
            .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
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
        viewModelScope.launch { player.archiveEnded.collect { continueAfterCatchup() } }
    }

    /** Called when anything OTHER than a promoted live channel takes over full-screen (a movie/episode,
     *  catch-up, an EPG/search channel — all play on mpv). Clears the ExoPlayer flag so the shell renders
     *  mpv's surface (not the leftover live channel) and stops the preview so it doesn't hold a connection. */
    /** Back out of full-screen to the Live screen: we're no longer full-screen on ExoPlayer, so the preview
     *  pane may re-take the engine (and re-apply the preview mute) on the next focus. Keeps the stream
     *  playing (no stop) — just clears the flag so [playPreview] works again. */
    fun onFullscreenExited() {
        _liveOnExo.value = false
        // Leaving full-screen ends the rewind: the archive stream is torn down with the player, and the
        // 1 Hz "behind live" ticker would otherwise keep running against nothing for the rest of the session.
        clearTimeshift()
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

    /** Final startup/deep-entry visibility check, including profile source and Customize policy. */
    suspend fun isVisibleToActiveProfile(channel: ChannelEntity): Boolean {
        val current = ctx.first { it.profileId >= 0L }
        if (channel.sourceId !in current.sourceIds) return false
        if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(current.profileId, channel.categoryId, profileDao, categoryDao)) return false
        val customizations = customize.observe(current.profileId, MediaType.LIVE).first()
        if (CustomizeKeys.channel(channel) in customizations.hiddenItems) return false
        val category = channel.categoryId?.let { categoryDao.getById(it) }
        return category == null || CustomizeKeys.category(category) !in customizations.hiddenCategories
    }

    /** Open a channel fullscreen, preserving the browse context it was launched from. The channel's
     *  provider category is metadata (used by [previewCategoryName]); it must not replace Favorites,
     *  History, All, a custom category, or the provider folder the user is actually browsing. */
    fun watchFullscreen(channel: ChannelEntity, list: List<ChannelEntity>) {
        // Opened with no browse list behind it — the catch-up programme dialog does exactly this. Without
        // a zap list, CH+/CH− and the channel-list button are dead for the rest of the session, so rebuild
        // the channel's own category the way the Guide/Search path does.
        if (list.none { it.id == channel.id }) {
            zapList.armFor(channel)
            ensurePlaying(channel)
            return
        }
        val key = _selected.value
        zapList.armFromBrowse(
            channels = list,
            title = railItems.value.firstOrNull { it.key == key }?.title,
            // Built-in rails (Favorites / History / All) carry no title — their labels are UI strings.
            // Hand the key out so the overlay can name them properly instead of "All channels".
            key = key,
            categoryId = (key as? LiveKey.Folder)?.id,
        )
        ensurePlaying(channel)
    }

    /** Tune a channel picked outside the Live TV list — the Guide, or a Search result (F05). Same as
     *  [ensurePlaying] except history is written straight away: this is a deliberate one-shot pick (you
     *  chose a channel, or "Watch channel" in a programme dialog), not the zap-through-a-category flow
     *  the history debounce exists to filter, and such a channel was not reliably landing in History.
     *
     *  Every live entry point funnels through here into [playChannel], so Prefer HLS, the
     *  ExoPlayer→mpv ladder, compatibility-mode pins, learned stream quirks, the per-playlist
     *  pre-buffer and the external-player toggle apply however the channel was found. */
    fun watchFromGuide(channel: ChannelEntity) {
        zapList.armFor(channel)
        ensurePlaying(channel)
        recordLiveHistory(channel, immediate = true)
    }

    /** Zap to the neighbouring channel ([delta] = +1 down / -1 up) — see [LiveZapList.next] for how
     *  the target is chosen. [ensurePlaying], reached through [zapTo], cancels any pending rebuild. */
    fun zap(delta: Int) {
        zapList.next(delta)?.let { zapTo(it) }
    }

    /**
     * CH+/- step: show the channel at once, open the stream once the user stops moving.
     *
     * Holding the channel key steps roughly every 170 ms, and every step used to open a stream —
     * thirteen opens in 3.6 s on a real remote. Providers answer that as abuse: a one-session Xtream
     * panel locks the account for ~2 minutes (HTTP 458, every channel refused until it clears), and a
     * Stalker portal returns 429 for the duration of the burst. Neither is a playback problem, and no
     * fallback ladder can help, because the stream the user actually wants is refused too.
     *
     * So the *decision* stays instant — the channel changes on screen, history and the zap anchor move,
     * and the next press steps from here — while the network work waits [ZAP_TUNE_DELAY_MS] and is
     * cancelled by the next press. Ten channels passed at speed cost one stream open instead of ten.
     *
     * Deliberately NOT applied to deliberate picks ([ensurePlaying] from the channel list, Guide, Home
     * or a deep link): those are a single considered choice and must open immediately.
     */
    private fun zapTo(channel: ChannelEntity) {
        _previewChannel.value = channel
        pendingZapTuneJob?.cancel()
        pendingZapTuneJob = viewModelScope.launch {
            delay(ZAP_TUNE_DELAY_MS)
            // Cleared before handing over, so this tune's own [ensurePlaying] doesn't cancel the job
            // it is running inside.
            pendingZapTuneJob = null
            ensurePlaying(channel)
        }
    }

    private var pendingZapTuneJob: Job? = null

    /**
     * Direct-tune: resolve a provider channel number to a channel and tune it.
     *
     * Two-stage lookup: the playing channel's source first, then other active Live sources only when
     * the current source has zero visible matches. Duplicate numbers are resolved via zap-context
     * tiebreaker. Hidden channels/categories are excluded.
     *
     * What happens after resolution — immediate playback, the guarded background rebuild of the
     * bounded window, and the CH+/- anchor that covers the gap — is [LiveZapList.directTune].
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
            val snapshotZapList = zapList.channels.value

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

            // Playback starts IMMEDIATELY — the rebuild that follows it is the zap list's business.
            zapList.directTune(currentChannel.id, tuned) { playChannel(it) }

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

    /** The bounded provider-order window around a channel, so CH+/- and the channel-list overlay work
     *  after a numeric tune jumped outside the original list (half before, half after), with
     *  hidden-channel/category filtering and custom names applied. Pure builder: it mutates nothing,
     *  so [LiveZapList] can discard a stale or cancelled result. */
    private suspend fun buildZapList(channel: ChannelEntity): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val cust = custom.value
        val hiddenCats = hiddenCategoryIds.value
        val half = ZAP_WINDOW_HALF
        val afterRaw: List<ChannelEntity>
        val beforeRaw: List<ChannelEntity>
        val categoryId = channel.categoryId
        if (categoryId != null) {
            afterRaw = channelDao.channelsAfterCategory(categoryId, channel.sortOrder, channel.id, half)
            beforeRaw = channelDao.channelsBeforeCategory(categoryId, channel.sortOrder, channel.id, half)
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

    /** "External player" is on for Live TV — the screen must NOT open the fullscreen in-app player
     *  (mounting it spins up an engine even though the channel went to the external app). */
    val externalPlayerOn: StateFlow<Boolean> = settings.externalPlayerLive
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Hand [channel] to an external app (VLC, MX Player). Used by the Live TV long-press menu, and by
     *  [playChannel] when Live TV's external-player default is on. Stalker channels store a portal
     *  cmd rather than a URL, so it's resolved first — an external app can't mint one. */
    fun playExternal(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, channel.categoryId, profileDao, categoryDao)) return@launch
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
            externalPlayerLauncher.launch(
                url = url,
                title = channel.name,
                userAgent = source?.userAgent,
                httpHeaders = channel.httpHeaders,
            )
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
        zapList.cancelPendingRebuild()
        // A deliberate pick supersedes a CH+/- step still waiting out its delay — otherwise the deferred
        // tune would land half a second later and drag the user off the channel they just chose.
        pendingZapTuneJob?.cancel()
        pendingZapTuneJob = null
        viewModelScope.launch { playChannel(channel) }
    }

    private suspend fun getSource(sourceId: Long): tv.own.owntv.core.database.entity.SourceEntity? =
        sourceById[sourceId] ?: sourceDao.getById(sourceId)

    /** The URL to actually tune for [channel]: [playStreamUrl] — i.e. the playlist's "Prefer HLS" `.ts`
     *  → `.m3u8` swap — except on a channel already caught having no working `.m3u8`, which goes back
     *  to the `.ts` its panel does serve. See [LiveStreamQuirks.rememberNoHlsVariant]. */
    private fun tuneUrl(channel: ChannelEntity, source: SourceEntity?): String =
        if (forceTsForExo == channel.streamUrl || LiveStreamQuirks.lacksHlsVariant(channel.streamUrl)) channel.streamUrl
        else channel.playStreamUrl(source)

    /** The channel whose ladder is on an explicit `.ts` rung, so [tuneUrl] serves the original stream
     *  even before the "no HLS variant" lesson has been written (the rung must not depend on that order).
     *
     *  Keyed by channel, not a bare flag: [playPreview] tunes through [tuneUrl] too, so a global flag left
     *  set by channel A's TS rung sent the preview of every *other* channel to `.ts` until the next
     *  [armLadder]. Pressing OK then computed the `.m3u8` again, found it didn't match the preview's URL,
     *  and rebuilt the stream from scratch instead of promoting the one already playing. */
    private var forceTsForExo: String? = null

    /** Internal playback: the canonical ExoPlayer / mpv / Stalker / history side-effects for a
     *  channel. Direct-tune's background rebuild path calls this without cancelling the rebuild
     *  so the in-flight rebuild it owns isn't killed by its own play. */
    private suspend fun playChannel(channel: ChannelEntity) {
        val pid = currentProfileId() ?: return
        if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, channel.categoryId, profileDao, categoryDao)) return
        // Live TV set to play externally: hand the channel over instead of tuning an in-app engine.
        // History is still recorded, so the channel shows up in History/Recently watched either way.
        // #115 — a protected channel stays in-app whatever this setting says: no standard intent extra
        // carries a licence URL, so the external player would open it and fail immediately.
        if (externalPlayerOn.value && channel.drmConfig == null) { playExternal(channel); return }
        _previewChannel.value = channel
        clearTimeshift() // normal live = not timeshifted
        _catchupActive.value = false // tuning live ends any archive playback the HUD was showing
        // Three inputs, in descending authority: what the user pinned for THIS channel, the engine
        // setting, and what the app has learned about the panel.
        // The setting itself resolves per-item pin → per-playlist → global: a playlist override replaces
        // the global choice for this provider's channels only, and is still outranked by a pin below and
        // by the DRM rule further down, both of which are per-channel and therefore more specific.
        val setting = enginePreferenceFor(channel.sourceId) ?: liveEnginePreference.value
        // Self-learning routing: a channel the user pinned skips the other engine entirely (no
        // artifacts/silent first), straight to the one that plays it. A pin is a deliberate per-channel
        // exception and therefore outranks the global setting — that is the whole point of the toggle.
        val pin = enginePin(channel)
        // Same idea, learned instead of pinned: a panel already caught refusing its own signed segment
        // URLs can never be satisfied by ExoPlayer, so skip straight to mpv rather than replaying the
        // detection (two dead 403s + the wait) on every further channel of that panel. Only ever consulted
        // when the setting still allows both engines: in an "only" mode the user has ruled the other engine
        // out, and a lesson the app taught itself may not overturn that. A pin may, because the user made it.
        val refusing = pin == null && setting.allowsHandover && panelRefusesSegments(channel)
        // #115 — a protected (Widevine/ClearKey) channel. mpv ships no CDM, so it cannot obtain a key
        // from a licence server (it can only decrypt CENC from a key handed to it directly, which a
        // playlist never provides). So this is not a preference to weigh against the others: it
        // outranks the setting AND a per-channel pin, because handing such a channel to mpv can only
        // produce a failure and a wasted handover.
        val drmProtected = channel.drmConfig != null
        val onMpv = if (drmProtected) false else pin ?: (refusing || setting.startsOnMpv)
        // A pin that contradicts an "only" setting re-opens the handover for this one channel. Without
        // that, the exception channel would be locked to the engine the user just said cannot play it,
        // with the ladder forbidden from ever reaching the one that can — a dead end of our own making.
        val preference = when {
            drmProtected -> tv.own.owntv.core.player.EnginePreference.EXO_ONLY
            setting.allowsHandover -> tv.own.owntv.core.player.EnginePreference.firstOn(onMpv)
            onMpv == setting.startsOnMpv -> setting
            else -> tv.own.owntv.core.player.EnginePreference.firstOn(onMpv)
        }
        val reason = when {
            drmProtected -> "exoplayer (drm)"
            pin != null -> "${if (onMpv) "mpv" else "exoplayer"} (pinned)"
            refusing -> "mpv (panel refuses segments)"
            else -> "${if (onMpv) "mpv" else "exoplayer"} (setting)"
        }
        engineLog("tune '${channel.name}' -> $reason [${preference.name}]")
        armLadder(channel, preference)
        if (onMpv) startOnMpv(channel, reason) else startOnExo(channel)
        recordLiveHistory(channel)
    }

    /** Live engine routing decisions go to Logcat (unconditionally, so a release build can be diagnosed
     *  from `adb logcat -s LiveEngine`) and to [LiveDiagnosticsLog]'s ring buffer. A report of "this
     *  channel starts on mpv but plays fine on ExoPlayer" then carries the reason it was routed there.
     *  Channel names only — never a stream URL. */
    private fun engineLog(message: String) {
        android.util.Log.i(ENGINE_TAG, message)
        tv.own.owntv.player.LiveDiagnosticsLog.event("engine: $message")
    }

    /**
     * Mirror a fallback-ladder decision into the user-visible playback log (Settings → "Playback error
     * log"), on top of the diagnostics ring [engineLog] already writes.
     *
     * That log's whole point is that a TV user can't read logcat, and it explicitly advertises engine
     * handoffs — but the live ladder wrote to neither it nor anything else the user can reach, so a
     * channel that walked ExoPlayer → mpv → dead produced a completely empty log and every report of it
     * became guesswork. Called once per ladder step (at most four per tune, only ever on a failure), so
     * it stays inside the "keep these rare" rule the log is built on. URLs in [reason] are redacted by
     * [PlaybackErrorLog] itself.
     *
     * Call this BEFORE the engine actually switches: [_liveOnExo] then still names the engine that failed.
     */
    private fun recordLadderEvent(
        event: tv.own.owntv.player.PlayerFailureReason,
        channel: ChannelEntity,
        detail: String,
    ) {
        tv.own.owntv.player.PlaybackErrorLog.event(
            context = appContext,
            engine = if (_liveOnExo.value) "ExoPlayer" else "mpv",
            live = true,
            reason = event,
            detail = "'${channel.name}': $detail",
        )
    }

    /** P6 — the stable "compatibility mode" pin key for [channel], or null when the row has no
     *  provider id (some hand-made M3U entries), where the stream URL stays the key. */
    private fun mpvPinKey(channel: ChannelEntity): String? =
        tv.own.owntv.core.player.enginePinKey(channel.sourceId, "LIVE", channel.remoteId)

    /** The engine [channel] is pinned to — true = mpv, false = ExoPlayer, null = not pinned, follow the
     *  setting. Honours pins written by older builds under the stream URL; a legacy hit is rewritten under
     *  the stable key so it survives the next re-sync/Stalker resolve. */
    private fun enginePin(channel: ChannelEntity): Boolean? {
        val key = mpvPinKey(channel)
        val mpvPins = forceMpvUrls.value
        val exoPins = forceExoUrls.value
        if (key != null && key in mpvPins) return true
        if (key != null && key in exoPins) return false
        val legacy = when (channel.streamUrl) {
            in mpvPins -> true
            in exoPins -> false
            else -> return null
        }
        if (key != null) viewModelScope.launch { forceMpvStore.migrateKey(channel.streamUrl, key) }
        return legacy
    }

    /**
     * Whether this channel's panel has already been caught handing out signed segment URLs it then
     * refuses ([LiveStreamQuirks.rememberSegmentRefusal]).
     *
     * The lesson is panel-wide and session-only, so the first channel of the session still pays the
     * detection and a provider that fixes its panel is back to the ExoPlayer-first path after the next
     * app start. An explicit "use ExoPlayer" pin for this channel still wins, and so does an "only" engine
     * setting — the caller does not consult this at all in either case, because a quirk the app taught
     * itself may never overturn a choice the user made. Stalker sources are excluded: their `streamUrl` is a cmd, not a
     * URL, so there is no host to key on until it has been resolved (a network call this decision
     * must not make).
     */
    private suspend fun panelRefusesSegments(channel: ChannelEntity): Boolean {
        val source = getSource(channel.sourceId)
        if (streamUrlResolver.needsResolve(source)) return false
        return LiveStreamQuirks.refusesSegments(channel.playStreamUrl(source))
    }

    private suspend fun startOnExo(channel: ChannelEntity) {
        mpvOutcomeJob?.cancel() // ExoPlayer owns the channel now
        _liveOnExo.value = true
        player.stop() // free mpv (decoder/connection) if a previous full-screen used it
        val source = getSource(channel.sourceId)
        if (streamUrlResolver.needsResolve(source)) { startOnExoStalker(channel, source!!); return }
        val targetUrl = tuneUrl(channel, source)
        if (previewEngine.currentUrl == targetUrl) {
            previewEngine.setMuted(false) // promote — instant if already PLAYING, otherwise keeps loading
        } else {
            // In-player zap to a DIFFERENT channel (CH+/-, D-pad, channel-list overlay): if we're leaving a
            // UHD channel, fully release its 4K decoder before the reuse/rebuild (no-op for SD/HD). Matches
            // the Back/exit path — so the Hisense 4K-decoder leak is avoided however you leave the channel.
            previewEngine.releaseDecoderForUhd()
            stalkerPreviewCmd = null
            setStalkerReconnect(null) // non-Stalker: URLs are stable, replay on reconnect
            previewEngine.play(
                targetUrl, muted = false,
                meta = tv.own.owntv.player.MediaMeta(title = channel.name, subtitle = channelNumberLabel(channel), logoUrl = channel.displayLogoUrl, contentKey = mpvPinKey(channel)),
                userAgent = sourceUaMap[channel.sourceId] ?: source?.userAgent,
                prerollSecsOverride = prerollFor(channel.sourceId),
                liveBufferOverride = liveBufferFor(channel.sourceId),
                httpHeaders = channel.httpHeaders,
                drmConfig = channel.drmConfig,
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
                meta = tv.own.owntv.player.MediaMeta(title = channel.name, subtitle = channelNumberLabel(channel), logoUrl = channel.displayLogoUrl, contentKey = mpvPinKey(channel)),
                userAgent = source.userAgent,
                prerollSecsOverride = prerollFor(channel.sourceId),
                liveBufferOverride = liveBufferFor(channel.sourceId),
                httpHeaders = channel.httpHeaders,
                drmConfig = channel.drmConfig,
            )
            watchExoOutcome(channel)
        }
    }

    /** Start [channel] on the full mpv engine — a pinned "compatibility" channel, the engine setting
     *  asking for mpv first, or an ExoPlayer fallback. [reason] rides into the diagnostics, so it must
     *  say which of those it was: a log that calls every mpv start a pin makes a setting-driven start
     *  look like a stray pin, which is exactly the confusion these lines exist to prevent. */
    private fun startOnMpv(channel: ChannelEntity, reason: String) {
        viewModelScope.launch { fallbackToMpv(channel, reason) }
    }

    /** HUD "compatibility mode" toggle: pin/unpin the current channel to mpv and swap engines live. */
    fun toggleForceMpv() {
        val channel = _previewChannel.value ?: return
        // A catch-up archive is loaded, not the live stream: re-tuning here would replace the programme
        // the user is watching with the channel's CURRENT one. The HUD already hides this toggle during
        // catch-up (it offers the VOD engine toggle instead) — this is the belt-and-braces guard.
        if (_catchupActive.value) return
        // Same reasoning while rewound into the live archive: swapping engines re-opens the channel at the
        // live edge, throwing the user out of the rewind. The HUD hides the toggle then; this is the guard.
        if (timeshift.isRewound) return
        // #115 — a protected channel has only one engine that can obtain its key. Swapping to mpv would
        // trade a playing channel for a guaranteed failure, so the toggle does nothing here; the HUD
        // hides it for such a channel, and this is the belt-and-braces guard.
        if (channel.drmConfig != null) return
        // Base the swap on the ACTUAL running engine, not the pin: after an auto-fallback to mpv the channel
        // runs on mpv while still unpinned, and the old pin-based logic then did nothing on click. Keying off
        // _liveOnExo makes every click flip the live engine, with the pin following the choice.
        val goToMpv = _liveOnExo.value // on Exo now → switch to mpv; on mpv now → switch to Exo
        engineLog("engine toggle '${channel.name}' -> ${if (goToMpv) "mpv" else "exoplayer"} (currentlyOnExo=${_liveOnExo.value})")
        viewModelScope.launch {
            // Pin to the chosen engine. Write the stable key, and clear any legacy URL-keyed entry too so
            // the pin can't be contradicted by an older one left behind (P6).
            val key = mpvPinKey(channel)
            forceMpvStore.pin(key ?: channel.streamUrl, goToMpv)
            if (key != null) forceMpvStore.forget(channel.streamUrl)
            // A manual engine choice restarts the ladder around that engine — and, for this tune only, as
            // an "only" mode. This is the click the user just made, on the engine they just named, while
            // watching: bouncing them off it seconds later (as the undecodable-audio check did, over and
            // over, on a box where neither engine has sound) makes the control look broken and leaves no
            // way to stay put. The NEXT tune of the same channel reads the pin instead and gets the full
            // ladder back, so a channel the chosen engine genuinely cannot play still ends up somewhere
            // that plays it rather than stuck forever on one bad decision.
            armLadder(channel, tv.own.owntv.core.player.EnginePreference.onlyOn(goToMpv))
            if (goToMpv) {
                fallbackToMpv(channel, "user chose compatibility mode") // ExoPlayer → mpv now
            } else {                    // mpv → ExoPlayer now
                switchToExo(channel)
            }
        }
    }

    fun ensurePlayingById(channelId: Long) {
        viewModelScope.launch {
            val channel = channelDao.getById(channelId) ?: return@launch
            zapList.armFor(channel)
            ensurePlaying(channel)
        }
    }

    /** Open a channel from a caller-owned rail (for example Home favorites), preserving that rail as the
     *  playback browse/zap context instead of replacing it with provider-category metadata. */
    suspend fun ensurePlayingByIdAsync(channelId: Long, zapChannels: List<ChannelEntity> = emptyList()): Boolean {
        val channel = channelDao.getById(channelId) ?: return false
        if (zapChannels.isEmpty()) {
            // A single Home "continue watching" tile has no browse rail of its own.
            zapList.armFor(channel)
        } else {
            zapList.armFromBrowse(zapChannels, title = null, key = null, categoryId = null)
        }
        ensurePlaying(channel)
        return true
    }

    /** One-shot: hand [channel] to mpv if ExoPlayer can't play it fully — either it **errors** opening, or it
     *  plays but ExoPlayer can decode **none of its audio** (e.g. an AC3/E-AC3/DTS movie file added via M3U,
     *  on a device without that decoder — it'd play silently). mpv (FFmpeg) decodes everything. */
    private var exoOutcomeJob: Job? = null

    private fun watchExoOutcome(channel: ChannelEntity) {
        exoOutcomeJob?.cancel()
        exoOutcomeJob = viewModelScope.launch {
            // Runs alongside the terminal-state wait below: audio/position can be progressing fine (so
            // ExoPlayer never reaches ERROR) while a video track never renders a single frame — the "audio
            // plays, no picture" case. One-shot per tune; mpv's own outcome (success or its own error state)
            // takes it from there, same as the ERROR branch below.
            launch {
                previewEngine.noVideoDetected.first { it }
                if (!isStill(channel)) return@launch
                val reason = "no video frame rendered (audio plays, no picture)"
                advanceLadder(channel, reason)
            }
            // The provider signs each segment URL with an expiring token and refuses them all; Media3 can
            // only re-issue the URL it already resolved, so no amount of retrying recovers this. mpv/FFmpeg
            // re-reads the playlist and fetches with a fresh token, so hand over as soon as it's proven.
            launch {
                previewEngine.segmentsRefused.first { it }
                if (isStill(channel)) advanceLadder(channel, "provider refuses ExoPlayer's signed segment URLs")
            }
            // Bounded, because "neither" is a real outcome. A stream can open its HLS playlist, report
            // BUFFERING and then simply never deliver a playable segment — no first frame, no error. The
            // engine's own stall watchdog can't save that one: it is armed only AFTER the first successful
            // play, so nothing times out and the spinner sits there forever. Without this deadline that
            // channel never reaches mpv, which usually plays it fine.
            // A pre-buffer is requested silence: 10s of it means the first frame is *supposed* to be ~10s
            // out, so the deadline has to move with it or every pre-buffered channel looks stuck.
            val openBudgetMs = EXO_OPEN_TIMEOUT_MS + previewEngine.activePrerollSecs.coerceAtLeast(0) * 1000L
            // A provider back-off (HTTP 429 + Retry-After) is the panel naming the second at which this
            // channel frees up, and the engine is counting it down behind the spinner. Expiring the budget
            // in the middle of that would hand a perfectly good channel to TS/mpv over a wait we asked for
            // — so the deadline restarts for as long as the countdown is running, and only a stream that
            // goes quiet for a whole budget with nothing pending counts as "never opened".
            var terminal: tv.own.owntv.player.LivePreviewEngine.State?
            var waitsSeen = 0
            while (true) {
                terminal = kotlinx.coroutines.withTimeoutOrNull(openBudgetMs) {
                    previewEngine.state.first {
                        it == tv.own.owntv.player.LivePreviewEngine.State.PLAYING ||
                            it == tv.own.owntv.player.LivePreviewEngine.State.ERROR
                    }
                }
                if (terminal != null) break
                if (!isStill(channel)) return@launch
                val waits = previewEngine.providerBackOffsSpent
                // Nothing pending and no new wait since the last deadline → this really is a stuck open.
                if (previewEngine.providerBackOff.value == null && waits == waitsSeen) break
                waitsSeen = waits
                // The budget just spent was the panel's own countdown, not this channel failing to open,
                // so give it back — otherwise the whole-tune deadline would abandon a channel that is
                // simply queued behind a wait OwnTV agreed to.
                ladder.postponeDeadline(openBudgetMs)
            }
            if (!isStill(channel)) return@launch
            if (terminal == null) {
                val reason = "ExoPlayer never opened it (${openBudgetMs / 1000}s, no frame and no error)"
                advanceLadder(channel, reason)
                return@launch
            }
            if (terminal == tv.own.owntv.player.LivePreviewEngine.State.ERROR) {
                // onPlayerError assigns _state before _errorInfo, and this collector resumes inline on
                // Dispatchers.Main.immediate — so yield first, or the detail is always read as null.
                kotlinx.coroutines.yield()
                val info = previewEngine.errorInfo.value
                val reason = "ExoPlayer error before first frame: ${info?.raw ?: previewEngine.error.value}"
                advanceLadder(channel, reason)
                return@launch
            }
            // One unconditional line per tune saying whether ExoPlayer ever opened it. Without this a
            // support log shows the tune and then nothing at all, which reads identically whether the
            // channel played, wedged with the watchers still waiting, or the watcher itself never ran.
            engineLog("'${channel.name}' opened on ExoPlayer")
            ladderOpened()
            // PLAYING: give the track list a moment to settle, then route silent (undecodable-audio) streams to mpv.
            delay(300)
            if (!isStill(channel)) return@launch
            if (previewEngine.audioUnsupported.value) { advanceLadder(channel, "no decodable audio track"); return@launch }
            watchExoAfterFirstFrame(channel)
        }
    }

    /**
     * Keep watching a live channel that HAS opened, and hand it over if it then wedges for good.
     *
     * Everything above this is a first-frame check, so a stream that starts and dies used to be nobody's
     * problem: the engine's reconnect ladder took it from there, and that ladder is deliberately patient
     * — a dozen seconds to call a buffer a stall, then eight attempts backing off to 15 s each. Well over
     * two minutes of frozen picture behind a spinner before the honest "Lost connection" appears, and mpv
     * — which often plays the very same channel — was never given a turn. (Seen on a `.m3u8` that hands
     * out a few seconds of video and then nothing.)
     *
     * A brief re-buffer is not that: it is normal on live TV and the engine recovers by itself, so only a
     * stall that outlasts [EXO_STALL_HANDOFF_MS] counts. Nothing here fires while the channel is playing.
     */
    private suspend fun watchExoAfterFirstFrame(channel: ChannelEntity) {
        var lastLoggedMs = 0L
        while (isStill(channel)) {
            // Suspends for as long as the channel is healthy — LOADING here means buffering or reconnecting.
            val left = previewEngine.state.first { it != tv.own.owntv.player.LivePreviewEngine.State.PLAYING }
            if (!isStill(channel)) return
            if (left == tv.own.owntv.player.LivePreviewEngine.State.IDLE) return // stopped/zapped away — not our business
            // Throttled: a stream that re-buffers several times a second (the flap the engine's
            // [noteRebufferFlap] catches) would otherwise fill the log with this one line.
            val nowMs = android.os.SystemClock.elapsedRealtime()
            if (nowMs - lastLoggedMs >= STALL_LOG_THROTTLE_MS) {
                lastLoggedMs = nowMs
                engineLog("'${channel.name}' stopped playing (state=$left) — ${EXO_STALL_HANDOFF_MS / 1000}s to recover")
            }
            val recovered = if (left == tv.own.owntv.player.LivePreviewEngine.State.ERROR) null else
                kotlinx.coroutines.withTimeoutOrNull(EXO_STALL_HANDOFF_MS) {
                    previewEngine.state.first {
                        it != tv.own.owntv.player.LivePreviewEngine.State.LOADING
                    }
                }
            if (!isStill(channel)) return
            if (recovered == tv.own.owntv.player.LivePreviewEngine.State.PLAYING) continue // it came back — keep watching
            if (recovered == tv.own.owntv.player.LivePreviewEngine.State.IDLE) return
            kotlinx.coroutines.yield() // let onPlayerError finish assigning errorInfo (see the ERROR branch above)
            val reason = if (left == tv.own.owntv.player.LivePreviewEngine.State.ERROR || recovered != null) {
                "ExoPlayer gave up mid-stream: ${previewEngine.errorInfo.value?.raw ?: previewEngine.error.value}"
            } else {
                "played, then stalled for ${EXO_STALL_HANDOFF_MS / 1000}s without recovering"
            }
            advanceLadder(channel, reason)
            return
        }
    }

    private fun isStill(channel: ChannelEntity) =
        _liveOnExo.value && _previewChannel.value?.streamUrl == channel.streamUrl

    // ---- the fallback ladder ---------------------------------------------------------------------

    /** The ladder for the tune currently on screen. The order, the HLS filtering and the per-engine
     *  format lessons all live in [LiveLadder], which is unit-tested; everything with a side effect —
     *  the log, the failure record and the engine handoff itself — stays here. */
    private val ladder = LiveLadder()

    /** Reset the ladder for a fresh tune of [channel]. Rungs already climbed are forgotten — a new tune
     *  is a new chance, including for a channel that ended the last one on its final rung. */
    private suspend fun armLadder(channel: ChannelEntity, preference: tv.own.owntv.core.player.EnginePreference) {
        forceTsForExo = null
        ladder.arm(
            channel.streamUrl,
            preference,
            budgetMs = ladderBudgetMs.value,
            nowMs = android.os.SystemClock.elapsedRealtime(),
        ) { hasHlsAlternative(channel) }
        startLadderDeadline(channel)
    }

    /** The alarm that ends a tune which never opened. Cancelled the moment a channel does open. */
    private var ladderDeadlineJob: Job? = null

    /**
     * Wall-clock alarm for Settings → "Give up after", so the budget bounds the black screen itself rather
     * than only the decision to climb another rung.
     *
     * Checking the deadline at rung boundaries alone is not enough: a rung entered at 24 s with a 35 s
     * timeout of its own runs to 59 s before anyone asks the time. The alarm cuts in during the rung, so
     * "30 seconds" means thirty seconds to the person watching the screen — which is what makes the
     * setting built on top of it honest.
     *
     * The deadline is re-read on each pass instead of being captured once, so a provider back-off that
     * bought the tune more time ([LiveLadder.postponeDeadline]) moves this alarm with it.
     *
     * A channel that OPENS cancels this job outright ([ladderOpened]), so a stream that plays and later
     * stalls is never touched by it — that one belongs to [watchExoAfterFirstFrame], which is about
     * recovery, not about opening.
     */
    private fun startLadderDeadline(channel: ChannelEntity) {
        ladderDeadlineJob?.cancel()
        ladderDeadlineJob = viewModelScope.launch {
            while (ladder.owns(channel.streamUrl)) {
                val left = (ladder.deadlineAt() ?: return@launch) - android.os.SystemClock.elapsedRealtime()
                if (left <= 0) break
                delay(left)
            }
            // Both gates matter. The ladder can still "own" a channel the user has walked away from, and
            // this alarm stops the engine — on the mpv branch that is the shared full player, which by
            // then may be showing a film. It may only ever act on the channel still on screen.
            if (!ladder.owns(channel.streamUrl)) return@launch
            if (!isStill(channel) && !isStillOnMpv(channel)) return@launch
            val detail = "no picture within ${ladderBudgetMs.value / 1000}s of tuning"
            engineLog("'${channel.name}' — giving up: $detail")
            recordLadderEvent(tv.own.owntv.player.PlayerFailureReason.LIVE_NO_FALLBACK, channel, detail)
            exoOutcomeJob?.cancel()
            mpvOutcomeJob?.cancel()
            mpvHandoffJob?.cancel()
            abandonTune(channel, detail)
        }
    }

    /** The channel produced a picture — the opening budget has been met, so stand the alarm down. */
    private fun ladderOpened() {
        ladderDeadlineJob?.cancel()
        ladderDeadlineJob = null
    }

    /** Whether "Prefer HLS" actually rewrote this channel's URL, i.e. whether an HLS rung differs from a
     *  TS one at all. Stalker channels carry a portal cmd, so nothing was swapped there either. */
    private suspend fun hasHlsAlternative(channel: ChannelEntity): Boolean {
        val source = getSource(channel.sourceId)
        if (streamUrlResolver.needsResolve(source)) return false
        return channel.playStreamUrl(source) != channel.streamUrl
    }

    /**
     * Whether [reason] is the panel refusing the *request* rather than the stream failing: an
     * account-busy 458, a 403, a rate limit and the rest of [LiveStreamQuirks.isRequestRefusal].
     *
     * Such a refusal must not move the ladder and must not teach it anything. It is not a property of
     * the channel, the format or the engine — it is the account being busy, and it clears on its own.
     * Traced on a one-session panel: ten channels opened in 1.5 s by scrolling the list locked the
     * account for two minutes, three channels fell to their `.ts` rung during the lockout, and the app
     * concluded the provider had no HLS at all for the rest of the session.
     */
    private fun isRequestRefusal(reason: String): Boolean =
        tv.own.owntv.player.PlayerErrors.httpStatusIn(reason)
            ?.let { LiveStreamQuirks.isRequestRefusal(it) } == true

    /**
     * Move to the next untried rung after a failure on the current one, or return false when the ladder
     * is exhausted (the caller then leaves the failure on screen — there is genuinely nothing left).
     */
    private suspend fun advanceLadder(channel: ChannelEntity, reason: String): Boolean {
        if (!ladder.owns(channel.streamUrl)) return false // a newer tune owns the ladder now
        // Defensive second gate: whatever this rung is stepping away from, a panel that refused the
        // *request* has said nothing about stream format, so nothing may be learned from it. The primary
        // gate is at the call sites, which do not step at all on a refusal — this one exists so a path
        // added later cannot silently reintroduce the false lesson.
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val outOfTime = ladder.expired(nowMs)
        val next = ladder.advance(failureWasAboutFormat = !isRequestRefusal(reason), nowMs = nowMs) ?: run {
            val detail = if (outOfTime) {
                "$reason — gave up after ${ladderBudgetMs.value / 1000}s"
            } else {
                reason
            }
            engineLog("'${channel.name}' — no fallback left ($detail)")
            recordLadderEvent(tv.own.owntv.player.PlayerFailureReason.LIVE_NO_FALLBACK, channel, detail)
            abandonTune(channel, detail)
            return false
        }
        val label = ladder.label(next)
        engineLog("'${channel.name}' falling back to $label ($reason)")
        recordLadderEvent(tv.own.owntv.player.PlayerFailureReason.LIVE_FALLBACK, channel, "$label — $reason")
        if (next.onMpv) {
            // Detached on purpose. [fallbackToMpv] cancels [exoOutcomeJob] the moment mpv takes over —
            // but every automatic rung is dispatched from INSIDE that job (the audio/no-video/error
            // watchers and [watchExoAfterFirstFrame] all run there), so calling it inline meant the
            // handoff cancelled itself and died at its first suspension point: the shell had already
            // flipped to mpv's surface, mpv was never asked to load anything, and [watchMpvOutcome]
            // never armed. That is a permanent black screen whose diagnostics stop dead at
            // "starting mpv" — no mpv_load, no mpv error, no further rung. Running the handoff as a
            // sibling of the watcher instead of its child keeps the cancel meaning what it says.
            mpvHandoffJob?.cancel()
            mpvHandoffJob = viewModelScope.launch { fallbackToMpv(channel, reason, forceTs = !next.isHls) }
        } else {
            forceTsForExo = if (next.isHls) null else channel.streamUrl
            switchToExo(channel)
        }
        return true
    }

    /**
     * The ladder has nothing left for [channel] — either it ran out of rungs or it ran out of time. Put
     * the failure on screen.
     *
     * Without this the spinner simply stays up: the engine that owns the screen may have nothing to
     * report (a stream that opens its playlist and then delivers no segment produces no frame AND no
     * error), so the honest answer has to be written by whoever decided to stop trying. Whichever engine
     * is showing is the one told to give up, because that is the one the HUD is reading.
     */
    private fun abandonTune(channel: ChannelEntity, detail: String) {
        val reason = "'${channel.name}': $detail"
        if (_liveOnExo.value) previewEngine.abandon(reason) else player.abandonLive(reason)
    }

    /** Put [channel] on ExoPlayer, releasing mpv first when it currently holds the stream. mpv's stop is
     *  asynchronous, so on a one-session panel handing over too early makes us our own competitor. */
    private suspend fun switchToExo(channel: ChannelEntity) {
        clearTimeshift() // this restarts the channel at the live edge — the rewind is over
        if (!_liveOnExo.value) {
            player.stopAndAwaitRelease()
            delay(tv.own.owntv.player.OwnTVPlayer.SURFACE_HANDOFF_MS)
            if (_previewChannel.value?.streamUrl != channel.streamUrl) return
        }
        startOnExo(channel)
    }

    /** The in-flight automatic ExoPlayer→mpv handoff, so a newer ladder step supersedes an older one.
     *  Deliberately NOT a child of [exoOutcomeJob] — see the call site in [advanceLadder]. */
    private var mpvHandoffJob: Job? = null

    private suspend fun fallbackToMpv(channel: ChannelEntity, reason: String, forceTs: Boolean = false) {
        engineLog("starting mpv for '${channel.name}' — reason=$reason")
        clearTimeshift() // the live channel is being re-opened at the edge — the rewind is over
        // Preserve the format Exo actually discovered. Some panels redirect their advertised `.ts`
        // endpoint to HLS; handing that misleading URL to mpv traps FFmpeg at the manifest EOF.
        //
        // "Discovered" strictly means Exo asked for something that was *not* HLS and got HLS anyway.
        // Playing an `.m3u8` we deliberately requested teaches nothing about the `.ts` endpoint — and
        // recording it as a redirect was actively harmful, because [LiveStreamQuirks.rememberHlsRedirect]
        // keys by host: one "Prefer HLS" channel that fell back to mpv branded the entire panel as
        // "its `.ts` is really HLS". Turning Prefer HLS off in that same session then routed every plain
        // `.ts` into HlsMediaSource, which dies on the first bytes ("Input does not start with the
        // #EXTM3U header") — so every channel failed before its first frame and walked the ladder to mpv.
        val exoTuneUrl = previewEngine.currentUrl
        val exoDiscoveredHls = _liveOnExo.value && previewEngine.isHlsStream &&
            exoTuneUrl != null && !LiveStreamQuirks.isExplicitHlsUrl(exoTuneUrl)
        exoOutcomeJob?.cancel()             // mpv owns the channel now
        _liveOnExo.value = false            // shell flips to mpv's surface
        stalkerPreviewCmd = null
        previewEngine.stop()
        // Let ExoPlayer's decoder release before mpv inits. Measured at 508 ms per handoff on the owner's
        // Realtek box (C-F20) and deliberately kept: nothing exposes "the MediaCodec is released", so the
        // only alternative to waiting is guessing, and guessing short reproduces the 0x80001000 claim
        // failure these constants exist to prevent.
        delay(tv.own.owntv.player.OwnTVPlayer.SURFACE_HANDOFF_MS)
        if (_previewChannel.value?.streamUrl == channel.streamUrl) {
            val source = sourceById[channel.sourceId] ?: sourceDao.getById(channel.sourceId)
            // Stalker stores the portal cmd — resolve it to a real URL (create_link) before mpv plays.
            val isStalker = streamUrlResolver.needsResolve(source)
            val rawUrl = if (isStalker) {
                runCatching { streamUrlResolver.resolve(source!!, channel.streamUrl) }
                    .onFailure { Log.w(ENGINE_TAG, "stalker mpv resolve failed '${channel.name}'", it) }
                    .getOrNull() ?: return
            } else {
                channel.streamUrl
            }
            // Same "Prefer HLS" opt-out as [tuneUrl], but keyed to **mpv's own** verdict: ExoPlayer
            // failing this channel's `.m3u8` says nothing about whether mpv can play it, and on the
            // traced channel mpv plays exactly the manifest ExoPlayer cannot (see [Rung]).
            val preferredUrl = if (forceTs || LiveStreamQuirks.lacksHlsVariantMpv(channel.streamUrl)) rawUrl
                else resolveStreamUrl(rawUrl, source)
            // Record what Exo discovered against the *panel*, not this one channel: the redirect is a
            // property of the provider, so every later channel — and mpv's own option choices — start
            // out knowing this `.ts` is really HLS instead of re-learning it the slow way.
            if (exoDiscoveredHls) LiveStreamQuirks.rememberHlsRedirect(preferredUrl)
            val url = if (LiveStreamQuirks.isKnownHlsHost(preferredUrl)) {
                LiveStreamQuirks.toHlsUrl(preferredUrl)
            } else {
                preferredUrl
            }
            if (_previewChannel.value?.streamUrl != channel.streamUrl) return // zapped away while resolving
            // C-3: mpv is now the active engine — install/clear the reconnect provider to match.
            setStalkerReconnect(if (isStalker) channel.streamUrl else null)
            player.play(url, title = channel.name, subtitle = channelNumberLabel(channel), logoUrl = channel.displayLogoUrl, isLive = true, muted = false, userAgent = source?.userAgent, httpHeaders = channel.httpHeaders, contentKey = mpvPinKey(channel), livePrerollSecsOverride = prerollFor(channel.sourceId), liveBufferOverride = liveBufferFor(channel.sourceId))
            watchMpvOutcome(channel)
        } else {
            // The only way out of this function that leaves the shell on mpv's surface with nothing
            // loaded. Normal when the user zapped during the decoder-release wait; in a support log it
            // is the difference between "the handoff was abandoned" and "the handoff vanished".
            engineLog("mpv handoff for '${channel.name}' abandoned — the channel changed while ExoPlayer released")
        }
    }

    private var mpvOutcomeJob: Job? = null

    /**
     * Watch a channel mpv has just been handed, and take it to the next rung if mpv can't play it.
     *
     * mpv had no watcher at all before this: a channel pinned to "compatibility mode" whose stream mpv
     * couldn't open simply sat there, because every fallback in this file was written for the
     * ExoPlayer-first direction. That left the two combinations the ladder now covers — mpv on the
     * other format, and ExoPlayer — permanently out of reach for a pinned channel.
     *
     * "Opened" is a decoded picture (`videoRes`) or the spinner clearing, not `isPlaying`: mpv seeds
     * that flag `true` at load time, so it says nothing about whether the stream arrived. mpv runs its
     * own retry/format ladder internally first, so this deadline is deliberately looser than
     * ExoPlayer's.
     */
    private fun watchMpvOutcome(channel: ChannelEntity) {
        mpvOutcomeJob?.cancel()
        mpvOutcomeJob = viewModelScope.launch {
            val failure = kotlinx.coroutines.withTimeoutOrNull(MPV_OPEN_TIMEOUT_MS) {
                kotlinx.coroutines.flow.combine(player.videoRes, player.buffering, player.error) { res, buf, err ->
                    when {
                        err != null -> false to err         // mpv gave up
                        res != null || !buf -> true to null // a picture, or the spinner cleared: it opened
                        else -> null                        // still trying
                    }
                }.first { it != null }
            }
            if (!isStillOnMpv(channel)) return@launch
            val reason = when {
                failure == null -> "mpv never opened it (${MPV_OPEN_TIMEOUT_MS / 1000}s, no picture and no error)"
                failure.first -> { engineLog("'${channel.name}' opened on mpv"); ladderOpened(); return@launch }
                else -> "mpv couldn't play it: ${failure.second}"
            }
            advanceLadder(channel, reason)
        }
    }

    /** mpv's counterpart to [isStill] — still the same channel, and mpv still owns the screen. */
    private fun isStillOnMpv(channel: ChannelEntity) =
        !_liveOnExo.value && _previewChannel.value?.streamUrl == channel.streamUrl

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
    suspend fun catchupProgrammes(ch: ChannelEntity): List<tv.own.owntv.core.database.entity.EpgProgrammeEntity> =
        epgReader.catchupProgrammes(ch, custom.value, epgOffset.value, ctx.value.sourceIds)

    /** Full description for a programme picked in the catch-up dialog. The list query drops it to stay
     *  under the CursorWindow limit, so the detail popup fetches it on demand (same as the Guide). */
    suspend fun programmeDescription(programmeId: Long): String? = epgReader.programmeDescription(programmeId)

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

    /** Hand an archive programme to an external app (VLC, MX Player). No HUD, resume or engine toggle
     *  once it leaves, but external players cope with mid-GOP archive segments some providers serve. */
    fun playCatchupExternal(ch: ChannelEntity, programme: tv.own.owntv.core.database.entity.EpgProgrammeEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, ch.categoryId, profileDao, categoryDao)) return@launch
            val url = archiveUrls.forProgramme(ch, programme) ?: return@launch
            Log.i(ENGINE_TAG, "catch-up external '${ch.name}' prog='${programme.title}'")
            externalPlayerLauncher.launch(url, ch.name, programme.title)
            recordLiveHistory(ch, immediate = true)
        }
    }

    /** The archive programme on screen, for [continueAfterCatchup] to find the one after it. Null
     *  whenever the picture is not a fixed catch-up programme. */
    private var playingCatchupProgramme: tv.own.owntv.core.database.entity.EpgProgrammeEntity? = null

    /** Replay a past programme from the channel's archive (seekable, like the Guide's "Watch from start"). */
    fun playCatchupProgramme(ch: ChannelEntity, programme: tv.own.owntv.core.database.entity.EpgProgrammeEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, ch.categoryId, profileDao, categoryDao)) return@launch
            val url = archiveUrls.forProgramme(ch, programme) ?: return@launch
            val sourceUa = withContext(Dispatchers.IO) { sourceDao.getById(ch.sourceId)?.userAgent }
            // The archive URL shape decides whether ExoPlayer can take it at all (progressive .ts vs
            // .m3u8 vs an extension-less panel endpoint), so log it redacted — it's the first thing
            // needed when the HUD's engine toggle can't get a catch-up programme playing.
            Log.i(ENGINE_TAG, "catch-up '${ch.name}' prog='${programme.title}' -> ${tv.own.owntv.core.network.HttpClient.redactUrl(url)}")
            _previewChannel.value = ch
            clearTimeshift()                 // a fixed programme replaces any live rewind in progress
            _catchupActive.value = true      // HUD: VOD engine toggle, not the live compatibility toggle
            playingCatchupProgramme = programme // …and the anchor auto-play continues from
            clearLiveOnExo() // catch-up is a VOD-style archive on mpv, not the live ExoPlayer channel
            // isLive=false → seekable archive; isArchive → mid-GOP tolerant (hardware first, software rescue).
            player.play(url, title = ch.name, subtitle = programme.title, logoUrl = ch.displayLogoUrl, isLive = false, isArchive = true, userAgent = sourceUa, httpHeaders = ch.httpHeaders)
            // The picture is this programme's own airtime, not the present — drive the "watching" clock
            // from its start, exactly as the rewind path does from the archive's start. The offset stays
            // null: a fixed programme is not the live rewind, so the HUD keeps its VOD chrome.
            timeshift.followArchiveFrom(programme.startMs)
            // Watching a programme from a channel's archive is watching that channel — the external
            // catch-up path above has always recorded it, and this one silently did not, so the channel
            // never reached History or Recently watched when catch-up played in-app.
            recordLiveHistory(ch, immediate = true)
        }
    }

    /**
     * A catch-up programme played to its end and auto-play is on ("Auto-play next" in Settings, the
     * same switch that carries a series from one episode to the next). Continue down the guide instead
     * of leaving the black screen a finished archive used to leave.
     *
     * The player raises [OwnTVPlayer.archiveEnded] because it cannot know what "next" is — that lives
     * in the guide. [CatchupContinue] decides between the next programme, the live stream and stopping;
     * everything here is the lookup.
     *
     * Guarded on [_catchupActive] rather than on the remembered programme: that flag is the app's one
     * answer to "is a fixed archive programme on screen", and it is already cleared by every path that
     * puts something else there — tuning live, a rewind, leaving full-screen.
     */
    private fun continueAfterCatchup() {
        if (!_catchupActive.value) return
        val ch = _previewChannel.value ?: return
        val ended = playingCatchupProgramme ?: return
        viewModelScope.launch {
            val next = epgReader.programmeAfter(ch, ended.stopMs, custom.value, epgOffset.value, ctx.value.sourceIds)
            when (CatchupContinue.decide(next?.startMs, next?.stopMs, System.currentTimeMillis())) {
                is CatchupContinue.Next.Programme -> next?.let { playCatchupProgramme(ch, it) }
                CatchupContinue.Next.Live -> {
                    // Caught up with the present: the next programme IS what is on air, so tune it.
                    _catchupActive.value = false
                    ensurePlaying(ch)
                }
                CatchupContinue.Next.Stop -> Unit // no guide beyond here — stop, as before
            }
        }
    }

    // ---- Live rewind / timeshift: the view model's half ---------------------------------------------
    // The state machine is [LiveTimeshift] (declared near the top, beside the other extracted helpers).
    // What is left here is what only this view model can do: turn an offset into an archive URL and put
    // it on screen, and the HUD-facing wrappers that add the channel currently being previewed.

    /** How far behind live the picture is. Null at the live edge. */
    val timeshiftOffsetSec: StateFlow<Int?> = timeshift.offsetSec

    /** True when the channel on screen records an archive — the HUD then offers "Rewind" on live. */
    val canRewindLive: StateFlow<Boolean> =
        _previewChannel.map { it?.catchup == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Settings → Live rewind step (default 30 s), read live so a change applies without a restart. */
    private val rewindStepSec: StateFlow<Int> = settings.liveRewindStepSec
        .stateIn(
            viewModelScope, SharingStarted.Eagerly,
            tv.own.owntv.core.settings.SeekSteps.DEFAULT_LIVE_REWIND_STEP_SEC,
        )

    /** One press of the archive rewind/forward buttons. */
    fun rewindLive() = scrubLive(rewindStepSec.value)
    fun forwardLive() = scrubLive(-rewindStepSec.value)

    // ---- "Go back to…" — aim at a point in the archive instead of nudging toward it ----------------
    // The rewind button moves 30 s a press, so three hours back is a held key and a crawling counter.
    // These jump straight there. Same archive machinery, just a bigger offset, so nothing about how a
    // stream is opened changes.

    /** Offsets offered for [ch], nearest first. Empty when the channel has no archive. */
    fun catchupJumpOptions(ch: ChannelEntity): List<Int> = timeshift.jumpOptions(ch)

    /** Offsets for the channel on screen — the player HUD's "Go back to…" list. */
    fun currentJumpOptions(): List<Int> = _previewChannel.value?.let { catchupJumpOptions(it) } ?: emptyList()

    /** Archive depth of [ch] / of the channel on screen, in seconds — the bound the exact-time picker
     *  clamps its day and HH:MM wheels to. */
    fun catchupWindowOf(ch: ChannelEntity): Int = if (!ch.catchup) 0 else timeshift.windowSec(ch)
    fun currentCatchupWindowSec(): Int = _previewChannel.value?.let { catchupWindowOf(it) } ?: 0

    /** Jump the channel already on screen to [offsetSec] behind live. */
    fun jumpBackTo(offsetSec: Int) {
        val ch = _previewChannel.value ?: return
        if (!ch.catchup) return
        _catchupActive.value = false // a live rewind, not a fixed programme: the HUD keeps its live chrome
        timeshift.beginAt(ch, offsetSec)
    }

    /** Open [ch] straight into its archive at [offsetSec] behind live, from the browse list — the
     *  channel is not playing yet, so this also arms CH+/CH− and records the watch, exactly as tuning
     *  it live would. Without that the channel-list overlay and zapping stay dead for the session. */
    fun playCatchupAt(ch: ChannelEntity, offsetSec: Int) {
        if (!ch.catchup) return
        if (ch.categoryId != null && ch.categoryId in hiddenCategoryIds.value) return
        zapList.armFor(ch)
        _previewChannel.value = ch
        _catchupActive.value = false
        timeshift.beginAt(ch, offsetSec)
        recordLiveHistory(ch, immediate = true)
    }

    /** Move [deltaSec] further back (+) or toward live (−) into the archive (also drives the timeline
     *  scrubber). */
    fun scrubLive(deltaSec: Int) {
        val ch = _previewChannel.value ?: return
        timeshift.scrub(ch, deltaSec)
    }

    /** Drop every trace of a live rewind. Anything that puts the channel back on a real-time stream has
     *  to call this — see [LiveTimeshift.clear]. */
    private fun clearTimeshift() = timeshift.clear()

    /** Jump back to the real-time live edge (back on the fast ExoPlayer engine). */
    fun goToLive() {
        clearTimeshift()
        _previewChannel.value?.let { ensurePlaying(it) }
    }

    /**
     * Turn one point in [ch]'s archive into a playing stream — the "URL out" half of [LiveTimeshift],
     * and the only part of a rewind that needs this view model's collaborators.
     *
     * Returns false when no archive URL can be built, or when the user reached the live edge while it
     * was being resolved.
     */
    private suspend fun loadArchiveStream(ch: ChannelEntity, startMs: Long, offsetSec: Int): Boolean {
        val tz = withContext(Dispatchers.IO) { settings.resolveCatchupTimeZone() }
        val (url, sourceUa) = withContext(Dispatchers.IO) {
            val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
            archiveUrls.forTimeshift(ch, source, startMs, offsetSec, tz)?.let { it to source.userAgent }
        } ?: return false
        if (timeshift.offsetSec.value == null) return false // user jumped back to live meanwhile
        // Keep the rewind instant semantic. The player HUD formats it with the current
        // localized context, so an in-session locale switch updates an already-visible subtitle.
        _previewChannel.value = ch
        clearLiveOnExo() // archive plays as a VOD-style mpv stream, not the live ExoPlayer channel
        player.play(
            url = url,
            title = ch.name,
            logoUrl = ch.displayLogoUrl,
            isArchive = true,
            userAgent = sourceUa,
            httpHeaders = ch.httpHeaders,
            rewindStartMs = startMs,
        )
        return true
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
        ladderOpened() // nothing is tuning any more; the opening alarm must not fire on a torn-down channel
        previewEngine.stop()
        player.stop()
        _previewChannel.value = null
    }

    /**
     * The programme currently airing on each of [channels] (channel id → title), looked up in ONE batch
     * against the stored bulk guide — same query the Home "On Now" rail uses. This powers the small
     * "current programme" subtitle under each channel row in the Live list and the in-player channel
     * overlay. Only the stored guide is consulted (no per-channel short-EPG API calls): a channel with no
     * guide simply has no entry here, and the row shows no second line. Returns only channels that
     * actually have something airing right now.
     */
    suspend fun nowPlayingFor(channels: List<ChannelEntity>): Map<Long, String> =
        epgReader.nowPlayingFor(channels, custom.value, epgOffset.value)

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, ChannelEntity> =
        livePagingSource(
            key = key,
            profileId = c.profileId,
            sourceIds = c.sourceIds,
            query = query,
            sort = sort,
            channelDao = channelDao,
            customCategoryDao = customCategoryDao,
            contextKey = { folderContextKeys.value[it] },
            hasManualOrder = { it in orderedContexts.value },
        )

    fun enterMoveMode(channel: ChannelEntity, key: LiveKey) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val contextKey = when (key) {
                is LiveKey.Folder -> folderContextKeys.value[key.id] ?: return@launch
                is LiveKey.Custom -> key.id
                LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                else -> return@launch
            }
            val items = when (key) {
                is LiveKey.Folder -> channelDao.snapshotByCategoryManual(key.id, pid, contextKey, 5000)
                is LiveKey.Custom -> customCategoryDao.snapshotChannels(pid, key.id, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                LiveKey.Favorites -> channelDao.snapshotFavoritesManual(pid, contextKey, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                // Catch-up joins History/All as a computed view: it has no stored order to move within.
                LiveKey.History, LiveKey.All, LiveKey.Catchup -> return@launch
            }
            val idx = items.indexOfFirst { it.id == channel.id }
            if (idx < 0) return@launch
            _moveState.value = ChannelMoveState(items, idx, contextKey)
            // Manual order is only visible in playlist order, so Move switches the list to it — but that
            // is a means, not a choice the user made. Remember what they had so Cancel can put it back.
            sortBeforeMove = sortMode.value
            settings.setSortLive(SettingsRepository.SortMode.PLAYLIST)
        }
    }

    /** The sort the user was on before [enterMoveMode] switched the list to playlist order. */
    private var sortBeforeMove: SettingsRepository.SortMode? = null

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
        sortBeforeMove = null // the new order IS playlist order — staying on it is the point
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

    fun cancelMove() {
        _moveState.value = null
        // Cancel means nothing changed — including the sort Move switched away from.
        val previous = sortBeforeMove ?: return
        sortBeforeMove = null
        if (previous != SettingsRepository.SortMode.PLAYLIST) {
            viewModelScope.launch { settings.setSortLive(previous) }
        }
    }

    fun removeFromHistory(channelId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            historyDao.remove(pid, MediaType.LIVE, channelId)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx, hiddenCats: Set<Long>): Flow<Int> =
        liveCountFlow(key, c.profileId, c.sourceIds, hiddenCats, channelDao, customCategoryDao)

    private companion object {
        const val ENGINE_TAG = "LiveEngine"

        /** How long a channel must stay tuned before it counts as watched — see [recordLiveHistory]. */
        const val HISTORY_DEBOUNCE_MS = 5_000L
        const val TAG = "OwnTVHome"
        // In-player channel lists: one category is normally far smaller, but cap the uncategorized
        // → All Channels fallback so a huge playlist can't be pulled into memory on every tune.
        const val ZAP_LIST_LIMIT = 2_000

        /** How long a CH+/- step waits before it actually opens the stream. Comfortably longer than the
         *  ~170 ms D-pad repeat, so a held key resolves to one tune, and short enough that a single
         *  deliberate step still feels immediate. */
        const val ZAP_TUNE_DELAY_MS = 500L
        const val HISTORY_LIST_LIMIT = 30
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, icon = OwnTVIcon.FAVORITE),
            LiveRailItem(LiveKey.History, icon = OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All),
        )

        /** [defaultRail] with the Catch-up entry inserted before All when [hasCatchup]. */
        fun railWithCatchup(hasCatchup: Boolean): List<LiveRailItem> =
            if (!hasCatchup) defaultRail
            // CATCHUP (a TV with a replay loop), not EPG or a calendar: this rail is the guide-free
            // route, and a calendar next to Favorites/History would read as "schedule" — the one thing
            // it deliberately is not.
            else defaultRail.dropLast(1) + LiveRailItem(LiveKey.Catchup, icon = OwnTVIcon.CATCHUP) + defaultRail.last()
        const val ZAP_WINDOW_HALF = 50 // channels loaded on each side of the tuned channel for CH+/-
        /** How long ExoPlayer gets to reach a first frame (or an error) before the channel goes to mpv,
         *  *on top of* any requested pre-buffer. Past this it is not slow, it is stuck — see
         *  [watchExoOutcome].
         *
         *  Was 25s while a channel could buffer forever without starting; the engine now calls that in
         *  about four seconds and fails the load ([LivePreviewEngine.openWatchdog]), so the only thing
         *  left to wait for is a genuinely slow panel. Still not 5s: a 4K channel on a distant panel
         *  legitimately spends several seconds on the first segment plus decoder setup, and bouncing those
         *  off the faster engine costs more than the extra seconds save. */
        const val EXO_OPEN_TIMEOUT_MS = 12_000L
        /**
         * How long a channel that HAS played may stay stalled before it goes to mpv — see
         * [watchExoAfterFirstFrame].
         *
         * DERIVED from the engine's own death verdict rather than chosen independently. The two numbers
         * had drifted badly apart: this was a flat 30s while the engine calls a stalled feed dead at 12s
         * and reconnects at 13.5s, so the handoff sat through two of the engine's own reconnect attempts
         * — fifteen seconds of frozen picture spent waiting for a recovery that had already been tried
         * and failed.
         *
         * The grace on top is one reconnect's worth: the engine's first retry gets a fair chance to
         * actually open before the channel is offered to the other player. Sitting through a SECOND one
         * is what this no longer does.
         */
        val EXO_STALL_HANDOFF_MS =
            tv.own.owntv.player.LivePreviewEngine.DEATH_VERDICT_MS + STALL_HANDOFF_GRACE_MS

        /** Grace on top of the engine's own verdict: room for the engine's first reconnect to
         *  open, not for a second one to be attempted. */
        private const val STALL_HANDOFF_GRACE_MS = 2_000L

        /** How long mpv gets to produce a picture before the channel moves to the next rung of the
         *  ladder — see [watchMpvOutcome]. Looser than ExoPlayer's: mpv runs its own open watchdog
         *  (10s) plus a retry and a format alternate underneath this one, and cutting in before that
         *  finishes would throw away attempts that often succeed. */
        const val MPV_OPEN_TIMEOUT_MS = 35_000L

        /** Minimum gap between two "stopped playing" lines for the same tune (see [watchExoAfterFirstFrame]). */
        private const val STALL_LOG_THROTTLE_MS = 5_000L
    }
}
