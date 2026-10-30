package tv.own.owntv.features.automation

import android.app.Instrumentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Injects keys through Android's input system, so they reach the focused window exactly as a remote's
 * do. Android 12L and older allow that into the app's own windows without a permission and throw
 * [SecurityException] when the focused window belongs to another app or the system.
 *
 * [Instrumentation.sendKeyDownUpSync] waits until the window has handled each key, and the main thread
 * is the one handling it, so the key goes from an IO thread once the check has passed on main. Other
 * failures are silent: it returns nothing, so a key the system drops or times out on still reads as
 * pressed, and each of the two events can hold the IO thread until the system's injection timeout
 * (about 30 s, so up to about 60 s per key).
 */
class InjectedKeys(private val instrumentation: Instrumentation = Instrumentation()) : KeySender {
    override suspend fun send(keyCode: Int, allowed: () -> KeyPress): KeyPress {
        val gate = withContext(Dispatchers.Main) { allowed() }
        if (gate != KeyPress.PRESSED) return gate
        withContext(Dispatchers.IO) { instrumentation.sendKeyDownUpSync(keyCode) }
        return KeyPress.PRESSED
    }
}
