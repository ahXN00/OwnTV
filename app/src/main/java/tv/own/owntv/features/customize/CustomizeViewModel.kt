@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/** One category row in the Customize screen (hidden rows stay visible here, marked, for unhiding).
 *  [categoryId] is null for a user's custom combined category (issue #87) — there is no provider
 *  row behind it. [providerName] is null when only one source is in scope. */
data class CustomizeCatRow(
    val key: String,
    val categoryId: Long?,
    val originalName: String,
    val displayName: String,
    val hidden: Boolean,
    val renamed: Boolean,
    val providerName: String? = null,
)

enum class CustomizeVisibilityFilter {
    ALL,
    VISIBLE,
    HIDDEN;

    fun accepts(hidden: Boolean): Boolean = when (this) {
        ALL -> true
        VISIBLE -> !hidden
        HIDDEN -> hidden
    }
}

/**
 * Drives Settings → Customize: per-profile hide / rename / reorder of categories (Live/Movies/Series)
 * and the unhide list for hidden Live channels. All edits live in [CustomizationStore] (DataStore),
 * so they survive re-syncs and never touch the DB schema.
 */
class CustomizeViewModel(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val profileDao: tv.own.owntv.core.database.dao.ProfileDao,
    private val customCategoryDao: CustomCategoryDao,
    private val contentOrderDao: ContentOrderDao,
    private val customize: CustomizationStore,
) : ViewModel() {

    /** Moves serialize so a fast second press snapshots the result of the first, never the stale
     *  pre-move order. */
    private val moveMutex = Mutex()

    private data class Ctx(val profileId: Long, val sources: List<tv.own.owntv.core.database.entity.SourceEntity>) {
        fun sourceIdsFor(type: MediaType): List<Long> = ActiveProfileSources(profileId, sources).sourceIdsFor(type)
    }

    // Observe the active profile's sources reactively so adding/removing a playlist refreshes the
    // customize lists immediately. Goes through activeProfileSources (like the Browse screens) so the
    // chosen "active playlist" filter also applies HERE: with playlist A selected you only see A's
    // categories, not B's. "All playlists" (default -1) still shows the merged set. Per-section Off
    // flags also hide that source's categories for the selected section.
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps -> Ctx(aps.profileId, aps.sources) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    /** Optional PIN lock on this screen (per profile). [loaded]=false while DataStore is still read,
     *  so the screen never flashes unlocked content before the lock state is known. */
    data class PinLock(val loaded: Boolean = false, val pin: String? = null)

    val pinLock: StateFlow<PinLock> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(PinLock(loaded = true))
            else settings.customizePin(c.profileId).map { PinLock(loaded = true, pin = it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PinLock())

    /** Set (or with null: remove) the PIN lock for this profile. */
    fun setPin(pin: String?) {
        val pid = ctx.value.profileId
        if (pid < 0) return
        viewModelScope.launch { settings.setCustomizePin(pid, pin) }
    }

    private val _section = MutableStateFlow(MediaType.LIVE)
    val section: StateFlow<MediaType> = _section.asStateFlow()

    private val _visibilityFilter = MutableStateFlow(CustomizeVisibilityFilter.ALL)
    val visibilityFilter: StateFlow<CustomizeVisibilityFilter> = _visibilityFilter.asStateFlow()

    fun setVisibilityFilter(filter: CustomizeVisibilityFilter) {
        _visibilityFilter.value = filter
        span.cancel()
    }

    fun selectSection(type: MediaType) {
        _section.value = type
        // A pending range belongs to the section it was started in — switching sections cancels it.
        span.cancel()
    }

    /** Categories of the selected section, in their customized order, including hidden ones. */
    val rows: StateFlow<List<CustomizeCatRow>> = combine(_section, ctx, _visibilityFilter) { s, c, filter ->
        Triple(s, c, filter)
    }.flatMapLatest { (type, c, filter) ->
            if (c.profileId < 0) flowOf(emptyList())
            else {
                val sourceIds = c.sourceIdsFor(type)
                combine(
                    categoryDao.observe(sourceIds, type),
                    customize.observe(c.profileId, type),
                    sourceDao.observeForProfile(c.profileId),
                    profileDao.observeById(c.profileId),
                ) { cats, cust, sources, profile ->
                    val providerNames = sources.associate { it.id to it.name }.takeIf { sourceIds.size > 1 }
                    val orderIndex = cust.categoryOrder.withIndex().associate { (i, k) -> k to i }
                    // Provider folders + the user's custom combined categories (#87) in ONE list, so
                    // hide/rename/reorder apply uniformly (their keys share the CustomizeKeys
                    // namespace). Custom rows carry categoryId = null and no provider name.
                    val visibleCustomCategories = if (profile?.isKids == true) {
                        cust.customCategories.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) }
                    } else cust.customCategories
                    val visibleProviderCategories = if (profile?.isKids == true) {
                        cats.filterNot { tv.own.owntv.core.content.AdultCategoryClassifier.isAdult(it.name) }
                    } else cats
                    val entries = visibleCustomCategories.map { cc ->
                        CustomizeCatRow(
                            key = cc.id,
                            categoryId = null,
                            originalName = cc.name,
                            displayName = cust.categoryNames[cc.id] ?: cc.name,
                            hidden = cc.id in cust.hiddenCategories,
                            renamed = cc.id in cust.categoryNames,
                            providerName = "Custom",
                        )
                    } + visibleProviderCategories.map { cat ->
                        val key = CustomizeKeys.category(cat)
                        CustomizeCatRow(
                            key = key,
                            categoryId = cat.id,
                            originalName = cat.name,
                            displayName = cust.categoryNames[key] ?: cat.name,
                            hidden = key in cust.hiddenCategories,
                            renamed = key in cust.categoryNames,
                            providerName = providerNames?.get(cat.sourceId),
                        )
                    }
                    val (pinned, rest) = entries.partition { it.key in orderIndex }
                    (pinned.sortedBy { orderIndex.getValue(it.key) } + rest)
                        .filter { filter.accepts(it.hidden) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Span selection (shared machinery, see SpanSelector.kt) ---

    private val span = SpanSelector(
        rows = rows,
        getKey = { it.key },
        scope = viewModelScope,
    )
    val rangeAnchorKey: StateFlow<String?> = span.anchorKey
    val rangeMode: StateFlow<SpanSelector.Mode> = span.mode
    val rangeEndKey: StateFlow<String?> = span.endKey

    /** Hidden items of the selected section (key → label) so they can be unhidden from here. */
    val hiddenChannels: StateFlow<Map<String, String>> = combine(ctx, _section) { c, s -> c to s }
        .flatMapLatest { (c, s) ->
            if (c.profileId < 0) flowOf(emptyMap())
            else customize.observe(c.profileId, s).map { it.hiddenItems }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Whether a category this provider adds on a future resync should be hidden automatically, for
     *  this profile — same across Live/Movies/Series, so it doesn't follow [_section]. */
    val hideNewCategories: StateFlow<Boolean> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(false) else settings.hideNewCategoriesDefault(c.profileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setHideNewCategories(hidden: Boolean) {
        val pid = ctx.value.profileId
        if (pid < 0) return
        viewModelScope.launch { settings.setHideNewCategoriesDefault(pid, hidden) }
    }

    // --- Sort (reuses SettingsRepository.SortMode — the same per-section value Browse uses) ---

    /** Current sort mode for the selected section, for the Sort pill in the header strip. */
    val currentSort: StateFlow<SettingsRepository.SortMode> = combine(ctx, _section) { c, s -> c to s }
        .flatMapLatest { (c, s) ->
            if (c.profileId < 0) flowOf(SettingsRepository.SortMode.PLAYLIST)
            else when (s) {
                MediaType.LIVE -> settings.sortLive
                MediaType.MOVIE -> settings.sortMovies
                MediaType.SERIES -> settings.sortSeries
                MediaType.EPISODE -> flowOf(SettingsRepository.SortMode.PLAYLIST)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.SortMode.PLAYLIST)

    fun setSort(mode: SettingsRepository.SortMode) {
        viewModelScope.launch {
            when (_section.value) {
                MediaType.LIVE -> settings.setSortLive(mode)
                MediaType.MOVIE -> settings.setSortMovies(mode)
                MediaType.SERIES -> settings.setSortSeries(mode)
                MediaType.EPISODE -> { } // not applicable
            }
        }
    }

    // --- Items screen navigation ---

    private val _selectedCategory = MutableStateFlow<CustomizeCatRow?>(null)
    val selectedCategory: StateFlow<CustomizeCatRow?> = _selectedCategory.asStateFlow()

    fun openItems(row: CustomizeCatRow) { _selectedCategory.value = row }
    fun closeItems() { _selectedCategory.value = null }

    /** Data the items ViewModel needs to page the selected category. [categoryId] is null for a
     *  custom combined category (issue #87) — the items screen then pages the membership join. */
    data class ItemsCtx(val categoryId: Long?, val mediaType: MediaType, val sourceIds: List<Long>)

    fun ctxForItems(): ItemsCtx? {
        val c = ctx.value
        if (c.profileId < 0) return null
        return ItemsCtx(
            categoryId = _selectedCategory.value?.categoryId,
            mediaType = _section.value,
            sourceIds = c.sourceIdsFor(_section.value),
        )
    }

    // --- custom combined categories (issue #87) ---

    /** Creates a custom category for the selected section — the "＋ New category" pill. */
    fun createCustomCategory(name: String) {
        val pid = ctx.value.profileId
        if (pid < 0) return
        viewModelScope.launch {
            customize.createCustomCategory(pid, _section.value, name)
        }
    }

    /**
     * Deletes a custom combined category (issue #87). Room rows are cleared FIRST (membership +
     * manual order inside it), then the DataStore definition — so the rail never shows a half-dead
     * category. The provider items themselves are never touched.
     */
    fun deleteCustomCategory(row: CustomizeCatRow) {
        val pid = ctx.value.profileId
        if (pid < 0 || !CustomizeKeys.isCustom(row.key)) return
        viewModelScope.launch {
            val type = _section.value
            // Capture stable member keys first so deleting a destination restores any provider
            // origins those items left when they were moved here.
            val formerMemberKeys = customCategoryDao.stableItemKeys(pid, row.key).toSet()
            // 1) Room: drop the category's membership + its content_order rows (the category's own
            //    rail-order rows ride the same contextKey as the browse screens' reorder).
            customCategoryDao.clearContext(pid, type, row.key)
            contentOrderDao.clearContext(pid, type, row.key)
            // 2) DataStore: remove the definition + any hide/rename pins on it.
            customize.deleteCustomCategory(pid, type, row.key, formerMemberKeys)
        }
    }

    fun setCategoryHidden(row: CustomizeCatRow, hidden: Boolean) {
        viewModelScope.launch {
            customize.setCategoryHidden(ctx.value.profileId, _section.value, row.key, hidden)
        }
    }

    /** Blank name restores the provider's original. */
    fun renameCategory(row: CustomizeCatRow, name: String?) {
        viewModelScope.launch {
            customize.renameCategory(ctx.value.profileId, _section.value, row.key, name)
        }
    }

    /** Moves a category one step up/down and persists the full resulting order. */
    fun move(row: CustomizeCatRow, up: Boolean) =
        moveSingle(row, if (up) MoveKind.UP else MoveKind.DOWN)

    /** Jumps a category straight to the top or bottom of the list and persists the new order. */
    fun moveToEdge(row: CustomizeCatRow, top: Boolean) =
        moveSingle(row, if (top) MoveKind.TOP else MoveKind.BOTTOM)

    private fun moveSingle(row: CustomizeCatRow, kind: MoveKind) {
        viewModelScope.launch {
            moveMutex.withLock {
                val current = rows.value
                val index = current.indexOfFirst { it.key == row.key }
                if (index < 0) return@withLock
                val reordered = moveBlock(current, index, index, kind) ?: return@withLock
                customize.setCategoryOrder(ctx.value.profileId, _section.value, reordered.map { it.key })
            }
        }
    }


    fun unhideChannel(key: String) {
        viewModelScope.launch {
            customize.setItemHidden(ctx.value.profileId, _section.value, key, "", false)
        }
    }

    // --- range (span) select: delegated to SpanSelector; persistence stays here. ---

    fun beginRange(row: CustomizeCatRow) = span.beginRange(row.key)
    fun beginMoveRange(row: CustomizeCatRow) = span.beginMoveRange(row.key)
    fun cancelRange() = span.cancel()
    fun keysInRange(endRow: CustomizeCatRow): List<String>? = span.keysInRange(endRow)

    fun applyRange(endRow: CustomizeCatRow, hidden: Boolean) {
        val keys = span.keysInRange(endRow) ?: return
        viewModelScope.launch {
            customize.setCategoriesHidden(ctx.value.profileId, _section.value, keys, hidden)
        }
        span.cancel()
    }

    fun moveRange(endRow: CustomizeCatRow, kind: MoveKind) {
        val anchorKey = span.anchorKey.value ?: return
        val endKey = span.endKey.value ?: endRow.key
        viewModelScope.launch {
            moveMutex.withLock {
                val current = rows.value
                val anchorIndex = current.indexOfFirst { it.key == anchorKey }
                val endIndex = current.indexOfFirst { it.key == endKey }
                if (anchorIndex < 0 || endIndex < 0) return@withLock
                span.setEndKey(endKey)
                val reordered = moveBlock(current, minOf(anchorIndex, endIndex), maxOf(anchorIndex, endIndex), kind) ?: return@withLock
                customize.setCategoryOrder(ctx.value.profileId, _section.value, reordered.map { it.key })
            }
        }
    }

    val rangeSelectedKeys: StateFlow<Set<String>> = span.selectedKeys

    // --- bulk rename (issue #86) ---

    /**
     * One-shot bulk-rename flow for the current section's category rows. Every accepted rename lands
     * in ONE [CustomizationStore] write; restore clears only the selected keys' entries.
     */
    val bulk = BulkRenameSession(
        scope = viewModelScope,
        persist = { renames ->
            val pid = ctx.value.profileId
            if (pid >= 0) customize.applyBulkRenames(pid, _section.value, renames)
        },
        restore = { keys ->
            val pid = ctx.value.profileId
            if (pid >= 0) customize.clearCategoryNames(pid, _section.value, keys)
        },
        existingNames = { selectedKeys ->
            val pid = ctx.value.profileId
            if (pid < 0) emptySet()
            else customize.observe(pid, _section.value).first().categoryNames
                .filterKeys { it !in selectedKeys }
                .values.toSet() + rows.value.filter { it.key !in selectedKeys }.map { it.originalName }
        },
    )

    fun beginRenameRange(row: CustomizeCatRow) = span.beginRenameRange(row.key)

    /**
     * Ends the RENAME span at [endRow] and opens the bulk flow over the spanned rows' ORIGINAL
     * names. Returns null when no span is active — the caller then opens the single-row rename.
     */
    fun finishRenameRange(endRow: CustomizeCatRow): List<String>? {
        val keys = span.finishRenameRange(endRow.key) ?: return null
        val byKey = rows.value.associateBy { it.key }
        bulk.start(keys.mapNotNull { byKey[it]?.let { row -> row.key to row.originalName } })
        return keys
    }
}
