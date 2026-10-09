package tv.own.owntv.features.live

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.player.wrappedZapIndex
import tv.own.owntv.core.live.LiveKey

/**
 * The channel list the player zaps within — CH+/CH−, the D-pad, the in-player channel-list overlay —
 * and the bookkeeping a numeric direct tune needs to stay responsive while that list catches up.
 *
 * Split out of `LiveViewModel`, which was doing four jobs at once. The seam is the same shape as
 * [LiveTimeshift]'s: this class owns **state and sequencing**, the view model owns **queries**. Which
 * list is armed, which channel CH+ lands on, which rebuild is allowed to publish and when the
 * direct-tune anchor expires all live here; every database read and every hide/rename rule arrives as
 * a loader lambda, because those rules are shared with the browse lists and belong with them.
 *
 * The two things that go wrong here are both invisible in a log, which is why they are pinned by
 * tests: a background rebuild publishing over a list the user has since navigated away from, and
 * CH+/CH− going dead in the window after a numeric jump lands outside the current list.
 */
class LiveZapList(
    private val scope: CoroutineScope,
    /** The channel on screen — CH± steps from it, and a rebuild only publishes while it still plays. */
    private val playingChannelId: () -> Long?,
    /** [ChannelEntity]'s own category (or all channels), refined the way the browse lists are. */
    private val loadForChannel: suspend (ChannelEntity) -> List<ChannelEntity>,
    /** One provider category, for the in-player category browser. */
    private val loadForCategory: suspend (Long) -> List<ChannelEntity>,
    /** A bounded provider-order window centred on a channel — the rebuild after a numeric tune. */
    private val loadWindowAround: suspend (ChannelEntity) -> List<ChannelEntity>,
    private val categoryName: suspend (Long) -> String?,
) {

    private var list: List<ChannelEntity> = emptyList()

    private val _canZap = MutableStateFlow(false)
    val canZap: StateFlow<Boolean> = _canZap.asStateFlow()

    /** The opened channel list, for the in-player channel-list overlay. */
    private val _channels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channels: StateFlow<List<ChannelEntity>> = _channels.asStateFlow()

    /** Heading for the left overlay — the name of the active playback browse context. */
    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    /** The rail the list came from, for the built-in ones (Favorites / History / All) whose names are
     *  UI strings rather than provider data and so can never appear in [title]. */
    private val _key = MutableStateFlow<LiveKey?>(null)
    val key: StateFlow<LiveKey?> = _key.asStateFlow()

    /** Provider category the list came from, or null for a synthetic/caller-owned rail. */
    private var categoryId: Long? = null
    private var armed = false

    private var loadJob: Job? = null

    /** Bumped every time a rebuild starts OR is cancelled. The background rebuild captures this at
     *  start and re-checks it before publishing, so an older build cannot overwrite the list after a
     *  newer navigation, a newer numeric tune, or a CH± fallback. */
    private var generation = 0L
    private var rebuildJob: Job? = null

    /** Fallback CH± anchor for the window during which the tuned channel is NOT yet in the list. Set
     *  by a numeric tune so the user can still navigate while the bounded window rebuilds. The list
     *  reference and the index are stored together, so a concurrent list replacement cannot silently
     *  redirect CH± into an unrelated channel. */
    private data class PendingDirectTune(
        val targetChannelId: Long,
        val previousList: List<ChannelEntity>,
        val previousIndex: Int,
    )
    private var pending: PendingDirectTune? = null

    // ---- Arming the list ---------------------------------------------------------------------------

    /** Rebuild from [channel]'s own provider category. A no-op while zapping inside the same category
     *  (the list is already right), so CH+/CH− stays a pure in-memory step. */
    fun armFor(channel: ChannelEntity) {
        if (armed && channel.categoryId == categoryId && list.any { it.id == channel.id }) return
        loadJob?.cancel()
        loadJob = scope.launch {
            publish(
                loaded = loadForChannel(channel),
                categoryId = channel.categoryId,
                title = channel.categoryId?.let { categoryName(it) }?.takeIf { it.isNotBlank() },
                key = null,
            )
        }
    }

    /** Load one provider category, picked in the in-player category browser. [onLoaded] runs only if
     *  the category had channels: an empty one would leave the user on a blank overlay with nothing
     *  focusable, so the browser stays open and they can pick another. */
    fun armForCategory(categoryId: Long, onLoaded: () -> Unit) {
        loadJob?.cancel()
        loadJob = scope.launch {
            val loaded = loadForCategory(categoryId)
            if (loaded.isEmpty()) return@launch
            publish(loaded, categoryId = categoryId, title = categoryName(categoryId), key = null)
            onLoaded()
        }
    }

    /** Load a custom category or a built-in rail (Favorites / History / Catch-up / All), picked in the
     *  in-player category browser — same contract as [armForCategory]. Members come from many provider
     *  folders, so there is no single [categoryId]; built-in rails pass no [title] (their names are UI
     *  strings, chosen from [key]). */
    fun armForKey(key: LiveKey, title: String?, load: suspend () -> List<ChannelEntity>, onLoaded: () -> Unit) {
        loadJob?.cancel()
        loadJob = scope.launch {
            val loaded = load()
            if (loaded.isEmpty()) return@launch
            publish(loaded, categoryId = null, title = title, key = key)
            onLoaded()
        }
    }

    /** Adopt a list the caller already has — the browse rail a channel was opened from, which must not
     *  be replaced by the channel's provider-category metadata. */
    fun armFromBrowse(
        channels: List<ChannelEntity>,
        title: String?,
        key: LiveKey?,
        categoryId: Long?,
    ) {
        loadJob?.cancel()
        publish(channels, categoryId = categoryId, title = title, key = key)
    }

    /**
     * Commit a new zap context. Any direct-tune rebuild still in flight is dropped first: it was
     * started for a context the user has now left, and its guards (newest generation, tuned channel
     * still playing) both still hold — picking a category does not change the channel — so it would
     * otherwise land afterwards and replace the list the user just chose with a bounded window.
     *
     * Here rather than at the top of each arming method, because that is the moment a *replacement*
     * actually happens: [armForCategory] loads first and publishes nothing when the category turns out
     * empty, and killing a healthy rebuild for a category the user cannot even enter would leave CH±
     * with neither a list nor an anchor.
     */
    private fun publish(loaded: List<ChannelEntity>, categoryId: Long?, title: String?, key: LiveKey?) {
        cancelPendingRebuild()
        this.categoryId = categoryId
        armed = true
        replace(loaded)
        _title.value = title
        _key.value = key
    }

    /** Publish a list without touching the context around it — the guarded rebuild's landing point. */
    private fun replace(loaded: List<ChannelEntity>) {
        list = loaded
        _channels.value = loaded
        _canZap.value = loaded.size > 1
    }

    fun contains(channelId: Long): Boolean = list.any { it.id == channelId }

    // ---- Stepping ----------------------------------------------------------------------------------

    /**
     * The channel CH± lands on ([delta] = +1 down / −1 up), or null when there is nowhere to go.
     *
     * Two-axis resolution:
     *  1. the playing channel is in the live list — the ordinary wrapped step;
     *  2. it is not (the window right after a numeric tune that landed outside the list, before the
     *     bounded rebuild has published) — step through the anchor saved at tune time instead, so CH±
     *     is responsive rather than dead. The anchor moves with the user, so a held key still chains.
     */
    fun next(delta: Int): ChannelEntity? {
        val currentId = playingChannelId()
        val i = if (currentId != null) list.indexOfFirst { it.id == currentId } else -1
        if (i >= 0) {
            val nextIdx = wrappedZapIndex(i, delta, list.size) ?: return null
            return list[nextIdx]
        }
        // The anchor names the channel it was created for. If that is no longer what is playing (a newer
        // numeric tune, or CH± already moved on), it is stale — drop it rather than jump somewhere else.
        val anchor = pending ?: return null
        if (anchor.targetChannelId != currentId) {
            pending = null
            return null
        }
        val prev = anchor.previousList
        val nextIdx = wrappedZapIndex(anchor.previousIndex, delta, prev.size) ?: run {
            pending = null
            return null
        }
        pending = anchor.copy(targetChannelId = prev[nextIdx].id, previousIndex = nextIdx)
        return prev[nextIdx]
    }

    // ---- Direct tune -------------------------------------------------------------------------------

    /**
     * Put [tuned] on screen after a numeric tune, and make CH± work again around it.
     *
     * [play] is called immediately — a direct tune must never wait for a database rebuild. Only when
     * the target is outside the current list does a bounded window get rebuilt afterwards, in the
     * background, and it publishes only if it is still the newest rebuild AND [tuned] is still playing.
     * Until then CH± steps through the anchor recorded here.
     *
     * The order matters and is the reason [play] is a parameter rather than the caller's next line:
     * the anchor has to be computed from the state *before* playback moves it.
     */
    suspend fun directTune(currentChannelId: Long, tuned: ChannelEntity, play: suspend (ChannelEntity) -> Unit) {
        val alreadyInList = contains(tuned.id)

        val inherited = pending?.takeIf { it.targetChannelId == currentChannelId }
        val anchorList = inherited?.previousList ?: list
        val anchorIndex = inherited?.previousIndex ?: list.indexOfFirst { it.id == currentChannelId }
        val hasValidAnchor = anchorList.size >= 2 && anchorIndex in anchorList.indices

        if (alreadyInList) {
            rebuildJob?.cancel()
            rebuildJob = null
            generation++
            pending = null
        }

        play(tuned)

        if (alreadyInList) return

        rebuildJob?.cancel()
        // The tune takes ownership of the list, so an ordinary arming load still in flight must not
        // land on top of the window — the mirror image of what [publish] guards against.
        loadJob?.cancel()
        generation++
        val myGeneration = generation

        pending = if (hasValidAnchor) {
            PendingDirectTune(tuned.id, previousList = anchorList, previousIndex = anchorIndex)
        } else {
            null
        }

        rebuildJob = scope.launch {
            try {
                val rebuilt = try {
                    loadWindowAround(tuned)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "direct tune: zap rebuild failed", e)
                    return@launch
                }
                if (myGeneration != generation) return@launch
                if (playingChannelId() != tuned.id) return@launch
                replace(rebuilt)
                pending = null
            } finally {
                if (myGeneration == generation) rebuildJob = null
            }
        }
    }

    /** Cancel any in-flight background rebuild and discard its fallback anchor. Ordinary navigation
     *  (CH±, the channel list, the Guide) does this before playing the new channel, and [publish] does
     *  it for anything that arms a new context, so an obsolete rebuild never publishes after the user
     *  has moved elsewhere. A direct tune deliberately does not — it manages its own rebuild, which is
     *  what keeps its playback immediate. */
    fun cancelPendingRebuild() {
        rebuildJob?.cancel()
        rebuildJob = null
        generation++
        pending = null
    }

    private companion object {
        const val TAG = "LiveZapList"
    }
}
