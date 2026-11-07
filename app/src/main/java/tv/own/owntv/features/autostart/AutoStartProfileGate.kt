package tv.own.owntv.features.autostart

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.getViewModel
import tv.own.owntv.MainActivity
import tv.own.owntv.features.profiles.ProfileGateSessionViewModel
import tv.own.owntv.features.profiles.ProfileLoadState
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.features.profiles.stopPlaybackForProfileGate

/**
 * Locks a profile with a PIN again when auto start brings OwnTV back (see [AutoStartPolicy.regateOnLaunch]),
 * before `MainActivity.onStart` brings playback back, so nothing of the locked profile plays behind the gate.
 * A cold start needs nothing: no profile is authenticated yet, and the gate opens as it always does.
 *
 * Two signals, because the order of a wake-up is not fixed. The `:autostart` process leaves
 * [AutoStartLaunchMark] when it opens OwnTV. But many televisions bring the app that was on screen back
 * by themselves as they wake, before that launch arrives — the activity has then already started and the
 * mark comes too late — and the screen-on broadcast can arrive after that start too. So, while auto start
 * on wake-up is on, an activity that stopped because the screen went off counts as woken when it starts
 * again, and the screen coming on locks an activity that never stopped.
 */
internal class AutoStartProfileGate(private val app: Application) : Application.ActivityLifecycleCallbacks {

    private var started: MainActivity? = null

    /** When the screen last came on with auto start on wake-up on (elapsed realtime), or null. */
    private var wokeAtMs: Long? = null

    /** MainActivity stopped while the screen was off (the TV went to sleep) with auto start on wake-up on. */
    private var stoppedAsleep = false

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON || !AutoStartStore(context).read().onWake) return
            val activity = started
            if (activity != null) regate(activity, launchMarkAgeMs = 0L) else wokeAtMs = SystemClock.elapsedRealtime()
        }
    }

    fun register() {
        app.registerActivityLifecycleCallbacks(this)
        // A protected system broadcast: only the system can send it, which a not-exported receiver still hears.
        ContextCompat.registerReceiver(app, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity !is MainActivity) return
        started = activity
        val markAge = AutoStartLaunchMark.consume(activity)
        val wokeAge = wokeAtMs?.let { SystemClock.elapsedRealtime() - it } ?: 0L.takeIf { stoppedAsleep }
        wokeAtMs = null
        stoppedAsleep = false
        regate(activity, markAge ?: wokeAge ?: return)
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity !== started) return
        started = null
        val interactive = activity.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        stoppedAsleep = !interactive && AutoStartStore(activity).read().onWake
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === started) started = null
    }

    private fun regate(activity: MainActivity, launchMarkAgeMs: Long) {
        // Whichever signal came first, the other one must not lock again a moment later.
        AutoStartLaunchMark.clear(activity)
        val session = activity.getViewModel<ProfileGateSessionViewModel>()
        val authenticated = session.authenticatedProfileId ?: return
        val profiles = (activity.getViewModel<ProfilesViewModel>().profileState.value as? ProfileLoadState.Loaded)
            ?.profiles.orEmpty()
        val hasPin = profiles.any { it.id == authenticated && it.pinHash != null }
        if (!AutoStartPolicy.regateOnLaunch(launchMarkAgeMs, hasPin)) return
        stopPlaybackForProfileGate(activity.getViewModel(), activity.get())
        session.invalidateAuthentication()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
