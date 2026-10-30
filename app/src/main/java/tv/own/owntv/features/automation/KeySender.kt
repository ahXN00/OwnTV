package tv.own.owntv.features.automation

import android.os.Build

/**
 * Delivers one remote button to the window that has focus on the TV, as a real remote's would arrive:
 * an open dialog or popup gets it, not the screen behind it. [send] first asks [allowed] on the main
 * thread, right before delivering, and sends nothing unless it says [KeyPress.PRESSED]; it returns
 * what [allowed] said. It throws [SecurityException] when the focused window is not OwnTV's (another
 * app, or the system's), which the caller reads as OwnTV not being in front.
 */
fun interface KeySender {
    suspend fun send(keyCode: Int, allowed: () -> KeyPress): KeyPress

    companion object {
        /**
         * Android 12L and older let an app inject keys into its own windows ([InjectedKeys]); Android 13
         * refuses that without a system permission, so there OwnTV hands them to its focused window
         * itself ([FocusedWindowKeys]).
         */
        fun forThisDevice(): KeySender =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) FocusedWindowKeys() else InjectedKeys()
    }
}
