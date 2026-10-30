package tv.own.owntv.features.live

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
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
