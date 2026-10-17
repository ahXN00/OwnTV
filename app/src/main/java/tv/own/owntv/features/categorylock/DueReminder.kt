package tv.own.owntv.features.categorylock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.ReminderEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.reminder.ReminderManager

/**
 * The programme reminder to bring up now for [profileId], null when none is due. One on a channel
 * filed under a locked category waits: it neither asks nor switches until the PIN opens the
 * category, so a reminder cannot show or tune what the lock keeps out of sight.
 */
@Composable
internal fun rememberDueReminder(profileId: Long?): ReminderEntity? {
    val reminders = koinInject<ReminderManager>()
    val customize = koinInject<CustomizationStore>()
    val channels = koinInject<ChannelDao>()
    val due = remember(profileId, reminders, customize, channels) {
        if (profileId == null) flowOf(null)
        else reminders.due.firstUnsealed(profileId, customize.sealedCategoryIds(profileId, MediaType.LIVE)) {
            channels.getById(it)?.categoryId
        }
    }
    val reminder by due.collectAsStateWithLifecycle(null)
    return reminder
}

/**
 * The first of these due reminders that belongs to [profileId] and whose channel is not in a
 * [sealed] category. [categoryOf] gives a channel's category; a reminder whose channel is gone stays,
 * since it can no longer be tied to a category (and watching it finds nothing to tune).
 */
internal fun Flow<List<ReminderEntity>>.firstUnsealed(
    profileId: Long,
    sealed: Flow<Set<Long>>,
    categoryOf: suspend (channelId: Long) -> Long?,
): Flow<ReminderEntity?> = combine(this, sealed) { due, ids ->
    due.firstOrNull { it.profileId == profileId && (ids.isEmpty() || categoryOf(it.channelId) !in ids) }
}
