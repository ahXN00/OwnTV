package tv.own.owntv.features.autostart

/** The moments at which OwnTV may open by itself. */
internal enum class AutoStartTrigger {
    /** The TV finished starting up (BOOT_COMPLETED). */
    BOOT,

    /** The screen came back on after sleep (ACTION_SCREEN_ON, heard by [WakeListenerService]). */
    WAKE,

    /** OwnTV was just updated (MY_PACKAGE_REPLACED): the wake listener has to be started again. */
    APP_UPDATED,
}

/**
 * When auto start acts, as plain decisions over plain inputs so they can be tested without a device.
 *
 * From Android 10 (API 29) an app in the background may not open an activity unless an exemption
 * applies; for an app that the user wants to open itself, the exemption is "Display over other apps"
 * (SYSTEM_ALERT_WINDOW). Without it a launch attempt is silently dropped by the system, so OwnTV does
 * not try, keeps no wake listener running for nothing, and the Settings row says what is missing.
 */
internal object AutoStartPolicy {

    /** The first Android version that blocks background activity starts. */
    const val BACKGROUND_START_LIMIT_SDK = 29

    fun canStartFromBackground(sdkInt: Int, overlayAllowed: Boolean): Boolean =
        sdkInt < BACKGROUND_START_LIMIT_SDK || overlayAllowed

    /** Whether [trigger] should open OwnTV. An option that is off never opens it; an update never does. */
    fun shouldLaunch(trigger: AutoStartTrigger, options: AutoStartOptions, canStartFromBackground: Boolean): Boolean =
        canStartFromBackground && when (trigger) {
            AutoStartTrigger.BOOT -> options.onBoot
            AutoStartTrigger.WAKE -> options.onWake
            AutoStartTrigger.APP_UPDATED -> false
        }

    /** How long after auto start opened OwnTV the activity's start still counts as that launch. */
    const val LAUNCH_MARK_MAX_AGE_MS = 30_000L

    /**
     * The wake listener runs only while it can do its job: on a television (a phone or tablet turns its
     * screen on all day, and opening OwnTV each time would be hostile), with the option on and a launch
     * allowed.
     */
    fun wakeListenerNeeded(options: AutoStartOptions, canStartFromBackground: Boolean, television: Boolean): Boolean =
        television && options.onWake && canStartFromBackground

    /**
     * Whether an activity start that auto start made must ask for the profile PIN again. A running OwnTV
     * comes back where it was, past the gate it passed earlier; nobody chose to open it this time, so a
     * profile with a PIN is locked again, as on a cold start. [launchMarkAgeMs] is null when auto start
     * did not open OwnTV.
     */
    fun regateOnLaunch(launchMarkAgeMs: Long?, authenticatedProfileHasPin: Boolean): Boolean =
        authenticatedProfileHasPin && launchMarkAgeMs != null && launchMarkAgeMs in 0..LAUNCH_MARK_MAX_AGE_MS

    /** Ask for "Display over other apps" only at the moment an option is turned on and it is missing. */
    fun shouldAskForOverlay(before: AutoStartOptions, after: AutoStartOptions, canStartFromBackground: Boolean): Boolean =
        !canStartFromBackground && ((after.onBoot && !before.onBoot) || (after.onWake && !before.onWake))

    /** Whether a Settings row that is [enabled] has to say that it cannot work yet. */
    fun needsOverlayNotice(enabled: Boolean, canStartFromBackground: Boolean): Boolean =
        enabled && !canStartFromBackground
}
