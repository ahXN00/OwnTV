package tv.own.owntv.features.categorylock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/** How long a category the PIN opened may take to list its first item before focus stops waiting. */
private const val LIST_WAIT_MS = 3_000L

/** Frames the first item of the opened category gets to take focus once it is listed (about half a second). */
private const val FIRST_ITEM_FRAMES = 30

/**
 * Where focus goes when a PIN prompt asked from a sealed category's Enter PIN button closes. The
 * button sits in the item list, so that is where focus stays: on the first item once the opened
 * category has loaded, or back on the button when the PIN did not open it. A prompt asked from the
 * categories is the screen's to close; when it opened the category from the sheet, the screen hands
 * the wait for the first item back here with [openedFromCategories].
 */
@Stable
internal class ListPinFocus {
    private var fromList = false
    internal var awaitingItems by mutableStateOf(false)
    internal var refocusPin by mutableStateOf(false)

    /** The prompt is being asked from the Enter PIN button. */
    fun askedFromList() { fromList = true }

    /** The category sheet's prompt opened its category: focus moves to the first item once it has loaded. */
    fun openedFromCategories() { awaitingItems = true }

    /** Takes the close of a prompt asked from the button and returns true; any other close is the screen's. */
    fun takeClose(opened: Boolean): Boolean {
        if (!fromList) return false
        fromList = false
        if (opened) awaitingItems = true else refocusPin = true
        return true
    }
}

/**
 * A [ListPinFocus] for a list of [itemCount] items whose Enter PIN button holds [pinFocus], as its first
 * item does once the list has one.
 * [scrollToFirst] brings the first item on screen, the way the screen's own focus moves scroll first.
 * [focusList] is the screen's own way into the list (the remembered item, else the first).
 */
@Composable
internal fun rememberListPinFocus(
    itemCount: Int,
    pinFocus: FocusRequester,
    scrollToFirst: suspend () -> Unit,
    focusList: () -> Unit,
): ListPinFocus {
    val state = remember { ListPinFocus() }
    val toFirst by rememberUpdatedState(scrollToFirst)
    val enterList by rememberUpdatedState(focusList)
    LaunchedEffect(state.awaitingItems, itemCount > 0) {
        if (!state.awaitingItems) return@LaunchedEffect
        // An opened category that stays empty still gets the list's own fallback (its search field).
        if (itemCount == 0) delay(LIST_WAIT_MS)
        if (itemCount > 0) {
            // Not the screen's own way in: it keeps a focus already inside the list (Movies and Series
            // count their search field), and it scrolls to the remembered item, so the first item could
            // take focus while scrolled off screen. The list scrolls to it first instead.
            toFirst()
            focusFirstItem(pinFocus)
        } else {
            enterList()
        }
        // Last: clearing the flag restarts this effect, which cancels whatever is still running in it.
        state.awaitingItems = false
    }
    LaunchedEffect(state.refocusPin) {
        if (!state.refocusPin) return@LaunchedEffect
        withFrameNanos { }
        runCatching { pinFocus.requestFocus() }
        state.refocusPin = false
    }
    return state
}

/** Moves focus to [firstItem] as soon as the list has composed it. */
private suspend fun focusFirstItem(firstItem: FocusRequester) {
    repeat(FIRST_ITEM_FRAMES) {
        withFrameNanos { }
        if (runCatching { firstItem.requestFocus() }.getOrDefault(false)) return
    }
}
