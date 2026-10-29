package tv.own.owntv.features.epg

import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * The catch-up channels whose past days the guide asks for, one at a time
 * ([tv.own.owntv.core.repository.EpgRepository.fillArchiveGuide]). The row seen last goes first: what
 * is on screen now wins over rows already scrolled past. A channel queued again moves to the front
 * rather than waiting twice, and past [max] the oldest entries are forgotten. Not thread-safe.
 */
internal class ArchiveGuideQueue(private val max: Int) {
    // Insertion order, newest last.
    private val pending = LinkedHashMap<Long, ChannelEntity>()

    val size: Int get() = pending.size

    fun add(channel: ChannelEntity) {
        pending.remove(channel.id)
        pending[channel.id] = channel
        while (pending.size > max) pending.remove(pending.keys.first())
    }

    /** The newest channel, removed from the queue; null when it is empty. */
    fun next(): ChannelEntity? = pending.keys.lastOrNull()?.let { pending.remove(it) }
}
