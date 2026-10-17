package tv.own.owntv.features.categorylock

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/**
 * The ids in [ids] whose title the app may show: those [hides] does not hold back. Looked at again
 * when the ids change and each time [locks] changes (a relock, a PIN entry, a lock edit). Each look
 * starts from none named, so a title whose category a lock has just closed never shows while the
 * look runs, and a new download or recording is not named before its own check.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun namedIds(ids: Flow<Set<Long>>, locks: Flow<*>, hides: suspend (id: Long) -> Boolean): Flow<Set<Long>> =
    ids.distinctUntilChanged().recheckedOn(locks).transformLatest { current ->
        emit(emptySet())
        emit(current.filterNotTo(HashSet()) { hides(it) })
    }
