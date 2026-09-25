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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import tv.own.owntv.core.live.StreamGrant
import tv.own.owntv.core.recording.RecordingSchedule
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.customize.CategoryMove
import tv.own.owntv.core.customize.CategoryRailEditor
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.railCategories
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
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SourceOverrides
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.player.LiveExoWatchdog
import tv.own.owntv.player.LiveProgramme
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
import tv.own.owntv.core.live.CatchupContinue

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
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val xtreamClient: XtreamClient,
    private val customize: CustomizationStore,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
    /** Shared with the Guide (T18): one now/next cache, so an invalidation reaches both screens. */
    private val epgReader: LiveEpgReader,
    val player: OwnTVPlayer,
    val previewEngine: tv.own.owntv.player.LivePreviewEngine,
    private val forceMpvStore: tv.own.owntv.core.player.ForceMpvStore,
    private val contentOrderDao: ContentOrderDao,
    private val customCategoryDao: CustomCategoryDao,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
    private val recordings: tv.own.owntv.core.recording.RecordingManager,
) : ViewModel() {

    // --- "Record what I'm watching" (Plan D, D3 mode b) -----------------------------------------

    /**
     * The row opened for the channel currently being recorded off the player's own stream, or null.
     *
     * Held here rather than derived from the table because this recording belongs to *this* playing
     * stream: it ends when the stream does, and nothing in the database says which stream that was.
     */
    private val _playerRecording =
        MutableStateFlow<tv.own.owntv.core.database.entity.RecordingEntity?>(null)

    val playerRecording: StateFlow<tv.own.owntv.core.database.entity.RecordingEntity?> = _playerRecording

    /**
     * Start or stop recording the channel on screen, from its start time of *now*.
     *
     * **This fetches the channel itself rather than copying the open stream, and that is deliberate.**
     * The original design tapped mpv's `stream-record`, which costs no second connection — but that
     * copies bytes as they pass through mpv's stream layer, and an HLS channel never puts them there:
     * FFmpeg's `hls` demuxer opens each segment on its own. mpv accepted the instruction
     * (`Set property: stream-record="…" -> 1`) and wrote nothing, every time, on every HLS channel —
     * which on a normal IPTV playlist is all of them. Proven on the television's own mpv log.
     *
     * So it goes through the same engine as a scheduled recording, and inherits its behaviour: it
     * costs one of the playlist's connections, it **keeps running** when the channel is changed or the
     * player is left, and the Recordings screen can stop it. The connection is why
     * [RecordingManager.canRecordOn] is asked first — a refusal is a sentence, not a failed recording.
     */
    fun togglePlayerRecording(channel: ChannelEntity) {
        viewModelScope.launch {
            val open = _playerRecording.value
            if (open != null) {
                recordings.stop(open)
                _playerRecording.value = null
                return@launch
            }
            val pid = currentProfileId() ?: return@launch
            _playerRecording.value = startRecordingNow(channel, pid, nowNext.value?.now)
        }
    }

    /**
     * Record this channel from now, whether or not it is the one on screen.
     *
     * The guide's Record needs a programme to record, so a channel the provider publishes no guide
     * for cannot be recorded from there at all — and those are exactly the channels a user is most
     * likely to want kept. This is the same recording by another door: long-press the channel in the
     * list. With a programme on screen it ends when that programme does; with none it runs for
     * [RecordingSchedule.NO_GUIDE_RUNTIME_MINUTES] and is stopped from Downloads → Live TV.
     */
    fun recordNow(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            // The programme is only known for the channel the guide pane is showing; from the list
            // this is a channel like any other, so its own now-programme is looked up here.
            startRecordingNow(channel, pid, epgReader.nowNext(channel, custom.value, epgOffset.value)?.now)
        }
    }

    private suspend fun startRecordingNow(
        channel: ChannelEntity,
        profileId: Long,
        programme: tv.own.owntv.core.parser.XtEpgEntry?,
    ): tv.own.owntv.core.database.entity.RecordingEntity? {
        if (recordings.canRecordOn(channel.sourceId) !is StreamGrant.Allowed) return null
        val startMs = System.currentTimeMillis()
        // Start now: the programme is already under way and a live edge cannot be rewound, so the
        // pre-roll has nothing to reach back to. The end is the programme's own, padding included.
        val stopMs = programme
            ?.let { recordings.windowFor(it.startMs, it.stopMs).last }
            ?.takeIf { it > startMs }
            ?: (startMs + RecordingSchedule.NO_GUIDE_RUNTIME_MINUTES * 60_000L)
        return recordings.schedule(
            tv.own.owntv.core.database.entity.RecordingEntity(
                profileId = profileId,
                sourceId = channel.sourceId,
                channelId = channel.id,
                channelName = channel.name,
                channelIconUrl = channel.logoUrl,
                epgChannelId = channel.epgChannelId,
                streamUrl = channel.streamUrl,
                httpHeaders = channel.httpHeaders,
                title = programme?.title ?: channel.name,
                description = programme?.description,
                programmeStartMs = programme?.startMs ?: startMs,
                programmeStopMs = programme?.stopMs ?: stopMs,
                startMs = startMs,
                stopMs = stopMs,
            ),
        )
    }


    data class ChannelMoveState(val items: List<ChannelEntity>, val activeIndex: Int, val contextKey: String)
    private val _moveState = MutableStateFlow<ChannelMoveState?>(null)
    val moveState: StateFlow<ChannelMoveState?> = _moveState.asStateFlow()

    /** Hide and reorder a category from the rail — the same editor Settings → Customize writes through. */
    private val categoryEditor = CategoryRailEditor(customize, categoryDao, profileDao)

    private val _categoryMoveState = MutableStateFlow<CategoryMove?>(null)
    val categoryMoveState: StateFlow<CategoryMove?> = _categoryMoveState.asStateFlow()

    val livePreviewEnabled: StateFlow<Boolean> = settings.livePreviewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
    // Full sources by id — lets the synchronous play() paths tell a Stalker source (needs play-time
    // create_link resolution) from M3U/Xtream (final URL already stored) without a DB round-trip.
    private var sourceById: Map<Long, tv.own.owntv.core.database.entity.SourceEntity> = emptyMap()

    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps ->
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

    /** The same candidate set the Guide's picker uses — filtered by no source. */
    private val guideCandidates = tv.own.owntv.core.epg.GuideCandidates(epgDao)

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
        // N4 — a channel without catch-up rewinds into its own saved copy. Forwarded, because the
        // controller is built further down.
        local = object : LiveTimeshift.Local {
            override fun windowSec(ch: ChannelEntity): Int? = live.localRewind.windowSec(ch)
            override fun depthSec(): Int = live.localRewind.depthSec()
            override fun watchingWallMs(): Long? = live.localRewind.watchingWallMs()
            override fun liveEdgeWallMs(): Long? = live.localRewind.liveEdgeWallMs()
            override fun seek(wallMs: Long?) = live.localRewind.seek(wallMs)
        },
    )

    /** "Watching" clock: the wall-clock instant actually on screen. During archive playback the HUD
     *  clock alone is misleading — it reads 10:00 while the picture is yesterday at 13:00. Null
     *  whenever the picture IS the present (live edge, or a movie/episode). */
    val watchingWallMs: StateFlow<Long?> = timeshift.watchingWallMs


    /** Now/next for the focused channel, fetched (debounced) — each answer with the id of the channel it
     *  was looked up for, so [zapGuide] can tell a stale one. [nowNext] is the answer alone. */
    private val keyedNowNext: StateFlow<Pair<Long?, EpgNowNext?>> = combine(_previewChannel, epgRefresh) { ch, tick -> ch to tick }
        .debounce(350)
        .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second }
        .mapLatest { (ch, _) -> ch?.id to ch?.let { epgReader.nowNext(it, custom.value, epgOffset.value) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to null)

    val nowNext: StateFlow<EpgNowNext?> = keyedNowNext.map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * N3 — now/next for the channel-change banner. Null until the answer is for the channel actually on
     * screen: [nowNext] is debounced, so for a moment after a zap it still holds the previous channel's
     * programme, and the banner would otherwise name the new channel over the old one's show.
     */
    val zapGuide: StateFlow<EpgNowNext?> = combine(keyedNowNext, _previewChannel) { (id, epg), ch ->
        epg.takeIf { id != null && id == ch?.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        .mapLatest { ch ->
            val id = ch?.categoryId ?: return@mapLatest null
            delay(150) // a quick scroll cancels this before the lookup runs
            categoryDao.getById(id)?.name
        }
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
        .onEach { epgReader.clearCache(); epgRefresh.value++; clearNowPlaying() }
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
                    originKey == ContentOrderEntity.FAV_CONTEXT -> userDataWriter.removeFavorite(pid, MediaType.LIVE, itemId)
                    CustomizeKeys.isCustom(originKey) -> userDataWriter.removeCustomCategoryMember(pid, MediaType.LIVE, originKey, itemId)
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
                val folders = cats.railCategories(
                    cust,
                    kids = profile?.isKids == true,
                    alphaRest = sort == SettingsRepository.SortMode.ALPHA,
                )
                val multiSourceNames = c.sourceNames.takeIf { it.size > 1 }.orEmpty()
                val categoriesById = cats.associateBy { it.id }
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
        viewModelScope.launch {
            categoryEditor.hide(currentProfileId() ?: return@launch, MediaType.LIVE, key)
        }
    }

    fun enterCategoryMoveMode(key: LiveKey) {
        if (key !is LiveKey.Folder && key !is LiveKey.Custom) return
        viewModelScope.launch {
            val c = ctx.first { it.profileId >= 0 }
            _categoryMoveState.value = categoryEditor.beginMove(
                profileId = c.profileId,
                sourceIds = c.sourceIds,
                type = MediaType.LIVE,
                alphaRest = sortMode.value == SettingsRepository.SortMode.ALPHA,
                key = key,
            )
        }
    }

    fun moveCategoryUp() {
        _categoryMoveState.value = _categoryMoveState.value?.moved(MoveKind.UP) ?: return
    }

    fun moveCategoryDown() {
        _categoryMoveState.value = _categoryMoveState.value?.moved(MoveKind.DOWN) ?: return
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
    fun currentEpgMatch(channel: ChannelEntity): String? = custom.value.epgMatchResolver.epgIdFor(channel)

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
    suspend fun availableEpgChannels(channelName: String, query: String): List<tv.own.owntv.core.epg.GuideCandidate> {
        if (currentProfileId() == null) return emptyList()
        return guideCandidates.forPicker(channelName, query)
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

    /**
     * Set while this view model is serving More -> Favourites or More -> History rather than the
     * browse section. Those screens are one folder each: the selection is theirs to fix, and it must
     * not be persisted as the remembered category.
     */
    private var lockedKey: LiveKey? = null

    /** What the browse section was showing before the pin, so [unlock] can put it back. */
    private var previousKey: LiveKey? = null

    fun lock(key: LiveKey) {
        if (lockedKey == null) previousKey = _selected.value
        lockedKey = key
        _selected.value = key
    }

    /**
     * Release the pin and put the browse section back where it was.
     *
     * **This is not optional bookkeeping.** On the television this view model is a single instance
     * shared between the browse section and the More screens, so a pin that outlived the screen that
     * took it froze the section's category rail — focusable, but every click a no-op. The screen that
     * locks therefore unlocks on dispose.
     */
    fun unlock() {
        val previous = previousKey ?: return
        lockedKey = null
        previousKey = null
        _selected.value = previous
    }

    fun select(key: LiveKey) {
        if (lockedKey != null) return
        _selected.value = key
    }

    init {
        // Persist the selected category (debounced — the rail fires select() on focus as you scroll).
        viewModelScope.launch {
            _selected.drop(1).debounce(800).distinctUntilChanged().collect {
                if (lockedKey == null) settings.setLastLiveCategory(it.serialize())
            }
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


    /**
     * Live routing, the fallback ladder, the ExoPlayer ⇄ mpv handovers, the Stalker reconnect link and
     * the preview pane's engine — core's, the same code the phone runs. What stays here is the
     * television's own: which channel is on screen, the zap list, history, catch-up and the HUD state.
     */
    private val live = tv.own.owntv.player.LiveTuneController(
        scope = viewModelScope,
        engines = tv.own.owntv.player.EnginePair(previewEngine, player),
        host = tv.own.owntv.player.LiveTuneController.CoreHost(
            context = appContext,
            settings = settings,
            sourceDao = sourceDao,
            resolver = streamUrlResolver,
            forceMpvStore = forceMpvStore,
            label = ::channelNumberLabel,
            // The warm playlist map, so a preview on a focus step never waits on the database.
            sourceLookup = { id -> sourceById[id] },
            // A handover re-opens the channel at its live edge: the rewind is over.
            backToLiveEdge = { clearTimeshift() },
        ),
    )

    // True while the in-pane preview is suppressed because the provider allows one stream at a time and
    // that stream is already in use (full-screen playback). The pane says so instead of sitting silently
    // dead — otherwise "no preview" looks like a broken channel (F31).
    val previewBlockedSingleSession: StateFlow<Boolean> = live.previewBlockedSingleSession

    /** In-pane preview playback (no history) — triggered by the UI after the focus settles. */
    fun playPreview(channel: ChannelEntity) {
        if (channel.categoryId != null && channel.categoryId in hiddenCategoryIds.value) return
        // Core keeps the preview off a promoted full-screen stream, re-mutes on a re-focus instead of
        // reloading, mints a Stalker link, and holds back on a one-session panel while mpv plays.
        live.preview(channel, muted = !livePreviewAudio.value)
    }

    // --- Multiview: channels kept from the browse screen ------------------------------------------
    // The plan's second entry point: pick two to four channels from the Live list, then press play and
    // the grid opens already filled. Held here rather than in the shell because the context menu that
    // fills it and the player that empties it are two different screens.
    private val _multiviewSelection = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val multiviewSelection: StateFlow<List<ChannelEntity>> = _multiviewSelection.asStateFlow()

    /** Keep [channel] for the grid, up to [limit] tiles. Adding one twice does nothing. */
    fun addToMultiview(channel: ChannelEntity, limit: Int) {
        val current = _multiviewSelection.value
        if (current.any { it.id == channel.id } || current.size >= limit) return
        _multiviewSelection.value = current + channel
    }

    fun clearMultiviewSelection() {
        _multiviewSelection.value = emptyList()
    }

    /** The playlist a channel came from, so a caller can ask what it allows (Multiview's tile budget). */
    fun sourceOf(channel: ChannelEntity): SourceEntity? = sourceById[channel.sourceId]

    /** The channel's own headers plus its playlist's Referer, unless the channel names one itself. */
    private fun headersFor(channel: ChannelEntity): String? =
        SourceOverrides.headersWithReferer(channel.httpHeaders, sourceById[channel.sourceId])

    /**
     * Tune [channel] into a Multiview tile's own engine.
     *
     * Deliberately routed through here rather than built in the grid: which URL a channel actually
     * plays is not a property of the channel row. It is the playlist's pre-buffer override, its live
     * latency, its User-Agent, the channel's own headers and DRM, and — for Stalker — a `cmd` that
     * has to be resolved to a link per play. All of that already lives here, and a second copy in the
     * grid would drift the first time one of them changed.
     */
    fun tuneTile(engine: tv.own.owntv.player.LivePreviewEngine, channel: ChannelEntity, muted: Boolean) {
        viewModelScope.launch { live.playTile(engine, channel, muted) }
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
    val liveOnExo: StateFlow<Boolean> = live.liveOnExo

    // Apply the "Preview audio" toggle to a preview that is ALREADY playing — it used to take effect only
    // on the next tune, so turning it off left the current channel audible until focus moved. Never while
    // promoted to full-screen (_liveOnExo): that stream is meant to be heard.
    // Declared HERE, below _liveOnExo/previewEngine: viewModelScope is Main.immediate, so this collector
    // starts inline during construction and must not touch a property initialized further down the class.
    init {
        viewModelScope.launch {
            livePreviewAudio.collect { on ->
                if (!liveOnExo.value && previewEngine.currentUrl != null) previewEngine.setMuted(!on)
            }
        }
        viewModelScope.launch { player.archiveEnded.collect { continueAfterCatchup() } }
        // T17 / decision 5: on a 2 GB TV the preview pane decodes at most 720p — a second full-size
        // decoder re-tuning on every focus step is the heaviest background cost there. Lifted the moment
        // the channel goes full-screen, so the promoted stream switches up to its full variant.
        if (tv.own.owntv.core.player.PlayerBudget.of(appContext).lowSpec) {
            viewModelScope.launch {
                liveOnExo.collect { fullScreen -> previewEngine.setMaxVideoHeight(if (fullScreen) null else PREVIEW_MAX_HEIGHT) }
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
        live.detach() // the stream stays; its watchers and the give-up alarm stand down
        // Leaving full-screen ends the rewind: the archive stream is torn down with the player, and the
        // 1 Hz "behind live" ticker would otherwise keep running against nothing for the rest of the session.
        clearTimeshift()
        // With the in-pane preview enabled, the preview pane re-takes the ExoPlayer engine on the next
        // focus and re-applies the preview mute — so we can leave it running here. But when live preview
        // is OFF, nothing ever re-takes it, and the engine would keep decoding the (unmuted) channel's
        // audio in the background after exit. Stop it so leaving fullscreen actually silences the stream.
        if (!livePreviewEnabled.value) live.stopExo()
    }

    fun clearLiveOnExo() {
        // A live tune still in flight would otherwise start over whatever takes mpv now.
        live.cancelTune()
        live.releaseForArchive()
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
            zapList.directTune(currentChannel.id, tuned) { live.launch { playChannel(it) }.join() }

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
                httpHeaders = SourceOverrides.headersWithReferer(channel.httpHeaders, source),
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
        // T6 — the controller's one tune job: a quicker second pick cancels the first wherever it has
        // got to, so two picks can no longer finish in the wrong order.
        live.launch { playChannel(channel) }
    }

    private suspend fun getSource(sourceId: Long): tv.own.owntv.core.database.entity.SourceEntity? =
        sourceById[sourceId] ?: sourceDao.getById(sourceId)

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
        // Core routes it (DRM, the pin, a learned panel refusal, the playlist, the setting), arms the
        // ladder and starts the engine — the same code the phone runs.
        live.start(channel, getSource(channel.sourceId))
        recordLiveHistory(channel)
    }

    /** HUD "compatibility mode" toggle: flip the current channel's engine and pin the choice. */
    fun toggleForceMpv() {
        // A catch-up archive is loaded, not the live stream: re-tuning here would replace the programme
        // the user is watching with the channel's CURRENT one. The HUD hides the toggle then too.
        if (_catchupActive.value) return
        // Same while rewound into the live archive: swapping engines re-opens the channel at the edge.
        // Not for a copy saved on this device (N4): the other engine continues it at the same moment.
        if (timeshift.isRewound && live.localTimeshift.value == null) return
        live.toggleEngine()
    }

    /**
     * N2 — tune the channel watched before this one (the remote's "last channel" key). Re-read by id, so
     * a channel removed by a sync is skipped, and only from a playlist this profile has active;
     * [playChannel] applies the adult filter. It is a deliberate pick, so it opens at once.
     */
    /** [previousChannel]'s target when it belongs to this profile's active playlists — the HUD button
     *  shows only while this is non-null. */
    val previousChannelTarget: StateFlow<ChannelEntity?> = combine(live.previousChannel, ctx) { p, c ->
        p?.takeIf { it.sourceId in c.sourceIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousChannel() {
        val previous = live.previousChannel.value ?: return
        viewModelScope.launch {
            val channel = withContext(Dispatchers.IO) { channelDao.getById(previous.id) } ?: return@launch
            if (channel.sourceId !in ctx.value.sourceIds) return@launch
            zapList.armFor(channel)
            ensurePlaying(custom.value.itemNames[CustomizeKeys.channel(channel)]?.let { channel.copy(name = it) } ?: channel)
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

    /**
     * No archive URL could be built for what the user just picked.
     *
     * Every one of these paths used to `return@launch` in silence, so pressing "Watch from start" or
     * "Go back to…" on a provider that cannot serve the archive did *nothing at all* — no picture, no
     * message, nothing to tell the difference between a broken app and a provider without a recording.
     * The Guide has said [EpgMatchSummary.CatchupUnavailable] all along; Live TV now says it too.
     */
    private val _catchupUnavailable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val catchupUnavailable: SharedFlow<Unit> = _catchupUnavailable.asSharedFlow()

    /** Hand an archive programme to an external app (VLC, MX Player). No HUD, resume or engine toggle
     *  once it leaves, but external players cope with mid-GOP archive segments some providers serve. */
    fun playCatchupExternal(ch: ChannelEntity, programme: tv.own.owntv.core.database.entity.EpgProgrammeEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, ch.categoryId, profileDao, categoryDao)) return@launch
            val url = archiveUrls.forProgramme(ch, programme) ?: run {
                _catchupUnavailable.tryEmit(Unit)
                return@launch
            }
            Log.i(ENGINE_TAG, "catch-up external '${ch.name}' prog='${programme.title}'")
            // The same identity the in-app catch-up opens with: playlist User-Agent, the channel's own
            // headers and the playlist's Referer — a provider that checks them checks the archive too.
            externalPlayerLauncher.launch(
                url, ch.name, programme.title,
                userAgent = sourceById[ch.sourceId]?.userAgent,
                httpHeaders = headersFor(ch),
            )
            recordLiveHistory(ch, immediate = true)
        }
    }

    /** The archive programme on screen, for [continueAfterCatchup] to find the one after it. Null
     *  whenever the picture is not a fixed catch-up programme. */
    private var playingCatchupProgramme: tv.own.owntv.core.database.entity.EpgProgrammeEntity? = null

    /** Replay a past programme from the channel's archive (seekable, like the Guide's "Watch from start"). */
    fun playCatchupProgramme(ch: ChannelEntity, programme: tv.own.owntv.core.database.entity.EpgProgrammeEntity) {
        // The controller's one tune job: a live tune still resolving is cancelled rather than starting
        // over the programme a moment later.
        live.launch {
            val pid = currentProfileId() ?: return@launch
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, ch.categoryId, profileDao, categoryDao)) return@launch
            val url = archiveUrls.forProgramme(ch, programme) ?: run {
                _catchupUnavailable.tryEmit(Unit)
                return@launch
            }
            val sourceUa = withContext(Dispatchers.IO) { sourceDao.getById(ch.sourceId)?.userAgent }
            // The archive URL shape decides whether ExoPlayer can take it at all (progressive .ts vs
            // .m3u8 vs an extension-less panel endpoint), so log it redacted — it's the first thing
            // needed when the HUD's engine toggle can't get a catch-up programme playing.
            Log.i(ENGINE_TAG, "catch-up '${ch.name}' prog='${programme.title}' -> ${tv.own.owntv.core.network.HttpClient.redactUrl(url)}")
            _previewChannel.value = ch
            clearTimeshift()                 // a fixed programme replaces any live rewind in progress
            _catchupActive.value = true      // HUD: VOD engine toggle, not the live compatibility toggle
            playingCatchupProgramme = programme // …and the anchor auto-play continues from
            releaseForArchive() // catch-up is a VOD-style archive on mpv, not the live ExoPlayer channel
            // isLive=false → seekable archive; isArchive → mid-GOP tolerant (hardware first, software rescue).
            player.play(url, title = ch.name, subtitle = programme.title, logoUrl = ch.displayLogoUrl, isLive = false, isArchive = true, userAgent = sourceUa, httpHeaders = headersFor(ch))
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

    /** True when the channel on screen records an archive, or is saved on this device (N4) — the HUD
     *  then offers "Rewind" on live. */
    val canRewindLive: StateFlow<Boolean> =
        combine(_previewChannel, live.localTimeshift) { ch, copy -> ch?.catchup == true || (ch != null && copy != null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** N4 — the user came back to a channel whose copy was kept: where they were, for "Resume from
     *  buffer / Go live". Null when there is nothing to offer. */
    val timeshiftResumeAt: StateFlow<Long?> = live.localTimeshift.map { it?.resumeAtWallMs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun resumeTimeshift() {
        val at = timeshiftResumeAt.value ?: return
        live.dismissResumeOffer()
        live.seekTimeshift(at)
    }

    fun dismissTimeshiftResume() = live.dismissResumeOffer()

    /** N4 — the wall-clock holes in the saved copy on screen, for the rewind bar. */
    fun timeshiftGaps(): List<LongRange> = live.localGaps()

    /** N4 — the channel on screen plays from its saved copy. */
    val onLocalCopy: StateFlow<Boolean> = live.localTimeshift.map { it != null }
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
    fun catchupWindowOf(ch: ChannelEntity): Int = if (!timeshift.canRewind(ch)) 0 else timeshift.windowSec(ch)
    fun currentCatchupWindowSec(): Int = _previewChannel.value?.let { catchupWindowOf(it) } ?: 0

    /** Jump the channel already on screen to [offsetSec] behind live. */
    fun jumpBackTo(offsetSec: Int) {
        val ch = _previewChannel.value ?: return
        if (!timeshift.canRewind(ch)) return
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
    private fun clearTimeshift() { timeshift.clear() }

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
        val (url, sourceUa) = withContext(Dispatchers.IO) {
            val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
            val tz = settings.resolveCatchupTimeZone(source)
            archiveUrls.forTimeshift(ch, source, startMs, offsetSec, tz)?.let { it to source.userAgent }
        } ?: run {
            // The rewind hands back to the live edge from here (see LiveTimeshift.scheduleLoad), which
            // on its own looks exactly like the button doing nothing. Say why.
            _catchupUnavailable.tryEmit(Unit)
            return false
        }
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
            httpHeaders = headersFor(ch),
            rewindStartMs = startMs,
        )
        return true
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteIds.value.contains(channel.id)) {
                userDataWriter.removeFavorite(pid, MediaType.LIVE, channel.id)
            } else {
                favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }
        }
    }

    fun stopPreview() {
        live.stop() // both engines, every watcher and the give-up alarm, and the reconnect link
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

    /**
     * Current programme title per channel id, shared by the Live list and the in-player overlays.
     *
     * Bounded and least-recently-used: any screen may add to it, and none of them prunes on another's
     * behalf. An earlier version had each caller drop everything outside its own list, which made the
     * Live list and the channel-list overlay evict each other's work every time one opened.
     */
    private val nowPlayingCache = object : LinkedHashMap<Long, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?) = size > MAX_NOW_PLAYING
    }

    private val _nowPlaying = MutableStateFlow<Map<Long, String>>(emptyMap())

    /**
     * Channel id → the programme airing now.
     *
     * Channels that were asked about and have no guide are held internally as a blank, so they are
     * not asked again on every append; that sentinel is filtered out here, so such a row simply has
     * no entry and draws no second line.
     */
    val nowPlaying: StateFlow<Map<Long, String>> = _nowPlaying
        .map { titles -> titles.filterValues { it.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var nowPlayingJob: kotlinx.coroutines.Job? = null

    private fun publishNowPlaying(resolved: Map<Long, String>) {
        synchronized(nowPlayingCache) {
            nowPlayingCache.putAll(resolved)
            _nowPlaying.value = HashMap(nowPlayingCache)
        }
    }

    private fun cachedNowPlaying(): Map<Long, String> = synchronized(nowPlayingCache) { HashMap(nowPlayingCache) }

    /** Forget every resolved title — the guide data or the offset moved underneath them. */
    private fun clearNowPlaying() {
        synchronized(nowPlayingCache) { nowPlayingCache.clear() }
        _nowPlaying.value = emptyMap()
    }

    /**
     * Fill in "now playing" for [loaded], querying **only what is missing**.
     *
     * The Live screen used to key a producer on the whole paging snapshot, so every appended page
     * re-asked for every channel already on screen, and a 60-second timer then repeated that for the
     * full set. Scrolled deep into a large category that is a dozen chunked queries a minute to learn
     * almost nothing new. The phone has always done it this way; this is the television adopting it.
     */
    fun ensureNowPlaying(loaded: List<ChannelEntity>) {
        if (loaded.isEmpty()) return
        val known = cachedNowPlaying()
        val missing = loaded.filter { it.id !in known }
        if (missing.isEmpty()) return
        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            val found = runCatching { nowPlayingFor(missing) }.getOrDefault(emptyMap())
            // Remember the ones with no guide too, as a blank — otherwise they are re-queried on
            // every single append, which is most of the cost this removes.
            publishNowPlaying(found + missing.filter { it.id !in found }.associate { it.id to "" })
        }
    }

    /**
     * Re-read [loaded] because the programmes have turned over.
     *
     * Called on the **minute boundary** by the screen, not 60 seconds after it opened: a programme
     * changes on the minute, so a timer started from mount shows rows changing up to 59 seconds late
     * and at different instants from one another.
     */
    fun refreshNowPlaying(loaded: List<ChannelEntity>) {
        if (loaded.isEmpty()) return
        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            val found = runCatching { nowPlayingFor(loaded) }.getOrDefault(emptyMap())
            publishNowPlaying(found + loaded.filter { it.id !in found }.associate { it.id to "" })
        }
    }

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
            userDataWriter.removeHistory(pid, MediaType.LIVE, channelId)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx, hiddenCats: Set<Long>): Flow<Int> =
        liveCountFlow(key, c.profileId, c.sourceIds, hiddenCats, channelDao, customCategoryDao)

    private companion object {
        const val ENGINE_TAG = "LiveEngine"

        /** How long a channel must stay tuned before it counts as watched — see [recordLiveHistory]. */
        const val HISTORY_DEBOUNCE_MS = 5_000L
        /** The preview pane's video ceiling on a 2 GB TV (T17). */
        const val PREVIEW_MAX_HEIGHT = 720
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
        /**
         * How long a channel that HAS played may stay stalled before it goes to mpv — see
         * [LiveExoWatchdog].
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

        /** How long mpv gets to produce a picture before the channel moves to the next rung of the
         *  ladder — see [watchMpvOutcome]. Looser than ExoPlayer's: mpv runs its own open watchdog
         *  (10s) plus a retry and a format alternate underneath this one, and cutting in before that
         *  finishes would throw away attempts that often succeed. */
        const val MPV_OPEN_TIMEOUT_MS = 35_000L

        /** Resolved "now playing" titles kept at once. Big enough for a large category plus the
         *  overlays that share the map, small enough that it cannot grow with the catalogue. */
        const val MAX_NOW_PLAYING = 2_000

    }
}
