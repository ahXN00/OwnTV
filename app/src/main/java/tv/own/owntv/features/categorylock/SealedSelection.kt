package tv.own.owntv.features.categorylock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The item this flow holds, each time it belongs to one of [sealedCategoryIds]: a title or channel
 * still on screen after the user locked its category, or after a relock closed it again. Items with
 * no category are never sealed.
 */
internal fun <T : Any> Flow<T?>.whileSealed(sealedCategoryIds: Flow<Set<Long>>, categoryOf: (T) -> Long?): Flow<T> =
    combine(this, sealedCategoryIds) { item, sealed -> item?.takeIf { categoryOf(it)?.let(sealed::contains) == true } }
        .filterNotNull()

/**
 * Empties [selection] while it holds an item of a sealed category, so a VOD hero, details card or show
 * page stops showing a locked title. A selection that moved on in the meantime is kept.
 */
internal fun <T : Any> CoroutineScope.clearWhenSealed(
    selection: MutableStateFlow<T?>,
    sealedCategoryIds: Flow<Set<Long>>,
    categoryOf: (T) -> Long?,
): Job = launch { selection.whileSealed(sealedCategoryIds, categoryOf).collect { selection.compareAndSet(it, null) } }
