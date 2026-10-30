package tv.own.owntv.features.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.koin.android.ext.android.inject
import tv.own.owntv.R

/**
 * Keeps the automation listener alive while OwnTV is in the background. A foreground service is the
 * only thing Android will not quietly kill; its notification is the visible sign that the box is
 * listening. Sticky, so the system brings it back after reclaiming the process.
 */
class AutomationService : Service() {
    private val server: AutomationServer by inject()
    private val store: AutomationStore by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!store.settings.value.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        server.start()
        return START_STICKY
    }

    override fun onDestroy() {
        server.stop()
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.automation_title), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(tv.own.owntv.core.R.drawable.owntv_notification)
            .setContentTitle(getString(R.string.automation_title))
            .setContentText(getString(R.string.automation_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "automation_api"
        private const val NOTIFICATION_ID = 0x4f41
        private const val TAG = "OwnTVAutomation"

        fun start(context: Context) {
            // Android 12+ refuses a foreground start from the background; the next app start retries.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, AutomationService::class.java)) }
                .onFailure { Log.w(TAG, "could not start the automation service", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutomationService::class.java))
        }
    }
}
