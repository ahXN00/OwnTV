package tv.own.owntv.features.automation

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.WindowManager

/**
 * The automation API ([AppLauncher]) asks for the screen to come on when it opens OwnTV on a sleeping TV.
 * Only for that start: [drop], called when [activity] stops, drops the request, so a TV that sleeps later
 * with OwnTV in front stays asleep. Only a start carrying this process's [WakeNonce] wakes the TV; another
 * app's intent cannot.
 */
class AutomationWake(private val activity: Activity, private val nonce: WakeNonce) {
    /** Set while an API start ([wakeIfAsked]) holds the screen-on request; cleared once OwnTV leaves the screen. */
    private var woke = false

    fun wakeIfAsked(intent: Intent) {
        if (!intent.hasExtra(AppLauncher.EXTRA_WAKE)) return
        val asked = intent.getStringExtra(AppLauncher.EXTRA_WAKE)
        intent.removeExtra(AppLauncher.EXTRA_WAKE)
        if (!nonce.matches(asked)) return
        woke = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setTurnScreenOn(true)
        } else {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    fun drop() {
        if (!woke) return
        woke = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setTurnScreenOn(false)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }
}
