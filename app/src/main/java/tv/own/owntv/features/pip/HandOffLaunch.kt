package tv.own.owntv.features.pip

import android.content.Intent
import android.os.Looper
import org.koin.core.context.GlobalContext

/**
 * Opens [intent] as an [AppHandOff] when leaving now would open the picture-in-picture window. Every
 * activity start OwnTV makes passes through here: the application's and the activity's
 * `startActivity`, and the few `ActivityResultLauncher`s.
 *
 * The launch is then also marked FLAG_ACTIVITY_NO_USER_ACTION, Android's own "the user is not leaving":
 * it suppresses `onUserLeaveHint` and, from Android 12, the auto-enter into the window as well.
 *
 * With no window on offer (the option off, a TV without picture-in-picture, nothing playing) the intent
 * starts untouched, exactly as it did before the option existed. Off the main thread the offer cannot
 * be read, so a launch there only gets the flag, and only while the option is on.
 */
internal fun <T> startAsHandOff(intent: Intent, start: (Intent) -> T): T {
    val koin = GlobalContext.getOrNull() ?: return start(intent)
    val pip = koin.getOrNull<PictureInPicture>() ?: return start(intent)
    val notLeaving = { Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION) }
    if (!Looper.getMainLooper().isCurrentThread) {
        return start(if (pip.supported && pip.enabled.value) notLeaving() else intent)
    }
    if (pip.windowOffer == null) return start(intent)
    return koin.get<AppHandOff>().launch { start(notLeaving()) }
}
