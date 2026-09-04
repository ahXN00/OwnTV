@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.asItemSnapshotListFlow
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.customize.BulkRenameSession
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.SpanSelector
import tv.own.owntv.core.customize.moveBlock
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/** One item row in the Customize items screen. */
data class CustomizeItemRow(
    val key: String,
    val itemId: Long,
    val originalName: String,
    val displayName: String,
    val hidden: Boolean,
    val renamed: Boolean,
)

/**
 * Drives the category-items screen (reached from Customize by pressing OK on a category name).
 * Paged over the category's items, with hide/show, rename (Live only), and reorder support.
 */
class CustomizeItemsViewModel(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val contentOrderDao: ContentOrderDao,
    private val customCategoryDao: CustomCategoryDao,
    private val customize: CustomizationStore,
) : ViewModel() {

    private data class Ctx(val profileId: Long, val sources: List<tv.own.owntv.core.database.entity.SourceEntity>) {
        fun sourceIdsFor(type: MediaType): List<Long> = ActiveProfileSources(profileId, sources).sourceIdsFor(type)
    }

    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps -> Ctx(aps.profileId, aps.sources) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    /** The category whose items are shown. Set via [open], cleared via [close]. [categoryId] is null
     *  for a user's custom combined category (issue #87) — its items live in the membership table. */
    data class CatInfo(
        val categoryId: Long?,
        val contextKey: String,
        val mediaType: MediaType,
        val sourceIds: List<Long>,
    ) {
        /** True when this is a user-created combined category (issue #87), keyed "custom:<uuid>". */
        val isCustom: Boolean get() = CustomizeKeys.isCustom(contextKey)
    }

    private val _catInfo = MutableStateFlow<CatInfo?>(null)
    val catInfo: StateFlow<CatInfo?> = _catInfo.asStateFlow()

    private val _visibilityFilter = MutableStateFlow(CustomizeVisibilityFilter.ALL)
    val visibilityFilter: StateFlow<CustomizeVisibilityFilter> = _visibilityFilter.asStateFlow()

    fun open(info: CatInfo) {
        span.cancel() // a stale span from a previous category must never carry over
        _visibilityFilter.value = CustomizeVisibilityFilter.ALL
        _catInfo.value = info
    }

    fun close() {
        span.cancel()
        _catInfo.value = null
    }

    /** Whether this category has manual-order rows (fast-path switch, same as LiveViewModel). */
    private val hasOrder: StateFlow<Boolean> = combine(ctx, _catInfo) { c, ci -> c to ci }
        .flatMapLatest { (c, ci) ->
            if (c.profileId < 0 || ci == null) flowOf(false)
            else contentOrderDao.observeContextKeys(c.profileId, ci.mediaType)
                .map { keys -> ci.contextKey in keys }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Customizations for the current section (drives hidden state + display names of items). */
    private val customizeForItems: StateFlow<tv.own.owntv.core.customize.SectionCustomizations> =
        combine(ctx, _catInfo) { c, ci -> c to ci }
            .flatMapLatest { (c, ci) ->
                if (c.profileId < 0 || ci == null) flowOf(tv.own.owntv.core.customize.SectionCustomizations())
                else customize.observe(c.profileId, ci.mediaType)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.customize.SectionCustomizations())

    /** Category display name (from customizations, or the provider name stored on the category). */
    val categoryName: StateFlow<String> = combine(ctx, _catInfo, customizeForItems) { c, ci, cust ->
        if (ci == null) ""
        else cust.categoryNames[ci.contextKey] ?: "" // will be set from outside (the row's displayName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Paged items in this category, with customizations applied (display names, hidden state). */
    private val allItems: Flow<PagingData<CustomizeItemRow>> = combine(_catInfo, hasOrder, customizeForItems) { ci, ordered, cust ->
        Triple(ci, ordered, cust)
    }.flatMapLatest { (ci, ordered, cust) ->
        if (ci == null) flowOf(PagingData.empty())
        else {
            Pager(PagingConfig(pageSize = 60)) { pagingSource(ci.categoryId, ci, ordered) }
                .flow
                .map { pagingData ->
                    pagingData.map { entity ->
                        mapToRow(entity, ci.mediaType, cust)
                    }
                }
        }
    }.cachedIn(viewModelScope)

    val items: Flow<PagingData<CustomizeItemRow>> = combine(allItems, _visibilityFilter) { pagingData, filter ->
        pagingData.filter { filter.accepts(it.hidden) }
    }.cachedIn(viewModelScope)

    fun setVisibilityFilter(filter: CustomizeVisibilityFilter) {
        _visibilityFilter.value = filter
        span.cancel()
    }

    /** Currently loaded rows (the paged list) — the span machinery computes anchors/ends against
     *  it. The anchor and end of any span are always rows the user pressed on, hence loaded. */
    private val loadedRows: StateFlow<List<CustomizeItemRow>> = items
        .asItemSnapshotListFlow()
        .map { it.items }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Span selection (shared machinery, see SpanSelector.kt) ---

    private val span = SpanSelector(
        getRows = { loadedRows.value },
        getKey = { it.key },
        scope = viewModelScope,
    )
    val rangeAnchorKey: StateFlow<String?> = span.anchorKey
    val rangeMode: StateFlow<SpanSelector.Mode> = span.mode
    val rangeEndKey: StateFlow<String?> = span.endKey
    val rangeSelectedKeys: StateFlow<Set<String>> = span.selectedKeys

    fun beginRange(row: CustomizeItemRow) = span.beginRange(row.key)
    fun beginMoveRange(row: CustomizeItemRow) = span.beginMoveRange(row.key)
    fun cancelRange() = span.cancel()
    fun keysInRange(endRow: CustomizeItemRow): List<String>? = span.keysInRange(endRow)

    /** Hide/show every item in the span in ONE atomic customization edit. */
    fun applyRange(endRow: CustomizeItemRow, hidden: Boolean) {
        val keys = span.keysInRange(endRow) ?: return
        val ci = _catInfo.value ?: return
        viewModelScope.launch {
            val labels = loadedRows.value.filter { it.key in keys }.associate { it.key to it.originalName }
            customize.setItemsHidden(ctx.value.profileId, ci.mediaType, labels, hidden)
        }
        span.cancel()
    }

    // --- Moves (persist to content_order via replaceContext — the browse Move-mode pattern) ---

    /** Moves serialize so a fast second press snapshots the result of the first, never the stale
     *  pre-move order. */
    private val moveMutex = Mutex()

    /** Snapshot of the whole category in current display order: (itemId, stable key) pairs.
     *  Null when the category can't be snapshotted (EPISODE or no profile). Custom categories (#87)
     *  snapshot the membership join (same rail-order query as the browse screens). */
    private suspend fun orderedSnapshot(ci: CatInfo): List<Pair<Long, String>>? {
        val pid = ctx.value.profileId
        if (pid < 0) return null
        if (ci.isCustom) {
            return when (ci.mediaType) {
                MediaType.LIVE -> customCategoryDao.snapshotChannels(pid, ci.contextKey, ci.sourceIds, SNAPSHOT_LIMIT)
                    .map { it.id to CustomizeKeys.channel(it) }
                MediaType.MOVIE -> customCategoryDao.snapshotMovies(pid, ci.contextKey, ci.sourceIds, SNAPSHOT_LIMIT)
                    .map { it.id to CustomizeKeys.movie(it) }
                MediaType.SERIES -> customCategoryDao.snapshotSeries(pid, ci.contextKey, ci.sourceIds, SNAPSHOT_LIMIT)
                    .map { it.id to CustomizeKeys.series(it) }
                MediaType.EPISODE -> null
            }
        }
        return when (ci.mediaType) {
            MediaType.LIVE -> channelDao.snapshotByCategoryManual(ci.categoryId!!, pid, ci.contextKey, SNAPSHOT_LIMIT)
                .map { it.id to CustomizeKeys.channel(it) }
            MediaType.MOVIE -> movieDao.snapshotByCategoryManual(ci.categoryId!!, pid, ci.contextKey, SNAPSHOT_LIMIT)
                .map { it.id to CustomizeKeys.movie(it) }
            MediaType.SERIES -> seriesDao.snapshotByCategoryManual(ci.categoryId!!, pid, ci.contextKey, SNAPSHOT_LIMIT)
                .map { it.id to CustomizeKeys.series(it) }
            MediaType.EPISODE -> null
        }
    }

    /** Writes the full resulting order for this context (the Move commit point). */
    private suspend fun persistOrder(ci: CatInfo, orderedIds: List<Long>) {
        val pid = ctx.value.profileId
        contentOrderDao.replaceContext(
            profileId = pid,
            type = ci.mediaType,
            contextKey = ci.contextKey,
            rows = orderedIds.mapIndexed { i, itemId ->
                ContentOrderEntity(profileId = pid, mediaType = ci.mediaType, contextKey = ci.contextKey, itemId = itemId, position = i)
            },
        )
    }

    private suspend fun runMove(ci: CatInfo, block: (List<Pair<Long, String>>) -> List<Long>?) {
        moveMutex.withLock {
            val snapshot = orderedSnapshot(ci) ?: return
            val reordered = block(snapshot) ?: return
            persistOrder(ci, reordered)
        }
    }

    /** Moves a single item one step up/down and persists the resulting order. */
    fun move(row: CustomizeItemRow, up: Boolean) {
        val ci = _catInfo.value ?: return
        viewModelScope.launch {
            runMove(ci) { snapshot ->
                val index = snapshot.indexOfFirst { it.second == row.key }
                if (index < 0) return@runMove null
                moveBlock(snapshot, index, index, if (up) MoveKind.UP else MoveKind.DOWN)?.map { it.first }
            }
        }
    }

    /** Jumps a single item straight to the top or bottom and persists the resulting order. */
    fun moveToEdge(row: CustomizeItemRow, top: Boolean) {
        val ci = _catInfo.value ?: return
        viewModelScope.launch {
            runMove(ci) { snapshot ->
                val index = snapshot.indexOfFirst { it.second == row.key }
                if (index < 0) return@runMove null
                moveBlock(snapshot, index, index, if (top) MoveKind.TOP else MoveKind.BOTTOM)?.map { it.first }
            }
        }
    }

    /** Moves the whole span (anchor..end) as one block. Called from any row's arrows while a move
     *  span is active; the block stays selected for repeated moves. */
    fun moveRange(endRow: CustomizeItemRow, kind: MoveKind) {
        val ci = _catInfo.value ?: return
        val anchorKey = span.anchorKey.value ?: return
        val endKey = span.endKey.value ?: endRow.key
        viewModelScope.launch {
            runMove(ci) { snapshot ->
                val anchorIndex = snapshot.indexOfFirst { it.second == anchorKey }
                val endIndex = snapshot.indexOfFirst { it.second == endKey }
                if (anchorIndex < 0 || endIndex < 0) return@runMove null
                span.setEndKey(endKey)
                moveBlock(snapshot, minOf(anchorIndex, endIndex), maxOf(anchorIndex, endIndex), kind)?.map { it.first }
            }
        }
    }

    private companion object {
        /** Same bound the browse screens' Move mode uses. */
        const val SNAPSHOT_LIMIT = 5000
    }

    /** Returns the right Room PagingSource for this media type, with or without manual-order join.
     *  Custom categories (#87) always take the membership join — it LEFT JOINs content_order itself,
     *  so [ordered] doesn't matter there. */
    private fun pagingSource(categoryId: Long?, info: CatInfo, ordered: Boolean): PagingSource<Int, *> {
        if (info.isCustom) {
            return when (info.mediaType) {
                MediaType.LIVE -> customCategoryDao.pagingChannels(ctx.value.profileId, info.contextKey, info.sourceIds)
                MediaType.MOVIE -> customCategoryDao.pagingMovies(ctx.value.profileId, info.contextKey, info.sourceIds)
                MediaType.SERIES -> customCategoryDao.pagingSeries(ctx.value.profileId, info.contextKey, info.sourceIds)
                MediaType.EPISODE -> error("EPISODE not applicable to category items")
            }
        }
        return when (info.mediaType) {
            MediaType.LIVE -> if (ordered) {
                channelDao.pagingByCategoryManual(categoryId!!, ctx.value.profileId, info.contextKey)
            } else {
                channelDao.pagingByCategory(categoryId!!)
            }
            MediaType.MOVIE -> if (ordered) {
                movieDao.pagingByCategoryManual(categoryId!!, ctx.value.profileId, info.contextKey)
            } else {
                movieDao.pagingByCategory(categoryId!!)
            }
            MediaType.SERIES -> if (ordered) {
                seriesDao.pagingByCategoryManual(categoryId!!, ctx.value.profileId, info.contextKey)
            } else {
                seriesDao.pagingByCategory(categoryId!!)
            }
            MediaType.EPISODE -> error("EPISODE not applicable to category items")
        }
    }

    /** Maps one entity (any media type) to [CustomizeItemRow] with customizations applied. */
    private fun mapToRow(
        entity: Any,
        mediaType: MediaType,
        cust: tv.own.owntv.core.customize.SectionCustomizations,
    ): CustomizeItemRow {
        val name: String
        val itemId: Long
        val key: String
        when (mediaType) {
            MediaType.LIVE -> {
                val ch = entity as tv.own.owntv.core.database.entity.ChannelEntity
                name = ch.name
                itemId = ch.id
                key = CustomizeKeys.channel(ch)
            }
            MediaType.MOVIE -> {
                val m = entity as tv.own.owntv.core.database.entity.MovieEntity
                name = m.name
                itemId = m.id
                key = CustomizeKeys.movie(m)
            }
            MediaType.SERIES -> {
                val s = entity as tv.own.owntv.core.database.entity.SeriesEntity
                name = s.name
                itemId = s.id
                key = CustomizeKeys.series(s)
            }
            MediaType.EPISODE -> error("EPISODE not applicable")
        }
        return CustomizeItemRow(
            key = key,
            itemId = itemId,
            originalName = name,
            displayName = cust.itemNames[key] ?: name,
            hidden = key in cust.hiddenItems,
            renamed = key in cust.itemNames,
        )
    }

    // --- Mutations ---

    fun setItemHidden(row: CustomizeItemRow, hidden: Boolean) {
        val ci = _catInfo.value ?: return
        viewModelScope.launch {
            customize.setItemHidden(ctx.value.profileId, ci.mediaType, row.key, row.originalName, hidden)
        }
    }

    /** Rename an item (Live TV channels only — Movies/Series use bulk rename in Phase 2). */
    fun renameItem(row: CustomizeItemRow, name: String?) {
        val ci = _catInfo.value ?: return
        viewModelScope.launch {
            customize.renameItem(ctx.value.profileId, ci.mediaType, row.key, name)
        }
    }

    // --- bulk rename (issue #86) ---

    /**
     * One-shot bulk-rename flow for the current category's items. Every accepted rename lands in ONE
     * [CustomizationStore] write; restore clears only the selected keys' entries.
     */
    val bulk = BulkRenameSession(
        scope = viewModelScope,
        persist = { renames ->
            val ci = _catInfo.value
            if (ci != null) customize.applyBulkRenames(ctx.value.profileId, ci.mediaType, renames)
        },
        restore = { keys ->
            val ci = _catInfo.value
            if (ci != null) customize.clearItemNames(ctx.value.profileId, ci.mediaType, keys)
        },
        existingNames = { selectedKeys ->
            val ci = _catInfo.value
            val pid = ctx.value.profileId
            if (ci == null || pid < 0) emptySet<String>()
            else customize.observe(pid, ci.mediaType).first().itemNames
                .filterKeys { it !in selectedKeys }
                .values.toSet() + loadedRows.value.filter { it.key !in selectedKeys }.map { it.originalName }
        },
    )

    fun beginRenameRange(row: CustomizeItemRow) = span.beginRenameRange(row.key)

    /**
     * Ends the RENAME span at [endRow] and opens the bulk flow over the spanned rows' ORIGINAL
     * names. Returns null when no span is active — the caller then opens the single-row rename.
     */
    fun finishRenameRange(endRow: CustomizeItemRow): List<String>? {
        val keys = span.finishRenameRange(endRow.key) ?: return null
        val byKey = loadedRows.value.associateBy { it.key }
        bulk.start(keys.mapNotNull { byKey[it]?.let { row -> row.key to row.originalName } })
        return keys
    }

    /**
     * Bulk-renames the WHOLE category — the ✎ Rename items / ✨ Auto cleanup pills (Movies/Series).
     * Snapshots every provider name in the category (manual-order aware, same DAO path as moves) and
     * opens the flow; with [autocleanup] the preset rules are applied straight away.
     */
    fun bulkRenameAll(autocleanup: Boolean) {
        val ci = _catInfo.value ?: return
        val pid = ctx.value.profileId
        if (pid < 0) return
        viewModelScope.launch {
            val entries: List<Pair<String, String>> = when {
                ci.isCustom -> when (ci.mediaType) {
                    MediaType.LIVE -> customCategoryDao.snapshotChannels(pid, ci.contextKey, ci.sourceIds, SNAPSHOT_LIMIT)
                        .map { CustomizeKeys.channel(it) to it.name }
                    MediaType.MOVIE -> customCategoryDao.snapshotMovies(pid, ci.contextKey, ci.sourceIds, SNAPSHOT_LIMIT)
                        .map { CustomizeKeys.movie(it) to it.name }
                    MediaType.SERIES -> customCategoryDao.snapshotSeries(pid, ci.contextKey, ci.sourceIds, SNAPSHOT_LIMIT)
                        .map { CustomizeKeys.series(it) to it.name }
                    MediaType.EPISODE -> return@launch
                }
                else -> when (ci.mediaType) {
                    MediaType.LIVE -> channelDao.snapshotByCategoryManual(ci.categoryId!!, pid, ci.contextKey, SNAPSHOT_LIMIT)
                        .map { CustomizeKeys.channel(it) to it.name }
                    MediaType.MOVIE -> movieDao.snapshotByCategoryManual(ci.categoryId!!, pid, ci.contextKey, SNAPSHOT_LIMIT)
                        .map { CustomizeKeys.movie(it) to it.name }
                    MediaType.SERIES -> seriesDao.snapshotByCategoryManual(ci.categoryId!!, pid, ci.contextKey, SNAPSHOT_LIMIT)
                        .map { CustomizeKeys.series(it) to it.name }
                    MediaType.EPISODE -> return@launch
                }
            }
            if (entries.isEmpty()) return@launch
            bulk.start(entries)
            if (autocleanup) bulk.autoCleanup()
        }
    }

    // --- Move to… (issue #87) ---

    /** The user's custom categories (with member counts) minus THIS category — the "Move to…"
     *  dialog's list. A category can't contain itself. */
    val moveTargets: StateFlow<List<MoveTarget>> = combine(ctx, _catInfo, customizeForItems) { c, ci, cust ->
        Triple(c, ci, cust)
    }.flatMapLatest { (c, ci, cust) ->
        if (c.profileId < 0 || ci == null || cust.customCategories.isEmpty()) flowOf(emptyList())
        else {
            val targets = cust.customCategories.map { it.id }.filterNot { it == ci.contextKey }
            if (targets.isEmpty()) flowOf(emptyList())
            else customCategoryDao.observeCountsByContexts(
                c.profileId,
                ci.mediaType,
                targets,
                ci.sourceIds.ifEmpty { listOf(-1L) },
            )
                .map { counts ->
                    targets.map { id ->
                        MoveTarget(
                            id = id,
                            displayName = cust.categoryNames[id] ?: cust.customCategories.first { it.id == id }.name,
                            count = counts.firstOrNull { it.contextKey == id }?.count ?: 0,
                        )
                    }
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates a custom category (issue #87) — the Move dialog's "＋ New category…" flow. */
    fun createCustomCategory(name: String) {
        val ci = _catInfo.value ?: return
        viewModelScope.launch {
            customize.createCustomCategory(ctx.value.profileId, ci.mediaType, name)
        }
    }

    /**
     * Moves (or copies, [keepInOrigin]) [row] into a custom category (issue #87). The origin is the
     * category this screen is showing: a provider folder marks the item in movedFromOrigin so its
     * browse pager drops it, a custom category deletes its membership row, and Favorites deletes the
     * favorite row. The item always stays in All/search/recent.
     */
    fun moveTo(row: CustomizeItemRow, targetId: String, keepInOrigin: Boolean) {
        val ci = _catInfo.value ?: return
        if (targetId == ci.contextKey) return
        viewModelScope.launch {
            val pid = ctx.value.profileId
            customCategoryDao.appendItem(pid, ci.mediaType, targetId, row.itemId)
            if (!keepInOrigin) {
                when {
                    // Custom origin → drop the membership row (the item leaves THIS category).
                    CustomizeKeys.isCustom(ci.contextKey) ->
                        customCategoryDao.deleteItem(pid, ci.mediaType, ci.contextKey, row.itemId)
                    // Provider-folder origin → mark it moved-out; the browse pager then drops the
                    // item from that folder while keeping it in All/search/recent. (Favorites can't
                    // be an origin here — this screen is only opened from Customize's category rows.)
                    else -> customize.setItemMovedFromOrigin(pid, ci.mediaType, row.key, ci.contextKey, moved = true)
                }
            }
        }
    }
}
