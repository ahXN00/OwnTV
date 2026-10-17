package tv.own.owntv.features.pip

import android.content.Intent
import android.os.Looper
import org.koin.core.context.GlobalContext

/**
 * Opens [intent] as an [AppHandOff]. Every activity start OwnTV makes passes through here: the
 * application's and the activity's `startActivity`, and the few `ActivityResultLauncher`s.
 *
 * The launch is also marked FLAG_ACTIVITY_NO_USER_ACTION, Android's own "the user is not leaving":
 * it suppresses `onUserLeaveHint` and, from Android 12, the auto-enter into the window as well.
 * Launches off the main thread, or before Koin starts, only get the flag.
 */
internal fun <T> startAsHandOff(intent: Intent, start: (Intent) -> T): T {
    val notLeaving = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    val handOff = if (Looper.getMainLooper().isCurrentThread) GlobalContext.getOrNull()?.getOrNull<AppHandOff>() else null
    return if (handOff == null) start(notLeaving) else handOff.launch { start(notLeaving) }
}
