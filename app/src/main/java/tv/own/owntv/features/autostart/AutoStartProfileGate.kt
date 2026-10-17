package tv.own.owntv.features.autostart

import android.app.Activity
import android.app.Application
import android.os.Bundle
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.getViewModel
import tv.own.owntv.MainActivity
import tv.own.owntv.features.profiles.ProfileGateSessionViewModel
import tv.own.owntv.features.profiles.ProfileLoadState
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.features.profiles.stopPlaybackForProfileGate

/**
 * Locks a profile with a PIN again when auto start brings a running OwnTV back
 * (see [AutoStartPolicy.regateOnLaunch]). It acts as the activity starts, before `MainActivity.onStart`
 * brings playback back, so nothing of the locked profile plays behind the gate. A cold start needs
 * nothing: no profile is authenticated yet, and the gate opens as it always does.
 */
internal class AutoStartProfileGate : Application.ActivityLifecycleCallbacks {

    override fun onActivityStarted(activity: Activity) {
        if (activity !is MainActivity) return
        val launchMarkAge = AutoStartLaunchMark.consume(activity) ?: return
        val session = activity.getViewModel<ProfileGateSessionViewModel>()
        val authenticated = session.authenticatedProfileId ?: return
        val profiles = (activity.getViewModel<ProfilesViewModel>().profileState.value as? ProfileLoadState.Loaded)
            ?.profiles.orEmpty()
        val hasPin = profiles.any { it.id == authenticated && it.pinHash != null }
        if (!AutoStartPolicy.regateOnLaunch(launchMarkAge, hasPin)) return
        stopPlaybackForProfileGate(activity.get(), activity.get(), activity.get())
        session.invalidateAuthentication()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
