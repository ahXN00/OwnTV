package tv.own.owntv.features.automation

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Whether a screen that remote buttons from the API must not drive is open on the TV.
 *
 * The keys route presses buttons on whatever is in front, so a paired script could otherwise walk the
 * proxy and DNS settings, open or type over a playlist's or guide's login, export or send a backup that
 * holds every login (Backup, Local sync), or pair another device on the Automation screen, all without
 * the person at the TV choosing to, nor enter or change a PIN or edit a profile (PIN entry, the profile
 * editors, Customize's PIN lock), restore a backup over the profiles, read a service's API key or login
 * (TMDB, OpenSubtitles), or see the pairing PIN that lets a phone send a key, a login or a file. Those
 * screens and dialogs, and the add-a-playlist pages, mark themselves open ([SensitiveScreenMarker]) and
 * the keys route refuses while any is.
 *
 * A count of open markers, not one flag: during navigation the next screen composes before the last
 * one is disposed, so two can be open for a moment. Screens open and close on the main thread while
 * requests read [anyOpen] from IO threads, hence the atomics.
 */
class SensitiveScreens {
    private val open = AtomicInteger(0)

    /** True while at least one sensitive screen is open. */
    val anyOpen: Boolean get() = open.get() > 0

    /** Marks one screen open until the returned handle is closed; closing it twice counts once. */
    fun enter(): AutoCloseable {
        open.incrementAndGet()
        val closed = AtomicBoolean(false)
        return AutoCloseable { if (closed.compareAndSet(false, true)) open.decrementAndGet() }
    }
}
