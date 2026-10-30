package tv.own.owntv.features.live

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.live.LiveKey

/**
 * #322: a hidden All or Catch-up (cold start, a remembered key, a playlist switch, or a Settings
 * toggle) must move to [LiveViewModel.fallbackKey]. Lives outside [LiveViewModel] so that move can
 * be tested without the view model's Android, Room, and player dependencies. The identity filter
 * skips the `stateIn` placeholder so a not-yet-loaded rail cannot pick Favorites over the first folder.
 */
internal suspend fun reconcileHiddenSelection(
    rail: Flow<List<LiveRailItem>>,
    placeholder: List<LiveRailItem>,
    showAll: Flow<Boolean>,
    showCatchup: Flow<Boolean>,
    selected: MutableStateFlow<LiveKey>,
    isLocked: () -> Boolean,
) = combine(
    rail.filter { it !== placeholder },
    showAll,
    showCatchup,
    selected,
) { items, all, catchup, current ->
    LiveViewModel.hiddenSelectionFallback(current, items, all, catchup)
}.collect { next ->
    if (!isLocked() && next != null) selected.value = next
}

/** #322: after a playlist or profile switch drops the selected folder [stale], wait for a loaded rail (not [placeholder]) that no longer lists it and land on its [LiveViewModel.fallbackKey], so a hidden All never picks a folder from the old playlist's rail. All when no such rail arrives within [timeoutMs]. */
internal suspend fun fallbackAfterSwitch(rail: Flow<List<LiveRailItem>>, placeholder: List<LiveRailItem>, stale: LiveKey, timeoutMs: Long = 5_000): LiveKey =
    kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { rail.first { r -> r !== placeholder && r.none { it.key == stale } } }?.let(LiveViewModel::fallbackKey) ?: LiveKey.All
