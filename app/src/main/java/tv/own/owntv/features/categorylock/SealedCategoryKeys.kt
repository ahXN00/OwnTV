package tv.own.owntv.features.categorylock

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.model.MediaType

private val LOCKABLE_SECTIONS = listOf(MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES)

/**
 * The sealed category keys of Live TV, Movies and Series for the profile [profileIds] holds. A new
 * value each time a lock closes or opens (a relock on leaving the app, a PIN entry, a lock edit), so a
 * screen that read its content once, like Home, knows to read it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun CustomizationStore.sealedCategoryKeys(profileIds: Flow<Long>): Flow<List<Set<String>>> =
    profileIds
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(emptyList())
            else combine(LOCKABLE_SECTIONS.map { type -> observeVisible(pid, type).map { it.sealedCategories } }) { it.toList() }
        }
        .distinctUntilChanged()

/** The sealed category ids of [type]'s section for [profileId]; a new value only when they change. */
internal fun CustomizationStore.sealedCategoryIds(profileId: Long, type: MediaType): Flow<Set<Long>> =
    observeVisible(profileId, type).map { it.sealedCategoryIds }.distinctUntilChanged()

/** True while [profileId] has a lock, in any section, that this session has not opened. */
internal fun CustomizationStore.anyLockSealed(profileId: Long): Flow<Boolean> =
    sealedCategoryKeys(flowOf(profileId)).map { sections -> sections.any { it.isNotEmpty() } }.distinctUntilChanged()

/**
 * This flow's latest value again each time [locks] changes, for a reader that checks the locks
 * itself: what it last let through, or hid, is looked at again when a lock closes or opens.
 */
internal fun <T> Flow<T>.recheckedOn(locks: Flow<*>): Flow<T> = combine(this, locks) { value, _ -> value }
