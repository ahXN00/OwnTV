package tv.own.owntv.features.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The TV finished starting up, or OwnTV was updated. Runs in the `:autostart` process (see [AutoStart]),
 * so a boot with only the wake option on starts the small listener, not the whole app.
 */
class AutoStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> AutoStartTrigger.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> AutoStartTrigger.APP_UPDATED
            else -> return
        }
        AutoStart.onBroadcast(context, trigger)
    }
}
