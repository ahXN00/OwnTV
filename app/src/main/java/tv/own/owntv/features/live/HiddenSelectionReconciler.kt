package tv.own.owntv.features.live

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.core.live.LiveBuiltIns
import tv.own.owntv.core.live.LiveKey

/**
 * A hidden All or Catch-up — at a cold start, from a remembered key, after a playlist switch, or
 * switched off in Settings — must move to [LiveViewModel.fallbackKey]. Lives outside [LiveViewModel]
 * so that move can be tested without the view model's Android, Room and player dependencies. The
 * identity filter skips the `stateIn` placeholder, so a rail not loaded yet cannot pick Favorites
 * over the first folder.
 */
internal suspend fun reconcileHiddenSelection(
    rail: Flow<List<LiveRailItem>>,
    placeholder: List<LiveRailItem>,
    builtIns: Flow<LiveBuiltIns>,
    selected: MutableStateFlow<LiveKey>,
    isLocked: () -> Boolean,
) = combine(rail.filter { it !== placeholder }, builtIns, selected) { items, shown, current ->
    if (shown.shows(current)) null else LiveViewModel.fallbackKey(items)
}.collect { next ->
    if (!isLocked() && next != null) selected.value = next
}

/**
 * After a playlist or profile switch drops the selected folder [stale], wait for a loaded rail (not
 * [placeholder]) that no longer lists it and land on its [LiveViewModel.fallbackKey], so a hidden All
 * never picks a folder from the old playlist's rail. All when no such rail arrives within [timeoutMs].
 */
internal suspend fun fallbackAfterSwitch(rail: Flow<List<LiveRailItem>>, placeholder: List<LiveRailItem>, stale: LiveKey, timeoutMs: Long = 5_000): LiveKey =
    withTimeoutOrNull(timeoutMs) { rail.first { r -> r !== placeholder && r.none { it.key == stale } } }?.let(LiveViewModel::fallbackKey) ?: LiveKey.All
