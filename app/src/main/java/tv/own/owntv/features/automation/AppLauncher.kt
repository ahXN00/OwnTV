package tv.own.owntv.features.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.MainActivity
import tv.own.owntv.R
import tv.own.owntv.core.brand.AppIconSwitcher

/**
 * Brings OwnTV to the front for the API, the way a home-screen tile does: opens a launcher deep link
 * (or just the app), wakes the TV from sleep or a screensaver ([ScreenWaker]; [EXTRA_WAKE], read by
 * [AutomationWake], keeps the screen on as it comes forward), and shows a short notice so whoever is
 * watching knows the change came from the API.
 *
 * Nothing comes up over a private screen ([SensitiveScreens]): [open] refuses with [PlayResult.Private].
 * Android lets a background app start itself only with "display over other apps". Without it, and with
 * OwnTV not in front, [open] refuses with [PlayResult.Blocked] and nothing changes on the TV.
 */
class AppLauncher(
    private val context: Context,
    private val foreground: AutomationForeground,
    private val notices: AutomationNotices,
    private val nonce: WakeNonce,
    private val sensitive: SensitiveScreens,
    private val waker: ScreenWaker,
) {
    /** True when OwnTV is in front, or may bring itself forward. */
    fun canComeForward(): Boolean = foreground.activity() != null || Settings.canDrawOverlays(context)

    /** Whether the TV screen is on (false while the TV sleeps). */
    fun screenOn(): Boolean = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    /**
     * Opens [link] in OwnTV, or brings OwnTV forward when [link] is null, then shows [notice].
     * When OwnTV is already in front and the screen is on, a null [link] changes nothing but the notice.
     */
    suspend fun open(link: Uri?, notice: String?): PlayResult {
        if (sensitive.anyOpen) return PlayResult.Private
        if (!canComeForward()) return PlayResult.Blocked
        val needsStart = link != null || foreground.activity() == null || !screenOn()
        if (needsStart) {
            val intent = (if (link != null) Intent(Intent.ACTION_VIEW, link) else appIntent())
                .setPackage(context.packageName)
                .putExtra(EXTRA_WAKE, nonce.value)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Log.d(TAG, "open link=${link?.host ?: "app"}")
            // Ends a screensaver too, which would otherwise stay on top of OwnTV.
            waker.wake()
            withContext(Dispatchers.Main) { context.startActivity(intent) }
        }
        notice?.let(notices::post)
        return PlayResult.Started
    }

    /** The launcher activity enabled right now: [MainActivity] itself is disabled while another icon colour is chosen. */
    private fun appIntent(): Intent = Intent().setComponent(AppIconSwitcher.launchComponent(context))

    /** "Remote: playing <title>". */
    fun playing(title: String): String = context.getString(R.string.automation_notice_playing, title)

    /** "Remote: opened <section>", for a section label such as Movies. */
    fun opened(@StringRes label: Int): String = opened(context.getString(label))

    /** "Remote: opened <title>", for a show's name. */
    fun opened(title: String): String = context.getString(R.string.automation_notice_opened, title)

    companion object {
        /** Set on intents the API starts, to [WakeNonce.value]: the activity turns the screen on when it comes forward. */
        const val EXTRA_WAKE = "tv.own.owntv.automation.WAKE"
        private const val TAG = "OwnTVAutomation"
    }
}
