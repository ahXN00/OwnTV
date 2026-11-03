package tv.own.owntv.features.pip

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.context.GlobalContext

/**
 * Picture-in-picture when leaving the app: the option, what the player offers right now, and whether
 * the activity is in the small window. The shell offers ([offer]); [PictureInPictureWindow] hands that
 * to Android; Android's answer comes back through [onWindowChanged].
 *
 * The option is stored on the device, not per profile, in SharedPreferences: it is about how this TV
 * behaves when the user presses Home, and it must be readable synchronously when the activity starts.
 *
 * Main thread only. Every change reaches the window at once, not on a later frame, so a window held
 * for an [AppHandOff] is already off when the other screen starts.
 */
internal class PictureInPicture(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val supported: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ON_LEAVE, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var offered: PipAspect? = null
    private var held = false

    /** The window to open if the user leaves now; null when leaving should stop playback as it always has. */
    val windowOffer: PipAspect? get() = if (held) null else offered

    /** Where the player is laid out in the window, for the window's opening animation. */
    var playerBounds: PipBounds? = null
        private set

    private val _inWindow = MutableStateFlow(false)
    val inWindow: StateFlow<Boolean> = _inWindow.asStateFlow()

    private var window: ((PipAspect?, PipBounds?) -> Unit)? = null
    private var published: Pair<PipAspect?, PipBounds?>? = null

    fun setEnabled(on: Boolean) {
        prefs.edit { putBoolean(KEY_ON_LEAVE, on) }
        _enabled.value = on
    }

    fun offer(aspect: PipAspect?) {
        offered = aspect
        publish()
    }

    /** True while OwnTV opens a screen of another app: leaving for it must not open the window. */
    fun holdWindow(hold: Boolean) {
        held = hold
        publish()
    }

    fun onPlayerBounds(bounds: PipBounds) {
        playerBounds = bounds
        publish()
    }

    fun onWindowChanged(inPictureInPicture: Boolean) {
        _inWindow.value = inPictureInPicture
    }

    /** [apply] receives every change of the window's settings, starting with the current ones. */
    fun bindWindow(apply: (PipAspect?, PipBounds?) -> Unit) {
        window = apply
        published = null
        publish()
    }

    fun unbindWindow(apply: (PipAspect?, PipBounds?) -> Unit) {
        if (window === apply) window = null
    }

    private fun publish() {
        val apply = window ?: return
        val aspect = windowOffer
        // Without a window to offer, where the player sits changes nothing Android is told.
        val next = aspect to playerBounds?.takeIf { aspect != null }
        if (next == published) return
        published = next
        apply(next.first, next.second)
    }

    companion object {
        private const val PREFS = "owntv_pip"
        private const val KEY_ON_LEAVE = "on_leave"

        /** For lists built outside composition (the Video player catalogue); false before Koin starts. */
        fun supported(): Boolean = GlobalContext.getOrNull()?.getOrNull<PictureInPicture>()?.supported == true
    }
}
