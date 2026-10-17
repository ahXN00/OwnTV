package tv.own.owntv.features.autostart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartPolicyTest {

    private val off = AutoStartOptions()
    private val boot = AutoStartOptions(onBoot = true)
    private val wake = AutoStartOptions(onWake = true)
    private val both = AutoStartOptions(onBoot = true, onWake = true)

    // --- defaults ---

    @Test
    fun bothOptionsAreOffByDefault() {
        assertEquals(AutoStartOptions(onBoot = false, onWake = false), AutoStartOptions())
    }

    // --- canStartFromBackground ---

    @Test
    fun beforeAndroid10_noPermissionIsNeeded() {
        assertTrue(AutoStartPolicy.canStartFromBackground(sdkInt = 28, overlayAllowed = false))
    }

    @Test
    fun fromAndroid10_theOverlayPermissionDecides() {
        assertFalse(AutoStartPolicy.canStartFromBackground(sdkInt = 29, overlayAllowed = false))
        assertTrue(AutoStartPolicy.canStartFromBackground(sdkInt = 29, overlayAllowed = true))
        assertFalse(AutoStartPolicy.canStartFromBackground(sdkInt = 36, overlayAllowed = false))
    }

    // --- shouldLaunch ---

    @Test
    fun optionsOff_neverLaunch() {
        AutoStartTrigger.entries.forEach { trigger ->
            assertFalse(trigger.name, AutoStartPolicy.shouldLaunch(trigger, off, canStartFromBackground = true))
        }
    }

    @Test
    fun boot_launchesOnlyWhenBootIsOn() {
        assertTrue(AutoStartPolicy.shouldLaunch(AutoStartTrigger.BOOT, boot, canStartFromBackground = true))
        assertFalse(AutoStartPolicy.shouldLaunch(AutoStartTrigger.BOOT, wake, canStartFromBackground = true))
    }

    @Test
    fun wake_launchesOnlyWhenWakeIsOn() {
        assertTrue(AutoStartPolicy.shouldLaunch(AutoStartTrigger.WAKE, wake, canStartFromBackground = true))
        assertFalse(AutoStartPolicy.shouldLaunch(AutoStartTrigger.WAKE, boot, canStartFromBackground = true))
    }

    @Test
    fun anUpdate_neverLaunches() {
        assertFalse(AutoStartPolicy.shouldLaunch(AutoStartTrigger.APP_UPDATED, both, canStartFromBackground = true))
    }

    @Test
    fun withoutPermission_nothingLaunches() {
        AutoStartTrigger.entries.forEach { trigger ->
            assertFalse(trigger.name, AutoStartPolicy.shouldLaunch(trigger, both, canStartFromBackground = false))
        }
    }

    // --- wakeListenerNeeded ---

    @Test
    fun wakeListener_runsOnlyForWakeWithPermission() {
        assertTrue(AutoStartPolicy.wakeListenerNeeded(wake, canStartFromBackground = true, television = true))
        assertTrue(AutoStartPolicy.wakeListenerNeeded(both, canStartFromBackground = true, television = true))
        assertFalse(AutoStartPolicy.wakeListenerNeeded(boot, canStartFromBackground = true, television = true))
        assertFalse(AutoStartPolicy.wakeListenerNeeded(off, canStartFromBackground = true, television = true))
        assertFalse(AutoStartPolicy.wakeListenerNeeded(wake, canStartFromBackground = false, television = true))
    }

    @Test
    fun wakeListener_neverRunsOffATelevision() {
        assertFalse(AutoStartPolicy.wakeListenerNeeded(wake, canStartFromBackground = true, television = false))
        assertFalse(AutoStartPolicy.wakeListenerNeeded(both, canStartFromBackground = true, television = false))
    }

    // --- regateOnLaunch ---

    @Test
    fun autoStartLaunch_locksAProfileWithAPinAgain() {
        assertTrue(AutoStartPolicy.regateOnLaunch(launchMarkAgeMs = 0L, authenticatedProfileHasPin = true))
        assertTrue(
            AutoStartPolicy.regateOnLaunch(AutoStartPolicy.LAUNCH_MARK_MAX_AGE_MS, authenticatedProfileHasPin = true),
        )
    }

    @Test
    fun autoStartLaunch_leavesAProfileWithoutAPinAlone() {
        assertFalse(AutoStartPolicy.regateOnLaunch(launchMarkAgeMs = 0L, authenticatedProfileHasPin = false))
    }

    @Test
    fun startNotMadeByAutoStart_neverLocks() {
        assertFalse(AutoStartPolicy.regateOnLaunch(launchMarkAgeMs = null, authenticatedProfileHasPin = true))
    }

    @Test
    fun staleOrFutureLaunchMark_neverLocks() {
        val stale = AutoStartPolicy.LAUNCH_MARK_MAX_AGE_MS + 1
        assertFalse(AutoStartPolicy.regateOnLaunch(launchMarkAgeMs = stale, authenticatedProfileHasPin = true))
        assertFalse(AutoStartPolicy.regateOnLaunch(launchMarkAgeMs = -1L, authenticatedProfileHasPin = true))
    }

    // --- shouldAskForOverlay ---

    @Test
    fun turningAnOptionOn_withoutPermission_asks() {
        assertTrue(AutoStartPolicy.shouldAskForOverlay(off, boot, canStartFromBackground = false))
        assertTrue(AutoStartPolicy.shouldAskForOverlay(boot, both, canStartFromBackground = false))
    }

    @Test
    fun turningAnOptionOff_neverAsks() {
        assertFalse(AutoStartPolicy.shouldAskForOverlay(both, boot, canStartFromBackground = false))
        assertFalse(AutoStartPolicy.shouldAskForOverlay(wake, off, canStartFromBackground = false))
    }

    @Test
    fun withPermission_neverAsks() {
        assertFalse(AutoStartPolicy.shouldAskForOverlay(off, both, canStartFromBackground = true))
    }

    // --- needsOverlayNotice ---

    @Test
    fun notice_onlyForAnEnabledSwitchThatCannotWork() {
        assertTrue(AutoStartPolicy.needsOverlayNotice(enabled = true, canStartFromBackground = false))
        assertFalse(AutoStartPolicy.needsOverlayNotice(enabled = false, canStartFromBackground = false))
        assertFalse(AutoStartPolicy.needsOverlayNotice(enabled = true, canStartFromBackground = true))
    }
}
