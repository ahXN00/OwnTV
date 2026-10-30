package tv.own.owntv.features.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** After a reboot nothing else starts OwnTV, so the listener would stay down until the app is opened. */
class AutomationBootReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) get<AutomationController>().startIfEnabled()
    }
}
