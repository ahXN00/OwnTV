package tv.own.owntv.features.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.LiveKey

/**
 * The right-hand list of the full-screen channel browser (categories on the left, a category's
 * channels on the right). Browsing here is only looking: nothing tunes and CH± keeps the list it had
 * until a channel is actually picked, so walking through categories never changes what CH+ does.
 *
 * It opens on the list CH± already holds, so opening it queries nothing. OK on a category loads that
 * category; a newer pick cancels an older load, and a load that still lands late (it was not
 * cancellable in time) is dropped by its generation. An empty category leaves the list as it was —
 * a blank pane with nothing to focus is a dead end on a remote. A folder locked while it is shown is
 * dropped for the CH± list.
 */
class LiveCategoryPeek(private val scope: CoroutineScope) {

    private val _channels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channels: StateFlow<List<ChannelEntity>> = _channels.asStateFlow()

    /** The category shown on the right; null when it is the CH± list of no named category. */
    private val _key = MutableStateFlow<LiveKey?>(null)
    val key: StateFlow<LiveKey?> = _key.asStateFlow()

    /** The channel last focused in each category this session, so coming back lands where it was. */
    private val lastFocused = HashMap<LiveKey?, Long>()

    private var generation = 0
    private var loadJob: Job? = null
    private var zapChannels: List<ChannelEntity> = emptyList()
    private var zapKey: LiveKey? = null

    /** Open on the list CH± zaps in ([key] its category, when it has one). */
    fun start(channels: List<ChannelEntity>, key: LiveKey?) {
        cancelLoad()
        zapChannels = channels
        zapKey = key
        _channels.value = channels
        _key.value = key
    }

    /** OK on category [key]: show its channels once [load] returns them. */
    fun load(key: LiveKey, load: suspend () -> List<ChannelEntity>) {
        cancelLoad()
        val mine = generation
        loadJob = scope.launch {
            val loaded = load()
            if (mine != generation || loaded.isEmpty()) return@launch
            _channels.value = loaded
            _key.value = key
        }
    }

    fun rememberFocus(channelId: Long) { lastFocused[_key.value] = channelId }

    /** Where the list is entered: the channel last focused in this category, else [playingId]. */
    fun entryChannel(playingId: Long?): Long? = lastFocused[_key.value] ?: playingId

    /** Locked folders not open this session: a shown folder among them goes back to the CH± list;
     *  any other list loses just those channels. */
    fun dropCategories(sealed: Set<Long>) {
        if (sealed.isEmpty()) return
        val key = _key.value
        if (key is LiveKey.Folder && key.id in sealed) {
            cancelLoad()
            _channels.value = zapChannels.filterNot { it.categoryId in sealed }
            _key.value = zapKey
            return
        }
        val list = _channels.value
        if (list.any { it.categoryId in sealed }) _channels.value = list.filterNot { it.categoryId in sealed }
    }

    private fun cancelLoad() {
        generation++
        loadJob?.cancel()
        loadJob = null
    }
}
