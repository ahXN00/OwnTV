package tv.own.owntv.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity

/** Saves a playlist's "Clean channel names" option ([SourceEntity.cleanChannelNames]). */
class ChannelNameCleanupViewModel(
    private val sourceDao: SourceDao,
) : ViewModel() {

    /**
     * Stores the option and the user's own words ([customText], `;`-separated; blank = none). Names
     * are cleaned at sync time, so [onNeedsResync] gets the updated playlist whenever the shown names
     * would change: the option was switched, or its words changed while it is on.
     */
    fun save(source: SourceEntity, enabled: Boolean, customText: String, onNeedsResync: (SourceEntity) -> Unit) {
        val strip = customText.trim().ifEmpty { null }
        if (enabled == source.cleanChannelNames && strip == source.channelNameStrip) return
        val namesChange = enabled || source.cleanChannelNames
        viewModelScope.launch {
            sourceDao.updateChannelNameCleanup(source.id, enabled, strip)
            if (namesChange) onNeedsResync(source.copy(cleanChannelNames = enabled, channelNameStrip = strip))
        }
    }
}
