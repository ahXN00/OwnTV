package tv.own.owntv.features.automation

import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hands each key to OwnTV's own window that has focus, for Android 13 and newer, which refuse an app's
 * key injection. [WindowInspector] lists the root view of every window OwnTV has open: the activity's,
 * and one per dialog or popup, oldest first. Android gives input focus to one window at a time, the
 * topmost one that takes keys; during a focus handoff two can briefly report it, so the newest wins.
 * The check and the delivery run in one main-thread step, so no screen can open between them.
 *
 * Every key, Back included, goes through the root view, which routes it as that window's input would,
 * through the dialog or activity that owns it, so Compose key handlers that watch Back (moving a
 * channel, the color picker, text fields) see it as from a remote. A Back that neither event handled
 * goes to the window's back dispatcher, the one Compose's BackHandler and dialogs listen to: Android 16
 * routes Back through back callbacks rather than the key, so there the activity may not turn the key
 * into a back press. That fallback is untested on hardware.
 *
 * When none of OwnTV's windows has focus, another app or the system has it: that throws
 * [SecurityException], the same refusal injection gives.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class FocusedWindowKeys : KeySender {
    override suspend fun send(keyCode: Int, allowed: () -> KeyPress): KeyPress = withContext(Dispatchers.Main) {
        val gate = allowed()
        if (gate != KeyPress.PRESSED) return@withContext gate
        val focused = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            ?: throw SecurityException("no OwnTV window has focus")
        val handled = press(focused, keyCode)
        if (!handled && keyCode == KeyEvent.KEYCODE_BACK) {
            focused.findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher?.onBackPressed()
        }
        KeyPress.PRESSED
    }

    /** Sends DOWN then UP, both always; true when either was handled. */
    private fun press(root: View, keyCode: Int): Boolean {
        val down = root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        val up = root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return down || up
    }
}
