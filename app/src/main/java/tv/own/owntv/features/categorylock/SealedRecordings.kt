package tv.own.owntv.features.categorylock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tv.own.owntv.core.customize.ItemCategory
import tv.own.owntv.core.customize.isSealed
import tv.own.owntv.core.database.entity.RecordingEntity

/**
 * These recordings without those of a channel filed under a [sealed] category. [categoryOf] gives a
 * channel's category; a recording whose channel is gone (a re-sync gave it a new id) can no longer be
 * tied to a category, so it stays out while [anySealed] says the profile has a lock that is not open.
 */
internal fun Flow<List<RecordingEntity>>.withoutSealed(
    sealed: Flow<Set<Long>>,
    anySealed: Flow<Boolean>,
    categoryOf: suspend (channelId: Long) -> ItemCategory,
): Flow<List<RecordingEntity>> = combine(this, sealed, anySealed) { rows, ids, any ->
    if (ids.isEmpty() && !any) rows
    else rows.filterNot { categoryOf(it.channelId).isSealed({ any }) { id -> id in ids } }
}
