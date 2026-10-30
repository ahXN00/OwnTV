package tv.own.owntv.features.automation

import android.content.Context
import android.os.PowerManager

/**
 * Wakes the TV the way a remote button does, before the API brings OwnTV forward ([AppLauncher]): ends a
 * screensaver, turns a sleeping screen on, and on Android TV asks the TV set to power on and switch to this
 * input (HDMI-CEC One Touch Play). The activity's own screen-on flag ([AutomationWake]) cannot end a
 * screensaver: the screensaver stays on top of the activity it starts.
 */
class ScreenWaker(context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)

    fun wake() {
        // Deprecated, but a wake-up wake lock is the only way an app can end a screensaver; its replacement,
        // FLAG_TURN_SCREEN_ON, leaves the screensaver up.
        @Suppress("DEPRECATION")
        val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE
        power?.newWakeLock(flags, TAG)?.acquire(HOLD_MS)
    }

    private companion object {
        const val TAG = "OwnTV:automation-wake"

        /** Long enough for the activity to come forward; it then keeps the screen on itself while playing. */
        const val HOLD_MS = 3_000L
    }
}
