package tv.own.owntv.features.autostart

import android.content.Context
import androidx.core.content.edit

/**
 * Settings › App › Auto start: open OwnTV by itself when the TV starts up, when it wakes from sleep,
 * or both. Device-wide rather than per profile, because both are about the television, not the viewer.
 * Both are off until the user turns them on.
 */
internal data class AutoStartOptions(val onBoot: Boolean = false, val onWake: Boolean = false)

/**
 * Where [AutoStartOptions] are kept. SharedPreferences rather than DataStore: [AutoStartReceiver] reads
 * them synchronously in the small `:autostart` process, which starts nothing else of the app.
 * That process only reads them right after it starts (a boot or an update always starts it afresh), so
 * the copy SharedPreferences caches per process is never a stale one.
 */
internal class AutoStartStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): AutoStartOptions = AutoStartOptions(
        onBoot = prefs.getBoolean(KEY_BOOT, false),
        onWake = prefs.getBoolean(KEY_WAKE, false),
    )

    fun write(options: AutoStartOptions) {
        prefs.edit {
            putBoolean(KEY_BOOT, options.onBoot)
            putBoolean(KEY_WAKE, options.onWake)
        }
    }

    private companion object {
        const val PREFS = "owntv_auto_start"
        const val KEY_BOOT = "on_boot"
        const val KEY_WAKE = "on_wake"
    }
}
