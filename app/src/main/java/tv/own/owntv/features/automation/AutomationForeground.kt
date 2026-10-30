package tv.own.owntv.features.automation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Which OwnTV screen is in front, if any. Remote keys from the API go to it, and tuning knows whether
 * OwnTV must bring itself forward. Registered at app start so the first screen is never missed.
 */
class AutomationForeground(app: Application) : Application.ActivityLifecycleCallbacks {
    @Volatile private var resumed: WeakReference<Activity>? = null

    init {
        app.registerActivityLifecycleCallbacks(this)
    }

    /** The resumed OwnTV activity; read it on the main thread before touching it. */
    fun activity(): Activity? = resumed?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
