package tv.own.owntv.features.pip

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * The activity side of picture-in-picture. Android 12+ moves the activity into the window by itself
 * on Home (auto-enter, kept in step with [PictureInPicture.windowOffer]); older versions are asked to
 * in `onUserLeaveHint`, the callback for "the user is leaving", which a dialog or a screen of our own
 * does not trigger.
 *
 * Nothing here stops playback. In the window the activity is only paused, so the player keeps going;
 * closing the window stops the activity, and `MainActivity.onStop` releases the players exactly as it
 * does for Home without picture-in-picture.
 */
internal object PictureInPictureWindow {

    fun attach(activity: ComponentActivity, pip: PictureInPicture) {
        pip.onWindowChanged(activity.isInPictureInPictureMode)
        if (!pip.supported) return
        activity.addOnPictureInPictureModeChangedListener { info -> pip.onWindowChanged(info.isInPictureInPictureMode) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            activity.addOnUserLeaveHintListener {
                val aspect = pip.windowOffer ?: return@addOnUserLeaveHintListener
                if (activity.isInPictureInPictureMode) return@addOnUserLeaveHintListener
                // A TV that lists the feature but refuses the call simply stops playback as before.
                runCatching { activity.enterPictureInPictureMode(params(aspect, pip.playerBounds)) }
            }
        }
        val apply: (PipAspect?, PipBounds?) -> Unit = { aspect, bounds ->
            runCatching { activity.setPictureInPictureParams(params(aspect, bounds)) }
        }
        pip.bindWindow(apply)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = pip.unbindWindow(apply)
        })
    }

    private fun params(aspect: PipAspect?, playerBounds: PipBounds?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (aspect != null) {
            builder.setAspectRatio(Rational(aspect.width, aspect.height))
            playerBounds?.let { PipPolicy.sourceRect(it, aspect) }?.let { r ->
                builder.setSourceRectHint(Rect(r.left, r.top, r.right, r.bottom))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(aspect != null)
        return builder.build()
    }
}
