package tv.own.owntv.features.categorylock

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Frames the closed sheet and the list get to settle before the list's focus is checked. */
private const val SETTLE_FRAMES = 2

/**
 * Focus for BACK out of the category sheet. [focusCurrent] moves it to the item the list was on, at
 * once. A lock from the sheet's menu can have emptied that list, so the item is gone (or goes once the
 * list reloads): when the list pane does not hold focus ([listFocused]) a few frames later,
 * [enterList] takes the screen's own way in, which lands on a locked category's Enter PIN button, else
 * the first item, else the search field.
 */
internal fun focusListAfterSheet(
    scope: CoroutineScope,
    focusCurrent: () -> Boolean,
    listFocused: () -> Boolean,
    enterList: () -> Unit,
) {
    focusCurrent()
    scope.launch {
        repeat(SETTLE_FRAMES) { withFrameNanos { } }
        if (!listFocused()) enterList()
    }
}
