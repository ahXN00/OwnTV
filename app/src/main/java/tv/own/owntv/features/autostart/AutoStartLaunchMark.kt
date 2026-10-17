package tv.own.owntv.features.autostart

import android.content.Context
import java.io.File

/**
 * Tells the app's own process that auto start, in the `:autostart` process, just opened OwnTV.
 *
 * Not an intent extra: a running OwnTV is only brought to the front, and Android hands the activity a
 * new intent, if at all, after it has started and its playback has come back. A small file is shared by
 * both processes and can be read before that ([AutoStartProfileGate]).
 */
internal object AutoStartLaunchMark {

    private const val FILE_NAME = "auto_start_launch"

    fun set(context: Context) {
        runCatching { file(context).writeText("") }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** How long ago auto start opened OwnTV, in milliseconds, and forget it; null if it did not. */
    fun consume(context: Context): Long? = runCatching {
        val file = file(context)
        if (!file.exists()) return@runCatching null
        val age = System.currentTimeMillis() - file.lastModified()
        file.delete()
        age
    }.getOrNull()

    private fun file(context: Context) = File(context.noBackupFilesDir, FILE_NAME)
}
